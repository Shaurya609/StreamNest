# StreamNest 0.22.0

Android / Google TV and standalone Windows: shared Home recommendations for existing sync groups. No re-pairing is needed.

- Home recommendation rows, title metadata and ordering now sync across paired devices. Watching on one device influences the group picks. Low Memory mode shows a shorter prefix of the same rows.
- Encrypted, bounded recommendation snapshots are separate from watch progress so older app versions cannot overwrite them. No new account or service is required.
- Conflict retries, stale-device merging, group-scoped offline caches and daily catalog refresh preserve usable Home behavior. Continue Watching and playback selection retain the existing progress flow.
- Subtitle credentials, playback sources and downloaded video are not shared. Windows remains an unsigned personal-project preview.

Validation: 105 Node checks; 178 Android unit checks; build/lint; real disposable desktop-to-Android recommendations and native Home UI; Android watch update read back by Windows without re-ranking; actual Windows Home row order and group isolation. Installed Windows and production Android profiles were not replaced during these tests.
