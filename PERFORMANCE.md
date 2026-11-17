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
