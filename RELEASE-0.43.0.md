# StreamNest 0.43.0 — episode matching and TV playback investigation

Android / Google TV / projector update. Windows remains at [0.42.0](https://github.com/Shaurya609/StreamNest/releases/tag/desktop-v0.42.0).

- Correct exact anime member matching for underscore-separated absolute episodes, Ep121 notation and explicit seasonal/absolute aliases such as S03E19 (121). Reject wrong episodes and ambiguous ranges; never choose the largest season-pack file as an episode fallback.
- Add bounded video-read diagnostics: verified header/footer pieces, requested coarse file location, piece waits, scheduling/storage/delivery stages, delivered bytes, coarse seek type/location and player input/demux/decoded-frame counters. Existing sharing controls apply; no raw paths, source URLs or absolute watch timestamps are added.
- Owner dashboard explains read stages and decoding counters in plain language. Startup samples continue before the player opens.

Validation: Android unit tests/lint/build and 250 desktop/catalog/backend checks; four actual Black Clover torrent metadata packs uniquely select episode 121 and reject 122; native verified-byte gating and cancellation; isolated ARM32 tablet S1E3 first-source playback, pause with continued downloading, resume and a 60-second advancing sample. Test app/settings/history were isolated from production.

Limits: actual Black Clover 121 streaming is not claimed. Physical TV S1E3 buffering is still under investigation: older logs do not identify the exact missing range or decoder failure. Update the TV and retry so the new measurements can resolve that distinction. The tablet's 124-second measured launch included an Android compatibility dialog and is not a clean startup benchmark. No speculative firmware/I/O-mode fix is included.

Use Check for updates in StreamNest. Android requires confirmation to install.
