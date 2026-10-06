# Packaging and privacy

## Public contents

Publish the production `.rvp`, GPL source archive, README, changelog, reviewed
Markdown findings and checksums. The build is versioned by `VERSION`.
The source includes production runtime helpers, build fixtures, behavior tests
and validation/release scripts. Investigation tooling and experimental patches
stay private; only their reviewed findings are public.

Never include TikTok APKs, DEX extractions, decompiled app source, screenshots,
logs, packet captures, TLS keys, account data, device identifiers, signing keys,
local CA material or emulator disks/backups. `private/`, `tools/`, `build/`, `dist/` and all
unreviewed research files are ignored. Review the staged file list before committing.

Public Markdown should explain the hypothesis, controlled change, result and
limitations without reproducing the participant's conversation, account details,
personal filesystem paths or device identifiers. Aggregate counts, code targets
and public app/package metadata are useful and sufficient here.

## Checks

```powershell
./build.ps1
python scripts/CheckPublishable.py --bundle dist/tiktok-feed-filters-1.2.0.rvp
```

The checker reads staged Git blobs by default. It enforces an explicit file allowlist
for production source, required fixtures/tests, maintenance scripts and reviewed Markdown,
rejects generated/private file types, flags personal home paths, non-example email
addresses, non-loopback IPv4 literals and PEM material, and checks tokens derived
from local identity plus optional `--deny-token` values. It reports only file names
and rule names. Review is still necessary: no pattern scanner proves arbitrary
text has no PII or secrets. It also checks the public bundle's compiled class set,
manifest version and absence of diagnostic resources/helpers and fixture models.

Use `--revision HEAD` to check the committed snapshot. Private investigation files
and diagnostic code are rejected even if staged accidentally. Adding a new public
file requires reviewing it and updating the checker allowlist.

After committing the reviewed files:

```powershell
python scripts/PackageRelease.py
```

This verifies `HEAD`, packages committed source with `git archive`, and writes
the production bundle, source ZIP, Manager update feed `patches.json`, checksums
and combined release ZIP under
ignored `dist/releases/`. It refuses dirty tracked/staged files and checks that
the bundle matches the committed version. Installed app data is not touched.
Package content is local and reviewable; this script does not upload anything.

Upload `patches.json` alongside the versioned `.rvp` on every published release.
Manager's permanent source URL is:

```text
https://github.com/niposch/revanced-tiktok-patches/releases/latest/download/patches.json
```

The feed uses Manager's `download_url`, `created_at`, `description` and `version`
fields. The download URL is pinned to that release's versioned bundle; the
permanent feed URL follows GitHub's latest published release. Packaging derives
the feed version from `VERSION` and its timestamp from the commit in UTC, without
a timezone suffix as required by Manager's `LocalDateTime` field. Publish the
complete asset set before marking a new release latest. A new patch version
updates the bundle in Manager; users still need to patch and install the app again.

Before publishing a subsequent release, run the behavior and APK-derived checks
documented in README and review the changelog. APK fixtures and private captures
remain local. Runtime validation should report whether authentication, content
removal, pagination and operation with the original app absent were actually tested.
