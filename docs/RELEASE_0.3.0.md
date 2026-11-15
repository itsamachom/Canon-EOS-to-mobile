# 0.3.0-prototype — Live View latency and exposure controls

## Concrete changes

- Removed the extra 150 ms post-frame delay (0.2.0 had a software ceiling below 6.7 requests/s before transport/render time). New request budget targets approximately 24 or 12 fps, counting work already spent; slow/empty replies back off.
- Acquisition runs independently of rendering. A latest-only mailbox retains the latest and in-flight JPEG; a slow renderer requests the newest frame and skips backlog. No interpolation or timer-driven replay of old real frames.
- No Base64 or disk writes per live frame. WebView reads an app-local JPEG response; immutable in-flight bytes are retained and responses use Cache-Control: no-store. No INTERNET permission or local TCP server added.
- Reused 16 KiB USB input buffer, direct bounded data reads and one streaming scratch buffer. JPEG dimensions read from bounded SOF parsing rather than raster decode.
- Telemetry: received JPEG response rate, browser display callback rate, read request time, and receipt-to-render callback age. These are not sensor unique-frame rate, MOV frame rate, or full motion-to-photon latency.
- App HUD for real ISO/aperture/shutter/EV and camera mode. It is not a capture of Canon menus, AF boxes or ML overlays.
- Parse EOS AvailListChanged options for four whitelisted exposure properties. Controls only offer recognized values advertised by the current camera/attached lens; modes gate aperture/shutter/EV, and Movie/recording/busy states block writes.
- Exposure writes require subsequent event readback for confirmation. Acknowledgement alone does not optimistically update the displayed value. Unknown/stale options and arbitrary properties are rejected.
- Same signer as published 0.1.1/0.2.0; versionCode 4. Original JPEG gallery and batch export retained.

## Evidence boundaries

Host protocol and mailbox/settings tests plus browser bridge tests pass; see TEST_RESULTS and BUILD_INFO. **0.3.0 has not been tested on a real Android/600D or emulator. No actual 24 fps or physical latency improvement is claimed.**

Camera JPEG generation, actual USB response time, phone rendering/thermal state and recording mode can limit performance. Segment 9 non-JPEG remains unsupported. Camera settings acceptance and advertised option delivery must be verified on hardware. Settings are intentionally limited to P/Tv/Av/M; there is no mode-dial write, REC command, touch AF or HDMI/UVC conversion.
