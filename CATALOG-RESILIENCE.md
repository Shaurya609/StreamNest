# Catalog resilience proposal

Status: design and current-code audit, not a claim that new servers have been deployed.

## Current coverage

Movies have three compatible endpoints and validation/failover. They are mirrors of one API family, so they do not provide independent catalog coverage. The default live movie feed still needs a responding endpoint; the Hindi HD snapshot is locally available.

Series have a saved/bundled full index and background refresh, but the main source catalog still depends on one backend. Hindi discovery uses a separate regional metadata feed. Searches and live source resolution can still fail even when the saved browsing index is intact.

Anime have saved/bundled catalog data, Kitsu-based discovery, TVmaze identity checks, canonical episode metadata/mappings and separate episode-source lookups. These are separate jobs, not interchangeable URL mirrors; replacing one endpoint with an unrelated API URL will not work.

## Recommended implementation

1. Store the last good movie, series and anime catalog locally, including title search and stable ordering. Refresh in the background and show a small cached/offline status when refresh fails. Never replace a populated catalog with an HTTP error, malformed response or suspicious empty full-catalog refresh. Genuine empty search results remain valid. Prefer a paged local database for the 1 GB devices.
2. Add adapters for independent metadata providers, exact IMDb/TMDB/TVmaze/Kitsu identity mapping, deduplication and one normalized schema. TMDB discovery supports language/filter/sort parameters; TVmaze supplies show and episode data. Metadata providers do not supply torrent availability, and the movie HD gate must remain a separate source check.
3. Add bounded endpoint health checks, failure cooldown, last-working-host preference and a total request deadline. Avoid repeatedly waiting for already failing hosts. Keep concurrency low on Low Memory devices. Do not send account credentials to catalog providers.
4. Publish our own versioned catalog snapshots and provider configuration, with a CDN/static mirror. Clients pin a catalog version while paging so a refresh cannot reorder earlier pages. A signed configuration can change approved endpoints without requiring an APK update; retain a packaged fallback and rollback. A catalog service needs an account/key and deployment decision before it can be hosted.
5. Resolve playable sources separately on Play, recheck availability and offer verified alternatives. Multiple catalogs cannot create peers for a dead torrent. Retain provider provenance, source quality/age and canonical episode numbering; do not label unverified metadata as playable HD.

Suggested source roles: TMDB for movie/Hindi discovery, TVmaze for series/episode identity, existing anime mappings/discovery as separate adapters, plus cached normalized snapshots. Verify provider terms, attribution and rate limits before deploying a public aggregator.

## Acceptance checks

Simulate a primary timeout, multiple host outages, invalid HTTP-200 payloads, offline startup, legitimate empty searches, a stale snapshot, provider identity conflicts, duplicate releases and catalog changes between pages. Browsing, sorting and cached search must remain usable when upstream refresh is down. Playback can still report that no live source is currently available.

References: [TMDB movie discovery](https://developer.themoviedb.org/reference/discover-movie), [TVmaze API](https://www.tvmaze.com/api), [Kitsu API](https://kitsu.docs.apiary.io/).
