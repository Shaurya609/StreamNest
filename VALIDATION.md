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


## 0.12.0 application updater

- Android RSA manifest checks reject altered and expired metadata; release build, unit tests and lint pass.
- Tablet in-place upgrade preserved hashes of all nine preference files, including the encrypted subtitle account store. No credential contents were read.
- Device update screen read the signed live feed and kept the installed newer version. A future version-code-20 fixture with the same certificate was accepted for handoff; the same APK re-signed with a different test key was rejected. Invalid/non-newer APKs were rejected. FileProvider installer URI was readable with the required grant flag. The future fixture was never installed.
- Desktop tests cover altered/expired manifests, version comparison, foreign artifacts, downloaded/cache corruption and partial cleanup. The actual updater handler deferred during playback and quit only after helper spawn in a controlled test; the generated PowerShell parsed successfully with spaces/apostrophes in paths.
- Android TV/projector system installation prompts still need device-specific verification. Tablet checks do not establish confirmation behavior on every firmware. Windows helper UI click-through and interrupted-installer recovery remain broader validation areas.

- Windows 0.11.1 to 0.12.0 silent `/UPDATE` installation passed in a generated folder. Test settings and account byte files were unchanged, the desktop shortcut was retained, and uninstall preserved user-added files. The original user shortcut was restored after the isolated check. Actual bundled NW.js verified the live signed feed and streamed/checksummed a controlled installer fixture successfully.

- Final totals: 148 Android unit tests and 50 desktop/publisher tests. Windows automatic `/UPDATE` preserved a deliberately absent desktop shortcut on a repeated upgrade as well as the initial present shortcut. Both published platform releases and the combined main-page artifacts matched local SHA256 digests and sizes. The signed feed was regenerated for Android 0.12.0/versionCode 19 and Windows 0.12.0.

## 0.13.0 private resume sync

- Android unit tests/build/lint passed. Desktop, catalog, updater and backend tests passed (57 checks).
- Deployed the free Cloudflare Worker and D1 schema. Live encrypted roundtrip and separate-group isolation passed; disposable groups were deleted.
- Real desktop client saved 125 seconds; tablet client restored 125000 ms, saved a rewind to 45000 ms, and desktop restored 45 seconds through the deployed backend. Test history and pairing were isolated from user settings/accounts.
- Hidden Windows startup and native signed-update/download probes passed. Payload verification found ten icon sizes, no bundled user state and no shared upstream vendor credentials.
- Actual desktop player control handlers passed intro seek and active-range visibility checks. Live IntroDB returned One Piece S1E1 intro/outro timestamps and Percy Jackson S1E1 outro only. AniSkip returned HTTP 403 from this network; fallback availability is not claimed. Timestamp coverage and matching release runtimes remain required.
- Fresh tablet decoded-video skip verification passed after unlock: rendered video continued after intro and credits seeks; previous/next kept the exact season-pack member; natural EOF advanced once; unavailable-next and autoplay-off protection passed. The initial locked-screen attempt could not observe video. Controlled timestamps were used; this does not establish live timestamp coverage for every streaming release or TV firmware.
- Windows 0.12.0 → 0.13.0 installer upgrade passed with settings/account sentinels unchanged, shortcut opt-out retained, and the exact quoted dedicated ICO registry path verified. Original user shortcut was restored.
- Packaged NW.js Node 13.10.1 performed live encrypted sync, restored 125 seconds and rendered the pairing QR in an isolated hidden renderer. Disposable cloud group was deleted. GitHub Android checks 42014154213 passed.

## Desktop 0.13.1 update-prompt contrast

- Actual hidden NW.js renderer computed Install and restart as RGB(16,24,39) on RGB(153,197,255), and Later as white on RGB(48,59,76); both had 42-pixel click targets. Hidden-window screenshot capture did not return, so no screenshot-based visual claim is made.
- Installer handoff regression passed; no update was applied to the user's running app during verification.

## Desktop 0.13.2 updater handoff repair

- Reproduced Node 13.10.1 detached Windows PowerShell launch exiting with code 0 without running its command. Direct hidden launch ran the command; a short launcher using Start-Process kept the actual helper independent of NW.js shutdown.
- Replaced unsupported child-process spawn-event waiting with a unique readiness marker. Replaced Wait-Process with bounded polling after the native test observed its suppressed failure returning before the renderer exited.
- Real bundled NW.js called the actual update action, confirmed helper readiness, exited, ran a local installer fixture with exact /S /UPDATE /D arguments, and restarted using the original isolated user-data directory. No real installation registration or saved user account was involved in this controlled test.
- All 57 desktop/catalog/updater/backend checks passed, including helper-start failure without quitting and successful readiness without a spawn event. Real installation upgrade preservation was previously verified separately; the native handoff probe uses a controlled executable, not a published installer.

- Final 0.13.2 packaged native handoff probe passed. Windows CI 42019463031 passed, including native handoff/restart and real per-user installer checks; Android CI 42019463026 passed.
- Applied the local 0.12.0 → 0.13.2 repair through the real installer after gracefully closing only StreamNest. All five pre-existing saved profile files retained exactly the same SHA256 hashes. Confirmed installed package version 0.13.2, then relaunched it.
- Confirmed the running local installation is 0.13.2 and its updater matches the repaired source byte-for-byte. A post-publication status probe initially returned false without recording the panel message; added panel-status diagnostics and the fresh rerun returned “StreamNest is up to date” with verified native download handling. No cause is claimed for the first probe.

## Desktop 0.13.3 sync dialog and Android verification

- Reproduced the actual desktop Settings-button route: panel existed at y=826 with an 826px viewport. CSS.supports(inset,12%) returned false in bundled Chromium. Explicit top/right/bottom/left produced a fully visible panel at y=66, height=694 in the same viewport.
- Native Settings UI probe passed visible dialog bounds, invalid-code feedback, live group creation and QR rendering, all five readable controls, Sync now, disconnect/QR clearing, close/reopen and disposable cloud group deletion. Added this probe to Windows CI.
- Tablet instrumentation clicked the real Settings row and opened WatchSyncActivity, checked visible input, invalid-code reporting and valid QR rendering/clearing. Saved pairing/account was untouched. No production Android change was needed.
- Live encrypted cross-device rerun passed: desktop 125 seconds restored on Android; deliberate Android rewind to 45 seconds restored on desktop. Test pairing/history were isolated and cloud group deleted. All 57 desktop/catalog/updater/backend checks passed.

- Published desktop-v0.13.3 as the regular Latest release with Windows 0.13.3 and the unchanged Android 0.13.0 packages. All eight uploaded asset sizes/digests matched local files. Windows CI 42030430823 and Android CI 42030430746 passed; signed-feed workflow 42031469310 succeeded.
- Live raw update-feed checks initially served cached 0.13.2; after the GitHub CDN refreshed, the real verifier accepted the signed feed offering Windows 0.13.3. Local installed Programs/StreamNest remains 0.13.2: user is still working, and Windows input detection blocked UI clicks. No manual installer or bypass was used for this release. Exercising the actual installed app's Check for updates → Install and restart remains pending the user's availability; generated-build sync and update-handoff checks passed.

## 0.14.0 automatic episode selection

- Shared Android/desktop vectors cover most recent unfinished episode, intentional earlier-episode replay, completion across seasons, isolated Specials, future/unmapped episodes, and early starts distinct from completion. Completion markers survive encrypted clean/merge and newer rewinds clear them. Local timestamps advance beyond observed history to preserve ordering across episodes.
- Actual desktop detail-selection handler tests cover synced selection, late sync refresh and preservation of manual choices. All 60 desktop/catalog/updater/backend checks passed. Android build/lint and 154 unit tests passed.
- A temporary separately packaged Android verification app opened the real series/anime title UI: resumed season/episode selected, timestamp retained, and a completed episode advanced across the season boundary. Verification app was removed; installed app was deliberately kept at 0.13.0 for normal update testing.
- Live encrypted desktop/Android verification roundtrip preserved 125 seconds, completion-based next-season selection and a newer 45-second rewind with correct episode selection. Disposable cloud group deleted; no saved account/pairing changed.
- Installed 0.13.0 tablet read the signed feed and accepted a valid future version-code-21 APK, rejected wrong signing certificate/invalid/non-newer APKs, and opened its granted content URI. Install-source permission was false; actual installation still requires the device owner's Android approval.
- Published Android and Windows 0.14.0 and verified the live signed update feed for both. On cold launch the installed 0.13.0 tablet app downloaded the 105,389,490-byte universal APK into its initially empty update folder. Its SHA-256 matched the published artifact. System installation confirmation is a separate device step.
- After Android system confirmation, the same tablet reported 0.14.0 (21). Watch-history storage matched its pre-update digest. Encrypted subtitle storage changed, so credential retention was not established by the digest check. Production title-screen checks passed for series and anime, including cross-season advancement. Live paired-device checks preserved 125 seconds from Windows to Android and a newer 45-second rewind back to Windows.
- Follow-up on actual installed devices: tablet subtitle settings retained anonymous .org mode after upgrade. Live anonymous service probe returned five English Percy Jackson matches, two Scorpion matches and a verified timed subtitle download. Windows 0.13.2 was updated through its actual Check for updates and Install and restart controls; installed package reports 0.14.0, helper recorded installation completed/restarting, and the new StreamNest window appeared.
