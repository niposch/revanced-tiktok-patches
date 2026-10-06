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

### Share dialog resource loss

The separate installation could resume its Share fragment while keeping its
view at zero dimensions. Back dismissed an empty fullscreen dialog and restored
feed input. View-only instrumentation found an `IllegalArgumentException` from
`BottomSheetBehavior.from()` during the dialog's `setContentView()` call. The
dialog-suppression checker was disabled and the underlying `Dialog.show()` ran.

The original shared dialog layout, resource `0x7f0d0b5e` (`layout/bl_`), assigns
behavior attribute `0x7f060eed` (`c4g`) to string resource `0x7f111ebf` on its
`@id/g3d` container. The original app resolves that string to
`com.google.android.material.bottomsheet.BottomSheetBehavior`. APKTool instead
decodes the reference as `@null`; recompilation keeps the null and loses the
behavior. Declaring the verified class name directly in that layout repairs the
resource without changing TikTok's Share implementation or the content filters.
The repair checks for exactly one expected container and fails on an unexpected
declaration rather than applying to a different layout silently.

An absent entry in a desktop resource dump is insufficient evidence that a
resource is unusable on Android. Compare compiled attributes and runtime resource
resolution when rebuilding obfuscated resources. Other decoded null references
need their own investigation; the Share repair is scoped to this verified layout.

The repaired production APK passed five Share open/dismiss cycles with scrolling
to a different feed item after each dismissal on a stock-boot Android 36.1
emulator. Both Share views measured 1080 by 1105 pixels instead of zero. The
201 examined Share method implementations matched the original semantically.
Temporary root instrumentation was stopped before validation. No recipient was
selected and nothing was sent. Physical-device validation is still pending;
the emulator's intermittent video-rendering corruption remains separate work.

## Download permissions and missing media sources

TikTok 47.1.4 separates download permissions from general sharing permissions.
`AwemeACLShare` exposes `downloadGeneral`, `downloadMaskPanel` and
`downloadSharePanel`, each an `ACLCommonShare` record. The investigated restricted
video and photo post both carried `code = 1`, `showType = 0`, `transcode = 1`.
Their native Share menus omitted Download. The Save action checks these records
when constructing, showing and executing the action; changing only a visible
button would leave later checks intact.

The optional **Enable downloads** patch changes `code` to zero and `showType` to
two when those three records are returned. It preserves a missing record as null,
retains transcode mode and messages, and leaves general/third-party sharing
records and the global `ACLCommonShare` getters unchanged. Targets check the
original getter instructions and registers before insertion. An inserted null
branch must bind to the existing return instruction: copying a locally assembled
label into another instruction list can move its destination and fail Android
verification. APK-derived checks and Android execution test that null path.

The restricted video had no dedicated download or no-watermark source, while its
H.264 playback source remained available. Native download selector `X/19k8.LIZ`
can skip its playback fallback under an experiment flag. The patch adds that
fallback immediately after the verified no-watermark lookup, only when its result
is null. Existing source selection, cache-key construction and native saving then
continue. A present download source remains selected. The photo pipeline uses
its existing image URLs and requires no source substitution.

These are client-side permission and source-selection changes. They cannot
restore deleted/private media, expired or unavailable URLs, or bypass server
authorization or protected media. Confirm success with a nonempty saved file and
media decoding, rather than button visibility, a toast or a MediaStore row alone.
Raw test links, post identifiers, screenshots and instrumentation stay private.

Before/after validation used the same restricted video and photo post. Final
tests ran on a stock-boot Android 36.1 emulator without Magisk or Frida processes:

| Test | Before | After |
| --- | --- | --- |
| Restricted video | Download absent; permission code 1 / show type 0 | Saved 7,377,646-byte video, 720 by 1280 pixels, duration 46.718 seconds; playback confirmed in the media player. |
| Restricted photo post | Download absent; permission code 1 / show type 0 | Saved 55,456-byte image, 1080 by 863 pixels; decoded pixels matched the displayed post. |

The permission-only trial exposed Download but produced an empty video file.
Source fallback was therefore part of the final tested patch. Physical file sizes
were checked independently of MediaStore, and the final files had no pending
write flag. Synthetic Android checks covered all three download getters: null
preservation, permission changes, transcode/message preservation and unchanged
general sharing permissions. APK integration checks cover download selection
alone and together with both feed filters. These results establish the two tested
cases, not universal saving across every post type or server configuration.

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
