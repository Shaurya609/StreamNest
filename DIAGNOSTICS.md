# Optional device diagnostics

Diagnostics are on by default. Reports upload after the device joins a sync group. Use **Share device diagnostics** in Android Playback & storage, or **Device diagnostics** in Windows Settings to turn sharing off or back on. Your sharing choice belongs to this installation. An explicit off choice survives restarts and group changes. Reports are uploaded only while connected to a sync group. The owner cannot remotely enable it.

The private [owner dashboard](https://streamnest-watch-sync.streamnest-shaurya609.workers.dev/admin) uses the existing owner access key. Select a device's Diagnostics button to see device details, session timelines, warning/error filters, JSON export and deletion. The owner can read and export shared reports. Watch progress remains separately encrypted.

Reports include app/OS/CPU architecture and model, RAM/process-memory snapshots, graphics API when available, technical playback transitions, torrent startup stages/timings, stalls, sync failures and Android subtitle/cache failures. GPU hardware is shown as unavailable when it cannot be identified reliably. Memory describes this process, not every Windows subprocess. Events deliberately exclude content titles, episode identifiers, media positions, passwords, pairing keys, torrent magnets, full URLs, raw exception messages and personal paths. These are structured events, not a copy of device system logs.

Each installation keeps at most 250 local events in app-private storage. Uploads run separately from playback, at most 25 events per minute, with bounded network timeouts. Offline reports retry automatically on a later launch. The backend validates fields again, permits only registered group devices, retains events for seven days, and caps storage at 500 events per device / 10,000 globally. An owner export is a separate local copy and does not expire automatically.

Turning sharing off clears the local journal and requests server deletion. If offline, server reports expire within seven days. Deleting reports from the dashboard does not disable sharing: a consenting device can send new events later. Android OS exit reasons may explain an earlier crash or low-memory kill on the next launch. A frozen native process cannot upload while frozen; its last persisted startup stage may be the final available evidence. Hard crashes can lose the newest queued events. No diagnostics promise captures every failure.

Deployment: apply the additive `sync/diagnostics-schema.sql` migration before deploying the worker. Existing encrypted sync schema and payloads are unchanged. No owner key is bundled in either application.


## Extended reports in 0.27.0

During playback, a sample is recorded at most every 30 seconds per player. Android uses cached torrent counters, VLC frame/audio counters and selected track metadata; Windows uses native video-element measurements. Samples include connected peers and download speed when available, buffer percentage (Android) or buffer headroom (Windows), free/process RAM, frame counts/loss, player mute/volume and audio decode counters. Android also includes selected audio codec/channels/sample rate, video codec/dimensions, system music volume and cache storage category. No media position is uploaded; Windows uses the current position only to compute buffer headroom locally.

Windows can report its WebGL graphics renderer, using one small context which is released afterward. This is graphics-renderer information, not proof of the video decoder in use. Android reports advertised hardware decoder availability for H.264/HEVC/AV1 on supported Android versions, without creating another graphics context. Decoder mode means the requested software/auto setting; actual hardware decoder selection and GPU model remain unavailable where the platform does not expose them.

Failure records now include service/operation, exception and cause classes, numeric HTTP status when recognized, and bounded app source locations. Source locations contain only trusted class/module names and line numbers. Raw messages, full stacks, URLs, file paths and credentials remain excluded. Transport/parse and snapshot-verification catalog failures, subtitle search/login/download, update check/download/verification/install, native torrent startup and sync failures have hooks. Backend/OS failures outside these entry points may still leave only an earlier stage or an exit reason.

The journal remains capped at 250 events and roughly 110 KB. Routine samples are discarded before fault events when trimming a full journal. Upload batches also have a byte limit below the server's request limit. Windows journals and saved off choices are written using temporary-file replacement; Android uses its atomic-file journal. Optional measurements may be absent, and reports cannot prove that sound reached speakers or that a future source will remain available. No 1 GB projector overhead benchmark or deliberate native-crash/ANR stress test is claimed.

## Separate watching-activity sharing

Version 0.28.0 adds an independent default-on switch for title/episode/state reports to the owner dashboard. Diagnostic events still exclude content identity. Turning off diagnostics does not turn off watching activity, or vice versa. See [watching activity and owner controls](WATCH-SYNC.md#owner-controls-and-watching-activity-0280) for privacy, retention and deletion behavior.

## Android ANR locations — 0.31.0

With diagnostic sharing enabled and a paired group, a subsequent launch may read up to the first 256 KiB of an OS-provided ANR report from the last seven days. Only up to 12 allowlisted main-thread Java method/line locations survive sanitization; native Java entry methods may use line 0 when the OS provides no line number. Raw reports, thread names, native addresses, process metadata, filenames, content titles, URLs and credentials are not saved or uploaded. Missing OS traces are allowed. The same disable switch and seven-day server retention apply.

## 0.35.0 source correlation and worker freshness

`mediaId` is an opaque HMAC-SHA256 of a validated v1 torrent infohash using a separate key derived from the sync-group secret. Identical sources can be compared within a group, but the ID differs across groups and cannot be looked up using only a public torrent hash. Raw hashes, magnets, title/episode IDs and positions are not added to technical reports. Android reports `telemetryAgeMs` so a responsive control process cannot make stalled native counters look fresh. Existing event frequency, retention limits and off controls are unchanged. Worker recovery is reported by the main app; planned child-worker exits are not counted as main-player crashes. No schema migration is required for these additive allowlisted fields.

Memory comparison scope after isolation: Android `processMemoryMb` describes the main player process. It does not aggregate the new native worker processes. Sprint 3 benchmarks must measure the main process plus active/preload worker PSS and graphics/mapped buffers; comparing 0.35.0 main-only memory directly with the earlier single-process total would be invalid. `mediaId` compares torrents within a group; matching a season pack does not establish that two devices selected the same member.

## Windows buffered playback recovery (0.36.1)

Compatibility audio reports its own ready state, buffered milliseconds, absolute A/V drift, helper phase and decoded bytes. Native audio-track counts remain separate. Video readiness and document visibility distinguish buffering/background conditions. A bounded `player.recovery` event records a decoder reopen. Thirty-second samples continue while the video clock is stuck; player disposal removes the sampling timer. Measurements contain no source URLs, titles or raw clock positions and retain existing sharing controls.

Windows0.36.2 samples startup before the player opens: metadata, local-server and video-buffer stage, elapsed time, attempt, peers and download rate. `torrent.stalled` records a bounded startup timeout. Metadata failures carry only an allowlisted provider label (Cinemeta, TVmaze, AniZip, regional or other), never the URL, query or title. Optional artwork/Home404 responses are cached misses; required requests and other service failures remain errors.


## Plain-language owner reports

The owner dashboard shows a summary of what the newest session in the selected view last reported, what the evidence supports, and suggested next steps. Each timeline event has a readable explanation, with the original event and payload under expandable Technical details. Device memory, app version and Low Memory mode appear as labeled facts. Reports older than two minutes are marked as potentially stale; upload time is shown separately from event time. The session filter changes the summary scope, while the level filter only changes the event list. Earlier failures are kept as history and are not automatically treated as current failures after later playback reports.

Explanations are deterministic browser-side interpretations of existing sanitized reports, not additional AI-service requests or proof of root cause. Peers, free RAM, download percentage and player buffer percentage do not independently establish a streaming failure or recovery. Episode-file matching, cache access, unconfirmed worker cleanup, subtitle/catalog/sync/update errors and playback snapshots have specific explanations. Reload the owner page and sign in if needed; no Android or Windows app update is required. Existing owner authorization, data retention, sharing controls and raw JSON export remain in place.
