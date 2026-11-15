# Validation — 0.3.0-prototype

## Computer checks

- PTP tests retain fragmentation/coalescing/ZLP, transaction, streaming, rejected-reply and bound checks. New coverage crosses a 64 KiB data/response boundary using the reused buffer and decodes byte-width EV events without reading padding.
- Existing EOS/cache tests cover repeat shutter, cleanup, busy/ready, mode guards, live EVF ownership/restore and non-JPEG rejection.
- New Live/settings tests: actual advertised enumeration type/stride/bounds, stale enum removal, rejected/unadvertised/arbitrary writes, P/Tv/Av/M/recording/busy guards, event-confirmed versus unconfirmed values, ISO/Av/Tv/EV labels; latest-only mailbox across 100 producer frames with a stalled consumer; immutable in-flight bytes; stream reset; work-budget pacing; bounded JPEG SOF dimensions.
- Browser tests: exposure choices, disabled modes, emitted setting command, no optimistic value, confirmed readback/HUD; app-local live URLs, next-frame request, display ACK, actual supplied telemetry and target-rate selector; stale/late frame and demo isolation; existing gallery/multi-select/full preview/batch cancellation/capture flows; five viewport sizes and four demo screenshots.
- APK compiles against API 35 with ECJ 3.39.0/build-tools 35.0.0, v2/v3 signatures verify, certificate matches published 0.1.1 and 0.2.0; versionCode 4. No private key in source archive.

Exact assertion counts are recorded in BUILD_INFO from the successful test outputs. Screenshots and browser data are marked simulations, not Canon captures or device performance measurements.

## Still needs device testing

Real Android permissions/WebView interceptor/runtime/document picker/lifecycle; camera option-list events; actual ISO/aperture/shutter/EV write/readback; USB JPEG generation/transfer and phone rendering rates; end-to-end visual latency; streaming in Movie/REC modes; non-JPEG movie formats; ML coexistence.

A 24 fps budget test is not evidence that the 600D or phone achieves 24 fps. A 0.2.0 hardware report of lag is the motivation, not a hardware validation of this release. The prior single-shot symptom still needs regression testing on the user's camera.
