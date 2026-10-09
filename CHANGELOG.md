# Changelog

## 1.4.0

- Extend Hide ads to remove creator-disclosed paid partnerships and self-promotional videos that do not carry platform ad flags. Use TikTok's native branded-content predicates.
- Filter commercial LIVE disclosures through their dedicated tag metadata. Retain ordinary LIVE recommendation tags and empty disclosures.
- Extend Hide Shop videos to recognize native commerce tag `1000001` across LIVE room tag lists, including cards without current-goods/product-count flags. Match the semantic ID rather than translated label text.
- Add positive/negative behavior cases and APK-derived checks for all newly referenced model members. Keep the same five selectable patches.

## 1.3.0

- Restore all 56 missing bottom-sheet and scrolling behavior references during resource rebuilding, using each original compiled layout to verify the replacement. Replace the Share-only workaround and fix the confirmation toast that crashed after voting in comment polls.
- Extend Hide Shop videos to inspect direct and nested LIVE room commerce metadata. Remove cards with current goods or preview products while preserving ordinary LIVE streams and selling permission alone.
- Fix Change package name failing in ReVanced Manager when removing Play split metadata. Snapshot manifest elements before removal to handle both Android and desktop XML parsers.
- Document the separate patcher process and larger heap needed when TikTok resource decoding exceeds Manager's default memory limit.
- Add compiled-layout validation against the unmodified APK, alongside LIVE filter behavior and ABI checks.

## 1.2.0

- Add optional Enable downloads for restricted videos and photo posts on TikTok 47.1.4. Scope permission changes to the three download records, preserving unrelated sharing permissions and existing download processing.
- Use the native H.264 playback source when the video downloader's no-watermark source is missing. Verify a previously restricted video and photo post produce valid saved files on a stock-boot emulator.

## 1.1.1

- Restore the shared bottom-sheet behavior declaration lost during resource decoding by Change package name. This repairs Share opening an empty dialog that blocks feed scrolling.

## 1.1.0

First source release, based on the tested local 1.0.7 registration repair.

- Add independent Hide ads and Hide Shop videos patches for TikTok 47.1.4.
- Add Change package name with separate app data and an automatic Fix device registration dependency.
- Correct the renamed package's registration header without installed-original or certificate-spoofing dependencies.
- Adopt short ReVanced-style patch names and concrete descriptions.
- Keep the repository and build limited to production patches/helpers and their maintenance tools; preserve investigation tooling privately.
- Add behavior and APK-derived validation, public findings, source packaging and privacy checks.

Authentication succeeded on a stock emulator with the registration repair.
Physical-device authentication, actual promotion removal and a run without the
original app installed remain unverified. See the research notes for evidence limits.
