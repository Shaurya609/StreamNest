# StreamNest Android 0.7.5 and desktop patch 0.8.0

## Mobile catalog fix

The previous movie catalog endpoint began returning HTTP 521. Movie lists, searches and detail/source lookups now use a shared compatible-endpoint selector, starting with the responding HTTPS endpoint and falling back automatically on HTTP failures or invalid responses. A failed endpoint cannot masquerade as an empty catalog. Existing movie HD filtering and paging/order behavior are preserved.

- 111 Android unit tests passed; Android lint reported zero errors.
- Installed ARM64 0.7.5 on the connected tablet and verified a populated movie catalog showing 48 loaded titles.
- Universal, ARM32 and ARM64 APKs are included. Use Universal for the TV/projector when architecture is uncertain.

## Desktop implementation

Desktop patch 0.8.0 ports the remaining episode features: canonical anime/series metadata, Auto quality/SD fallback, alternate sources, exact pack-member selection, bounded startup source retries, a manual retry icon, and previous-episode cache cleanup after torrent shutdown. It also adds trusted live anime releases, fixes season ordering/long-season ID collisions, handles movie endpoint outages, and supplies a bounded anonymous OpenSubtitles.org client.

The patch is installed locally with backups. See [desktop/README.md](desktop/README.md) for features, installation, license and verification details. The desktop ZIP targets Popcorn Time 0.5.1; it does not include account settings, the full original app or runtime media.

Verified desktop playback with Attack on Titan Season 2 Episode 1, and removal of its cache when Episode 2 started. Anonymous .org login, search and an actual timed-subtitle download passed a separate service check. Desktop adapter/cache tests passed; complete automatic exhaustion of six dead sources remains untested end-to-end.

TV operation was previously confirmed by the user. Testing the actual 1 GB Android 14 projector remains pending. Password-based .org account login failures remain distinct from the working anonymous mode.
