# StreamNest 0.24.0

- Manual Watched/Unwatched choices now influence recommendations and sync across paired Android and Windows devices, separately from playback positions. They never create fake Continue Watching entries.
- Android title details add a Watched toggle for movies, series and anime. Existing desktop Watched actions are connected; recognized legacy watched entries are imported without overwriting later undo choices.
- Android torrent startup shows separate worker, previous-session shutdown, cache, folder and native-engine stages with elapsed time and stalled-operation guidance. Busy native starts reject extra attempts rather than queueing source retries.
- Low-memory connection limits are supplied before native session startup. A narrowly scoped guard handles the pinned library's confirmed alert-thread null-session shutdown race; unrelated exceptions remain visible.

Validation includes live encrypted desktop-to-Android Watched and Android-to-desktop Unwatched exchange, unchanged playback positions, native initial settings and busy-start rejection on the tablet, and hidden desktop Home/navigation/detail checks. The exact projector stall still requires owner retesting; native calls that never return may require restarting StreamNest.

Android: universal, ARM32 and ARM64 APKs. Windows: standalone installer/portable package; no Popcorn Time required. Windows remains unsigned.
