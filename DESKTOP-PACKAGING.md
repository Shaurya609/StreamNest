# Standalone StreamNest Desktop proposal

Recommendation: publish a branded Windows x64 portable build first, then a per-user installer, with complete matching sources and a clean build workflow. This proposal does not publish a standalone desktop binary.

The current release is an overlay for Popcorn Time 0.5.1. Its installed package declares GPL-3.0; desktop/LICENSE.txt preserves the original notice. The former upstream GitHub URL now redirects to a different current project, so do not assume its latest code, build process or license matches the installed legacy version. Recover and pin the actual legacy source revision and dependencies before assembling the standalone build.

Required work:

- Maintain the full desktop source, not only overlay files, with scoped GPL notices, credits, corresponding dependency/source information, build scripts and pinned versions.
- Use the StreamNest name and vector artwork, a separate application identifier and a separate data/cache directory. Build from clean sources; never copy a personal installed profile, API keys, saved logins or media cache into an installer.
- Disable the original application's upstream update feed. Publish our own versioned releases, integrity metadata and rollback behavior so upstream updates cannot overwrite StreamNest modifications.
- Build Windows x64 portable ZIP and installer in CI. Include hashes and release notes. Code signing can be added when a signing identity is available.
- Verify installation on a clean Windows account without Popcorn Time present; test playback, codecs, subtitle search/download, cache switching/cleanup and network-failure behavior. Review runtime modernization against the native playback dependencies before changing the old NW.js runtime.
- Provide the complete corresponding GPL source alongside binary releases and preserve third-party license notices. The existing Android codebase has separate licensing scope.

Sources: [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.en.html), [GNU distribution/source FAQ](https://www.gnu.org/licenses/gpl-faq.en.html), [current redirect of the former upstream repository](https://github.com/popcorn-official/popcorn-desktop).
