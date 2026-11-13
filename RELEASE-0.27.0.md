# StreamNest 0.27.0

Richer technical diagnostics on Android / Google TV / projector and Windows.

- Playback samples every 30 seconds: available peer/download, buffering, memory, frame and audio measurements. Android includes selected codec details, system music volume and storage category; Windows includes native video-buffer headroom and audio decode counters.
- Safe error classes, cause classes, HTTP status and app source locations for catalog, subtitles, updater, torrent startup and sync. Raw messages, full stacks, URLs, personal paths, titles and media positions remain excluded.
- Advertised Android hardware decoder capabilities and best-effort Windows graphics-renderer details. Requested decoder mode is kept separate from proof of active hardware decoding; unavailable details remain unknown.
- Bounded, crash-resistant journal writes and upload byte limits. Fault events are retained ahead of routine samples when trimming. Default-on reporting and the persistent Settings off switch remain available.
- Compatibility correction for reading video dimensions with the bundled legacy desktop player.

Validation: Android unit tests/build/lint, isolated tablet live upload and actual native decoding measurements; Node policy/privacy/queue tests; hidden Windows generated video/audio decoding, live report upload and Settings controls. Full release acceptance is recorded in VALIDATION.md. No new physical projector/TV benchmark or deliberate native crash/ANR test is claimed.

Universal/ARM32/ARM64 Android APKs and standalone Windows installer/portable downloads. Windows remains unsigned. [Diagnostics guide](https://github.com/Shaurya609/StreamNest/blob/main/DIAGNOSTICS.md).
