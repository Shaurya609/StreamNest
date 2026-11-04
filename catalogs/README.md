# Signed catalog publishing

Application source tracks the public key, a signed bundled manifest and packaged fallback JSON files. Generated updates live on `catalog-data`, not `main`.

`.github/workflows/catalogs.yml` runs at minute 17 every six hours (UTC), or manually through Actions. It restores the previous snapshot, collects bounded provider updates, retains last good records on individual failures, validates source identities/HD rules and signs a new manifest. GitHub schedules can be delayed. An all-provider failure retains existing records rather than guaranteeing fresh titles.

Delivery bases:
- `https://raw.githubusercontent.com/Shaurya609/StreamNest/catalog-data/`
- `https://cdn.jsdelivr.net/gh/Shaurya609/StreamNest@catalog-data/`

`catalogs/v1/status.json` reports collection counts/failures. `manifest.json` is a signed envelope; decoded payloads list immutable file paths, hashes, lengths, counts and timestamps. The current files and seven previous sets remain in the generated branch. A CDN can lag the primary; clients reject invalid/incomplete updates and retain accepted local data.

## Keys and maintenance

`CATALOG_SIGNING_KEY` must contain a PEM RSA private key matching `public-key.pem`; it is supplied through a GitHub repository secret. Never commit or log it. The offline owner copy remains outside the repository. Rotating the trust key requires updating the pinned key in both clients and releasing them before changing the publisher.

Run tooling tests with `node --test tools/catalog/*.test.mjs`. Install parser dependencies with `npm ci --prefix tools/catalog --ignore-scripts`. With the signing secret set in the local environment, `node tools/catalog/publish.mjs` generates updates; add `--bundle` only when intentionally refreshing packaged fallbacks. Regenerate the desktop checksum manifest after changing its bundles. Release bundles must match `bundled-manifest.json` exactly.

For an upstream regression, correct the adapter/configuration and publish a new, newer signed manifest using retained good records. Do not replace a signed manifest with an older timestamp: clients reject replay. Do not force-push application source when publishing generated data.

Snapshots contain catalog metadata and source references, not video files or account credentials. TVmaze records use explicit IMDb mappings and retain metadata-only status; movie snapshots require a real provider-reported HD source identity. See THIRD_PARTY.md for source references.
