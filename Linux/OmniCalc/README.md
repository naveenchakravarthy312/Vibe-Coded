================================================================================
OMNICALC — ZERO-CHROME KEYBOARD-DRIVEN MATH & UNIT SCRATCHPAD (LINUX & MACOS)
================================================================================

OmniCalc is an ultra-minimalist, zero-chrome, distraction-free mathematical and
unit-conversion scratchpad for Linux and macOS. Modeled directly after
Scratchpad, 100% of the window is a flat #141414 matte canvas with zero visible
buttons, toolbars, menu bars, or scrollbars. Only your math and live inline
results are visible.

--------------------------------------------------------------------------------
LIVE MATH, VARIABLES & UNIT CONVERSION SYNTAX
--------------------------------------------------------------------------------
Type any expression on a line to see its live right-aligned `= result`:

  2 + 2                                                    = 4
  sqrt(144) + 5!                                           = 132
  sin(30)^2                                                = 0.25
  200 * 15%                                                = 30
  1E12 + 5                                                 = 1E12

Variables & Line Aggregations:
  r = 5                                                    = 5
  pi * r^2                                                 = 78.53981634
  + 10                                                     = 88.53981634
  ans * 2                                                  = 177.07963268
  sum                                                      = 480.86944902
  avg                                                      = 53.42993878

11-Category Natural Unit Conversions (`in`, `to`, `as`, `->`):
  100 km in mi                                             = 62.137119 mi
  100 C to F                                               = 212 °F
  0 C to K                                                 = 273.15 K
  2.5 kg in lb                                             = 5.511557 lb
  1024 MiB in GiB                                          = 1 GiB
  30 mpg in L/100km                                        = 7.840486 L/100km

--------------------------------------------------------------------------------
KEYBOARD SHORTCUTS (Use Ctrl on Linux / Cmd ⌘ on macOS)
--------------------------------------------------------------------------------
MATH & CLIPBOARD
  Ctrl + Enter         Commit / append ` = <result>` directly onto the active line
  Ctrl + C             Copy the current line's result when no text is selected
                       (or copy selected text normally when a selection exists)
  Ctrl + Shift + C     Copy all lines together with their evaluated `= result` values
  Ctrl + D  /  Alt + D Toggle trigonometry angle mode between DEG and RAD
  Ctrl + W             Clear / wipe the math buffer (undoable via Ctrl + Z)

FILE & SAVED CALCULATIONS LIBRARY
  Ctrl + N             New Math Pad: resets the canvas to a fresh, empty buffer
  Ctrl + O             Open a .txt, .calc, .md, or Android OmniCalc .json file
  Ctrl + S             Save current calculation sheet to the internal SQLite library
                       (or update the active file in place)
  Ctrl + Shift + S     Save As File (.txt math sheet or universal .json history)
  Ctrl + L  /  Ctrl + H
                       Toggle the zero-chrome Saved Calculations / History Library:
                         - Type to filter saved calculations
                         - Up / Down arrow keys: navigate list
                         - Enter: load selected calculation into the canvas
                         - Ctrl + P: pin / star selected calculation
                         - Ctrl + E: export selected calculation to .txt
                         - Delete / Ctrl + D: delete selected calculation
                         - Ctrl + Z: undo deletion
                         - Escape: return to math canvas

FIND, TYPOGRAPHY, THEME & WINDOW
  Ctrl + F             Toggle minimal inline Find bar at the bottom
  Ctrl + =  /  Ctrl + +  /  Ctrl + Mouse Wheel Up
                       Increase font size (+1pt)
  Ctrl + -  /  Ctrl + Mouse Wheel Down
                       Decrease font size (-1pt)
  Ctrl + 0             Reset font size (14pt default)
  Ctrl + M             Toggle font family (Monospace <-> Sans-Serif)
  Ctrl + Shift + D     Cycle surface theme:
                         Matte Dark (#141414) -> Pure AMOLED (#000000) -> Crisp Light (#f2f2f0)
  Ctrl + I             Cycle bottom-right telemetry corner:
                         Hidden (100% pure math default) -> Minimal -> Full (angle · sum · avg)
  F11 / Ctrl + Shift + F
                       Toggle Frameless Window Mode (Alt+Drag or 24px margin drag to move,
                       8px edge drag to resize)
  F1  /  Ctrl + /      Toggle zero-chrome Keyboard Shortcuts & Syntax Reference
  Escape  /  Ctrl + Q  Close active overlay/prompt, or wipe memory and exit immediately
================================================================================
