# StreamNest 0.26.0

Optional device diagnostics for Android / Google TV / projector and standalone Windows.

- Default-off sharing switch in Settings. Connect the device to a sync group first; consent is per device and resets when changing groups or disconnecting.
- Technical playback and startup events, stalls and failures, with CPU/OS/app/RAM details. Reports exclude content titles, media positions, credentials, pairing keys and personal paths.
- Private owner dashboard: select a device, inspect its session timeline, filter warnings/errors, export JSON or delete reports. The existing owner access key is required.
- Bounded local reports and background uploads, with offline retry. Seven-day server retention. Turning sharing off clears local reports and requests server deletion.
- Android torrent phase timings, buffering, subtitle/cache failures and available OS process-exit reasons. Windows playback transitions, sync failures and runtime error classes. GPU data is marked unavailable when it cannot be reliably identified.

Validation: 134 automated Node checks; Android unit tests/build/lint; isolated tablet consent control, live report upload and opt-out cleanup; hidden Windows consent, live upload, keyboard isolation and opt-out cleanup; owner dashboard session/filter/export/delete UI checks and authenticated live owner retrieval/deletion. No credentials or owner keys are bundled. Frozen processes cannot report until they recover or relaunch; hard crashes may lose the newest queued events.

Android universal, ARM32 and ARM64 APKs. Windows standalone installer and portable ZIP. Windows remains unsigned; signed auto-update verification is unchanged. [Diagnostics guide](https://github.com/Shaurya609/StreamNest/blob/main/DIAGNOSTICS.md).
