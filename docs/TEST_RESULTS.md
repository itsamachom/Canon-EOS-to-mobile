# Verification — 0.4.0-prototype

- Host Java: PTP parser/container/fuzz, EOS/EVF/cache regression and mailbox tests. Historical shutter/settings helpers remain tested but have no Android bridge/UI access in this release. Exact counts are in BUILD_INFO.
- Synthetic pixel tests: black/white/RGB histogram bins and totals, flat-image rejection, localized Sobel edges, disabled edges and malformed dimensions. These do not establish optical focus accuracy.
- Headless browser: three tabs with no capture/settings/FPS selector, five landscape viewport widths, gallery thumbnail/multiselect/full JPEG/batch cancellation, read-only HUD, same-origin JPEG canvas overlays, toggle/zoom/stale clearing, frame acknowledgement and demo/real isolation. Screenshots are demo UI only.
- APK v2/v3 signature, versionCode 5, matching published signing certificate, APK/source assets and archive checksums verified at packaging.

**Not tested:** physical Android or emulator, orientation lock/OS overrides, USB permission/dialogs/transport, folder picker, camera output/lifecycle/recording, focus accuracy, phone heat/battery and real frame rate/latency. 12 fps is a fixed target, not an achieved measurement. Histogram describes downsampled Live View JPEG, not RAW clipping.
