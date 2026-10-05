# Feed filtering and packaging findings

## Scope

Verified input: TikTok 47.1.4, version code 2024701040, minimum SDK 23,
target SDK 36, with 56 DEX files. Analysis used JADX 1.5.6 and dexlib2;
the bundle uses ReVanced CLI 6.0.0 / Patcher 22 and R8 8.13.17.
These notes contain implementation facts and aggregate validation outcomes,
not account data, device identifiers or capture excerpts.

## ReVanced approach

Patch definitions locate verified methods and insert small calls. A shared,
unnamed dependency merges the runtime extension. Content filtering stays
separate from resource changes and installation identity. The bundle contains
both JVM classes for CLI and DEX for Android Manager.

The [upstream Feed filter](https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/feedfilter/FeedFilterPatch.kt)
uses response-method hooks and a runtime extension, with compatibility pinned
to TikTok 36.5.4 in the reviewed source. In 47.1.4, `FeedApiService.LIZ()` returns
`IFeedApi`, rather than the feed response. The response method is now
`fetchFeedList`, and conversion lives in `com.ss.android.ugc.tiktok.ConvertHelper`.
Updating only an old patch's version allowlist would target the wrong method.

## Verified feed targets

`FeedItemList` in classes4.dex exposes `items` and `preloadAds`. `getItems()`
has four object-return paths; `getAwemeList()` delegates to it. Each selected
filter hooks those returns and six response returns across `FeedApiService`,
`FeedApi` and `ConvertHelper`. This covers both getters and direct-field consumers.

`Aweme` in classes2.dex exposes `_isAd` and `_isSoftAd`. Its `isAd()` also
requires a decoded ad payload, so transport flags cover cases before that payload
exists. The ad patch clears four original `preloadAds` writes: clone, setter,
protobuf conversion and optimized JSON adapter. Injected helpers are excluded
from that scan to prevent recursive instrumentation.

The runtime helper returns the existing list when it is clean. Otherwise it copies
survivors in order, allowing fixed-size or immutable input. It preserves null
lists and entries. When filtering the stored list, it updates `feed.items` so
direct size reads agree. Separate getter lists do not overwrite unrelated fields.
Pagination cursors and `hasMore` remain unchanged; an all-unwanted batch becomes
empty and needs device testing for continued pagination.

## Shop signals

The APK's commerce helpers and `X/09Jg` enum identify these anchor types:

| Value | Meaning |
| --- | --- |
| 3 | SHOP |
| 6 | ANCHOR_SHOP_LINK |
| 33 | ANCHOR_SHOP_WINDOW |
| 35 | ANCHOR_SHOP_MIX |
| 89 | ANCHOR_SHOWCASE |

The helper also checks `getProductsCount()`, nonempty `getProductsInfo()` and
`getIsLiveHasProduct()`. Tests retain ordinary links such as Quizlet 36,
CapCut 54 and POI 45. A match removes the entire feed item; the Shop tab stays.
No caption, audio or image classifier is used. Unmarked promotions and non-Shop
paid partnerships may remain.

## Package changes and resource rebuilding

The separate package is `com.zhiliaoapp.musically.filtered`, labeled TikTok Filtered.
Component class names are preserved. Owned permissions, process identities and
all provider authorities receive distinct identities. Matching app-owned string
literals change in DEX; injected helpers retain their original constants.

The investigated standalone APK contained 3,092 empty PNG placeholders. Resource
compilation requires replacing them with valid transparent one-pixel PNGs while
retaining names and IDs. Dangling Play split metadata is removed from the fused APK.

APKTool decoded obfuscated custom attributes without namespaces. Recompilation
then dropped their IDs and caused `FlexLayout_Layout` inflation to fail. Restoring
`res-auto` namespaces on recognized custom attributes in 9,154 XML files corrected
that startup failure. These resource changes are needed for package renaming;
the content filters alone do not rebuild resources.

## Validation and takeaways

Behavior checks cover independent and combined filters, immutable inputs,
survivor order, clean-list identity, null values and all-filtered batches.
APK-derived checks verify all nine referenced TikTok members, four getter returns,
six response returns and four preload writes. Branches cannot bypass the getter
filter, and fixture models never enter the extension.

Real-device startup and filter activation were observed. This does not prove
removal across every feed surface or continued pagination after empty batches.
Prefer structural checks plus runtime evidence over widening compatibility labels.
Rebuilding resources deserves separate validation from bytecode changes.
