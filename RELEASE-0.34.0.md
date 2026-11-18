StreamNest 0.34.0 strengthens episode selection and audio handling on Windows and Android / Google TV.

- Provider file indices must agree with the requested episode; contradicting episode names and ambiguous indexed packs are rejected.
- Auto selection prefers audio-compatible Windows releases. Release labels are hints; actual decoded tracks determine language availability.
- Preferred audio-track selection applies to movies and series as well as anime. Missing preferred-language audio is reported separately from subtitles.
- Windows reports video-without-decodable-audio and offers another source, track selection, or a locally installed VLC/mpv player. External playback keeps StreamNest running to serve the download.
- Android retains VLC codec support and offers another-source guidance when no playable audio track is exposed.

Windows installers remain unsigned. Source availability and accurate release labels cannot be guaranteed. Live verification and limitations are recorded in VALIDATION.md.
