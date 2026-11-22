#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -x "$DIR/.venv/bin/python3" ]]; then
    exec "$DIR/.venv/bin/python3" "$DIR/music_player.py" "$@"
elif [[ -x "$DIR/.venv/bin/python" ]]; then
    exec "$DIR/.venv/bin/python" "$DIR/music_player.py" "$@"
fi
exec /usr/bin/env python3 "$DIR/music_player.py" "$@"
