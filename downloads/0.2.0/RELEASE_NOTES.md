# 0.2.0-prototype — 15 November 2026

User feedback for the previous build: one still exposure in M then no visible response; automatic thumbnails needed; preview too blurry; multi-file selection/download wanted; remove guide tab; add a USB Live View monitor.

## Changes

- Foreground command state is separated from heartbeat polling. A shutter tap gets immediate feedback. Diagnostics report the last PTP operation and response state without serial numbers or pixel data.
- New EOS capture controller drains events after half press and between full/half release, releases both buttons after rejected commands, waits on camera-busy state, processes object-added events and never automatically retries a shutter request.
- Accept P/Tv/Av/M with single drive; AF is delegated to the camera. M Bulb, Movie still capture and unconfirmed card destination remain blocked.
- Decode known EOS uint16 properties as uint16 instead of consuming padding as value bits.
- Gallery grid with automatic background thumbnails, bounded cache, load more, multi-select and up to 80 original JPEGs per export batch using one Android folder picker.
- Full preview reads GetObject; the former GetThumb-only enlarged preview was low resolution. Original dimensions/size and fit/100% controls are shown. Camera JPEG quality/resolution remain the upper limit.
- New USB monitor with EVF ownership/restoration, bounded segment parser, JPEG-only display, one-frame ACK backpressure, real displayed FPS, stale-image overlay, stream generation IDs, grid/digital display zoom/landscape/focus mode.
- Leaving the monitor or pausing the Activity stops the feed. Do not issue recording stop or disable EVF mode during recording, movie mode, failed status refresh, or unknown recording status.
- Remove the guide tab. Documentation remains on GitHub. Same verified signer as published 0.1.1; versionCode 3.

## Evidence and limits

81 pure-Java assertions (41 PTP, 40 EOS/cache) plus 2,000 malformed datasets pass. Browser tests cover repeat capture UI, gallery/selection/batch callbacks, full preview, Live View ACK/stale/late frame handling, demo-to-real isolation and five viewport sizes. APK signature verifies.

**No physical 600D/Android or emulator test of 0.2.0 was performed.** The original one-shot symptom has not been reproduced or verified resolved on hardware; the cause remains unconfirmed without camera/phone traces. The source changes address concrete state/protocol weaknesses, not proof that every cause is eliminated.

Movie-mode monitoring is implemented only for JPEG frames delivered by the camera. EOS segment 9 non-JPEG/raw output is rejected explicitly. Streaming during REC, actual latency/FPS, Android document providers, USB detach/lifecycle and coexistence with ML need hardware verification. There is no movie REC command, sound recording, RAW/MLV transfer, USB UVC conversion, touch autofocus or HDMI-equivalent guarantee.
