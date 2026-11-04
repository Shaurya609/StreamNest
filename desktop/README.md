# StreamNest desktop patch 0.8.0

This is an overlay patch for the installed Windows Popcorn Time **0.5.1** application. It is not a standalone installer or a replacement for its native dependencies.

## Included

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

- 10 Node test groups passed, including six sort contracts/reverse direction, filter intersection, 12-page pagination, exact file selection, quality fallback, canonical numbering, live-release matching and cache-boundary tests.
- Live movie catalog returned 24 titles with additional pages. The installed movie grid was inspected.
- Live series page 15 returned 24 titles. The installed series grid appended more results while scrolling.
- One Piece metadata mapped absolute episode 1179 to Season 23 Episode 24; 1,209 canonical/special entries were returned at the test time.
- Attack on Titan Season 2 Episode 1 played in the installed desktop player. Starting Episode 2 removed the previous episode cache directory and created an Episode 2 directory.
- Anonymous OpenSubtitles.org login, English search and download of a valid 92,044-byte timed subtitle file succeeded in a separate service check.
- Automatic exhaustion of all six dead sources and every subtitle/UI language combination have not been reproduced end-to-end. Availability and source seed counts remain external/provider dependent.

Run portable tests from the repository root with `node --test desktop/tests/*.test.js` using Node 18 or later. The installed application continues to use its bundled NW.js/Node runtime.

## License

The overlay contains modifications to Popcorn Time desktop code and is distributed under its GNU GPL v3 terms. See [LICENSE.txt](LICENSE.txt) and the [upstream project](https://github.com/popcorn-official/popcorn-desktop). The Android implementation has a separate codebase; this scoped desktop license does not select a license for unrelated Android source.
