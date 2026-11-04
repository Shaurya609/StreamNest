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
