# StreamNest 0.24.1

Fixes a 0.24.0 regression where a manual or imported Watched preference could hide unfinished playback from Continue Watching, including Severance.

- Series/anime stay in Continue Watching or Up Next until all known available episodes are completed, or the whole title is explicitly marked Watched. Individual episode/legacy Watched flags do not complete a whole series. Starting a rewatch makes it resumable again.
- Home refreshes immediately after returning from the desktop player and rejects stale progress renders. Cached shared title metadata can fill local metadata gaps.
- Watched selections still influence recommendations and sync across devices, without inventing playback positions.
- Existing saved positions and Watched preferences are preserved. No profile reset or re-pairing is needed.

Android: universal, ARM32 and ARM64 APKs. Windows: standalone installer and portable package. Windows remains unsigned.
