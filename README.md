# StreamNest

Native Android app for browsing movies, series and anime, with torrent-based playback through libtorrent and LibVLC.

**Current Android version: 0.7.5. Desktop patch: 0.8.0.** [Download an APK](https://github.com/Shaurya609/StreamNest/releases/latest) · [Release notes](RELEASE-0.7.5.md) · [Desktop backlog](DESKTOP-PENDING.md)

## Features

- Movie, series and anime catalogs with search, continuous pagination, English/Hindi filters and sorting.
- Posters, descriptions, seasons and canonical episode details; inline quality selection and direct Watch Now.
- Movies in 1080p/2160p. Episodes default to Auto with 720p/SD fallback and explicit manual choices.
- Additional episode source discovery, exact season-pack member selection and bounded startup source recovery.
- Full-screen landscape playback, automatically hiding icon controls, seeking and remote navigation.
- Embedded/local subtitles and OpenSubtitles.com integration; API key and account details are entered inside the app.
- Configurable video-cache location, episode-by-episode cleanup and cleanup on app exit.
- TV launcher support and a projector/low-memory mode.

External catalogs and peers determine actual availability. Seed counts are provider reports rather than connected-peer guarantees. Source recovery tries up to six candidates and cannot guarantee every episode will stream.

## Install

Open [Releases](https://github.com/Shaurya609/StreamNest/releases/latest) and download an APK:

| APK | Device |
| --- | --- |
| Universal | Recommended when the device architecture is unknown; includes ARM32, ARM64 and x86_64 |
| ARM64 | 64-bit ARM Android firmware |
| ARM32 | 32-bit ARM Android firmware |

Android 9 or later is required. The APKs are debug-signed development builds. Enter subtitle credentials inside the app; no account credentials or API keys are supplied in this repository.

**Device status:** tablet playback has been tested on Android 14. Installation and operation on the Croma 55-inch Google TV were confirmed by the user. Testing on the 1 GB Android 14 projector is pending. See [projector setup](PROJECTOR-SETUP.md) and [validation](VALIDATION.md).

## Build

Requires JDK 17 and the Android SDK (platform 36). Configure `ANDROID_HOME`, or create an untracked `local.properties` containing your own `sdk.dir`.

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS:

```sh
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APKs are generated in `app/build/outputs/apk/debug/`. GitHub Actions runs the build, unit tests and lint on pushes and pull requests. Build outputs, local SDK paths, credentials, diagnostic captures and desktop backups are excluded from Git.

## Playback and cache behavior

The torrent engine verifies downloaded pieces and serves the chosen video over a loopback-only HTTP range endpoint. Closing the player stops its torrent. Controls hide after 3.5 seconds during playback and return with a tap or directional/OK remote input.

Switching to another episode stops the previous torrent and removes its recorded video folders. Season-pack episodes use isolated cache folders and only the chosen member receives download priority; adjoining torrent-piece fragments may also be written. Replaying a removed episode downloads it again. Same-episode source or quality changes retain that episode's caches until another episode starts or the app exits.

Use Back from the catalog or Settings → Exit StreamNest to clear video cache. After an abrupt Android force-stop, leftover cache is cleared on the next launch. Saved titles, settings and account credentials are preserved. Cleanup in a custom folder targets recorded StreamNest entries, not unrelated files.

## Project scope

This repository contains the Android app and an overlay patch for Windows Popcorn Time 0.5.1. See [desktop installation and verification](desktop/README.md). Desktop runtime account settings, backups and media caches are excluded. Production signing, casting, a background download library and broader device testing remain future work.

The interface and workflow were informed by Popcorn Time, with an independently implemented Android UI and original StreamNest artwork. This is not an official Popcorn Time release.

## Dependencies and providers

See [third-party references](THIRD_PARTY.md). Catalog/provider endpoints are external and may change or become unavailable. This repository contains source code and catalog metadata, not video media.
