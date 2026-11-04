# StreamNest Android 0.7.7

## Subtitle search correction

OpenSubtitles.org hash and IMDb/title searches now use independent XML-RPC criteria. Hash criteria include the selected file's byte size and are omitted when the hash/size is not available. An unmatched hash cannot suppress a valid movie or season/episode match. Leading-zero IMDb identifiers are normalized, title-only queries remove the player episode suffix, Specials retain season 0, duplicate subtitle files are removed, and malformed API results are reported as errors instead of empty subtitle lists.

On the tablet, anonymous English searches returned five choices for Percy Jackson and the Olympians S1E1 and two for Scorpion (2026), even with an intentionally unmatched file hash. A 42,211-byte timed English subtitle file downloaded successfully. This test did not read or change the user's saved login credentials.

## Storage and memory settings

- Renamed the visible setting to Low Memory mode, preserving its existing performance behavior and saved selection.
- Replaced raw cache-path entry with storage selection, directory browsing, root/subfolder selection and New folder.
- Displays friendly storage labels and free space. Detected SD cards and USB drives appear when mounted by Android.
- App-owned locations work without additional file permission. Choosing arbitrary shared/SD/USB folders uses optional Android all-files access because the native torrent engine needs real writable paths; a document-tree URI grant alone does not provide that access. The app explains this before opening Android settings and does not grant the permission automatically.
- Selecting a shared folder creates a dedicated StreamNest Cache child there. Existing unrelated files are kept, and write access is tested before saving. Changing the folder requires playback to be stopped.
- Directory rows are paged to bound view memory. Canonical path/name checks prevent browsing outside the selected storage through traversal or symlinks.

Validation: 122 Android unit tests, lint with zero errors, and 14 existing desktop check groups passed. The on-device storage test selected a writable subfolder via the native screen and confirmed a friendly label; the prior location was restored. Arbitrary USB/SD folder selection on every TV/projector firmware has not been hardware-tested. Lint reports the expected policy warning for optional all-files access; this APK is distributed on GitHub, not as a Google Play submission.

## Proposals

Catalog resilience and standalone desktop packaging were reviewed and documented in CATALOG-RESILIENCE.md and DESKTOP-PACKAGING.md. This release does not deploy new catalog servers or publish a full desktop installer. Desktop patch 0.8.1 remains the current desktop artifact.
