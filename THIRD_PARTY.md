# Third-party references

## Runtime dependencies

- [libtorrent4j](https://github.com/aldenml/libtorrent4j), version 2.1.0-39; MIT. Includes native libtorrent components and their notices.
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), version 2.3.1; BSD license.
- [LibVLC Android](https://code.videolan.org/videolan/vlc-android), version 3.7.7. See VideoLAN's licensing and the notices/source accompanying its native components.
- JUnit 4.13.2 and org.json 20240303 are used for local unit tests.

Dependency artifacts are resolved from Maven repositories; their source is not copied into this repository. Consult and preserve each dependency's license and distribution notices.

## Metadata and source interfaces

- [TVmaze API](https://www.tvmaze.com/api)
- [Cinemeta](https://github.com/Stremio/stremio-cinemeta)
- [AniZip mappings](https://api.ani.zip/)
- [Kitsu catalog adapter](https://github.com/TheBeastLT/stremio-kitsu-anime)
- [Torrentio](https://github.com/TheBeastLT/torrentio-scraper), using the [Stremio stream protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md)
- OpenSubtitles.com / OpenSubtitles.org
- Configured movie/series catalog providers and Nyaa release records

Bundled seed indexes contain provider metadata and remote poster URLs, not video files. Artwork, metadata and service interfaces retain their respective owners' rights and terms.

## Reference projects and test media

- [Popcorn Time Android](https://github.com/popcorn-official/popcorn-android-searchable) and [Popcorn API](https://github.com/popcorn-official/popcorn-api) informed protocol/workflow comparisons.
- [Big Buck Bunny](https://peach.blender.org/), Blender Foundation, CC BY 3.0, is the public playback test film. Its test torrent is hosted by [WebTorrent](https://webtorrent.io/).

The StreamNest icon is original generated artwork selected for this project, stored in branding/streamnest-approved.png.
