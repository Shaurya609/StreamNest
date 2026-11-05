# StreamNest Desktop 0.13.1

Fixes unreadable update-dialog buttons: Install and restart now uses dark text on a light blue button, and Later/Close use white text on a dark button. Buttons have larger click targets and keyboard-focus outlines.

Verified both buttons' actual computed text/background colors and 42-pixel height in the packaged NW.js runtime. The installer handoff regression passed. This Windows-only fix keeps Android at 0.13.0.
