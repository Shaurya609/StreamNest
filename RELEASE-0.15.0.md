# StreamNest 0.15.0

Android and Windows updates add Korean catalog filtering and preserve Korean metadata in discovery and signed catalog publishing. Original language does not establish English dub availability. English subtitles remain available through anonymous OpenSubtitles.org matching.

Anime episode details now show filler/recap flags for explicitly mapped episodes across metadata pages. Unmapped episodes remain unlabelled.

Series and anime skip controls use IntroDB, cached responses and an anonymous SkipDB fallback that accepts exact episode/duration matches with validated confidence and bounds. SkipDB requests returned HTTP 403 from the build connection, so live fallback availability remains unverified. Anime Skip itself requires a client ID and is not included.

Validation includes Android unit/build/lint and desktop/catalog checks. Anonymous English subtitle searches returned eight matches for Squid Game S1E1 and 28 for Parasite. English dub source verification and additional hands-on catalog/playback checks remain pending.
