# Anime audio, subtitles and episode matching

## Player tracks

Android's audio icon lists the actual audio tracks in the selected media file. Anime prefers a known English dialogue track when available; commentary is not chosen automatically. Selecting another known language remembers that preference on the installation. A catalog/source language label does not prove an English dub exists. If the source has only Japanese audio, select a different source to obtain a dub.

The subtitle icon now offers embedded tracks and online lookup. Automatic selection prefers full English dialogue over signs/songs-only tracks. Manual track choice and Off are preserved against late automatic downloads. A private fontconfig setup provides a local system font for Android ASS rendering on builds without a working default font configuration; no fonts are uploaded. System Devanagari fonts are included locally when available.

Standalone Windows enables Chromium's AudioVideoTracks feature. Audio and subtitle track controls sit inside the player control bar. Only tracks exposed by the media engine are listed; unsupported containers/tracks show an honest unavailable message. The packaged MKV regression exposes Japanese and English audio, selects English, and switches between tracks. Chromium may not expose embedded ASS subtitles: the online/local subtitle path remains available.

## Anonymous anime subtitles

TsukiHime is a separate community service, not the closed original AnimeTosho API. No account, API key or shared user credentials are used. Requests disclose the playing torrent info-hash and selected filename to the provider to identify the exact release/member. Only English full-dialogue results are selected automatically; signs/songs tracks can be selected manually on Android.

Matching requires the returned torrent hash and a unique matching video filename. A pack never falls back to its first file or another episode. Missing provider coverage remains possible; OpenSubtitles continues as fallback. Provider failure never creates guessed episode matches. Provider metadata and compressed/decompressed subtitle sizes are bounded. Android limits decoder memory; Windows checks XZ block/index limits before decoding. CRC32/CRC64/SHA256 archive checks are verified by the decoder. Windows converts ASS dialogue to timed SRT/VTT, retaining text and line breaks but not full typesetting/karaoke effects. Android uses ASS natively. No complete season packs, fonts or media files are downloaded from this subtitle endpoint.

## Episode fixes and freeze diagnosis

Episode member matching now reads the filename before inspecting episode tokens, avoiding false matches from a parent folder such as S01E158-S01E170. Black Clover's verified original MAL 34572 run can retain absolute numbering 1–170 when the mapping service is unavailable; this identity is never extended to specials or later seasons. Source discovery also understands a single SxxEyyy anime release name. These changes do not create peers or guarantee every episode/source is available.

Playback that stops advancing for 45 seconds while actively watching is reported with a retry/lower-quality hint. Paused, seeking, ending and background states are excluded. It does not silently skip an episode or delete active cache. The owner's reported freeze occurred on the projector, which remains to be retested; TheBeast's separate diagnostic samples showed decoded video, buffering and an error without enough information to prove the projector's cause.
