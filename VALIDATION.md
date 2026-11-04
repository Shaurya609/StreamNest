# Validation — StreamNest 0.7.5

- Local Android build succeeded with JDK 17 and compile SDK 36.
- 111 unit tests passed; Android lint reported zero errors.
- Android 14 Samsung tablet: catalog pagination, movie quality selection, full-screen playback, subtitles, control hiding and cache cleanup were tested.
- One Piece and Interstellar reached actual playback in earlier mobile checks.
- Attack on Titan Season 2 Episode 1 played from a 97-file torrent pack. File index 33 was selected; a decoded frame and VLC Playing event were observed.
- Manual source switching was exercised. Selecting Season 2 Episode 2 selected file 34 from the same pack and removed both previous-episode cache directories.
- Unit tests cover startup recovery timing; a complete automatic multi-source exhaustion scenario has not been exercised on the TV or projector.
- Croma 55-inch Google TV: the user confirmed that the APK works. This is user-reported device verification, not an automated TV performance benchmark.
- The user subsequently confirmed basic operation on the 1 GB Android 14 projector; no automated projector benchmark was run.
- GitHub Actions checks are separate from these local results; see the repository's Actions tab for their status.

Live observations are specific to the device/network and test time. Source seed reports and previous successful playback do not guarantee continued peer availability. See RELEASE-0.7.5.md for detailed behavior and limits.

Movie outage recovery: a server returned HTTP 521; the replacement/fallback client loaded 48 titles on the Android tablet. Desktop movie browsing, canonical anime episodes, actual playback and next-episode cache cleanup were checked. See desktop/README.md for the desktop test limits.

## Android 0.7.6 / desktop patch 0.8.1

- 118 unit tests passed; lint reported zero errors. Desktop tests: 14 passed.
- Device-only playback regression: decoded frames remained visible after subtitle menu dismissal and after returning from account settings. Playback position advanced, and deliberately paused playback stayed paused.
- On-tablet catalog check: 91 Hindi movies with HD sources, 24 mixed movie cards with six Hindi cards, and a full 24-card Hindi series page.
- Installed desktop grid visibly included Hindi movies in mixed Trending. Five mixed-page fixtures covered 120 unique cards without dropping English or Hindi items.
- Session tests cover launch renewal, expiry, missing tokens, sign-out, server rejection, shared desktop logins and bounded retries. Real server lifetime expiry was simulated; a long-running wall-clock expiry soak was not performed.
- The SVG has no embedded image or background rectangle. Android logo, launcher and TV banner reference native vector paths.

## Android 0.7.7

122 unit tests passed and lint had zero errors. Live on-tablet .org searches returned 5 English subtitle choices for Percy Jackson S1E1 and 2 for Scorpion (2026), with the hash criterion deliberately unable to match. Downloaded a valid timed 42,211-byte subtitle file. The subtitle probe used an isolated in-memory anonymous session and did not inspect or overwrite saved user credentials.

The native cache-folder test selected and saved an app-storage subfolder using the screen's Use this folder action, checked the friendly label and restored the previous folder. Canonical containment/name tests reject traversal and invalid names. Broad shared-folder access remains a user-enabled Android permission; it was not granted automatically during testing. USB/SD hardware and vendor Settings implementations still require device-specific validation.

## Android 0.8.0 / desktop patch 0.8.2

Catalog fault tests on the Android 14 tablet passed: 120 unique saved movie titles across five pages, offline search after database reopening, suspicious empty root protection, independent Hindi TVmaze metadata, saved anime series/releases and live RSA-verified GitHub mirror download. Failures were injected inside the test process, without disabling networking or accessing saved subtitle credentials. Desktop/tool tests cover cold-start fallback, saved search, pinned paging, schema rejection, compatible-host cooldown and signed snapshot integrity.

Local release checks: 134 Android unit tests passed, lint reported zero errors, and 26 desktop/publisher test groups passed. The installed desktop movie grid loaded English and Hindi titles after the patch; full UI outage scenarios remain covered by fixtures rather than a live network-disconnection test.

## Android / desktop patch 0.9.0

Local Android unit tests: 142 passed, with zero lint errors. Desktop/publisher fixtures: 37 passing groups. New tests cover canonical previous/next boundaries, specials isolation, future/missing sources, pack-file selection, completion guards, explicit MAL mapping, timestamp identity/confidence/runtime checks, post-credit preservation and timestamp-provider outages. The actual desktop player view's ended handler and controls/timer methods are exercised in a background VM fixture, including autoplay-off controls and duplicate-end suppression.

Android 14 tablet: a local video fixture produced decoded frames after intro seeking; credits seeking stopped before later content. Native previous/next controls retained the exact selected pack member. Natural LibVLC EOF advanced once; a missing next source left the current episode selected; turning autoplay off prevented advancement. Subtitle-menu/account-settings return still preserved decoded video, playback position and deliberate pause. These are local media/injected-source regressions, not claims that every live torrent or community timestamp has been verified. The service is promoted on each start request to support repeated episode transitions.

An anonymous live IntroDB lookup for Breaking Bad S1E1 returned a matching outro range (3431–3500 seconds). AniSkip returned HTTP 403 from this network, so its parser/mapping behavior is fixture-tested and failures are handled as unavailable timestamps. No full native desktop player check was run in 0.9.0, to avoid interrupting the owner's laptop work. Earlier desktop playback checks remain documented above.

## Standalone Windows desktop 0.10.0 preview

- Clean Windows CI build at b984fae passed package identity/credential-default inspection, native fresh-profile startup, per-user installer and safe-uninstaller checks. Run: https://github.com/Shaurya609/StreamNest/actions/runs/41827321956
- All four downloaded CI artifacts matched the generated SHA-256 checksums.
- The CI-built app started locally using a throwaway profile/cache and rendered the catalog plus movie/series/anime and language controls without renderer errors.
- The packaged WebTorrent engine transferred and verified an 11,053,871-byte local fixture between diagnostic clients, then served it through its local HTTP server. The native renderer decoded 854x480 video, a visible frame and 57,236 audio bytes. No public torrent was used for this check.
- Silent install/uninstall also passed locally. An unrelated sentinel file survived uninstall; the existing Popcorn Time installation/profile was not used as build input.
- The 40 desktop/publisher test groups passed. The Android source is unchanged from the 0.9.0 release; the accompanying Android CI job passed as well.
- Hands-on standalone subtitle/player/binge behavior on additional Windows machines, code signing, runtime modernization, optional metadata integration registration and other desktop operating systems remain pending.


## 0.11.0 — 4 November 2026

- Android: 146 unit tests; all ABI/universal debug APKs; lint passed. CI run 41868549572 passed.
- Connected tablet: fresh anonymous .org session and explicit expiry renewal in isolated memory; Percy Jackson S1E1 (5 English matches) and Scorpion (2 matches); 42,211-byte timed episode subtitle download. No saved account credentials used or changed.
- Tablet foreground playback fixture: nonblack decoded frames after subtitle menu and account-screen return; advancing position and preserved user pause. The first instrumentation attempt after replacing/restarting the app twice hit an Android foreground-service startup timeout; the awake foreground rerun passed. This is a test-startup limitation still worth monitoring, not a reproduced TV failure.
- Startup regression tests reject an 80%-complete file without required headers/footer, cover unaligned pack-file offsets and cap retries. Decoder retry preserves the requested initial resume position.
- Desktop/publisher: 45 automated checks passed. Windows CI 41867838077 passed clean build, payload identity, isolated hidden renderer and per-user installation/uninstall preserving user files.
- Actual hidden Windows video.js display: anonymous Percy Jackson S1E1 search returned 10 English choices; full 42,208-byte SRT yielded 540 parsed cues; a downloaded cue was rendered and attached to the display. Controlled cue time was used for the renderer check; this does not establish synchronization for every torrent release.
- Desktop Scorpion movie: 4 English choices and a 54,174-byte timed SRT downloaded anonymously. No movie scene synchronization claim.
- Signed catalog publication 41867874027 passed; remote manifest signature and anime payload digest independently verified. Expanded snapshot has 302 anime series, including 280 metadata-only entries; desktop adapter returned 13 unique pages / 302 titles. Torrent availability remains a separate resolution step.
- Reported Percy Jackson stall happened on the TV. Exact TV codec/source and long-form playback require user retesting; tablet checks do not verify that TV.

- Public Windows preview desktop-v0.11.0 was published by workflow 41870009937 directly from successful build 41867838077; the complete three-file checksum list and all four uploaded asset sizes/digests were verified before publication. Android v0.11.0 uploaded asset sizes/SHA256 digests were independently matched against the local APKs before publication.


## Desktop 0.11.1 local installer verification

Built locally from commit 30742955e184231918c8d2dd48342d3eed142680 while GitHub Actions was queued. All 45 desktop/publisher checks passed; payload identity and isolated hidden startup passed. Silent installation created the desktop shortcut by default; uninstall removed it and preserved a user-added sentinel file. The visible checkbox page compiles; clearing its state bypasses shortcut creation. An interactive unchecked-option walkthrough remains unverified. All four release asset sizes and SHA256 digests matched the local files before publication.
