# StreamNest 0.17.0 — Sprint 1

Desktop player Previous episode and Next episode controls are larger and include labels. Android and desktop show a skip icon plus Skip Intro / Skip Credits text, an animated five-second progress bar, and a Settings switch to disable automatic skipping.

Short-code input on Windows now normalizes full-width characters, invisible pasted separators and common dash variants before validation, and distinguishes incomplete short codes from recovery keys.

Progress sync uploads on pause, seek completion and player exit, queues a final upload when an important event occurs during another sync, and reduces periodic sync throttling to ten seconds. Completed episodes are protected from a zero-position teardown save.

Verification includes Android fixture playback with labeled/manual/automatic skip and Settings disable, pause/settings-return checks, desktop countdown/pause/Settings controls and a pending-upload timestamp regression. Windows clipboard-input cases are covered by regression tests; the exact original MSI input remains unknown. Network outages and immediate forced process termination can still delay sync.
