#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -x "$DIR/.venv/bin/python3" ]; then
    exec "$DIR/.venv/bin/python3" "$DIR/ephemeral_pad.py" "$@"
fi
exec python3 "$DIR/ephemeral_pad.py" "$@"
