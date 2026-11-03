# Desktop pending work

Desktop changes remain paused while the laptop is in use. This list distinguishes unfinished work from installed changes awaiting verification.

1. **Episode cache cleanup — not implemented on desktop.** Port mobile's cleanup on switching to a different anime/series episode, after the previous torrent stops. Preserve the current episode, unrelated custom-folder files and same-episode replay/quality changes.
2. **Anime series — integration and playback verification pending.** An anime-series adapter is present, but the installed UI needs testing with populated series, episode navigation and active sources. Mobile's newer explicit absolute-to-season/episode mapping and trusted live-release attachment have not been ported.
3. **Series pagination — end-to-end verification pending.** The complete-index adapter and session snapshots are installed. Verify browsing well beyond row 8, stable append behavior, end-of-list handling, search and filter changes, including Hindi series. Port any remaining mobile fixes found necessary.
4. **OpenSubtitles.org anonymous access — activation and player verification pending.** The Use without account option and handler exist. Verify connection, language listing, search and actual subtitle download in the desktop player. The account-specific .org login rejection remains unresolved; anonymous access does not fix account login.
5. **Catalog accuracy and sorting — regression verification pending.** Hindi inclusion and movie pagination/sorting were previously patched and checked, but the latest series/anime adapters still need live checks for all six sorts, both directions, combined language/genre/quality filters, duplicate identities and available HD sources. Trending currently uses recent-upload windows rather than a global popularity chart.
6. **UI polish — pending.** Check current adapter integration, loading/error/end states, disabled quality choices and episode details in the installed desktop application.

Desktop already has its own full-screen player and cache-folder/exit-cache features. Their behavior should be regression-checked when desktop work resumes; this mobile release does not replace or certify them.

7. **Older episode quality fallback — not ported.** Match mobile 0.7.3: Auto and manual episode qualities, SD/DVD support, reported-seed alternatives, lower-quality anime release lookup, movie HD minimum unchanged. Alternative-provider/season-pack support remains future work on both clients.

8. **Alternative episode sources and season packs — not ported.** Match mobile 0.7.4: episode-specific source discovery, exact file-index selection, bounded startup failover/manual retry, and isolated per-episode pack caches with cleanup after switching.
