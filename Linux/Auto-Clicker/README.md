# Auto Clicker for Linux (Wayland & KDE Plasma)

An ultra-minimalist, zero-chrome GUI autoclicker for Linux written in Python with zero external `pip` dependencies required.

## Design & Surface Specifications
1. **Color Palette & Surface:**
   - **Matte Dark Canvas (`#141414`):** Low-glare, flat charcoal background filling 100% of the window with zero borders, dividers, or bevels.
   - **Soft Off-White Foreground (`#e0e0e0`):** High-legibility text without harsh pure-white eye strain.
   - **Muted Slate Selection (`#323842` with `#ffffff` text):** Cool-toned highlight when selecting text inside input fields.
   - **Whisper-Quiet Metadata (`#585858`):** Low-contrast peripheral status and click counter anchored in the bottom-right corner (`idle · 0 clicks · F6`).

2. **Layout & Spatial Geometry:**
   - **Zero Chrome:** No menu bars, toolbars, scrollbars, borders, or boxed buttons—pure interactive typography.
   - **Breathing Room (`24px` Viewport Padding):** Generous `24px` internal margin around all four edges of the canvas.
   - **Optional Frameless Mode (`F11` or `Ctrl+Shift+F`):** Strips away the OS titlebar completely, leaving a floating `#141414` rectangle that can be moved (`Alt+Drag` or dragging the `24px` margin) and resized from any of its 8 edges/corners.

3. **Typography & Cursor Feel:**
   - **Modern Monospace Stack:** Automatically detects and loads `JetBrains Mono`, `Fira Code`, `Cascadia Code`, `Iosevka`, `Source Code Pro`, or system monospace at `13pt` default size.
   - **On-the-Fly Scaling:** Scale typography dynamically with `Ctrl+=` / `Ctrl+-` / `Ctrl+0` / `Ctrl+Wheel`.
   - **Crisp Caret & Plain-Text Paste:** `2px`-wide blinking cursor (`1000ms` cycle: `500ms` on / `500ms` off) and sanitized plain-text-only clipboard pasting.

## Running
```bash
./autoclicker.py
# or
python3 autoclicker.py
```
