# TV playback and anime matching — 0.43.0

Exact Black Clover 121 member parsing is corrected and verified against four real torrent packs; wrong episode 122 requests are rejected. Live Black Clover video acceptance remains distinct. TV buffering investigation now includes bounded range waits/delivery, startup verified pieces, seek kind and decoder/input counters. ARM32 isolated tablet S1E3 pause/download/resume and short sustained playback passed. Physical TV retry on the new version is still required; no confirmed TV root-cause fix is claimed. See VALIDATION.md.

# Remaining optimization investigation - 0.42.0

The remaining synchronous Windows large-piece hash path now yields with pending-hash request backpressure. Fragmented-piece assembly was measured and still requires a whole-piece allocation with the current store contract; no unsafe early preallocation or verification bypass is introduced. Eight alternating whole-app Home profiles did not establish an overall RAM/startup/CPU saving, and three Android runs establish a current baseline only. Previously shipped cache/read/contiguous-flush optimizations remain closed. Further fragmented-buffer elimination would be a separately scoped engine/store redesign; sustained physical-projector profiling is separate from the owner-confirmed 0.41.0 S1E3 playback pass.

# Projector follow-up - 0.41.0

The owner tested 0.40.0 on the physical projector. After correcting a disconnected USB cache drive, startup again blocked in native have_piece; shutdown sometimes self-aborted but also remained unconfirmed, later exhausting worker slots. Internal-cache checks also passed before worker-slot failure. Physical acceptance of 0.40.0 therefore failed. The 0.41.0 candidate removes per-piece synchronous queries, isolates the control looper, adds exact UID/process-name fallback validation, halts cycling during unconfirmed cleanup and extends bounded diagnostics. Physical-projector retry remains required; this is not a confirmed firmware diagnosis.

# Projector recovery follow-up - 0.40.0

Token-authorized self-abort, Binder-only writer death confirmation and retryable cleanup are implemented. A frozen-worker/no-process-list test and repeated same-cache restarts passed; S1E3 pause/download/resume plus a sustained tablet sample passed. Projector normal-update acceptance and the underlying cause of remaining native operation stalls are still open. New shutdown-mode and slow-worker stack reports support that diagnosis. Contiguous large-piece copying is reduced; fragmented-piece assembly and whole-device sustained profiling remain distinct.

# Optimization follow-up - 0.39.0

Byte-budgeted Windows caches/writeback, revision-based Home polls, selected-record Android startup and cached diagnostic scope hashes are implemented and measured. Short whole-app profiles do not establish a consistent total RAM/CPU/startup percentage improvement. Full-piece hash assembly, additional matching trials and long physical-projector playback remain open. See PERFORMANCE.md for exact observations.

# Projector and subtitle follow-up - 0.38.0

Projector remains top priority: bounded Java crash capture, correlated attempt timelines, worker/heap observations, main-thread stall stacks and protected cleanup containment are implemented. The original reported Java crash lacked its stack; the verified cleanup exception is a candidate path, not confirmed historical attribution. Physical S1E3 retry and sustained paused-download/relaunch testing require the projector to update normally.

American Dad S2E1 English subtitles currently search/download/display in an isolated Windows app. Late provider results previously could miss the already-created player; the new path updates that player without reopening video or overriding manual subtitle choices. TheBeast normal-use confirmation remains open.

Windows bounded range reads address large media-read allocations. Large-piece download assembly, comparable whole-app profiling and sustained projector verification remain broader optimization work.

# Current reliability cycle

## New report: next episode repeats the current episode

Reported 19 November 2026 after the owner confirmed the TheBeast startup fix works. After The Last of Us S1E2 finished, playback restarted S1E2 instead of advancing to S1E3. Open backlog item: verify next-episode preloading, automatic episode selection and preload handoff; determine whether the repeated episode comes from navigation, source/member selection, resume state or preload reuse. The cause and preload involvement are unconfirmed. Acceptance: S1E2 completion starts S1E3, adopts its preload when enabled, and preserves previous-cache cleanup until the next episode actually renders. Verify with preload on and off. Implemented for0.37.0: adjacent identity validation, endpoint/audio safeguards, bounded preload source fallback and tracker/encryption parity. Actual tablet Last of Us S1E2-to-S1E3 off/on handoff and hidden Windows American Dad full-download preload/cache handoff passed. Exact original TheBeast replay was not reproduced; owner confirmation remains pending.

Owner confirmation: the reported TheBeast startup issue is resolved on0.36.2. This does not establish that every historical buffered stall or catalog failure is resolved.

0.36.2 follow-up arms the missing Windows source-startup watchdog, waits for reused-torrent metadata, guards disposed player controls and reduces optional metadata404 retry noise. The owner confirmed the reported TheBeast startup issue resolved; broader normal buffered-recovery verification is distinct.

0.36.1 adds bounded Windows buffered-stall recovery and compatibility-audio pause/seek/buffering coordination. The observed TheBeast S1E1 stall is addressed through same-source, same-cache reopening with saved position, not an assertion that its original decoder interruption is fully explained. Physical TheBeast confirmation remains pending; its current playback is not interrupted.

0.36.0 addresses the reported 0.34.0 episode-selection freeze with compiled release matching, linear source selection and background playback planning. A tablet 2,000-source comparison measured 8,110 ms before versus 224 ms after, with the same selected source; title-dialog launch handled the click in 1 ms. It also adds Windows missing-audio decoding and embedded text subtitle extraction/display. Real Last of Us English E-AC-3 audio and Black Clover English embedded subtitle playback passed in an isolated Windows profile. Physical-projector/USB confirmation remains pending.

0.35.0 already added isolated Android torrent workers, bounded worker recovery, cache-writer leases, preserved warm preload adoption and bounded Windows stop/server waits. The new ranking ANR happened outside those workers and needed the separate 0.36.0 fix. Remaining planned development is Sprint 3 memory/sustained stability, Sprint 4 resume/subtitle/touch consistency and Sprint 5 browsing/cleanup. See SPRINTS.md and PERFORMANCE.md.

## Earlier release notes and remaining verification

0.32.0 content coverage: trusted batch-only fallback, independently mapped Black Clover/One Piece filler fallback, adjacent-episode skip identity and Korean English track preference implemented. Native tablet/Windows checks pass. Remaining source-dependent coverage: no validated skip data for checked One Piece1000, future/split-season mappings require explicit identity, Korean sources may lack English dubs, and release-specific subtitle timing needs playback verification. See VALIDATION.md.

Owner backlog corrections: original-phone status-bar overlap is confirmed fixed. Cloudflare usage monitoring is routine operation, not a development backlog item.

0.31.0 optimization: fewer piece-deadline calls, native-free UI completion checks, nonblocking Low Memory catalog setup/trim, cached hardware profile, released background Home caches, worker-side memory sampling and unchanged desktop-history write elimination are implemented. Controlled native pause/navigation and CPU/IO workloads pass; see PERFORMANCE.md. The physical projector ANR and engine-start hang are still open for retest with sanitized OS ANR locations. No device-specific cure is claimed from the fixture alone.

0.30.0 maintenance: targeted artwork optimization, guarded Windows launch/restart, and repeatable live content checks. Live Android/Windows validated timestamps and Korean English subtitle downloads pass for the checked titles; later-page Jikan requests still time out. English dub coverage is not inferred from metadata. The projector native engine-start hang remains open; no engine-process recovery fix is claimed.

# Pending fixes and verification

Anime 0.29.0: real audio/embedded subtitle controls, anonymous exact-source TsukiHime fallback, Android ASS font setup and season-pack filename matching are implemented. See ANIME-PLAYBACK.md and VALIDATION.md. The reported projector freeze remains open for the owner’s retest; healthy TheBeast samples do not establish its cause.

Owner group deletion and separate current watching activity are implemented for 0.28.0. See WATCH-SYNC.md for privacy, freshness and deletion scope. Release acceptance is recorded in VALIDATION.md.


## Mobile status-bar overlap — reported 17 November 2026

Implemented for Android 0.28.1 after the owner requested the fix. The supplied screenshot showed the StreamNest logo, title and top navigation overlapping Android's notification/status bar in the mobile Home screen. Shared browsing/settings safe-area handling now reserves system-bar, display-cutout and keyboard space. Fullscreen player remains separate. Tablet and synthetic-inset acceptance is tracked in VALIDATION.md. The owner has now confirmed the original physical phone issue is fixed; this item is closed.

## Cross-platform app optimization — first maintenance pass 0.30.0

Authorized 18 November 2026. First pass implemented for 0.30.0: Android cancels superseded poster queues, skips stale views, reuses queued cache hits and amortizes disk pruning; Windows defers off-screen Home artwork. Native targeted image and packaged Home checks pass. See PERFORMANCE.md. The broader launch/steady/peak RAM and CPU benchmark remains open, especially on the physical 1 GB projector. Make Android/mobile, TV/projector and Windows lighter and faster without removing functionality. Measure launch/catalog responsiveness, peak and steady-state RAM, CPU/background work and network requests before and after changes, with particular attention to the 1 GB projector. Review duplicate requests, bounded metadata/poster caches, image decoding, player/torrent resource lifetimes and optional episode preloading. Preserve playback, subtitles, sync, resume, recommendations, cache cleanup and auto-update behavior. Existing targeted Low Memory improvements do not close this broader optimization pass.

## Planned additions — requested 12 November 2026

Status is recorded per item below.

1. **Device diagnostics — implemented for 0.26.0.** Default-on per-device sharing with a persistent off choice, bounded local journals, background uploads, seven-day retention and owner-only device/session timelines with filter/export/delete. Live tablet and hidden Windows upload/opt-out checks passed, as did live owner retrieval/deletion and dashboard UI checks. Device CPU/OS/app/RAM, Android startup phase timings/stalls/OS exit reasons, playback/buffering/source changes and selected failures are captured. See [privacy and usage](DIAGNOSTICS.md). Released 0.27.0 extends reports with periodic peer/rate/buffer/memory/frame/audio samples, safe exception locations and broad catalog/subtitle/update failure hooks. Actual hardware decoder identity, Android GPU model where unavailable, deliberate crash/ANR stress tests, a 1 GB projector overhead benchmark and explicit title-bearing user reports remain future work. Unavailable measurements are labelled honestly. Packaging and feed acceptance are recorded in VALIDATION.md.
2. **Content trailers — released in 0.25.1.** IMDb-matched movie/series/anime trailer metadata is checked against YouTube availability. Title details show a play icon and Trailer action only when a verified link exists. Opens the YouTube app/browser rather than the torrent player. No YouTube application key or StreamNest user login is needed. Removed candidates fall back to another metadata-provided video, and optional failures leave core playback usable. Regional restrictions or later video removal can still affect YouTube playback.
3. **Public project website and user guides.** Explain what StreamNest is and how it works in plain language, using the owner's intended screenshot as a visual reference once available (no new screenshot was attached to this request). Provide clear Android/TV/projector and Windows download/update guidance. Include illustrated how-to guides for enabling next-episode preload and explaining previous-episode cache cleanup; creating/naming a sync group and pairing by short code/QR; resume watching/shared recommendations and Watched behavior; choosing internal/USB cache storage; Low Memory mode and remote navigation; subtitles/audio/quality/source selection; auto-next and intro/credits settings; and basic playback/update troubleshooting. Include practical torrent/P2P and source-availability explanations, diagnostic privacy/consent guidance when that feature exists, and links to GitHub releases/support. This request records the website scope; it does not authorize starting website implementation now.

Current sprint/release status is in [SPRINTS.md](SPRINTS.md) and [VALIDATION.md](VALIDATION.md). Version 0.22.2 adds missing-artwork recovery and explicit desktop resume fill, following 0.22.1 Home layout validation; 0.22.0 added shared recommendations to the existing Home, broader catalogs/independent series lookup and modern Windows runtime. Owner-confirmed MSI sync and TV/projector issues are closed. Remaining external verification/setup: live Jikan filler data (timed out) (the Android local network preload harness is a test-infrastructure limitation; the owner confirmed real preload working on their TV using the universal APK). Public source and language availability remain variable. Windows signing was explicitly declined by the owner for this personal project; it is not a release blocker. Older dated entries below are historical verification notes.

Updated 4 November 2026. Version 0.11.0 implements launch renewal, subtitle archive decoding and completed conversion writes, dynamic loopback subtitle serving, pinned anime pagination and broader independent discovery. Live desktop Percy Jackson S1E1 search/download produced 540 cues and the real video.js subtitle display rendered a downloaded cue in an isolated hidden renderer. Android fresh/expired anonymous access is covered by regression probes; tablet playback survives subtitle settings return.

TV correction: the reported Percy Jackson S1E1 stall occurred on the user's TV, not the tablet. Android now waits for verified video headers/footer, detects actual video output and retries startup twice (including one software decoder attempt). The original TV/source combination still needs a TV retest. Local fixtures do not establish that it is fixed on that TV.

The remaining checks below describe broader verification, not a claim that every title or service is available.

## OpenSubtitles.org — desktop (priority)

- Ensure anonymous login happens automatically at launch and renews after session expiry/rejection, including in the standalone app.
- Investigate subtitles failing to appear even when the desktop UI reports an anonymous signed-in session. Treat login status, subtitle search results, download and actual player attachment/rendering as separate checks.
- Reproduce the full movie and series episode flow in the standalone player, verify correct title/season/episode/language matching and confirm downloaded subtitles become visible during playback. Check the legacy patch separately where applicable.
- Show an accurate status/error if login, search, download or rendering fails; a signed-in indicator must not imply subtitles are available or displayed.

## OpenSubtitles.org — mobile

- Verify automatic anonymous login on a fresh launch and session renewal after expiry/rejection on the installed build.
- Confirm search, download and visible subtitle rendering for both a movie and series episode, without relying on an existing manual login. Preserve user-selected account mode and credentials.

## Standalone desktop release visibility

- User reports that visitors to the GitHub profile/repository cannot easily discover the standalone desktop release. The current desktop release is public but marked pre-release; the Latest release still points to Android v0.9.0.
- Review the repository landing page and release navigation from a visitor's perspective. Make desktop and Android downloads clearly distinguishable and easy to find.
- Consider a prominent desktop download/release section and an appropriate stable/latest release arrangement once validation is complete. Keep the preview label accurate while known issues remain; do not promote it to stable merely to improve visibility.
- Verify that download links lead directly to the correct standalone installer/portable packages and that the Android download remains easy to find.

## Anime catalog — desktop pagination

- User reports that desktop anime pagination is not working and only some titles appear. Treat this as an unresolved user-reported issue.
- Reproduce scrolling/load-more in the standalone desktop app; check the legacy patch separately. Distinguish a paging failure from the limited saved anime-series seed, discovery coverage and active format/language/quality filters.
- Check provider page/skip parameters, hasMore/end-of-list handling, duplicate-page detection and paging-session behavior during background refresh. Verify several pages of unique titles and revisiting/filtering/searching the catalog.
- Do not claim complete anime coverage solely because the available small snapshot paginates correctly.

## Subtitle account scope — 5 November 2026

The owner selected anonymous access only. Personal subtitle-account upgrade-retention testing is out of scope. Continue checking anonymous launch access, expiry/rejection renewal, search, download and visible playback subtitles on Android and Windows. The tablet retained anonymous mode through the 0.14.0 upgrade; live anonymous search/download passed. Windows in-app upgrade from 0.13.2 to 0.14.0 also completed and restarted successfully.

## Regional catalogs and anime metadata — implementation in progress

- Korean language is accepted by Android/desktop series metadata adapters, movie adapters and the signed snapshot schema. Both language filters expose Korean. Independent publisher scans now retain Korean series as well as anime. These labels identify original title language, not verified English audio/subtitles.
- Android and desktop attach Jikan filler/recap flags only to explicitly mapped MAL episodes, currently from the first API page. Unknown, unmapped and later-page episodes receive no inferred labels. Android shows flags in selected episode heading; desktop shows them in episode rows.
- Remaining before release: extend mapped filler pagination with provider rate limits, verify UI/network behavior, add confirmed source-audio/subtitle availability, verify IntroDB and cached timestamp fallback, and publish updated signed catalog snapshots and app packages.

Timestamp provider scope — owner selected IntroDB and cached timestamps for now. Anime Skip client registration/integration is deferred. Continue validating episode identity and segment boundaries before showing skip controls.

Owner confirmation: Percy Jackson TV playback, projector operation and USB cache storage are fixed. Removed from active device backlog based on that confirmation.


## Windows update launch guard — implemented for 0.30.0

A window launched from Explorer while installation was still extracting files kept older Home controls in memory, even though the final files/package version were updated. The normal helper completed and attempted restart, but the already-running instance remained. A full quit/reopen after installation completed loaded the corrected controls. 0.30.0 adds an installer-owned marker and guarded app entry, serializes installers, gracefully closes an instance opened during extraction before the updater restarts, and records restart survival. Profile resets and force-killing unrelated apps are not used. Packaging/installer acceptance is recorded in VALIDATION.md.

## Owner acceptance — 11 November 2026

The owner confirmed next-episode preload working on TV with the universal APK, and the latest Home layout passed on physical TV/projector. Both product verification items are closed. The isolated Android network harness remains a diagnostic limitation, not an open user-reported preload defect.

## Android projector startup follow-up — 0.24.0

Added elapsed stage diagnostics, initial low-memory session settings, busy-start rejection and safe cleanup timeout guidance. A native alert-loop shutdown race was reproduced and guarded narrowly. Tablet native and live encrypted watched-sync checks passed. The owner's specific intermittent projector stall across internal/USB storage has not been reproduced or retested on that projector; a permanently blocked native call still requires app restart. Black Clover diagnosis remains read-only and is not part of this change.
