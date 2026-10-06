# ReVanced conventions and project decisions

## Reviewed upstream examples

- [TikTok Feed filter](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/feedfilter/FeedFilterPatch.kt): a short sentence-case name, behavior description, exact supported versions, response hooks and a shared runtime extension.
- [Change package name](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/all/misc/packagename/ChangePackageNamePatch.kt): a resource patch with options, excluded by default.
- [Fix Google login](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/login/fixgoogle/FixGoogleLoginPatch.kt): a specific fix name describing the authentication method it changes. It does not repair email/password registration.
- [Patches template](https://github.com/ReVanced/revanced-patches-template): separated extension sources, semantic versioning and semantic commit messages.

These are examples of upstream practice, not a claim that every convention is a
formal requirement. Upstream source links may evolve. Implementation in this
project was checked against the investigated APK; upstream code was not copied
into its runtime helpers.

## Naming

The package compatibility metadata already identifies the app, so selectable names
do not repeat TikTok unnecessarily. Descriptions state concrete behavior and use
sentence case with a full stop. Developer notes and validation status belong in
documentation, not the Manager selection description.

| Earlier local name | Public name |
| --- | --- |
| Hide TikTok ads | Hide ads |
| Hide TikTok Shop videos | Hide Shop videos |
| Install alongside TikTok | Change package name |
| Fix alongside device registration | Fix device registration |

Java patch fields use camelCase with a `Patch` suffix. Source classes use a `Patch`
suffix and runtime helpers describe their task. The stable installation identity
and TikTok Filtered label are retained for updates and independent app data.

## Structure and deliberate differences

The code uses the same Patcher DSL through Java interop: compatible packages,
bytecode/resource patches, dependencies and a merged runtime extension.
An unnamed shared extension patch stays out of the Manager selection list.
**Change package name** depends on **Fix device registration**, so a separate
installation cannot omit the demonstrated repair accidentally. The two filters
remain independently selectable and enabled by default; package changes stay opt-in.
**Enable downloads** is also opt-in and has no resource or runtime-extension dependency.

Upstream commonly uses Kotlin, Gradle and fingerprints. This small bundle retains
its tested Java/PowerShell build without Maven credentials. Targets are restricted
to 47.1.4 and checked by class, method, return/parameter types, fields and expected
instruction counts. These guards fail when the verified layout changes rather than
claiming compatibility through a broad heuristic. Future version support should
add evidence and version-specific fingerprints or guards.

Only the five production patches and FeedFilter/RegistrationIdentity helpers enter
the public repository and bundle. Investigation tools and obsolete identity
experiments are kept in ignored local storage. The public build has no diagnostic mode.

## Practical takeaways

Keep bytecode changes small, runtime logic testable and dependencies explicit.
Preserve register order and branch destinations when inserting calls. Exclude the
injected extension from scans that would instrument its own operations. Version
compatibility is evidence, not a label to widen until patching succeeds.
