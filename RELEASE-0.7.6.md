# StreamNest Android 0.7.6 and desktop patch 0.8.1

- Replaced the flattened black-background logo with a transparent SVG made of paths. Android uses the matching VectorDrawable for its launcher, in-app logo and TV banner. No embedded bitmap is used in the SVG. Android launchers can still apply their own icon mask/background.
- Fixed video restoration after subtitle UI: the player uses a TextureView, detaches/re-attaches its video output across stopped/started activities and resumes only if playback was running beforehand. A deliberate user pause is preserved.
- Fixed the mobile Hindi filter routing. Both versions include a 91-title snapshot of exact IMDb-matched Hindi movies with provider-reported 1080p/4K web/Blu-ray sources. Background discovery checks recent regional entries and keeps previously confirmed entries when a source is temporarily unavailable. Upcoming metadata-only films and lower-quality/camera releases are excluded.
- Mixed Trending movie pages interleave up to six Hindi movies with 18 English movies without dropping cards between pages. Explicit English/Hindi filters remain available. Other sort modes retain their selected ordering; Trending is language-balanced curation rather than a worldwide popularity chart.
- Removed the artificial 10,000-rank penalty from regional Hindi series in Trending/Last added.
- Anonymous .org sessions refresh on launch, renew before requests when expired, and retry once after a 401/406 session rejection. Requests do not retry indefinitely. Android sign-out disables its remembered anonymous mode; .com credentials are unaffected.

Validation: 118 Android unit tests and lint passed; 14 desktop test groups passed. Tablet checks confirmed 91 Hindi HD movies, six Hindi titles in a 24-card mixed movie page and 24 Hindi series. A device test inspected decoded video frames after closing the actual subtitle menu and returning from subtitle settings, verified position advancement and preserved user pause. The installed desktop grid showed the mixed Hindi titles.

Source availability, reported quality and seed counts depend on external providers; every torrent was not downloaded or independently quality-inspected. Projector/low-memory hardware validation and additional subtitle-language/codec combinations remain pending. Password-based .org authentication is separate from anonymous session renewal.
