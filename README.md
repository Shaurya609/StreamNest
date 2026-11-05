# StreamNest

Native Android app for browsing movies, series and anime, with torrent-based playback through libtorrent and LibVLC.

| Download | Package |
| --- | --- |
| [Android / Google TV / projector](https://github.com/Shaurya609/StreamNest/releases/latest) | Universal APK — includes ARM32 and ARM64 |
| [Windows desktop — standalone preview](https://github.com/Shaurya609/StreamNest/releases/tag/desktop-v0.15.1) | Installer or portable ZIP; Popcorn Time is not required |

[All releases](https://github.com/Shaurya609/StreamNest/releases) · [Desktop setup](desktop/STANDALONE.md) · [Pending verification](PENDING-FIXES.md)

Windows 0.12.0–0.13.1 users: install 0.13.2 once to repair the update handoff. Android 0.12.0 and newer supports application updates. Future versions check and download automatically; Windows offers Install and restart, while Android uses its system installation confirmation. [Update behavior and publishing](UPDATES.md).

Automatic series/anime episode selection and cross-device resume are available in 0.14.0. [Pair your devices](WATCH-SYNC.md).

## Features

- Signed application-update checks on launch, background downloads and manual update checks.
- Movie, series and anime catalogs with search, continuous pagination, English/Hindi filters and sorting.
- Persistent saved catalogs/search, bounded failover and signed background snapshot updates.
- Posters, descriptions, seasons and canonical episode details; inline quality selection and direct Watch Now.
- Movies in 1080p/2160p. Episodes default to Auto with 720p/SD fallback and explicit manual choices.
- Additional episode source discovery, exact season-pack member selection and bounded startup source recovery.
- Previous/next episode icons and automatic next episode on natural playback completion, including season boundaries.
- Intro/credits skip controls when community timestamps match the episode; credits skipping preserves later scenes.
- Full-screen landscape playback, automatically hiding icon controls, seeking and remote navigation.
- Transparent SVG branding and Android vector launcher/TV artwork.
- Embedded/local subtitles, automatically renewed anonymous OpenSubtitles.org access and OpenSubtitles.com integration; API key and account details are entered inside the app.
- Storage and folder picker for the video cache, episode-by-episode cleanup and cleanup on app exit.
- TV launcher support and Low Memory mode.

External catalogs and peers determine actual availability. Seed counts are provider reports rather than connected-peer guarantees. Source recovery tries up to six candidates and cannot guarantee every episode will stream.

## Install

Open [Releases](https://github.com/Shaurya609/StreamNest/releases/latest) and download an APK:

| APK | Device |
| --- | --- |
| Universal | Recommended when the device architecture is unknown; includes ARM32, ARM64 and x86_64 |
| ARM64 | 64-bit ARM Android firmware |
| ARM32 | 32-bit ARM Android firmware |

Android 9 or later is required. The APKs are debug-signed development builds. Enter subtitle credentials inside the app; no account credentials or API keys are supplied in this repository.

**Device status:** tablet playback has been tested on Android 14. Installation and operation on the Croma 55-inch Google TV were confirmed by the user. Basic operation on the 1 GB Android 14 projector was also confirmed by the user; longer performance and external-storage tests remain pending. See [projector setup](PROJECTOR-SETUP.md) and [validation](VALIDATION.md).

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

This repository contains the Android app, a standalone Windows desktop preview and an overlay patch for Windows Popcorn Time 0.5.1. See [desktop installation and verification](desktop/README.md). Desktop runtime account settings, backups and media caches are excluded. Production signing, casting, a background download library and broader device testing remain future work.

The interface and workflow were informed by Popcorn Time, with an independently implemented Android UI and original StreamNest artwork. This is not an official Popcorn Time release.

## Dependencies and providers

See [third-party references](THIRD_PARTY.md). Catalog/provider endpoints are external and may change or become unavailable. This repository contains source code and catalog metadata, not video media.

## Reliability and desktop packaging

Saved catalogs/search, endpoint cooldown, independent TVmaze series discovery and signed GitHub/CDN snapshot distribution are implemented. See [catalog resilience](CATALOG-RESILIENCE.md) for behavior, verification and remaining limits.

The standalone Windows x64 preview has its own executable, profile/cache, approved branding and per-user installer. It does not require Popcorn Time. See [standalone setup and build instructions](desktop/STANDALONE.md); the older overlay remains available separately.

## Episode playback

The built-in player exposes previous/next icons for canonical anime and series episode lists, even with autoplay disabled. Regular seasons continue in numeric order; Specials stay in their own sequence. Future or source-less adjacent episodes are reported instead of silently jumping to a later episode. Source selection is resolved before stopping the current player, and exact season-pack member indexes are retained. A different episode uses the existing cache-cleanup policy.

Autoplay starts the next episode after a natural end near the media runtime, rather than at an estimated credits time. Turn it off in Playback & storage (Android) or Playback settings (desktop). Desktop enables autoplay once when migrating to the new episode controls; later choices are preserved. External players manage their own navigation/autoplay.

Skip icons appear only inside validated intro/outro ranges from [IntroDB](https://introdb.app/docs/api) or [AniSkip](https://api.aniskip.com/api-docs). Lookup is read-only and needs no account key. IntroDB uses exact IMDb/season/episode identity; AniSkip requires an explicit MAL ID, first-season mapping and a matching reported runtime. Later-season MAL offsets are not guessed. Unsupported episodes or provider outages keep normal controls. Release edits can still shift community timings, so skipping is manual. Credits skip to the end of the marked segment, not directly to the next episode. Disable these icons in playback settings if preferred.

Skip timestamp attribution: SkipDB (https://skipdb.tv) data is licensed under ODbL 1.0 and its service-provider reciprocity term (https://skipdb.tv/license). Read-only episode lookups are cached locally for playback.
