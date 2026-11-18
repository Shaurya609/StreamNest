StreamNest 0.35.0 — Sprint 2: engine recovery and startup

Android / Google TV:
- Torrent sessions run in app-owned worker processes, separate from the player. A stalled native worker can be replaced without restarting StreamNest.
- Independent control snapshots and native-progress deadlines detect failed startup, lost workers and blocked polling. Cancellation has a bounded graceful-close window, then terminates only the verified app-owned worker if necessary.
- A new writer cannot reuse a cache owned by an unconfirmed old worker. Native preloading keeps a second slot, and a prefetched worker is adopted when the episode changes.
- Video header/footer pieces receive startup priority. Startup hints are retired when decoder reads begin, so they cannot compete with exact playback requests. A saved resume timestamp adds an approximate download-priority hint; the actual player still seeks by timestamp.

Windows:
- Shutdown waits are bounded. An overdue close keeps its cache protected; another source can proceed once the real close callback arrives.
- Late startup and metadata callbacks from superseded attempts are ignored.
- Local stream-server startup waits for an actual listening socket, can fall back from an occupied port and has a fixed timeout.
- Header/footer and resume-window pieces are prioritized.

Diagnostics add a group-scoped opaque source comparison ID and native telemetry age. The ID uses a group-derived key; reports still exclude raw torrent hashes, links, titles and playback positions. The existing sharing off switch remains available.

Validation includes real Last of Us playback, an intentionally frozen worker recovering in about 14 seconds without restarting the player process, and real episode-2 warm-worker reuse with previous-cache removal after video. The physical projector's original JNI hang and USB storage scenario still need confirmation on that device. No general startup-speed percentage is claimed. Windows remains unsigned.
