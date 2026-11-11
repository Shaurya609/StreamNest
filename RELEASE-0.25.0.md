# StreamNest 0.25.0

YouTube trailers on movie, series and anime details.

- A play-icon Trailer action appears when an IMDb-matched trailer is available and passes YouTube's metadata check.
- Opens the YouTube app or browser on Android/Google TV/projector, and the default browser on Windows. A handler must be installed. No app-specific YouTube key is required.
- Trailer viewing is separate from torrent playback and never creates watch progress, Watched selections or sync history.
- Deleted candidates can fall back to another trailer from the same title metadata. Bounded background lookups and caching keep title browsing responsive; unavailable trailers leave the action hidden.

Validation: movie/series/anime native title controls and intercepted YouTube intent on the tablet; live lookup for Coco, Severance and Black Clover on both platforms; hidden Windows movie/series controls, visible keyboard focus and intercepted external launch; viewing-progress preservation. The external YouTube application's playback and regional restrictions remain outside StreamNest's control.

Android: universal, ARM32 and ARM64 APKs. Windows: standalone installer and portable package; no Popcorn Time required. Windows remains unsigned.
