Standalone Windows x64 preview: see [STANDALONE.md](STANDALONE.md). The instructions below apply to the separate legacy overlay patch.

# StreamNest desktop patch 0.9.0

This is an overlay patch for the installed Windows Popcorn Time **0.5.1** application. It is not a standalone installer or a replacement for its native dependencies.

## Included

- Previous/next episode icons independent of autoplay, canonical season-boundary navigation, source resolution before stopping playback and automatic advancement on natural end.
- Optional intro/credits skip icons from validated IntroDB/AniSkip community timestamps. Credits jump to the marked segment end, preserving later content. Timestamp coverage is not universal; later-season anime IDs are not guessed.
- Autoplay is enabled once during migration; Playback settings can disable it. Preloading remains disabled to preserve previous-episode cache cleanup.

- Persistent saved movie search/filter/sort/paging, validated metadata fallback, endpoint cooldown and signed GitHub/CDN catalog updates.
- Independent TVmaze series search with exact IMDb identity and metadata-only provenance.
- Packaged fallback catalogs: 777 HD movie records, 8,652 series, 30 anime series and 69 latest anime releases.

- Hindi movie snapshot with 91 exact IMDb matches and confirmed-by-provider HD sources, background refresh, a working Hindi filter and language-balanced mixed Trending pages.
- Regional Hindi series rank correction so Trending/Last added no longer bury them behind thousands of English entries.
- Anonymous subtitle session renewal at launch/on expiry, shared concurrent login and one retry after a 401/406 session rejection.

- Movie API endpoint failover after the former endpoint began returning HTTP 521.
- Full-index series browsing with stable page snapshots, Hindi inclusion and six sort options.
- Canonical episode metadata, explicit anime absolute numbering, regular seasons before Specials and collision-free IDs for long anime seasons.
- Episode Auto quality with 720p/SD fallback; movies retain the HD minimum.
- Alternate episode sources, exact torrent-member indexes and selection of only the requested season-pack video.
- Startup source recovery and a retry icon in loading/player controls. Recovery is bounded to six sources.
- Separate episode cache directories. Starting a different episode waits for the previous torrent to close, then removes previous episode data. Same-episode source/quality changes retain their caches. Preloading is disabled so it cannot evict the episode still playing.
- Trusted individual anime releases, exact series-name matching, explicit absolute-episode attachment and a Latest episodes view.
- Anonymous OpenSubtitles.org login/search through bounded HTTPS XML-RPC. Switching to anonymous mode preserves saved account details; no credentials are included in the patch.
- Disabled Watch controls for unavailable sources, clearer quality choices and startup-window handling.

## Apply

Close Popcorn Time, extract the ZIP and run `Install.ps1` from PowerShell. If required, supply `-InstallDir` with the folder containing `Popcorn-Time.exe` and `package.json`.

The installer verifies version 0.5.1 and patch checksums, backs up existing files under `%LOCALAPPDATA%\StreamNest\desktop-backups`, then copies only the listed application files. It does not copy or alter runtime account databases. Reopen Popcorn Time afterward.

Use Settings → OpenSubtitles.org → **Use without account** to activate anonymous access. Password-based .org account login is a separate upstream service behavior and is not claimed repaired by anonymous mode.

For rollback, close the app and restore the corresponding files from the backup folder. Newly added `streamnest-*` modules can remain unused when the original entry points are restored.

## Verification

- The current full desktop/publisher suite has 37 passing groups. Earlier playback checks below predate 0.9.0; the new desktop controls have background fixture tests, including the real ended handler, and still need a hands-on player check.
- Earlier checks included including six sort contracts/reverse direction, filter intersection, 12-page pagination, exact file selection, quality fallback, canonical numbering, live-release matching and cache-boundary tests.
- Live movie catalog returned 24 titles with additional pages. The installed movie grid was inspected.
- Live series page 15 returned 24 titles. The installed series grid appended more results while scrolling.
- One Piece metadata mapped absolute episode 1179 to Season 23 Episode 24; 1,209 canonical/special entries were returned at the test time.
- Attack on Titan Season 2 Episode 1 played in the installed desktop player. Starting Episode 2 removed the previous episode cache directory and created an Episode 2 directory.
- Anonymous OpenSubtitles.org login, English search and download of a valid 92,044-byte timed subtitle file succeeded in a separate service check.
- Automatic exhaustion of all six dead sources and every subtitle/UI language combination have not been reproduced end-to-end. Availability and source seed counts remain external/provider dependent.

Run portable tests from the repository root with `node --test desktop/tests/*.test.js` using Node 18 or later. The installed application continues to use its bundled NW.js/Node runtime.

## License

The overlay contains modifications to Popcorn Time desktop code and is distributed under its GNU GPL v3 terms. See [LICENSE.txt](LICENSE.txt) and the [upstream project](https://github.com/popcorn-official/popcorn-desktop). The Android implementation has a separate codebase; this scoped desktop license does not select a license for unrelated Android source.
