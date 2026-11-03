# Validation — StreamNest 0.7.4

- Local Android build succeeded with JDK 17 and compile SDK 36.
- 108 unit tests passed; Android lint reported zero errors.
- Android 14 Samsung tablet: catalog pagination, movie quality selection, full-screen playback, subtitles, control hiding and cache cleanup were tested.
- One Piece and Interstellar reached actual playback in earlier mobile checks.
- Attack on Titan Season 2 Episode 1 played from a 97-file torrent pack. File index 33 was selected; a decoded frame and VLC Playing event were observed.
- Manual source switching was exercised. Selecting Season 2 Episode 2 selected file 34 from the same pack and removed both previous-episode cache directories.
- Unit tests cover startup recovery timing; a complete automatic multi-source exhaustion scenario has not been exercised on the TV or projector.
- Croma 55-inch Google TV: the user confirmed that the APK works. This is user-reported device verification, not an automated TV performance benchmark.
- The 1 GB Android 14 projector remains untested.
- GitHub Actions checks are separate from these local results; see the repository's Actions tab for their status.

Live observations are specific to the device/network and test time. Source seed reports and previous successful playback do not guarantee continued peer availability. See RELEASE-0.7.4.md for detailed behavior and limits.
