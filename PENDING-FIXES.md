# Pending fixes and verification

Current sprint/release status is in [SPRINTS.md](SPRINTS.md) and [VALIDATION.md](VALIDATION.md). Version 0.22.2 adds missing-artwork recovery and explicit desktop resume fill, following 0.22.1 Home layout validation; 0.22.0 added shared recommendations to the existing Home, broader catalogs/independent series lookup and modern Windows runtime. Owner-confirmed MSI sync and TV/projector issues are closed. Remaining external verification/setup: live Jikan filler data (timed out) and the previously documented Android local network preload harness. Public source and language availability remain variable. Windows signing was explicitly declined by the owner for this personal project; it is not a release blocker. Older dated entries below are historical verification notes.

Updated 4 November 2026. Version 0.11.0 implements launch renewal, subtitle archive decoding and completed conversion writes, dynamic loopback subtitle serving, pinned anime pagination and broader independent discovery. Live desktop Percy Jackson S1E1 search/download produced 540 cues and the real video.js subtitle display rendered a downloaded cue in an isolated hidden renderer. Android fresh/expired anonymous access is covered by regression probes; tablet playback survives subtitle settings return.

TV correction: the reported Percy Jackson S1E1 stall occurred on the user's TV, not the tablet. Android now waits for verified video headers/footer, detects actual video output and retries startup twice (including one software decoder attempt). The original TV/source combination still needs a TV retest. Local fixtures do not establish that it is fixed on that TV.

The remaining checks below describe broader verification, not a claim that every title or service is available.

## OpenSubtitles.org — desktop (priority)

- Ensure anonymous login happens automatically at launch and renews after session expiry/rejection, including in the standalone app.
- Investigate subtitles failing to appear even when the desktop UI reports an anonymous signed-in session. Treat login status, subtitle search results, download and actual player attachment/rendering as separate checks.
- Reproduce the full movie and series episode flow in the standalone player, verify correct title/season/episode/language matching and confirm downloaded subtitles become visible during playback. Check the legacy patch separately where applicable.
- Show an accurate status/error if login, search, download or rendering fails; a signed-in indicator must not imply subtitles are available or displayed.

## OpenSubtitles.org — mobile

- Verify automatic anonymous login on a fresh launch and session renewal after expiry/rejection on the installed build.
- Confirm search, download and visible subtitle rendering for both a movie and series episode, without relying on an existing manual login. Preserve user-selected account mode and credentials.

## Standalone desktop release visibility

- User reports that visitors to the GitHub profile/repository cannot easily discover the standalone desktop release. The current desktop release is public but marked pre-release; the Latest release still points to Android v0.9.0.
- Review the repository landing page and release navigation from a visitor's perspective. Make desktop and Android downloads clearly distinguishable and easy to find.
- Consider a prominent desktop download/release section and an appropriate stable/latest release arrangement once validation is complete. Keep the preview label accurate while known issues remain; do not promote it to stable merely to improve visibility.
- Verify that download links lead directly to the correct standalone installer/portable packages and that the Android download remains easy to find.

## Anime catalog — desktop pagination

- User reports that desktop anime pagination is not working and only some titles appear. Treat this as an unresolved user-reported issue.
- Reproduce scrolling/load-more in the standalone desktop app; check the legacy patch separately. Distinguish a paging failure from the limited saved anime-series seed, discovery coverage and active format/language/quality filters.
- Check provider page/skip parameters, hasMore/end-of-list handling, duplicate-page detection and paging-session behavior during background refresh. Verify several pages of unique titles and revisiting/filtering/searching the catalog.
- Do not claim complete anime coverage solely because the available small snapshot paginates correctly.

## Subtitle account scope — 5 November 2026

The owner selected anonymous access only. Personal subtitle-account upgrade-retention testing is out of scope. Continue checking anonymous launch access, expiry/rejection renewal, search, download and visible playback subtitles on Android and Windows. The tablet retained anonymous mode through the 0.14.0 upgrade; live anonymous search/download passed. Windows in-app upgrade from 0.13.2 to 0.14.0 also completed and restarted successfully.

## Regional catalogs and anime metadata — implementation in progress

- Korean language is accepted by Android/desktop series metadata adapters, movie adapters and the signed snapshot schema. Both language filters expose Korean. Independent publisher scans now retain Korean series as well as anime. These labels identify original title language, not verified English audio/subtitles.
- Android and desktop attach Jikan filler/recap flags only to explicitly mapped MAL episodes, currently from the first API page. Unknown, unmapped and later-page episodes receive no inferred labels. Android shows flags in selected episode heading; desktop shows them in episode rows.
- Remaining before release: extend mapped filler pagination with provider rate limits, verify UI/network behavior, add confirmed source-audio/subtitle availability, verify IntroDB and cached timestamp fallback, and publish updated signed catalog snapshots and app packages.

Timestamp provider scope — owner selected IntroDB and cached timestamps for now. Anime Skip client registration/integration is deferred. Continue validating episode identity and segment boundaries before showing skip controls.

Owner confirmation: Percy Jackson TV playback, projector operation and USB cache storage are fixed. Removed from active device backlog based on that confirmation.


## Windows update launch guard — observed 11 November 2026

A window launched from Explorer while installation was still extracting files kept older Home controls in memory, even though the final files/package version were updated. The normal helper completed and attempted restart, but the already-running instance remained. A full quit/reopen after installation completed loaded the corrected controls. Add an installation-in-progress launch guard or clearer wait/restart UX; do not reset the profile to work around this condition.
