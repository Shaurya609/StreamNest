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

## Additional backlog investigation, 5 November 2026

- Extended the Android real-player test to click both intro and credits controls against explicit fixture segments, verify the resulting seek and a rendered frame, then clear segments. Passed on installed 0.14.0 after a clean app start. This verifies playback controls with fixture timestamps, not public timestamp coverage. The first run encountered a foreground-service startup timeout; a clean restart passed, so the intermittent startup failure remains open.
- Live IntroDB Percy Jackson S1E1 returned a validated credits interval but no intro. AniSkip request returned HTTP 403 from this connection. Broader live coverage and desktop skip playback verification remain open.
- No code-signing certificate was present in the current Windows user certificate store. Publicly trusted signing and runtime migration remain open. Corrected generated desktop README update instructions.

## Series/anime anonymous redundancy and filler pagination

Implemented anonymous SkipDB fallback for missing IntroDB intro/outro segments in Android and desktop; exact episode identity, exact duration match, confidence and playback bounds required. Existing HTTP caches cover successful responses. Added attribution in playback settings and README. Filler/recap metadata now requests all pages containing explicitly mapped MAL episodes, with 400ms spacing. Android unit/build/lint and 57 desktop/catalog tests passed. Live SkipDB and TheIntroDB requests returned HTTP 403 from this connection; live fallback availability remains unverified. Anime Skip requires a client ID and is not integrated as an account-free service. Korean English-audio/subtitle confirmation and release packaging remain pending.

## Published 0.15.0 — Korean catalog and episode metadata

- Anonymous live .org searches returned 8 English matches for Squid Game S1E1 and 28 for Parasite. Live movie lookup returned Parasite (Gisaengchung), Korean original language, and two listed HD sources. English dubbing was not established by those source records; subtitles are title/episode matches, not verified timing for every release.
- Android unit/build/lint and all 61 desktop/catalog/sync checks passed. Final native Windows startup and package inspection passed. Clean Windows CI run 42064117891 passed including installer checks; local installer harness correctly declined to replace the registered installation. Signed catalog refresh 42064343073 succeeded.
- Published v0.15.0 with eight assets and desktop-v0.15.0 with four assets. All uploaded sizes/digests matched local artifacts. Final live signature verifier accepted Android 0.15.0/code22 and Windows 0.15.0. Signed-feed workflows 42065477123 and 42065473503 succeeded. Actual local-device upgrade to 0.15.0 is not claimed.

## 0.15.1 anime discovery release

Published Android v0.15.1 (code23) and Windows desktop-v0.15.1. Android unit/build/lint and 61 Node checks passed; native Windows fresh-profile startup/package inspection passed. Windows CI 42068117194 succeeded. Main release has eight verified assets; desktop release has four, with local size/SHA matching GitHub digests. Live signature verification accepted 0.15.1 on both platforms after feed workflow 42069835579 succeeded. Local device installation was not performed for this release.

## Follow-up source and anime verification

- Live SkipDB is now reachable from this connection. Squid Game S1E1 at duration 3655 seconds returned exact-match credits 3313–3655 with confidence 0.9, accepted by the production desktop parser. Requesting 3600 seconds returned out-of-range/confidence 0.6 and is rejected. This establishes service/parser access, not rendered skip control playback on both devices.
- Black Clover MAL 34572 mapping returned IMDb tt7441658 and 196 episode entries. Jikan second-page requests timed out twice; live filler flags beyond 100 remain unverified. Added Android boundary regression for episodes 100/101, missing flags and unmapped split-season isolation. Extracted the production page-selection/attachment logic for those checks.
- No Android device is connected for current playback/audio-track verification. Korean English audio and release-specific subtitle timing remain open; earlier subtitle matches do not establish sync for every source.

Tablet follow-up: R52W3013K6X connected, still on 0.14.0. Expanded anonymous subtitle regression returned Squid Game S1E1 English=4, Parasite English=25, plus existing Percy/Scorpion checks and timed subtitle download. Normal launcher automatically downloaded 0.15.1 universal APK; SHA-256 matched published artifact. Android install confirmation requested before exercising new fallback/search behavior. English audio and release-specific subtitle timing remain unverified.

Post-confirmation tablet: package verified 0.15.1/code23 following the normal update workflow. Live anonymous SkipDB probe accepted Squid Game S1E1 credits 3313000–3655000 ms and rejected mismatched episode identity. Playback fixture test passed intro/outro seek plus decoded frame checks, subtitle-menu/settings return and pause preservation on retry. Initial run again encountered foreground-service startup failure, so intermittent startup remains open. Actual Korean source audio and subtitle timing and live Jikan beyond-page-one labels remain unverified.

## 0.16.0 temporary pairing and QR verification

- Added five-minute, single-use eight-character pairing codes with encrypted tickets and distinct encryption/lookup derivations. Backend deployed with additive D1 ticket table. Existing progress protocol and recovery keys remain supported. Backend tests cover expiry, duplicate issuance, concurrent consume and wrong-key decryption; live encrypted exchange passed. Android unit test decrypts a desktop-produced fixture.
- Android verification clone decoded the actual sync-screen QR, checked its HTTPS landing URI, opened the custom camera link and verified short-code prefilling. A disposable desktop ticket was redeemed/decrypted by Android and second redemption was rejected. Clone removed and production build settings restored. Initial attempt via fresh-clone Settings navigation could not click its row; targeted pairing-screen verification subsequently passed. User pairing/history was not changed. Hardware optical camera scanning was not performed.
- Native generated Windows 0.16.0 sync dialog passed live group creation, temporary-code generation, QR visibility, disconnect/reopen and disposable-group cleanup. Windows CI 42077260244 passed. Android build/lint/unit and 63 desktop/catalog/backend checks passed. All Android/Windows release asset sizes/digests matched local files.

Final 0.16.0 live update-feed verification accepted Android code24/version0.16.0 and Windows0.16.0 after the public cache refreshed. Signed feed workflow 42079430169 succeeded.

Actual tablet update follow-up: the 0.15.1 launch did not produce an update file on its first check. Opening Settings > Check for updates downloaded 0.16.0; its universal APK SHA-256 matched the published artifact. Android installation confirmation requested. No direct APK installation was used for the production app.

Actual installed tablet subsequently reported 0.16.0/code24. Production-app targeted pairing screen passed QR decoding, camera-link short-code prefilling and invalid-input handling without changing saved pairing. A fresh disposable desktop-created ticket was redeemed/decrypted by the installed Android app; second redemption was rejected.

Normal installed-app entry was also verified using the tablet UI: Settings > Resume & sync devices opened the 0.16.0 screen with eight-character input, Show pairing code and Show recovery key controls. The automated Settings instrumentation had stalled behind the Android install-completion screen and was stopped; this UI check replaced that blocked navigation check.

## Sprint 1 implementation — 0.17.0

- Android verification clone rendered decoded fixture video with icon/text/progress skip controls, manual seeking, Settings-disabled automatic skipping and five-second automatic seeking. Subtitle settings return, advancing video and preserved user pause passed. Clone removed and production Gradle configuration restored. The first cold fixture attempt did not render; starting the clone in the foreground and allowing 15 seconds for decoder startup passed.
- Desktop source-handler regressions cover five playing seconds, pause, Settings disable/manual skip, larger navigation controls, completion-teardown protection and a pause arriving during an in-flight sync. All 67 desktop/catalog/backend checks passed. Clipboard tests cover full-width text, invisible separators and dash variants; the exact original MSI input is unknown.

Sprint 1 final verification: native Windows 0.17.0 fixture playback passed icon/text rendering, five-second auto-seek, Settings disable and unclipped labeled Previous/Next controls in the normal desktop skin at 960px. Initial clipping was a diagnostic fixture using video.js default 300px sizing; correcting the fixture dimensions/skin passed. Live hidden Windows sync dialog accepted a short code containing an invisible pasted separator. Disposable groups were deleted. Live encrypted desktop-to-Android transfer preserved 125 seconds and a newer 45-second rewind back to desktop, using an isolated Android clone. Windows CI 42836650237 succeeded. All Windows checks used hidden isolated profiles.

Sprint 1 publication: final Windows CI 42839701682 succeeded at source 946680a. The clean CI portable package passed hidden native player verification; uploaded installer/portable/source and Android APK sizes and SHA-256 digests matched local artifacts. All 68 desktop/catalog/backend checks passed. Both v0.17.0 and desktop-v0.17.0 were published. The application's live signed-feed verifier accepted Windows 0.17.0 and Android 0.17.0/code25. Actual tablet/Windows production upgrades remain pending; no installed Windows app was foregrounded or restarted.

## Sprint 2 — named groups and private dashboard, 0.18.0

The additive Cloudflare migration created sync_groups/sync_devices without changing existing rooms/history. Live deployment 1c98cd43-7879-44ac-aa4a-c84a35703f06 retained existing service compatibility. Owner dashboard checks returned 401 without authorization and 200 with the separate owner key. The key is stored locally using Windows user encryption; no key was committed or bundled. Names are intentionally administrative metadata, while watch history remains encrypted.

All 72 desktop/catalog/backend tests passed, including existing-key/history migration, group isolation, name/device validation, authenticated owner data and device expiry/deletion. Android build/unit/lint passed. Isolated tablet tests registered Android alongside a desktop device, retained the existing group name/key/history, renamed the group to Household and verified the connected UI hid group creation and displayed two devices. Desktop then read the same name/roster and preserved 125-second resume / 45-second rewind. Disposable groups and clone apps were removed; production Gradle settings restored.

Hidden Windows diagnostic on the clean 0.17.0 package with the new sync adapters passed named group creation, hidden Create while connected, live clipboard short-code pairing, QR, disconnect and dialog reopening. It was a diagnostic overlay, not the release archive. Initial full-overlay diagnostic failed because it replaced standalone-specific Settings; rerunning with only changed adapters passed. Clean packaging also caught mixed text encoding in the new sources; source files were normalized to UTF-8 and release builds restarted. No installed Windows app was foregrounded or restarted.

Sprint 2 release verification: final Windows CI 42844638636 passed all renderer, update-handoff, named-group/pairing and installer checks. Android CI 42844638398 passed. Local clean 0.18.0 verification and hidden named-group UI passed. All downloaded CI package checksums matched, and both sync adapter files in the portable package matched tested source after normal branding/newline handling. Both public releases contain verified package sizes/SHA-256 values; main v0.18.0 includes Android and Windows assets. APK signing certificate remains 1269337c400725a1db05ab7443b76d59c67f9165333d4e87118e1ca182d8b269.

The live updater verifier accepted Windows 0.18.0 and Android 0.18.0/code26. A redundant feed refresh failed because forced line-ending normalization made checkout appear dirty. Source line endings were normalized and publisher 42846995934 then succeeded. This follow-up changes formatting only; release artifacts remain from tested source 720401c. The release tags retain their exact source/build provenance.

Installed tablet follow-up: the normal production auto-update downloaded universal APK SHA-256 40171e48fde114e640d8d7e077a2c8984fb7ace64f7ca89750f70a907c898a33, matching the public package. The owner confirmed Android installation; package is 0.18.0/code26. A private before/after check confirmed its saved pairing state did not change. This particular tablet was already unpaired before the update, so this is not a claim that a personal existing group was migrated on it; that migration was established using disposable paired devices. No production app APK was installed through ADB. Windows installed-app upgrade and exact original MSI pairing input remain deferred while the owner works. Dashboard authorization was rechecked, and disposable device/group rows were absent after cleanup.

## Sprint 3 — optional preloading and cache ownership, 0.19.0

Implementation: Android has one speculative native torrent engine after the current selected video verifies complete, with per-episode isolated cache and adoption by the playback service. Cache registration is separate from decoded-playback activation. Retiring engines and speculative writers protect their cache paths; failed/cancelled preload entries close before deletion. Windows has an independent speculative WebTorrent client, exact episode-file selection and bounded storage checks; navigation releases it before using its cache. Previous-client release and decoded next video are both required before previous-cache deletion. Application exit awaits speculative client closure before global cache cleanup. The checkbox is disabled by default on both platforms.

All 79 desktop/catalog/backend checks passed, including opt-in/completion gates, partial-cache promotion, release/render ordering, low-storage failover, cancellation, path/junction safety and concurrent shutdown waits. Hidden Windows 0.19.0 local-torrent fixture passed actual file download, decoded playback, completed next download, cache reuse on episode switch, previous-cache purge and third-preload cancellation without losing active video (reference/sprint3-windows-preload.log).

Isolated tablet: first HTTP/local-peer fixture attempts stalled at 0 bytes; a cold run also reproduced the pre-existing foreground-service startup timeout. Initial service start was moved to a posted callback after view attachment. The native fixture test then passed opt-in preload, selected-file verification, warm-engine adoption, previous-cache retention while next video views were detached, deletion after decoded playback and cancellation preserving the active episode. For this passing native test, the test APK supplied local fixture pieces through libtorrent's hash-verifying addPiece API; it does not establish a successful native HTTP/peer transfer. The network harness remains an open verification item. Clone apps were removed and production build configuration restored. The installed production app was not directly replaced through ADB. Windows stayed hidden and used isolated profiles.

Final native partial handover: the isolated tablet adopted an incomplete preload (verified header/footer pieces), retained the previous episode while views were detached, then rendered a non-black next-video bitmap and purged the old cache. Production cleanup now requires LibVLC displayedPictures > 0. Disabling preloading retained the active video's cache; app exit closed and purged it. The passing fixture supplied hash-verified pieces through the test APK, not a successful native network transfer. The production APK includes no fixture injection or loopback-interface override. Final Android guards reserve the foreground destination before cancellation cleanup, publish the preload-to-active mode change between threads, and protect retiring engines until close completes.

Windows CI 42886423323 succeeded at abe354b, including renderer, updater, sync dialog and installer checks. Its downloaded package checksums matched and its preload module, player, streamer, Settings template and application-exit source matched the locally tested source after standard branding/newline conversion. The subsequent guards affect Android only; desktop source did not change. Final hidden Windows test also exercised the actual opt-in Settings checkbox and preserved active playback after speculative cancellation.

Sprint 3 publication: Android final source 163456d passed local assemble/unit/lint and GitHub Android checks 42888636572. Its isolated tablet rerun passed partial-cache adoption, non-black video frames, displayed-picture cleanup gating, cancellation and exit purge. Windows source remained identical to tested CI 42886423323/abe354b through the Android-only follow-ups. All Android and Windows public asset sizes/digests matched local release files. Both v0.19.0 and desktop-v0.19.0 were published, and signed-feed workflow 42889072978 succeeded. The actual updater verifier accepted Android 0.19.0/code27 and Windows 0.19.0. Installed-tablet update handoff is in progress; installed Windows update is left to the owner while working.

Installed tablet 0.19.0 follow-up: the production in-app updater downloaded SHA-256 9b9ed93b316897d5f085ca85d36ba4f68594b6b9686fc9d5e89366d958837115, matching the published universal APK, and Android package inspection confirmed 0.19.0/code27. No production APK was installed through ADB. Before/after encrypted pairing-entry digests were unchanged; this tablet was already unpaired. Pre-load next episode remains disabled after upgrade, as intended. Windows installed-app update was not invoked; the owner's laptop stays available for work. The native Android local HTTP/peer harness remains an open test item; hash-verified native fixture/video/partial handover tests passed.

## Desktop episode toolbar fix — 0.19.1

The screenshot exposed a duplicate selector: the title header also has vjs-control-bar. Navigation had been appended to both header and toolbar, and the previous fixture used the default video.js skin instead of the product skin. The player now excludes player-header-background, inserts icon-only Previous/Next immediately beside Pause, retains the theme's control-bar height and shifts the seek region to leave space. Hover titles and accessible labels remain. The retry-source insertion also excludes the header.

The hidden probe now uses the actual vjs-popcorn-skin and a representative title-header control-bar decoy. It passed single-instance navigation, Pause adjacency, icons-only display, aligned baselines, no seek overlap in narrow/windowed/fullscreen-style layouts, transparent backgrounds and inherited auto-hiding. Existing skip icon/text/countdown and decoded fixture video passed. 79 desktop/catalog/backend checks passed. Windows installed app was not opened or restarted.

Desktop 0.19.1 publication: final hidden generated 0.19.1 fixture passed actual-theme single-toolbar icon navigation, Pause adjacency, seek separation, inherited hiding, existing skip settings/countdown and video decoding. Windows CI 42890484434 passed renderer, update helper, pairing dialog and installer checks at 8724aca. Downloaded package digests and exact branded player source matched the tested code. All four public release asset sizes/digests verified. Signed-feed runs 42892056375 and 42892060984 succeeded; the live updater accepted Windows 0.19.1 while Android remains 0.19.0. README and main-release notes link the desktop hotfix prominently. No installed Windows app was foregrounded, installed or restarted.


## Source discovery — 0.20.0

Added independent trusted Nyaa episode/batch discovery and bounded metadata parsing on both platforms. Exact title, TV identity, episode member and torrent hash are checked; a retryable provider failure is not cached as a no-source result. Indexed Kitsu fallback shares Torrentio infrastructure and is not counted as independent redundancy. No non-working alternate endpoint was added.

Passed: 87 desktop/catalog/backend checks; Android unit/build/lint. Isolated physical tablet native parsing returned Black Clover S1E1 indexed=44, independent=1, exact member `[SubsPlease] Black Clover - 01 (1080p) [AA3F8F16].mkv`. Desktop live injected primary outage independently selected the verified 1080p batch member. Its 4,740,399-byte metadata explains the previous 4 MB rejection; fetches are now capped at 8 MiB. TV MAL identities 34572/30694 were checked live against matching IMDb/typeTV; Black Clover season 1 has170 mapped episodes, Dragon Ball Super retains explicit split-season mappings. A reverse IMDb lookup returned a related Black Clover OVA and is deliberately not used.

This closes discovery implementation, not a promise that every external torrent has reachable peers. Newly discovered public-torrent video playback was not asserted. Production update installation and final release publication are tracked below when completed. Installed Windows was never foregrounded or modified.

Final release checks: source 9f5442b passed Android GitHub checks 42897589456 and Windows build/renderer/update-helper/sync/installer checks 42897589356. Local hidden clean-profile Windows startup passed. Desktop publisher 42897902749 verified four asset hashes/sizes and published desktop-v0.20.0. Live default title-detail loading retained Black Clover170 episodes and Dragon Ball Super131 episodes; live indexed lookup returned44/40 sources respectively and Severance S1E1 returned 59. Repeated injected-index outage selected the independent verified Black Clover1080p member.

Publication completed: v0.20.0 contains eight locally verified Android/Windows assets; desktop-v0.20.0 contains four verified standalone assets. Signed-feed workflow 42898868801 succeeded. The actual updater verifier fetched and accepted Android0.20.0/code28 and Windows0.20.0. Production installations were not changed: Android remains available through its normal system-confirmed update flow, and installed Windows was left untouched while the owner worked.


## Sprint 4 and final-roadmap work — 0.21.0

Implemented native Android/Windows Home with local recommendation ranking and unchanged paired progress protocol. Home groups history by IMDb title, selects the latest episode event (including rewinds), excludes completed entries and refreshes after sync. Diverse cold-start rows, recently added/type/language rails and bounded Android image/row work are included. Standard detail/quality flows are retained; Home startup is selectable on Windows. Existing explicitly chosen non-Movies startup pages are preserved.

Verified and bundled the public signed catalog using the pinned key and exact file digests: 866 movies (7 Korean, 102 Hindi), 10,029 series (1,158 Korean, 559 Hindi), 2,681 anime and 252 recent anime releases. Metadata coverage is distinct from source availability/audio language. Live EZTV returned 5 exact sources each for Severance S1E1/S2E10; injected index outage passes. Searches are bounded; a partial scan is retryable rather than falsely exhaustive. Anime reverse mappings require matching IMDb/title and TV/ONA type. My Hero Academia retained 170 episodes across 8 seasons while mapping only its 13 verified first-season MAL episodes; later offsets are not invented.

Passed isolated physical-tablet Home startup/remote focus/progress refresh/catalog navigation; live independent series=5, injected primary-outage regular-series timestamp fallback=1 and preceding-provider-outage anime fallback=2. Native fixture player checks passed manual intro/outro, five-second automatic intro/credits, settings disable, decoded frames after subtitle/settings return, advancing position and preserved user pause. A notification-permission dialog initially blocked the test; granting notifications only to the disposable test package resolved that harness issue. No production APK was installed through ADB.

Jikan page 2 timed out. Optional filler/recap fetching now runs outside episode detail loading, preserves cached explicit flags and leaves unknown episodes unlabelled. Page-boundary/boolean/mapping checks pass with controlled data; no live filler-page success is claimed.

NW.js 0.119.2 official SDK digest e99aa554cf38e42b2ed47e20e7fd1320330c4f21483a0e792d4965862c6e10d0; matching community codec ZIP digest 5c173adcebb48f7796233c34b6c9339923ef5909b045824c8d411e871dcb40ac. Native hidden video decoding, Home/standard Severance detail flow, automatic intro/credits controls, English subtitle download/rendering (540 cues), real local fixture preload/next-cache reuse/previous-cache purge/cancellation, and updater-helper restart passed on the modern runtime. Hidden Chromium suspends CSS transitions; the control test disables that transition only in the diagnostic to verify the inactive visibility target, not foreground animation timing. The first preload run lacked its fixture server and failed with ECONNREFUSED; rerunning with that server passed. Installed Windows was not foregrounded, installed or restarted.

100 Node checks and Android unit/build/lint pass. Windows publisher signing is externally pending; no certificate was purchased or identity application submitted. See WINDOWS-SIGNING.md. Production release and signed-feed acceptance follow below when complete.

Low Memory follow-up: the isolated physical tablet passed Home startup, remote focus, sync refresh, known-next episode card and catalog navigation in forced Low Memory mode. The observed Java heap at the end of that test was 36 MiB; this is not a measurement of the actual projector or total video-playback memory. Home reads catalog rows incrementally and retains lean metadata; full catalog browsing is unchanged. Completed-episode Home cards use the same canonical episode order as title details, and the hidden Windows test verified that Up next opens Severance S1E2 after S1E1 is completed.


Final 0.21.0 publication: Android CI 42981754595 and Windows CI 42981754619 passed. Windows CI included installer/uninstaller checks on an isolated runner. Desktop publisher 42982977670 succeeded. Both public release pages contain verified artifact sizes and SHA-256 digests: v0.21.0 has eight Android/Windows assets, desktop-v0.21.0 has four standalone assets. The actual updater fetched and verified the signed public feed, accepting Android 0.21.0/code29 and Windows 0.21.0. Production installations were not replaced; Android retains its system confirmation and installed Windows was left untouched.

An isolated official 0.20.0-to-0.21.0 profile migration passed across Node 13.10.1 to 26.12.1, preserving 125-second progress, disabled auto-skip, enabled preload, TV-series startup and anonymous subtitle mode. The same isolated application data/profile paths were retained. This does not claim a personal-account or installed-owner-profile migration.

The separate native Android real-network preload harness still stalled at zero bytes/no peers despite test-only loopback settings. Hash-verified native fixture handover remains verified; successful native HTTP/peer preload transfer is not claimed. External live filler-page coverage and broader Korean source audio/subtitle timing remain bounded verification items.


## Shared Home recommendations — 0.22.0

106 Node checks and 179 Android unit checks cover domain-separated encrypted Home snapshots, desktop-to-Java cryptographic vectors, cross-platform row generation, different/missing local catalogs, bounded metadata, conflict retry adopting the winner, stale-device watched-identity retention, group-scoped cache and Low Memory prefix behavior. Existing progress encryption vectors remain unchanged. Build/lint and standard hidden Windows Home/next-episode/detail/navigation smoke pass.

Live disposable-group test: desktop uploaded encrypted progress and recommendations; the isolated native Android verification app downloaded identical rows and displayed the shared first card even with its Home pool forced empty. Native watched-title update removed that title from shared recommendations. Desktop fetched the seven updated rows (84 card references) without re-ranking. The actual isolated Windows renderer displayed all seven rows in identical order, excluded the newly watched title and passed disconnect/group isolation. Both disposable encrypted rooms and group/device metadata were deleted. The verification package was uninstalled and production build configuration restored; no production Android APK was installed through ADB.

Recommendations are eventually consistent and need 0.22.0 on participating devices. Existing groups need no re-pairing or backend schema/deployment change. Offline devices use a scoped cached snapshot; source availability can still vary. The separate recommendation storage uses the existing service quota and 180-day expiry. Public Windows signing is intentionally not planned per owner decision; signed update-feed verification remains enabled.
