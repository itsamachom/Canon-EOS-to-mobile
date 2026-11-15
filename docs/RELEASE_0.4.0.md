# 0.4.0-prototype — landscape monitor and JPEG analysis

- Fixed target 12 fps; removed FPS selector and mutable Android bridge method.
- Three screens: connection, card gallery and monitor. Removed shutter and exposure controls, their bridge entry points and settings-event UI. Camera buttons control capture/exposure/recording. EVF setup/conditional restoration remains necessary for USB monitoring.
- Landscape activity orientation and left navigation rail. Monitor image, controls and status fit landscape; full-screen monitor is available. Device-specific Android orientation overrides/multiwindow remain untested.
- Original Sobel-based red peaking on JPEG luminance, maximum 320 px per side; fixed edge threshold. Focus aid only, not AF or an optical accuracy claim.
- Luminance JPEG histogram (256 bins, merged to 128 bars) from the full sampled frame before digital zoom. Not RAW/RGB or sensor clipping measurement. Both overlays enabled by default with simple toggles, aligned peaking zoom, and cleared on stale/stop/disconnect.
- Analysis stays on the phone, never alters downloaded originals; no ML code/overlay transfer. If analysis fails, live display and frame acknowledgement continue with a message.
- Latest-only mailbox, no Base64/disk per live frame, JPEG gallery and batch exports retained. App-local analysis JS uses an explicit asset route; no network permission.
- APK versionCode 5, same prototype signing key as previous public builds. Matching source, Thai instructions and SHA256SUMS included.

Host protocol, synthetic pixel and browser tests pass (exact counts in BUILD_INFO). Not tested on an actual Android/emulator/600D. No measured 12 fps, optical-focus accuracy, USB performance, orientation-lock or recording compatibility claim. Fixed 12 fps is an acquisition target; actual rates can be lower. Historical exposure/capture tests exercise retained protocol helpers, not user-accessible functions in 0.4.0.
