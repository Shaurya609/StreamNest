# StreamNest 0.9.0 — episode playback controls

Android and desktop patch 0.9.0 add previous/next episode icons for canonical anime/series lists and automatic next episode at natural playback completion. Navigation crosses regular-season boundaries, keeps Specials separate and resolves the requested episode's sources before stopping the current player. Missing sources and future episodes are reported rather than skipped. Auto/selected quality, exact pack member indexes, subtitle preferences and existing episode-cache cleanup are retained.

Intro/credits skip icons appear inside validated community timestamp ranges from IntroDB or AniSkip. Lookups require no user key. Exact episode identity, confidence/bounds and (for AniSkip) matching runtime are checked. Only explicitly mapped first-season MAL IDs are used; later-season numbering is not guessed. Provider outages or absent timestamps leave normal playback available. Skipping remains manual because different release edits can shift timings. Credits seek to their segment end so later scenes remain playable.

Playback settings can disable autoplay or timestamp buttons. Desktop enables autoplay once on migration and preserves later choices. Preloading stays disabled to protect episode-cache cleanup. Navigation is available independently of autoplay; external players control their own behavior.

Universal, ARM32 and ARM64 APKs are debug-signed development builds for Android 9+. Desktop is an overlay for installed Popcorn Time 0.5.1: close it and run Install.ps1, which creates a backup. No desktop mouse/keyboard interaction was used to test this release while the owner worked; full native desktop playback verification remains pending.

Checks: 142 Android unit tests, zero lint errors and 37 desktop/publisher test groups passed. Tablet regressions covered decoded video, segment seeking, previous/next, natural-end autoplay, unavailable-next protection, autoplay-off behavior and subtitle-return playback. Live IntroDB returned matching credits for a sample episode; AniSkip was unavailable (HTTP 403) on the test network and remains an optional fallback.
