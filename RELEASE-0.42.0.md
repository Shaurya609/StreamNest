# StreamNest Desktop 0.42.0 — large-piece hashing responsiveness

Windows-only optimization; Android remains at 0.41.0.

- Large verified-piece SHA1 computation yields between 256 KiB steps instead of blocking the renderer for a whole piece. The pinned simple-sha1 3.1.0 callback interface, small-input path, synchronous API and WebTorrent integrity comparison remain intact. No extra whole-piece hash buffer is created.
- Include pending hash bytes in the existing request backpressure budget, so yielding cannot freely queue additional completed pieces. In-flight requests can still exceed a threshold; this is not a hard whole-app RAM ceiling.
- Preserve 0.40.0 contiguous backing-buffer reuse. Fragmented peer blocks still use upstream complete-piece assembly before verified writes; eliminating that buffer requires a larger engine/store redesign and is not claimed here.
- Reproducible focused hash, fragmented assembly, actual WebTorrent webseed and whole-app profile tools accompany the change.

Validation and measured limits are in PERFORMANCE.md and VALIDATION.md. The package remains an unsigned personal-project preview. Signed update metadata and normal download verification remain enabled.
