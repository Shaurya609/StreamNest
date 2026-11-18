StreamNest 0.33.0 adds Remove from Continue Watching on Android, Google TV and Windows.

Use × Remove below a Continue Watching card. It clears that title’s saved playback positions across the sync group, including all episodes of series/anime. Removal does not mark the title Watched. Watching it again creates a fresh resume entry.

Encrypted removal markers prevent older offline history from restoring cleared positions. Changes reach other devices on their next sync; disconnected devices catch up after reconnecting. Update every group device to 0.33.0 for this behavior. No account or new backend deployment is required.

Validation: Android unit tests, lint/build and native disposable-tablet button/store/legacy-resume checks; desktop/catalog/backend tests and native Windows Home, keyboard navigation, persisted deletion, stale history rejection and fresh playback checks. Windows preview remains unsigned.
