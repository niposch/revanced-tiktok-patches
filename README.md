# TikTok feed filters

Independent ReVanced patches for TikTok **47.1.4** (`com.zhiliaoapp.musically`).
This is a community project using ReVanced Patcher, with no official ReVanced affiliation.

| Patch | Behavior | Selected by default |
| --- | --- | --- |
| Hide ads | Removes flagged ads, paid partnerships and disclosed promotional videos/LIVE cards; clears preloaded feed ads. | Yes |
| Hide Shop videos | Removes whole videos with product links, product metadata or shopping live promotions. | Yes |
| Enable downloads | Enables saving videos and photos whose download permission disables or hides the action. | No |
| Change package name | Installs as TikTok Filtered (`com.zhiliaoapp.musically.filtered`) with separate app data. Includes the registration repair. | No |
| Fix device registration | Corrects the renamed package's AppLog registration header. Requires no installed original app. | No; automatic dependency of Change package name |

The Shop tab remains accessible. Shop filtering removes feed items, including organic
product promotions and LIVE cards carrying current goods or preview-product metadata.
Ordinary LIVE streams and selling permission alone are preserved. Shop commerce
tags are recognized even when goods/count fields are absent. Hide ads also removes
creator-disclosed paid partnerships and self-promotions, including commercial LIVE
disclosures. Neither filter classifies video, audio or captions. Unmarked promotions
cannot be identified reliably.

## Use with ReVanced Manager

In Manager, open **Patches**, tap the pencil, then **+** and **Enter URL**.
Use this permanent update feed and keep automatic updates enabled:

```text
https://github.com/niposch/revanced-tiktok-patches/releases/latest/download/patches.json
```

The feed follows the latest published release. It updates the patch bundle;
apply the patches again to update an installed app.
Alternatively, build or obtain `tiktok-feed-filters-1.4.0.rvp` and import it using
**Select from storage**. Select an original TikTok 47.1.4 APK.
Enable either or both content filters. For a separate installation, also select
**Change package name**; its dependency applies **Fix device registration**.
Select **Enable downloads** to enable saving restricted videos and photo posts.
This changes the app's download permission records; unavailable media and server
access restrictions are outside its scope.

The bundle targets Patcher 22 and was used with Manager 2.6.0. Compatibility with
other Patcher APIs or TikTok versions is not claimed. Keep only one source for
this bundle: remove older local imports and duplicate remote/local copies when
switching sources. Manager can load the older classes even when only the newer
source's patches are selected. A patched APK update must use the same signing
key as the installed app. Keep your signing key locally.

If Manager closes during **Decoding resources**, enable **Settings > Advanced >
Run Patcher in another process (experimental)** and set **Patcher process memory
limit** to **2048 MiB**. A 512 MiB app heap was insufficient to decode this APK.
ReVanced also recommends the separate process for out-of-memory failures;
see its [troubleshooting guide](https://github.com/ReVanced/revanced-manager/blob/main/docs/3_troubleshooting.md).

The APK is supplied by the person patching the app. App binaries, signing keys and
device captures are not included in source or release archives.

## Build

Requirements: Windows PowerShell, Git, and a JDK 17 or newer. JDK 21 is verified.
Set `JAVA_HOME` to your JDK if it is not detected under Program Files.

```powershell
./build.ps1
```

The build downloads and checksums pinned official ReVanced CLI 6.0.0 and R8
8.13.17 binaries into ignored `tools/`. It compiles Java definitions, runs runtime
behavior tests, and packages both JVM classes and Android DEX in
`dist/tiktok-feed-filters-1.4.0.rvp`. Neither Maven credentials nor Gradle is required.

The source and bundle contain only the five production patches and two runtime
helpers. The repository also includes the fixtures, tests and validation/release
scripts needed to maintain them. Investigation tools, capture diagnostics and
unsuccessful compatibility experiments stay in ignored local storage. The public
build has no diagnostic mode and embeds no local certificates.

## Patch with the CLI

```powershell
java -Xmx6g -jar tools/revanced-cli.jar patch input.apk `
  -p dist/tiktok-feed-filters-1.4.0.rvp -b `
  -e 'Change package name' `
  -o dist/tiktok-filtered.apk -t build/local-patch `
  --keystore dist/local-signing.keystore
```

Both filters are selected by default. Omit **Change package name** to retain the
original installation identity. Use `--exclusive -e 'Hide ads'` or
`--exclusive -e 'Hide Shop videos'` to select a single filter; add
`-e 'Change package name'` for a separate installation.
Add `-e 'Enable downloads'` to enable saving restricted posts.

The CLI's `-b` permits the unsigned local patch bundle; it is not an APK signing
option. APK signing is handled by Manager or CLI using your local signing key.
If signing fails, preserve the temporary patched output and check your JDK and
keystore configuration. Local keystore recovery helpers are outside this repository.

## Validation and evidence

`build.ps1` checks independent filters, list identity, survivor order, immutable
input, null input, product anchors, LIVE commerce/disclosure tags, branded-content
predicates and registration JSON semantics.
APK integration also checks download null branches and unchanged general sharing
permissions, both alone and combined with the feed filters. Saving was verified
on one previously restricted video and one restricted photo post on a stock-boot
emulator, including actual saved-file size and decoding/playback.
APK-derived integration checks are run against a locally supplied original APK:

```powershell
javac -cp tools/revanced-cli.jar -d build/validation-tools `
  scripts/ValidateExtension.java scripts/ValidatePatches.java scripts/ValidateRegistration.java scripts/ValidateResources.java
java -Xmx3g -cp 'tools/revanced-cli.jar;build/validation-tools' ValidateExtension input.apk dist/tiktok-feed-filters-1.4.0.rvp
java -Xmx3g -cp 'tools/revanced-cli.jar;build/validation-tools' ValidatePatches input.apk dist/tiktok-feed-filters-1.4.0.rvp build/integration
java -Xmx3g -cp 'tools/revanced-cli.jar;build/validation-tools' ValidateRegistration input.apk dist/tiktok-feed-filters-1.4.0.rvp build/registration-validation
java -Xmx3g -cp 'tools/revanced-cli.jar;build/validation-tools' ValidateResources input.apk dist/tiktok-filtered.apk
```

Registration repair restored nonzero device IDs in a controlled capture and
successful authentication was confirmed on a stock emulator. The repaired APK
also installed successfully as an update on a physical device. Authentication
on that device and an explicit run with the original app absent remain unverified.
Both filter activations were observed; removal of real feed promotions and
pagination after an entirely filtered batch still need device validation.

Change package name preserves 56 behavior references that resource decoding lost.
All 126 compiled layout behavior declarations were compared with the original APK.
The poll crash was traced to its confirmation toast; the same toast can be tested
directly without submitting a vote. See the research notes for runtime results
and the limits of LIVE filtering validation.
The initial phone build through Manager loaded older duplicate bundle classes;
its successful install and Share smoke check do not validate these new repairs.
The corrected desktop build passed the complete resource comparison and emulator
toast check. A real poll vote and the corrected build on a phone remain pending.

Version 1.4.0 was installed and tested in an emulator. Replaying an actual
paid-partnership video model removed it with Hide ads; 1.3.0 retained the same
model. Native Android LIVE fixtures verified tag-only Shop and commercial
disclosure removal with ordinary LIVE retention and independent selection. The
specific supplied LIVE broadcast was not verified in a fresh For You response.

## Documentation

- [Patch conventions and naming decisions](docs/PATCH_CONVENTIONS.md)
- [Feed targets, Shop signals and resource rebuild lessons](research/NOTES.md)
- [Registration repair and limits of the root-detection theory](research/ROOT_SIGNAL_ANALYSIS.md)
- [Traffic analysis and decryption takeaways](research/EMULATOR_CAPTURE.md)
- [Publication and privacy checks](docs/RELEASING.md)
- [Changes](CHANGELOG.md) and [contributing](CONTRIBUTING.md)

GPL-3.0-only; see [LICENSE](LICENSE).
