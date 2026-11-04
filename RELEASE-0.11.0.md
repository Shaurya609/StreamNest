# StreamNest 0.11.0

Android / Google TV: universal, ARM32 and ARM64 development APKs.
Windows: standalone preview installer/portable package and optional Popcorn Time 0.5.1 overlay patch.

- Verify initial and footer torrent pieces before opening video; bounded same-source startup retry and one software decoder fallback. Audio alone no longer marks video startup successful.
- Default anonymous OpenSubtitles.org access on fresh Android launches, renewal after expiry, preserved explicit account/signed-out choices.
- Desktop subtitle downloads decode gzip/ZIP by signature, retain API file IDs, wait for finished VTT writes and use a dynamic loopback port. VIP placeholders produce an actionable error. Upstream subtitle protocol identifiers survive UI rebranding.
- Anonymous desktop renewal starts after database settings are available; account mode is preserved.
- Anime pages retain their snapshot across background refresh. Independent TVmaze metadata discovery scans bounded pages on each signed catalog publication, preserving its cursor and existing records. Metadata listing does not guarantee an available torrent or HD episode.
- Repository download table distinguishes Android and standalone Windows clearly. Desktop remains a preview.

Validation: Android unit tests/build/lint; desktop/publisher automated tests; tablet playback/settings-return and live Percy Jackson/Scorpion subtitle download probes; actual video.js cue rendering in an isolated hidden Windows renderer. Further release checks are recorded in VALIDATION.md.

The Percy Jackson S1E1 stall was reported on TV. Exact TV/source retesting remains pending; no TV-specific reproduction is claimed. External subtitle services, catalog servers and peer availability can still fail.
