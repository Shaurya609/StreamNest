# Playback and startup maintenance — 0.31.0

## Changes

- Pause follows explicit user intent even when the decoder reports not-playing during startup/buffering. A late Playing event is paused again, and ready media does not auto-open until the user resumes. The torrent service remains running.

- Android video HTTP reads schedule the forward piece-deadline window on piece transitions, rather than on every small read. Seeking/new requests still establish their own window. Waiting for missing pieces uses monotonic time and observes stream closure across threads.
- Completion and integrity checks run on the engine worker. UI/preload checks read a volatile verified result instead of making synchronous native torrent calls. Downloading continues while video is paused.
- Low Memory playback trimming no longer acquires the series-index monitor on the UI thread. Initial movie-index loading is deferred to the background catalog path, and setup does not acquire its network-held monitor.
- Low Memory browsing cancels an unfinished browse job when backgrounded, releases the rebuildable Home metadata cache and trims unused poster-cache entries. A cancelled browse is reloaded on return; filters, history and recommendations are retained.
- Stable physical low-RAM detection is cached per process; user mode changes are still checked. Dynamic free-memory reporting is not cached. PSS memory sampling moves from the player UI to the diagnostic worker.
- Desktop sync skips rewriting unchanged watch history. New progress still persists and syncs immediately as before; no debounce window is added.
- Optional diagnostics can attach allowlisted main-thread Java locations from Android's ANR report after restart. See DIAGNOSTICS.md. This helps distinguish a remaining native hang from ordinary buffering.

## Focused measurements

These are controlled workloads, not whole-app speed guarantees or a physical-projector reproduction.

- Real PieceStream code reading a deterministic 4 MB file with a controlled torrent-handle adapter delivered identical bytes. The previous per-read policy issued 640 deadline updates for this workload; the transition policy issues 10. This counts deadline updates, not all native calls, and does not test peer availability.
- Native UI regression under held catalog monitors: setup/trim returned in 2 ms rather than waiting for those monitors. A cold-class-initialization issue in the first harness run was corrected by initializing the classes before the contention fixture; that rejected run is not acceptance.
- On the same tablet, 2,000 profile checks with the previous DeviceProfile implementation took 397 ms; cached detection took 13 ms. Other code was held constant for this comparison.
- A bundled 866-title Home cache used about 4 MB Java heap in the isolated fixture, falling to 2 MB after clearing and explicit GC. This measures live Java heap, not guaranteed OS PSS/RSS reclamation.
- Desktop 100 unchanged merges of 200 history records: synchronous file writes 100→0, wall time 145→51 ms and process CPU 203→62 ms in the recorded runs. RSS 49→47 MB is noisy and is not claimed as a memory reduction. A newer record still writes immediately.
- Native local-video pause 31 seconds: 302 UI heartbeats, maximum observed gap 117 ms, paused time retained and playback resumed. This fixture does not recreate a low-peer USB torrent.
- Low Memory native Home regression passes startup/cards, focus, synced history refresh, next-episode selection and navigation; reported Java heap 36 MB in that workload.

## Remaining physical verification

The projector's exact paused/buffering ANR and its separate long native engine-start operation require owner-device retesting. Changes remove measured CPU/UI bottlenecks but do not claim every native hang is fixed. Capture the new sanitized ANR locations before attributing a remaining freeze to RAM or peers. Cold/warm launch, sustained RAM/CPU and external-storage behavior still need comparable physical-projector measurements.

# Performance maintenance — 0.30.0

This is a targeted first pass, not a claim that the projector startup hang is solved or a complete cross-platform RAM benchmark.

## Changes

Android poster loads have an independent browsing generation. Replacing content cancels queued obsolete work; a running download that becomes obsolete does not proceed to bitmap decoding or update the new screen. Reused views are checked before work. The worker checks its memory cache again when dequeuing a request. Existing sampled RGB565 decode sizes, bounded queue/thread count and 3/8 MB memory limits are preserved. Disk thumbnail pruning runs at most once a minute instead of scanning and sorting on every successful poster download. Cache storage may temporarily exceed 128 thumbnails between pruning passes; it is in the disposable application cache.

Windows Home defers artwork for cards outside the viewport margin. IntersectionObserver triggers loading when needed, while a geometry check after DOM insertion handles hidden-window layout checks. Hidden documents and runtimes without the observer retain eager fallback. Cleanup cancels observers and timers. Image failure/alternate lookup and resume bars are preserved.

## Focused measurements

The tablet's isolated native test queued 30 obsolete poster requests and observed zero fetches; a current image then passed real bitmap decoding. The accepted final workload drained in 33 ms (fixture data, not public-network latency). This demonstrates eliminated stale work; it is not an overall startup or RAM improvement percentage.

The desktop regression creates 100 off-screen cards: zero image sources are assigned until visibility, then one assignment for the first intersecting card. The previous implementation assigned all 100 immediately. This measures scheduling, not 100 guaranteed network transfers because browser lazy loading/cache may already defer downloads.

## Still to measure

Cold/warm launch, catalog latency, peak/steady RAM, CPU and request counts on Android and Windows under comparable real workloads. Prioritize the 951 MB RK3562 Android14 ARM32 projector. Its measured engine-start stall is distinct from artwork work: a native startup took 334 seconds and subsequent sources waited behind that worker. A blocked native call cannot be safely interrupted by a Java timeout; safe engine recovery and root-cause work remain open.

## Content acceptance

`node tools/verify-content.cjs reference/standalone-build/0.30.0/StreamNest/src/app` uses packaged production parsers/transports. It reports individual service failures without fabricating labels or timestamps. An isolated Android ContentRegression exercises the native equivalents. Neither test starts a public torrent or claims an English audio dub solely from the language metadata.
