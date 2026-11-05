# StreamNest Desktop 0.13.2

Fixes Install and restart doing nothing on Windows, including installed versions 0.12.0–0.13.1.

- Uses explicit helper readiness instead of the spawn event unsupported by the bundled Node 13 runtime.
- Starts the hidden PowerShell helper independently, waits for the app processes to exit, installs and restarts with the same custom profile.
- Shows preparation status, blocks duplicate clicks and records helper failures.
- Adds a real-runtime shutdown/helper/installer-fixture/restart check to Windows CI.

Older installed versions need this installer once to repair their updater. Settings, subtitle accounts and watch progress are preserved. Android remains 0.13.0.
