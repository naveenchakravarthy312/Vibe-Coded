# Vibe-Coded

A cross-platform collection of small, focused, and distraction-free applications for Android, Linux, macOS, and Windows.

The project explores consistent minimalist interfaces across platforms, including matte dark themes, keyboard-first workflows, compact layouts, and lightweight local utilities.

## Projects

### Android

| Project | Purpose |
| --- | --- |
| [`deskterm`](Android/deskterm) | Terminal-inspired Android utility |
| [`omnicalc`](Android/omnicalc) | Mobile calculator interface |
| [`rally-timer`](Android/rally-timer) | Timing utility |
| [`scratchpad`](Android/scratchpad) | Distraction-free notes and scratchpad app |
| [`vibehabits`](Android/vibehabits) | Habit tracking app |

### Linux

| Project | Purpose |
| --- | --- |
| [`Auto-Clicker`](Linux/Auto-Clicker) | Desktop input automation utility |
| [`MegaPad`](Linux/MegaPad) | Expanded text editing utility |
| [`Music-Player`](Linux/Music-Player) | Local music player |
| [`Notepad`](Linux/Notepad) | Minimal text editor |
| [`OmniCalc`](Linux/OmniCalc) | Desktop calculator |
| [`YT-Downloader`](Linux/YT-Downloader) | YouTube download utility |

### macOS

| Project | Purpose |
| --- | --- |
| [`Music-Player`](MacOS/Music-Player) | Local music player |
| [`Notepad`](MacOS/Notepad) | Minimal text editor |
| [`OmniCalc`](MacOS/OmniCalc) | Desktop calculator |

### Windows

| Project | Purpose |
| --- | --- |
| [`WinFix`](Windows/WinFix) | Windows utility collection |

## Design Guidelines

- [Android design system](ANDROID-STYLE.md) — Jetpack Compose themes, typography, layouts, storage, and interaction patterns
- [Desktop design system](DESKTOP-STYLE.md) — zero-chrome layouts, palettes, shortcuts, typography, and desktop integration

## Technology

- **Kotlin / Android:** Jetpack Compose-based mobile applications
- **Python:** Desktop applications and platform utilities

## Language Composition

- Kotlin — 80.5%
- Python — 19.5%

## Getting Started

Each application is maintained independently inside its platform directory. Open the relevant project in Android Studio, or inspect the project folder for its Python entry point and dependencies.

### Android

Open `Android/<project>` in Android Studio, select a device or emulator, and run the app module.

### Linux, macOS, and Windows

Each desktop project may use a different Python framework or dependency set. Enter the project directory and review any bundled setup scripts or dependency files before running it.

```bash
cd Linux/<project>
python3 --help
```

Use the appropriate project-specific instructions or entry point if they are included in its directory.

## Repository Structure

```text
Vibe-Coded/
├── Android/          # Android applications
├── Linux/            # Linux desktop applications
├── MacOS/            # macOS applications
├── Windows/          # Windows applications
├── ANDROID-STYLE.md  # Android design specification
└── DESKTOP-STYLE.md  # Desktop design specification
```

## Contributing

Contributions are welcome. Follow the design conventions in the relevant style guide and keep changes focused on a specific application or shared specification.

Before submitting changes:

1. Work inside the application directory you are changing.
2. Preserve existing platform conventions and style tokens.
3. Test on the target platform when possible.
4. Document new dependencies or setup requirements.

## License

No license is currently specified. Check with the repository owner before reusing or distributing this project.
