# Windows publisher signing

StreamNest releases remain explicitly unsigned until the owner obtains an approved signing identity. Signed update-feed metadata and artifact hashes are already implemented; these authenticate our updater's downloads but do not replace Windows publisher signing.

## Free open-source route

Check [SignPath Foundation](https://signpath.org/) and its [eligibility terms](https://signpath.org/terms). Apply with the public StreamNest repository, license, project/maintainer details and automated build information. Approval is external and is not guaranteed. Do not buy a certificate before checking eligibility.

After approval, the owner can connect only this repository and approve the signing policy. Sign the branded application and final installer, verify the signatures and timestamps, then generate checksums and publish. Keep signing operations tied to successful protected-branch builds. GitHub signing access and publisher identity require owner setup; no application was submitted or account created on the owner's behalf.

## Paid alternative

A certificate authority can validate an individual or organization and supply hardware-backed or cloud-backed signing. For example, [DigiCert provisioning options](https://docs.digicert.com/en/certcentral/get-started/set-up-your-certcentral-subscription/add-a-code-signing-certificate-subscription.html). Confirm local identity eligibility, renewal charges, hardware/cloud costs and automated-build integration before purchasing. Modern public-trust keys generally stay in hardware or a signing service; do not assume a downloadable PFX is available.

A self-signed development certificate does not establish public publisher trust and will not be presented as a solution. Public signing can identify the publisher, but does not guarantee that every SmartScreen warning immediately disappears.
