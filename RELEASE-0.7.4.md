# StreamNest 0.7.4 — alternative episode sources and season packs

Mobile-only update. Episode details now supplement the configured provider with Torrentio's episode-specific Stremio stream records. Requests use the canonical IMDb ID, season and episode. Provider failures preserve existing sources. Results are cached for ten minutes; stale selection responses do not replace a different episode's sources. A missing-source message can be tapped to retry discovery.

## Playback and recovery

- Alternative torrent records must supply a valid info hash and explicit video file index. Direct/debrid links, camera labels, unidentified qualities and missing indexes are excluded. Reported seed counts are ranking hints, not connected-peer guarantees.
- Watch Now still starts directly. Up to six ranked candidates are passed to the player. Manual quality choices stay within that resolution; Auto uses the existing quality/seed/compatibility policy.
- The player tries the next candidate after 45 seconds without startup download progress, on an initial playback error, or after three minutes without starting video despite progress. Automatic switching stops once video is playing and does not occur while explicitly paused or while the player lacks window focus.
- A new retry icon lets the user try the next candidate manually, preserving the current playback position. The player's source counter shows the active attempt. Exhausted candidates require returning to details to change quality or retry discovery.
- Subtitle responses from an old source attempt cannot be attached to a new attempt.

## Season packs and cleanup

- The exact episode member is selected using the provider's file index, with filename verification when supplied. It never selects the largest file as a substitute for an indexed episode.
- For episode torrents without an explicit index, the engine can identify SxxExx, NxNN or explicitly mapped anime absolute filenames. Ambiguous multi-video packs are rejected rather than guessing. Movies retain largest-video selection.
- Only the chosen member receives download priority. Torrent piece boundaries may require small amounts of adjoining-file data.
- Episodes use separate cache directories even when their sources point to the same season pack. Changing episodes stops the old writer and removes previous episode folders, including their pack fragments. The new episode's selected member and cache remain intact. Same-episode source/quality changes retain that episode's caches until a different episode starts or the app exits.

## Verification

- 108 unit tests passed; Android lint reported zero errors. New coverage includes indexed selection, filename/index rejection, ambiguous packs, smaller requested episodes, absolute numbering, stream parsing, manual-quality boundaries and recovery timing.
- Attack on Titan Season 2 Episode 1 played on the connected Android 14 tablet from a 97-file pack. The engine selected file 33; 18 connected peers and approximately 5,729 KB/s were observed while buffering. VLC reached Playing and a video frame was inspected. This is a tablet observation, not a projector performance guarantee.
- The manual retry control started a second source attempt for the same episode.
- Selecting Season 2 Episode 2 selected file 34 of the same 97-file pack. Both old Episode 1 cache directories disappeared, leaving the new episode directory. This verifies pack selection and cleanup; Episode 2 was stopped after that check.
- The final ARM64 build is installed on the tablet. Universal and ARM32 variants are supplied in Downloads and dist/.

External providers and peers can still be unavailable. This does not guarantee every title or language has a working source. Desktop is unchanged and tracked in DESKTOP-PENDING.md.

## References

- [Stremio stream protocol: infoHash and fileIdx](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md)
- [Torrentio project](https://github.com/TheBeastLT/torrentio-scraper)

The project implements a protocol client; it does not bundle third-party scraper source or account credentials.
