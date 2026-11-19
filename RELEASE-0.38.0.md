# StreamNest 0.38.0

Projector crash evidence and safer cleanup, late desktop subtitles, and bounded Windows media reads.

- Android saves one redacted, scope-bound Java crash capsule before handing control to Android's original crash handler. The next launch recovers it for ordinary diagnostic upload. Sharing off or leaving a group clears the capsule; no credentials, raw paths or exception messages are recorded.
- Playback attempts now have opaque IDs linking source changes, startup stages and errors. Worker startup exceptions reach the parent journal; periodic samples include worker PSS, worker snapshot age and Java heap usage. A bounded main-thread heartbeat records one sanitized stack per sustained UI stall.
- Unconfirmed torrent-worker cleanup is reported and keeps the cache protected, rather than throwing out of the cleanup executor and terminating the app. This is a verified crash path, not proof of the exact original projector exception.
- Windows media reads use at most256KiB blocks and the pinned disk store's partial-read interface, retaining verified-piece checks, pending-write visibility, backpressure and cancellation. Download piece assembly may still allocate a whole piece.
- Subtitle results arriving after playback starts populate the existing player's language menu and can enable its requested default. Explicit subtitle choices are preserved. Video is not restarted.

The physical projector still needs a normal0.38.0 update and S1E3 retry, followed by paused-download/long-session acceptance. The original TheBeast American Dad subtitle failure was not reproduced; live English lookup/download and current playback succeeded. The late-provider path is tested separately. Windows remains unsigned by owner choice.
