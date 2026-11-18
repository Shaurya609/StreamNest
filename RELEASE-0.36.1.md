# StreamNest 0.36.1

Windows buffered-playback recovery and compatibility-audio reliability.

- Detect a visible player whose video clock and decoded frames stop for 15 seconds despite at least five seconds of reported video buffer. Reopen the same source once, preserving the exact episode file, cache folder and playback position. Paused, seeking, hidden and ending playback do not trigger this recovery. Repeated failures offer another source rather than looping.
- Coordinate compatibility audio with video buffering, seeking and user pauses. Preserve a pause during preparation, stop silent playback after an audio-helper failure, limit automatic audio restarts, and recover a buffered playback interruption through the same bounded reopen path.
- Report compatibility-audio readiness, buffer, drift and decoder recovery separately from native audio counters, including samples while the video clock is stopped. Existing diagnostic sharing controls and filtering apply.
- Android version alignment only; Android playback behavior is unchanged.

Validation and source-dependent limitations are recorded in VALIDATION.md. Windows remains unsigned for this personal project. These changes address the observed buffered-stall failure mode; they do not guarantee peer availability or establish the original decoder interruption’s underlying cause.
