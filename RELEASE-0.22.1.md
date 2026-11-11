# StreamNest 0.22.1

Home layout correction and cross-platform layout verification.

- Windows Home styles now attach to the visible application window, fixing oversized posters, unstyled rows and unreadable text.
- Home sits below the navigation bar, with consistent cards, dark scrollbars, visible focus and preserved keyboard position when recommendations refresh. Arrow keys move within and between rows.
- Windows validation now checks computed sizes, row layout, focus and header spacing, with screenshots at 960, 1366 and 1920 pixels across three themes.
- Android Home checked on the connected tablet at phone, tablet, TV-sized and Low Memory configurations, including poster/card bounds and D-pad navigation. Display overrides were restored. Actual TV/projector hardware was not connected for this check.

108 Node tests; 180 Android unit tests; Android build/lint and isolated native layout probes. Existing sync groups, watch progress and recommendations remain compatible.
