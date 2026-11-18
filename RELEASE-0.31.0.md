# StreamNest 0.31.0

Android playback/startup optimization and lighter desktop sync.

- Respect Pause during buffering/startup; continue downloading without unexpectedly starting video.
- Reduce repeated torrent piece-deadline updates; keep native completion checks off the player UI thread.
- Avoid Low Memory player startup waiting for a catalog network lock. Defer movie-index loading, release rebuildable background metadata and cache stable device-profile detection.
- Move expensive memory sampling off the player UI. Keep pause-to-download behavior, preload, cache cleanup, subtitles, resume and sync.
- Skip desktop disk writes when synced watch history has not changed; persist new progress normally.
- Include sanitized Android main-thread ANR locations in optional diagnostics after restart, without raw reports or content metadata.

Validation includes native deterministic streaming-byte/completion checks, held-lock UI checks, a 31-second native video pause/resume, Low Memory Home/navigation and scoped before/after CPU/IO workloads. See PERFORMANCE.md and VALIDATION.md.

The physical projector paused/buffering freeze and long native engine startup still require retesting; this release removes identified bottlenecks without claiming every native hang is solved. Peer availability remains independent.
