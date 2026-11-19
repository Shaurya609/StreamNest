# StreamNest 0.37.0

Episode handoff reliability and the first sustained-memory pass.

- Android and Windows: an eligible episode clock stuck within100ms of its validated duration for3 seconds completes through the normal next-episode flow. Explicit pauses and ordinary earlier buffering do not trigger it. Completion is saved after validation; duplicate/late end callbacks cannot mark a new episode finished.
- Windows: stop pending compatibility-audio resumes at episode end, validate adjacent episode identity, keep next playback at the beginning, inherit tracker/encryption policy in preload, and try bounded alternate preload sources after metadata failure. Previous cache stays protected until next video renders.
- Release player subscriptions, timers and old client references. Skip inactive optional Watchlist refreshes and handle failures. Query Windows free space through native asynchronous filesystem calls, with bounded legacy fallback.
- Bound Windows torrent read caches to2 pieces for active playback and1 for preload instead of the library default20. Large-piece assembly/HTTP reads still use substantial memory; no overall Windows RSS reduction is claimed.
- Android Low Memory mode uses normal file I/O instead of mapping video files, caps disk workers at2, and sends steady active/preload status at1s/2s intervals while retaining500ms startup feedback. Full-power active settings, user preferences and preload opt-in are retained.

Validation includes real Last of Us S1E2-to-S1E3 on a separate tablet app with preload off/on, real American Dad S2E1-to-S2E2 full-download preload/cache handoff on hidden Windows, pause/resume and cleanup, and completion/cache/resource regressions. Android warm preparation was initiated directly to avoid downloading an entire public episode; the normal full-download preload trigger was exercised on Windows. See VALIDATION.md and PERFORMANCE.md for measurements and limits.

The exact original TheBeast replay was not reproduced with preload off. The fixes address verified endpoint/preload failures; owner confirmation and long physical-projector sessions remain pending. Peer availability remains source-dependent. Windows stays unsigned by owner choice.
