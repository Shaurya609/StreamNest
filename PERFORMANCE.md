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

## Engine recovery and startup — 0.35.0

Native torrent sessions move into two app-owned, non-exported Android service processes. The player, preferences, encrypted sync and cache bookkeeping stay in the main process. The second slot preserves optional preloading; claiming it promotes the existing worker rather than recreating its session. Only scalar snapshots cross the process boundary; the player no longer reads native torrent handles for status or subtitle fingerprints.

Worker handshake/control freshness is bounded at 10 seconds. Native initial-stage limits remain explicit (e.g. session start 45 seconds); after startup, 20 seconds without worker progress is distinct from fresh control snapshots. Close allows 3.5 seconds for graceful resume-data/session shutdown, then kills only a PID verified as the app's leased torrent worker, followed by a bounded death confirmation. Confirmed worker death is required before its lease/cache is released. A second close cannot turn an unconfirmed death into success. App and worker PIDs cannot be confused. Managed worker exits are excluded from main-player crash reports.

Startup now schedules the verified header/footer windows first, with a lower-deadline-priority 4 MiB approximate resume window when duration is known. These are download hints; timestamp seeking and integrity checks remain authoritative. Writable-storage probes run in the recoverable worker. Parent-side cache bookkeeping/deletion still uses OS filesystem calls; a hardware/filesystem-wide USB stall is not claimed solved by killing a torrent worker.

Windows preserves a real close barrier after a UI timeout. A timeout does not authorize deleting old cache or adding another writer. A late successful close unblocks the next request. Binding the local video server has a 4-second limit and one fallback to an OS-selected port. Source-generation guards stop old callbacks from modifying a replacement request. Header/footer and resume-window pieces get higher priority than the whole-file download.

Native measured acceptance, isolated tablet and real media: a deliberately SIGSTOP-frozen torrent child was replaced automatically in 14,297 ms (repeat 14,196 ms), with old-writer death confirmed, a different worker PID and the original player PID retained. The recovered Last of Us S1E1 decoded beyond 10 seconds with selected audio; UI heartbeats continued, max recorded gaps 553 ms and 473 ms respectively. The repeat then warmed a real S1E2 source in the second process, reused that same PID on episode transition, retained the previous cache until new video, then purged it. The warm-worker test initiated prefetch directly to avoid waiting for an entire public torrent; it does not claim a full-duration binge/network benchmark or a physical-projector reproduction. Public peer rates and sources differ, so there is no claimed across-the-board first-frame speedup percentage.

Final resume-priority acceptance: bootstrap hints are retired on the first decoder GET; subsequent exact ranges are authoritative. An isolated Android 0.35.0 test saved 94,857 ms for real Last of Us S1E1, opened its HEVC source and advanced to 104,481 ms with English selected. An earlier hint-policy run remained at the saved seek time and was not accepted; no success is inferred from that run. Windows 0.35.0 local pinned-source checks required repeated frame and time advancement, not initial frames alone: Last of Us S1E1 advanced past its saved position and showed the expected unsupported-Dolby warning; American Dad S2E1 and Black Clover 108 advanced with English AAC decoded. The first unpinned Auto attempt and an unavailable lookup were not counted as sustained-playback passes. Packet rates, cache state and peers vary, so these are functional acceptance checks rather than a comparable startup-speed benchmark.

## Episode handoff and memory pass - 0.37.0

Android Low Memory mode sets the native disk-worker budget to2 (default10), disables video-file mappings with `mmap_file_size_cutoff=2147483647` and normal writes, and keeps steady status snapshots at1s active/2s preload,500ms startup. Promotion restores the appropriate active budget on the worker thread. The cutoff uses16KiB blocks; see the [upstream settings reference](https://www.libtorrent.org/reference-Settings.html#mmap_file_size_cutoff). Peer limits, integrity checks and playback capabilities are retained. This shifts file caching outside the app mapping; it does not eliminate the operating-system page cache.

Separate real-source observations on the tablet recorded a mapped worker at653,116KiB PSS/618,252KiB Private Other and a normal-I/O worker at37,023KiB/1,960KiB. Transfer positions/cache state differ: these are not a controlled percentage RAM reduction or a whole-device saving. Native idle-session tests read back budgets10/2 and the full cutoff; idle thread count remained17 in both runs, so no immediate eight-thread saving is claimed. Actual Low Memory Last of Us S1E2-to-S1E3 off/on transitions passed, with main-process PSS205,963/204,843KiB and maximum UI heartbeat gap459ms. Android warm-worker preparation was initiated directly, not by a completed whole-episode download. This is a physical tablet in Low Memory mode, not the1GB projector or a hard1GB memory sandbox.

Windows player-owned subscriptions and all three player timers are released on disposal. Retired cache records discard old client/torrent references and deduplicate paths without deleting active data. A100-lifetime ownership regression returns window subscriptions to baseline; actual two-episode closure left no player subtitle subscription. Native free-space queries took1/0/0ms versus231/172/179ms for the former three PowerShell calls on this host. This is a filesystem-check measurement, not a whole-app launch speedup.

Read caches are capped at2 pieces active/1 preload (library default20), verified on real128MiB-piece packs. Piece assembly and media range readers can still allocate large buffers: a handoff RSS sample was1,277,612,032bytes. No overall Windows RSS improvement is claimed; bounded range reads/large-pack peak memory and comparable cold/warm CPU/startup profiling remain work. Native/mapped/graphics measurements over long physical-projector sessions also remain open.

A fully downloaded real American Dad S2E1 triggered preload for S2E2. Real end playback reused that cache and purged the previous directory only after next video. Last of Us's slower public source was not counted as a full-download preload pass. Endpoint checks additionally observed Android clocks4ms short of duration without EndReached; a3s/100ms tail fallback now uses normal completion validation. Early EOF does not save a fake completion. The original TheBeast replay remains an owner retest.


## 0.38.0 bounded Windows reads

Verified-piece media streams now request at most256KiB through the pinned filesystem store range API. ImmediateStore stays above this path so verified data pending disk flush remains available. Full-piece reads for other purposes retain the original path. Range boundary, backpressure, unverified-piece and cancellation tests apply. The separate-process synthetic128MiB-piece/16MiB-range benchmark recorded maximum reads128MiB versus256KiB and peak external memory270452979 versus26396991bytes. These measurements do not establish a whole-app or projector RAM percentage saving. Download piece assembly and other full-piece consumers remain open. Native long-session projector measurements remain pending.


## 0.39.0 measured follow-up - 19 November 2026

### Changes and focused measurements

Windows whole-piece caches are now byte-budgeted at32MiB active/8MiB preload rather than permitting two arbitrary-sized pieces. An actual pinned filesystem/cache/immediate-store test of one128MiB piece retained134217728bytes before and0 after; both reads delivered134217728bytes with the same sampled-byte checksum. The existing256KiB media reader remains in place. A verified-write queue gates new download requests at64MiB active/32MiB preload, releasing them after the existing disk callback. Already-in-flight blocks/hash buffers may exceed this threshold. Nothing discards unverified progress or bypasses integrity checks. Controlled delayed/error write tests and preload-to-active promotion pass. The live cached playback had no pending writes, so it is not a physical slow-USB backpressure test.

Home's3-second poll now compares a progress revision. At500 records,100 old stringify/clean/sort checks took89.97ms wall/125ms process CPU; revision checks took0.017ms/below the CPU counter's resolution. This is a focused poll workload, not a whole-app speedup. Unchanged remote merges keep their revision; real local/remote/removal changes increment it. Diagnostic fallback wakeups are2/minute instead of60, while timeupdate/other playback events remain active.

Separate tablet instrumentation at2000 stored records (500 survive the existing sync sanitizer):25 previous startup duration lookups via full records took3965ms wall/4971ms process CPU;25 selected-record lookups took2ms/4ms. The selected record was within the retained500. Removal-marker reads, numeric legacy values and malformed records passed.2000 previous scope-hash calls took476ms/749ms CPU; cached identical calls took5ms/6ms. These counts isolate specific work and do not predict projector latency.

### Whole-app profiles and limits

Same host, published0.38.0 Windows payload versus the same runtime with only the changed modules, fresh isolated profile then second launch, same84-card Home,35-second observation, all owned application processes:

| Workload | Before readiness ms | After readiness ms | Before peak private commit bytes | After peak private commit bytes | Before CPU seconds | After CPU seconds |
|---|---:|---:|---:|---:|---:|---:|
| First profile launch |2801|3197|568348672|595697664|8.391|8.469|
| Second profile launch |3261|2986|551092224|554803200|8.125|7.453|

Private commit is not resident physical RAM/PSS. First-profile launch is not an OS disk-cache flush. There were no window/promise errors in accepted runs. Earlier mixed-source/packaging-configuration attempts were rejected. Public metadata/artwork and host activity vary: no consistent overall Windows startup/CPU/RAM improvement is established by these two short observations.

Unlocked tablet, separately installed verification package, identical forced Low Memory Home workload and30-second main-process profile: launch-to-idle1031ms before/874ms after; peak PSS124893KiB/119931KiB; process CPU11185ms/13198ms. One observation per version with network/cache variability does not establish an overall percentage improvement. A locked-device initial profile was rejected. This is a tablet running the policy, not a hard1GB emulator or the actual projector.

Actual hidden American Dad S2E1 check decoded1440-wide video,1393 frames and1013147 native audio bytes through66.98seconds of media time. Pause retained its position, resume and a10-second seek advanced normally; maximum UI heartbeat gap118.3ms; actual128MiB-piece retained cache0bytes and no window/promise errors. It used an isolated cache/profile without production sync or history changes.

The remaining large allocation is required complete-piece assembly/hash verification. Reducing that safely needs deeper engine work, not disabling checks or discarding partial pieces. Long physical-projector sessions, peak playback-process profiling under matching peer/cache conditions, and additional alternating whole-app trials remain open. Public reproducible profile tooling: tools/performance/README.md.


## 0.40.0 recovery and contiguous-piece assembly

The pinned torrent-piece flush now avoids a second whole-piece buffer only for pieces>=1MiB whose complete block views are consecutive in one backing allocation no more than64KiB larger than the piece. It preserves the original path for fragmented/incomplete/small/unrelated-backed buffers. SHA1 verification and store commits are still performed by WebTorrent. This targets contiguous webseed payloads; it is not a claim that all peer block assembly is now bounded.

Focused128MiB flush: previous additional external allocation134280863bytes/39.34ms; shared-view allocation63135bytes/2.36ms, identical SHA1. Actual pinned WebTorrent+filesystem local HTTP webseed transfers used a fresh download directory and no public peer discovery. All128MiB bytes were verified and stored. Alternating optimized/previous/previous/optimized runs recorded peak external memory437130673/540153265/540153265/440669617bytes and RSS474828800/609284096/608210944/475455488bytes. Each optimized transfer used one shared flush; previous transfers used none. CPU totals843/1016/1484/1001ms and elapsed704/753/909/660ms vary with cache/scheduling, so no general playback-speed percentage is claimed. These are engine-only observations, not full UI/projector measurements. Reproduction: tools/performance/webseed-piece-profile.cjs.

Native S1E3 sustained test: Low Memory tablet verification app, first candidate, pause10seconds retained time while received bytes increased, resume advanced, then60seconds further observation; final media position67220ms, sustained main-process peak PSS186586KiB, final180955KiB. The reported96455ms was total exercise duration, not startup; the test label is corrected for future runs. An earlier shorter S1E3 run reached video in31891ms. The older pinned S1E2 source had0 peers/0KBps and did not start; it is not counted as a pass. Physical projector/native driver behavior remains a separate acceptance item.


## 0.42.0 — remaining hash work and comparable profiles

The remaining Windows `simple-sha1` Node path hashed whole completed pieces synchronously in the renderer process. For Buffer inputs >=1 MiB, the pinned 3.1.0 adapter now calls the same native SHA1 implementation in 256 KiB updates with an event-loop yield between steps. Its callback result and synchronous API stay unchanged; WebTorrent still compares every digest before store commit. The original path handles small/non-Buffer input. Pending hash bytes participate in active/preload request budgets; already-in-flight blocks, resume verification and OS caches are not a hard RAM cap. The hot path reuses the budget-state reference.

### Allocation investigation and focused measurements

Three alternating fresh-process trials per hash mode on identical 128 MiB input recorded maximum 2 ms-heartbeat gaps of 87.46/88.61/86.27 ms previously versus 11.70/7.66/15.75 ms cooperatively. CPU was 94/94/78 ms versus 94/78/78 ms; elapsed 87.23/88.31/85.99 versus 82.10/90.68/79.96 ms. All SHA1 values matched and external-memory delta was 40 bytes in both paths. This supports responsiveness during hashing, not reduced hash CPU, a universal UI latency bound, or whole-app RAM savings.

Independent 16 KiB peer blocks using the actual pinned Piece implementation still added 134,217,768 external bytes when assembling a 128 MiB piece in both modes. Digest matched; heartbeat gap 78.88 ms previous/15.28 ms cooperative. The final contiguous store API and whole-piece verification still require the assembled buffer. Safely eliminating it would require a deeper engine/store change; preallocating a full buffer for every partial piece could increase memory and is not introduced. The earlier contiguous webseed reuse remains closed and unchanged.

Actual local WebTorrent + HTTP webseed + filesystem transfers, sharing the 0.40.0 flush optimization in both variants: all 134,217,728 bytes verified/stored, shared flush count 1 each; peak external 438,310,321/440,145,329 bytes and RSS 475,553,792/476,098,560 bytes (previous/cooperative). CPU 906/1000 ms; elapsed 737.8/715.4 ms. No RAM or throughput improvement is inferred. Earlier exploratory transfers overlapped extraction/build activity and are not accepted comparisons.

### Whole-app Windows measurements

Same extracted 0.40.0 payload and NW.js 0.119.2, with only candidate hash/request hooks switched; isolated user/profile/cache directories, identical 84-card Home and 35-second observation, candidate/previous/previous/candidate order, first-profile and repeat-profile launches. Build/extraction warm-up was excluded; no renderer errors or timeouts in accepted trials. The renderer probe confirmed the candidate hook true and previous false. Private commit covers the owned app process tree; it is not physical RAM/PSS. Public metadata/artwork and host activity remain variable.

| Trial | Profile | Home ready ms | Peak private commit bytes | App CPU seconds |
|---|---|---:|---:|---:|
|candidate-valid1|fresh-profile|4605|580366336|14.062|
|candidate-valid1|warm-profile|4019|542830592|10.266|
|previous-valid1|fresh-profile|4101|566112256|10.359|
|previous-valid1|warm-profile|3779|561790976|9.250|
|previous-valid2|fresh-profile|4284|569073664|12.922|
|previous-valid2|warm-profile|4521|563503104|11.141|
|candidate-valid2|fresh-profile|4951|599629824|14.422|
|candidate-valid2|warm-profile|3512|560259072|8.609|

These observations do not establish an overall startup, CPU or RAM improvement. Candidate fresh-profile launches were slower in this set; repeat-profile results and memory/CPU varied. The release claim is the isolated large-hash responsiveness result, not a general application speedup. An initial profile timed out without a Home probe and was rejected; explicit baseline/candidate startup checks and all eight accepted launches later passed. Profiles never changed the installed app or production history.

### Android baseline

Android 0.41.0 code is unchanged in this pass. Three sequential Low Memory Home profiles in an unlocked, separately installed tablet app recorded launch-to-idle 1058/636/635 ms, 30-second process CPU 11734/8755/9051 ms, and peak main-process PSS 125021/112052/107444 KiB. The first run uses a fresh test install; later runs retain its cache. These are baselines rather than a before/after saving, Home idle is not first decoded video, no torrent worker was active, and this is not a 1 GiB physical-projector measurement. The owner separately confirmed S1E3 smooth playback on projector 0.41.0; that functional result does not supply sustained projector CPU/RAM measurements. Test packages were removed and production build configuration restored.


## 23 November 2026 — desktop early launch and saved Home

Owner clarified the delay is before any window appears. Desktop0.45.0 now requests the lightweight native shell from launch.html before index.html loads media/provider dependencies, yields64ms for the starting screen, and retains the active-installer guard. Normal launch shows Starting StreamNest rather than an updating message. Home paints current-history Continue Watching and saved recommendation metadata before deferred catalog work; series/anime/latest bundles load on demand. Snapshot contains title metadata only, never resume timestamps.

219 desktop tests pass. Packaged hidden Home regression passed keyboard focus, themes/sizes, all-category search, live progress, removal tombstones, manual Watched, next episode selection and details. The packaged installer guard blocks an active installer and continues after it completes. Production app, account and history were untouched.

Hidden disposable-profile timings (not actual foreground paint measurements):
- fresh-profile: shell request495ms; Home cards detected3787ms.
- warm-profile: shell request456ms; Home cards detected3030ms.
The earlier0.44.0 baseline observed16748ms first-profile and3004ms repeat-profile Home readiness. Candidate observations are3787ms and3030ms respectively. The original cold run had different OS/file-cache conditions, so these are observations, not a claimed cold-start percentage improvement. Shell-request time is not first visible paint, and antivirus/runtime startup before launch.html remains outside this change.
