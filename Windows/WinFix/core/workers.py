"""Non-blocking QThread workers for shell commands, elevated actions, and Python tasks."""

from __future__ import annotations

import os
import subprocess
import tempfile
import uuid
from dataclasses import dataclass
from typing import Callable

from PyQt6.QtCore import QThread, pyqtSignal

from core.system_info import IS_WINDOWS, is_admin

CREATE_NO_WINDOW = 0x08000000 if IS_WINDOWS else 0


@dataclass
class ShellStep:
    """Represents a single shell or PowerShell command step."""

    command: str
    title: str = ""
    requires_admin: bool = False
    is_powershell: bool = False


def run_streaming_command(
    step: ShellStep,
    log: Callable[[str, str], None],
) -> int:
    """Execute a ShellStep, streaming output to `log(level, text)` and returning exit code."""
    if step.title:
        log("INFO", f"--- {step.title} ---")

    if step.requires_admin and IS_WINDOWS and not is_admin():
        return _run_elevated_step(step, log)

    if step.is_powershell:
        ps_wrapped = (
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
            + step.command
        )
        cmd: str | list[str] = [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            ps_wrapped,
        ]
        use_shell = False
        enc = "utf-8"
        log("CMD", f"powershell> {step.command}")
    else:
        cmd = step.command
        use_shell = True
        enc = "oem" if IS_WINDOWS else "utf-8"
        log("CMD", f"> {step.command}")

    try:
        proc = subprocess.Popen(
            cmd,
            shell=use_shell,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding=enc,
            errors="replace",
            creationflags=CREATE_NO_WINDOW,
        )
        assert proc.stdout is not None
        for raw_line in proc.stdout:
            line = raw_line.rstrip("\r\n")
            if line:
                log("OUT", line)
        rc = proc.wait()
        level = "OK" if rc == 0 else "ERR"
        log(level, f"Exit code: {rc}")
        return rc
    except FileNotFoundError as exc:
        log("ERR", f"Command not found: {exc}")
        return 1
    except Exception as exc:
        log("ERR", f"Execution error: {exc}")
        return 1


def _run_elevated_step(step: ShellStep, log: Callable[[str, str], None]) -> int:
    """Run a single command with UAC elevation and capture its output via temp log files."""
    log("WARN", "Standard user detected — requesting UAC elevation for this action...")
    tmp_dir = tempfile.gettempdir()
    token = f"{os.getpid()}_{uuid.uuid4().hex[:8]}"
    out_file = os.path.join(tmp_dir, f"winfix_elev_{token}.out.log")
    err_file = os.path.join(tmp_dir, f"winfix_elev_{token}.err.log")
    script_file = os.path.join(tmp_dir, f"winfix_elev_{token}.ps1")

    try:
        if step.is_powershell:
            body = (
                f"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n"
                f"try {{\n"
                f"  $out = & {{ {step.command} }} *>&1 | Out-String\n"
                f"  Set-Content -Path '{out_file}' -Value $out -Encoding UTF8\n"
                f"  exit $LASTEXITCODE\n"
                f"}} catch {{\n"
                f"  Set-Content -Path '{err_file}' -Value $_.ToString() -Encoding UTF8\n"
                f"  exit 1\n"
                f"}}\n"
            )
        else:
            escaped_cmd = step.command.replace("'", "''")
            body = (
                f"$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c {escaped_cmd}' "
                f"-NoNewWindow -Wait -PassThru -RedirectStandardOutput '{out_file}' "
                f"-RedirectStandardError '{err_file}'\n"
                f"exit $proc.ExitCode\n"
            )

        with open(script_file, "w", encoding="utf-8") as fh:
            fh.write(body)

        ps_launcher = (
            f"$p = Start-Process -FilePath 'powershell.exe' "
            f"-ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"{script_file}\"' "
            f"-Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode"
        )
        log("CMD", f"[UAC Elevated] {step.command}")
        proc = subprocess.run(
            ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps_launcher],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            creationflags=CREATE_NO_WINDOW,
        )

        for path, lvl in ((out_file, "OUT"), (err_file, "ERR")):
            if os.path.exists(path):
                with open(path, "r", encoding="utf-8", errors="replace") as fh:
                    for raw_line in fh:
                        line = raw_line.rstrip("\r\n")
                        if line:
                            log(lvl, line)

        if proc.stderr and proc.stderr.strip():
            log("ERR", proc.stderr.strip())

        rc = proc.returncode
        log("OK" if rc == 0 else "ERR", f"Elevated exit code: {rc}")
        return rc
    except Exception as exc:
        log("ERR", f"UAC elevation cancelled or failed: {exc}")
        return 1
    finally:
        for path in (out_file, err_file, script_file):
            try:
                if os.path.exists(path):
                    os.remove(path)
            except OSError:
                pass


class StepsWorker(QThread):
    """Runs a sequence of ShellSteps in a background QThread."""

    log_signal = pyqtSignal(str, str)
    finished_signal = pyqtSignal(bool, str)

    def __init__(self, task_name: str, steps: list[ShellStep], parent=None):
        super().__init__(parent)
        self.task_name = task_name
        self.steps = steps

    def run(self) -> None:
        def _log(level: str, msg: str) -> None:
            self.log_signal.emit(level, msg)

        _log("INFO", f"=== Starting: {self.task_name} ===")
        all_ok = True
        for step in self.steps:
            rc = run_streaming_command(step, _log)
            if rc != 0:
                all_ok = False

        if all_ok:
            _log("OK", f"=== Completed: {self.task_name} ===")
            self.finished_signal.emit(True, f"{self.task_name} completed successfully.")
        else:
            _log("WARN", f"=== Finished with warnings/errors: {self.task_name} ===")
            self.finished_signal.emit(False, f"{self.task_name} finished with non-zero exit code.")


class CallableWorker(QThread):
    """Runs an arbitrary Python callable `fn(log_cb)` in a background QThread."""

    log_signal = pyqtSignal(str, str)
    finished_signal = pyqtSignal(bool, str)

    def __init__(
        self,
        task_name: str,
        fn: Callable[[Callable[[str, str], None]], tuple[bool, str]],
        parent=None,
    ):
        super().__init__(parent)
        self.task_name = task_name
        self.fn = fn

    def run(self) -> None:
        def _log(level: str, msg: str) -> None:
            self.log_signal.emit(level, msg)

        _log("INFO", f"=== Starting: {self.task_name} ===")
        try:
            ok, summary = self.fn(_log)
            _log("OK" if ok else "ERR", f"=== {summary} ===")
            self.finished_signal.emit(ok, summary)
        except Exception as exc:
            _log("ERR", f"{self.task_name} failed: {exc}")
            self.finished_signal.emit(False, f"{self.task_name} failed: {exc}")
