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
need their own investigation. The initial Share-only repair was superseded by the
systematic behavior-reference restoration described below.

The repaired production APK passed five Share open/dismiss cycles with scrolling
to a different feed item after each dismissal on a stock-boot Android 36.1
emulator. Both Share views measured 1080 by 1105 pixels instead of zero. The
201 examined Share method implementations matched the original semantically.
Temporary root instrumentation was stopped before validation. No recipient was
selected and nothing was sent. Physical-device validation is still pending;
the emulator's intermittent video-rendering corruption remains separate work.

## Poll confirmation crashes and systematic resource restoration

A retained Java crash pointed to `BottomSheetBehavior.from()` in the bottom-toast
constructor `X/0I8f`, reached through `X/07p6.LJIIJJI` and `X/0YJK`. The latter
runnable belongs to `PollCell.scheduleVotedToast`: the crash happens while showing
the confirmation after a comment-poll vote, rather than while parsing a poll or
sending its vote request. Original toast layout `0x7f0d0009` (`layout/gu`) assigns
behavior attribute `0x7f060eed` to string `0x7f111ebf` on `@id/zm3`. In the installed
rebuilt APK that attribute was null.

TikTok's runtime resources are wrapped by `X/03Ia`, a `Resources` subclass. Its
`getString()` calls its overridden `getText()`, which uses
`NxResourceUtils.nativeGetText()` through `NxRewordManager` and the `nxreword`
native library. The missing strings therefore resolve at runtime even though
they are absent from the APK's normal resource table. APKTool lacks this runtime
lookup and emits null for the references. This is a decoder/runtime resource
mismatch, not evidence that the original reference was broken.

**Change package name** causes a full resource decode and rebuild under Patcher
22. The feed filters and download patch change bytecode only. The locally reviewed
ReVanced package-name and YouTube microG resource implementations likewise edit
the manifest through a resource patch; their authentication changes do not
address TikTok's resource decoding. Patcher's raw resource API avoids decoding,
but its version-22 packaging excludes raw `AndroidManifest.xml` and `res` from
other-file output. Simply changing the patch type would not package the manifest
changes. See [Patcher resource handling](https://github.com/ReVanced/revanced-patcher/blob/v22.0.0/patcher/src/commonMain/kotlin/app/revanced/patcher/patch/ResourcePatchContext.kt).

The wider compiled-layout audit found 56 references lost during decoding:
12 to `BottomSheetBehavior` and 44 to `AppBarLayout$ScrollingViewBehavior` through
resource `0x7f1119be`. Android runtime lookup verified both class names. The
central `ResourceRepairs` pass reads the original resource table to map layouts
back to their binary XML, checks matching element structure and behavior attribute
IDs, and restores these two verified class references where the decoded value is
null. It does not infer the behavior from the view type or replace every null
attribute. Unknown references, changed structure or a changed repair count fail
patching. Temporary original files are removed so they cannot override rebuilt
resources. The custom-attribute namespace restoration still preserves their IDs.

All 126 compiled layout behavior declarations in the rebuilt APK matched the
unmodified APK, including the untouched literal declarations. An emulator test
called the same toast builder on the UI thread, without submitting a vote. The
original APK succeeded; the previous Filtered build threw the retained exception;
the repaired build succeeded. This test used temporary emulator instrumentation,
not a rooted phone. An actual comment-poll post was unavailable, so a real vote
on the updated phone remains a separate verification step.

This supersedes maintaining a separate fix for each Share/toast layout. It proves
preservation of the audited behavior declarations, not that every other resource
in the APK survives decoding without loss.

## Duplicate patch bundles in Manager

Manager 2.6.0's bundle loader passes all configured bundles to Patcher before
filtering selected patches. Patcher 22's Android implementation creates one
`DexClassLoader` with those bundle paths. If old and new versions contain the
same Java class names, the first path can provide both versions' implementations,
even when only the newer source's patches are checked. The manifest version shown
in the source list does not establish which class implementation ran.

A phone build with duplicate imports completed all selected patches and installed
using the existing signing key. Share opened successfully, but auditing the actual
installed APK found 55 null behavior declarations and the previous Share-only
repair. Therefore that install does not validate the new resource or LIVE changes.
The corrected desktop APK has all 126 behavior declarations intact and passed the
emulator toast check. Testing was stopped at the user's request before rebuilding
the phone update. A real comment-poll vote remains untested.

Keep one copy/source of this bundle configured in Manager, rather than merely
unchecking the older copy. Compare the compiled output with the unmodified APK;
successful patch logs and updated source metadata alone are insufficient.

## LIVE Shop room metadata

Shopping LIVE cards can lack the existing Aweme product flags and anchors. Their
commerce metadata is carried by `Aweme.newLiveRoomData`, or the `newLiveRoomData`
and `room` fields of its `RoomFeedCellStruct`. These are the feed model's
`NewLiveRoomStruct` and `LiveRoomStruct`, not the separate LIVE SDK `Room` class.

Hide Shop videos now reads `hasCommerceGoods` and the optional `FYPCommerceStruct`
fields `productNum` and `popProductId`. Current goods or a positive product count
or preview-product identifier removes the feed card. Selling permission and
historical goods alone are insufficient. The code reads existing decoded fields
without converting a room, parsing additional JSON or inspecting the stream's
video/audio/captions. Direct access is guarded against the exact APK field types,
including nullable boxed `Long` values; compile fixtures are excluded from the
runtime extension.

Behavior checks cover all three room representations, null commerce records,
null/zero/negative product values, ordinary LIVE retention, independent ad
selection, immutable input and survivor order. APK-derived ABI checks cover all
19 referenced feed members. Android execution using TikTok's actual model classes
also removed three synthetic Shop LIVE cards, retained an ordinary stream with
permission/history only, and preserved the separate ad selection and input list.
These checks do not establish that every promotional
LIVE uses these fields. The supplied public LIVE page did not expose feed commerce
metadata, so removal of that particular broadcast in an actual For You response
still needs observation. Shop access and opening a LIVE directly are unchanged;
the patch filters feed cards.

## Disclosed promotions and LIVE commerce tags (1.4.0)

The platform ad flags do not cover every creator advertisement. An actual public
video labeled Paid partnership had both `_isAd` and `_isSoftAd` false, while
`Aweme.getCommerceVideoAuthInfo()` returned an `AwemeCommerceStruct` with
`brandedContentType = 8` and `brandOrganicType = 0`. TikTok's native predicates
`isBrandedContent()` and `isBrandOrganicContent()` check whether their respective
types are positive. Hide ads now uses those predicates rather than guessing from
an anchor, captions or translated disclosure text. A commerce record by itself,
with zero/negative types, is insufficient.

LIVE cards also carry `FeedRoomTagList` on both feed room classes. The native
commerce-card renderer recognizes tag ID `1000001`, independently of current
goods/count fields. Hide Shop videos checks this ID across the first, sub, bottom,
bottom-sub, commercial-disclosure and boost tag lists. Null entries and unknown
tag IDs remain safe. The separate `bcToggleTags` field supplies commercial labels
to `BcToggleInfoWidget`; Hide ads removes LIVE cards with a nonempty disclosure
there. Ordinary recommendations and boost tags do not become ads merely because
their text looks promotional. No additional JSON parsing or room conversion is
required in the feed filter.

Before/after Android checks used the exact video model captured from the
unmodified app, replayed through TikTok's configured Gson decoder. Version 1.3.0
retained that video; 1.4.0 removed it while retaining the ordinary control. The
Shop-only selection still retained that non-Shop partnership. Controlled native
LIVE models exercised direct raw rooms and both nested room representations:
1.3.0 retained tag-only commerce cards and commercial disclosures; 1.4.0 removed
each through its corresponding filter. Ordinary LIVE recommendations, nulls,
immutable input and independent selection were preserved. These LIVE cases test
the actual Android model classes and production APK, but are not a replay of the
supplied broadcast's full For You response.
Fresh feed sampling was interrupted by a native host-emulator crash during touch
input, including with an alternative software renderer. That limitation is
separate from the successful Android model and filter execution checks.

Behavior tests include null/empty disclosures, all six tag-list placements,
noncommercial metadata, unknown IDs, multiple positive branded types and survivor
order. The extension's 32 referenced TikTok members passed APK-derived ABI checks,
and all six patch selection combinations passed integration checks. The full
updated APK retained all 126 compiled layout behavior declarations. Captures,
public test links and account/device data remain private.

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
APK-derived checks verify all 32 referenced TikTok members, four getter returns,
six response returns and four preload writes. Branches cannot bypass the getter
filter, and fixture models never enter the extension.

Real-device startup and filter activation were observed. This does not prove
removal across every feed surface or continued pagination after empty batches.
Prefer structural checks plus runtime evidence over widening compatibility labels.
Rebuilding resources deserves separate validation from bytecode changes.
