# Catalog resilience — implemented in Android 0.8.0 / desktop patch 0.8.2

## What survives an outage

Movies, series and anime retain valid packaged and downloaded catalogs. Saved browsing, title search, filtering and ordering continue when catalog servers time out, return errors or deliver malformed data. A populated root catalog is protected against suspicious empty refreshes; a genuine empty search remains valid.

Android movies use a persistent indexed SQLite database with paged queries and streaming snapshot imports, rather than loading the whole movie catalog into memory. Saved metadata is separate from video cache and survives episode cleanup and Exit StreamNest. The HTTP metadata cache is capped at 48 MiB. Desktop keeps a persistent movie index and validated metadata responses.

The initial packaged snapshot contains 777 source-confirmed HD movie records, 8,652 series, 30 mapped anime series and 69 individual anime releases. This is a fallback seed, not the entire upstream catalog. Live pages and later signed snapshots expand it. Movie inclusion still requires a provider-reported 1080p/2160p source; camera releases and metadata-only films do not pass that gate. Provider confirmation does not guarantee connected peers or independently verified video quality.

## Recovery and independent discovery

Compatible movie hosts have a preferred working endpoint, bounded request deadlines and exponential cooldown after failures. Cooling hosts are skipped instead of repeatedly delaying every page. Mobile displays saved movie results immediately and refreshes in the background. Desktop tries live pages first, then saved data. A small Saved catalog notice identifies fallback results.

Paging sessions pin their record order so incoming records do not move earlier pages while scrolling. Refreshing starts a new session. Existing series/anime snapshots remain available during partial upstream refreshes. Known series searches return saved matches immediately; primary lookup and an independent TVmaze adapter can add exact IMDb matches in the background. Unknown TVmaze matches retain explicit language and metadata-only provenance, with no invented playback availability.

The three movie API hosts belong to one API family. They provide endpoint redundancy, not independent movie coverage. The signed snapshot distribution adds independent delivery of previously collected data. A future independent movie discovery adapter still needs exact identity mapping and a separate HD-source check.

## Signed snapshot distribution

A GitHub Actions job collects and validates metadata every six hours, retaining known records when individual providers fail. RSA-signed manifests and content-hashed snapshots are published to the separate [catalog-data branch](https://github.com/Shaurya609/StreamNest/tree/catalog-data), with GitHub raw and jsDelivr delivery paths. Catalog history is kept out of application source commits.

Clients pin the public key, verify the manifest signature, file path, byte length, SHA-256 checksum and record count, and retain the last working data on a rejected/incomplete refresh. Older manifest replay is rejected. Signed provider configuration can update compatible HTTPS movie endpoints without an APK change. Packaged fallbacks remain usable if both distribution hosts fail. Seven earlier snapshot sets are retained for operator recovery; there is no automatic client rollback UI.

The publisher contains no subtitle credentials or user watch history. Its private signing key is outside Git and supplied to automation through a repository secret. See [publisher operations](catalogs/README.md).

## Verified behavior and limits

Android unit tests and desktop fixtures cover endpoint outages, cooldown, schema rejection, stable paging, exact identities and signature tampering. On the Android 14 tablet, injected outages returned five saved movie pages with 120 unique titles; saved search survived closing/reopening the database, legitimate empty searches remained empty, series/anime fallbacks worked and the real signed GitHub snapshot verified successfully. These tests did not disable device networking or alter subtitle credentials.

Catalog resilience cannot create torrent peers or guarantee live subtitle/provider availability. Episode/source requests are separate from discovery. Newly released titles cannot be discovered during a complete upstream outage until a provider or refreshed mirror becomes available. User-confirmed basic operation on the 1 GB Android 14 projector is recorded separately from performance/stress testing.
