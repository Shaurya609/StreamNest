# Validation — StreamNest 0.7.5

- Local Android build succeeded with JDK 17 and compile SDK 36.
- 111 unit tests passed; Android lint reported zero errors.
- Android 14 Samsung tablet: catalog pagination, movie quality selection, full-screen playback, subtitles, control hiding and cache cleanup were tested.
- One Piece and Interstellar reached actual playback in earlier mobile checks.
- Attack on Titan Season 2 Episode 1 played from a 97-file torrent pack. File index 33 was selected; a decoded frame and VLC Playing event were observed.
- Manual source switching was exercised. Selecting Season 2 Episode 2 selected file 34 from the same pack and removed both previous-episode cache directories.
- Unit tests cover startup recovery timing; a complete automatic multi-source exhaustion scenario has not been exercised on the TV or projector.
- Croma 55-inch Google TV: the user confirmed that the APK works. This is user-reported device verification, not an automated TV performance benchmark.
- The 1 GB Android 14 projector remains untested.
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
