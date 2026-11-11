# StreamNest 0.21.0

Android / Google TV / projector APKs and standalone Windows preview.

- New Home page: Continue Watching, mixed genre-based recommendations, recently added content and movie/series/anime/Hindi/Korean rows. New users get discovery rows; watch history personalizes returning users' suggestions locally.
- Resume cards show the latest active episode and timestamp and refresh after progress sync. Opening a card uses the existing title, quality and playback controls. Completed episodes offer the next known aired episode; completed movies and known finished series are excluded.
- Android Home bounds row sizes in Low Memory mode and loads posters as they become visible. Home search offers Movies, Series or Anime.
- Bundle a verified signed snapshot with 866 movies, 10,029 series (including 1,158 Korean-language entries) and 2,681 anime series. These are metadata counts, not guaranteed playable-source counts or English-audio confirmations.
- Add independent anonymous EZTV episode discovery with exact IMDb/season/episode checks, bounded paging and retries that continue scanning. Existing movie mirrors and trusted anime/batch discovery remain available.
- Broaden verified anime identity mapping. First-season anime metadata enriches compatible full-series metadata rather than truncating later seasons; no guessed MAL offsets are attached.
- Fetch optional filler/recap labels in the background with bounded persistent caching. A slow/unavailable metadata provider no longer delays episode details; unknown labels remain unknown.
- Honor timestamp providers' explicit no-intro/no-outro results. Verify manual and automatic intro/credits skipping and preservation of later content on native players.
- Modernize Windows from NW.js 0.44.5 to 0.119.2 with pinned matching codecs. Windows 10 or newer is required. Retain the installer, shortcut option and verified update flow.

Validation: 100 desktop/catalog/backend tests; Android unit/build/lint; isolated physical-tablet Home/progress/navigation and native player checks; hidden Windows Home/title flow, video, intro/credits controls, subtitles, local torrent/cache handover and update handoff. Live independent series and regular-series/anime timestamp fallbacks passed. Live filler page-2 verification was unavailable because Jikan timed out; synthetic page-boundary and identity checks passed. See VALIDATION.md for exact limits.

Windows remains an unsigned preview while external signing identity/approval is pending. Existing settings, accounts and sync groups use the same storage and update identities. Public sources, language tracks and community timestamps remain title/release dependent.
