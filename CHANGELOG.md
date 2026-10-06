# Changelog

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
