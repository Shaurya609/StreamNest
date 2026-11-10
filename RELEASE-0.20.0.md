# StreamNest 0.20.0

Android / Google TV / projector APKs and standalone Windows preview.

- Add independent trusted anime RSS discovery, including numbered episode batches, alongside the indexed episode provider.
- Verify torrent identity and exact episode file before offering batch sources. Reject related shows, wrong episodes, ambiguous packs and camera releases.
- Resolve Black Clover and Dragon Ball Super through explicit TV anime identities, avoiding ambiguous IMDb mappings to specials.
- Try the mapped Kitsu identity when the IMDb index is empty; preserve an independent candidate within the bounded six-source retry list.
- Show retryable lookup errors separately from a successful search with no matching sources. Short-lived empty caches can be retried.
- Permit bounded 8 MiB torrent metadata for larger anime batches.

Validation: 87 desktop/catalog/backend checks, Android unit/build/lint, physical-tablet live Black Clover indexed and independent batch-file discovery. Desktop injected-index-outage fallback verified against live independent metadata. Source discovery and exact-file validation do not guarantee current peer availability or successful playback of every release. Windows remains an unsigned preview.
