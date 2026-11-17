# StreamNest 0.29.0

Anime audio/subtitle controls and safer episode file matching on Android / Google TV / projector and standalone Windows.

- Real audio-track selection and English dialogue preference, with commentary excluded from automatic selection. Windows enables multi-track support in its bundled runtime.
- Embedded subtitle selection, full-dialogue preference over signs/songs, and native Android ASS font fallback. Manual subtitle/off choices are protected from late automatic results.
- Anonymous TsukiHime English subtitle fallback, matched to the exact torrent and selected file. OpenSubtitles remains available. Archives are bounded and integrity checked; Windows renders converted dialogue without complete ASS styling.
- Fix season-pack folder names overriding individual episode filenames. Preserve the verified original Black Clover run's absolute mapping when the mapping service is unavailable; no invented future-season mapping.
- Clear stalled-playback hints and technical measurements after a sustained freeze, without silently switching episodes.

Validation: unit/build/lint checks, native tablet MKV decoding with English/Japanese audio switching and visible full ASS subtitles, live anonymous exact-source subtitle download, hidden Windows MKV and audio-track checks, archive integrity and exact-file/episode regressions. Full acceptance is in VALIDATION.md.

The projector-specific freeze is not yet reproduced or confirmed fixed; the owner will retest. Public source/peer availability and subtitle coverage remain variable. See [anime playback details](https://github.com/Shaurya609/StreamNest/blob/main/ANIME-PLAYBACK.md).

Universal/ARM32/ARM64 APKs and standalone Windows installer/portable downloads. Windows remains unsigned.
