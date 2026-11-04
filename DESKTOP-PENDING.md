# Desktop status — patch 0.9.0

New user-reported regressions and follow-up work: see [PENDING-FIXES.md](PENDING-FIXES.md). Desktop anonymous subtitle login/rendering and standalone release discoverability remain pending, despite earlier service-level checks. Mobile anonymous auto-login also needs fresh-launch verification.

The desktop backlog has been implemented in the overlay patch and applied to the local Popcorn Time 0.5.1 installation. See [desktop/README.md](desktop/README.md) for detailed behavior and validation.

Implemented in 0.9.0: previous/next episode controls, season-boundary navigation, natural-end autoplay, validated intro/credits timestamps and playback toggles. Full desktop player UI verification remains pending while the owner is working; background fixtures cover the new paths.

Implemented in 0.8.2: persistent saved catalogs/search, movie endpoint cooldown, protected empty refreshes, pinned fallback paging, independent TVmaze series search and signed snapshot updates. See CATALOG-RESILIENCE.md.

Implemented in 0.8.1: visible Hindi Trending films, confirmed-HD Hindi movie discovery/snapshot, regional series ranking and automatic anonymous subtitle session renewal.

Previously implemented: movie endpoint recovery, episode cache cleanup, canonical anime/series metadata and explicit mappings, full-index series paging, Auto/SD episode qualities, alternative sources, indexed season packs, startup/manual source recovery, trusted live anime releases, anonymous OpenSubtitles.org access and episode UI fixes.

Verified: six sort contracts/reverse ordering/filter tests, 12-page paging tests, live series page 15, populated desktop movie/anime/series grids, actual Attack on Titan playback and cleanup on switching to Episode 2. Anonymous subtitle login/search/download passed a service-level test.

## Remaining verification and external limitations

- Exercise a complete six-source automatic failure chain and longer binge sessions on the installed desktop player.
- Check more codecs, custom/external cache drives, subtitle languages and the Latest episodes view in the installed UI. Parser, source selection and cache behavior have automated tests; that does not cover every UI/device combination.
- Confirm anonymous subtitle selection/download across additional titles in the desktop player. Direct anonymous login, English search and subtitle-file download succeeded.
- Password-based OpenSubtitles.org account login can still be rejected upstream; anonymous access does not repair account authentication.
- Movie/Hindi catalogs, reported seeds and playable sources remain dependent on external services. Trending uses recent provider windows rather than a global popularity chart.

The user confirmed basic Android projector operation; longer binge/performance and USB storage tests remain pending.

## Standalone preview

Windows x64 portable/installer packaging now lives in desktop/packaging, with pinned full sources, independent branding/profile/cache and no dependency on the installed Popcorn Time app. See desktop/STANDALONE.md. Runtime modernization, code signing, optional metadata-account registration and broader hands-on verification remain pending.
