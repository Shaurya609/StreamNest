# StreamNest 0.22.2

Missing Home artwork and desktop resume-progress correction.

- Titles without a poster URL now request artwork by IMDb identity. Valid original artwork is preserved, failed image requests try bounded alternatives, and series can use an independently matched TVmaze poster.
- Desktop shows a clean unavailable-artwork placeholder after failed alternatives. Lazy loading, successful-artwork reuse and cleanup avoid repeated stale requests.
- Desktop Continue Watching uses an explicit blue watched-progress fill. Small positive progress stays visible; Up next cards do not display an empty resume bar. Android progress rendering is unchanged.
- Android preserves Cinemeta poster metadata and retries fallback artwork when an original image fails. Shared recommendation ordering and pairing remain compatible.

Validation: 114 Node checks; 184 Android unit checks/build/lint; isolated native image decode/attachment with missing metadata and failed-original recovery; hidden desktop poster/progress/layout/keyboard/detail checks. The isolated 71-percent resume fixture paints a matching fill, and live artwork responses were confirmed for the five missing series.
