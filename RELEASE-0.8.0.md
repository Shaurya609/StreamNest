# StreamNest 0.8.0 — catalog resilience

Android 0.8.0 and desktop patch 0.8.2 retain saved movie, series and anime browsing/search during catalog outages. Invalid responses cannot replace valid saved catalogs. Healthy-host preference, failure cooldown and bounded deadlines reduce repeated waits.

Android uses a persistent indexed movie database, paged queries and streaming snapshot imports for low-memory devices. Saved metadata survives video-cache cleanup. Background refresh preserves the current paging session; updated catalogs can be refreshed without moving earlier pages while scrolling.

A signed snapshot publisher refreshes every six hours on GitHub Actions. Clients verify signatures, hashes and schemas across GitHub/CDN delivery and keep packaged/last good data when updates fail. The initial bundle contains 777 HD movie records, 8,652 series, 30 mapped anime series and 69 individual releases. TVmaze adds independent series metadata through exact IMDb IDs, including Hindi; metadata-only records do not claim playable sources.

The user confirmed basic projector operation. This release does not guarantee new releases during a complete provider outage, live peers for every title, or performance on every device. Movie API mirrors still share one provider family. See CATALOG-RESILIENCE.md and VALIDATION.md for scope and checks.

Install the universal APK when device architecture is unknown. These remain debug-signed development APKs. Desktop download is an overlay for installed Popcorn Time 0.5.1, not a standalone installer; close the desktop app and run Install.ps1 to apply it with a backup.
