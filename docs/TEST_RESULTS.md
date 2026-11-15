# Validation — 0.2.0-prototype / 15 November 2026

## Passed on the computer

- 41 PTP assertions: fragmented/coalesced/zero-length reads, partial writes, transaction IDs, streamed JPEG-sized payloads, strict bounds, EOS uint16/padding handling, property data-out transactions and diagnostic observations.
- 40 EOS/cache assertions: two captures without reconnect, P/Tv/Av/M, busy-ready waiting, no automatic shutter retry, full/half cleanup after failure, drain between release operations, forbidden mode guard, EVF start/restore/partial rollback, movie-mode busy setup, preserve external changes and recording/unknown-state EVF, frame segments 1/9-JPEG/11, busy/not-ready frames, reject non-JPEG/invalid lengths, token-only private cache access.
- 2,000 deterministic malformed datasets handled with bounded parser failures.
- Chromium/Playwright: four tabs without Guide; immediate shutter state and second explicit capture after completion; 3-column phone grid; progressive thumbnail updates; multi-select payload; folder batch callbacks/cancel-after-file/retained selection; original preview distinct from thumbnail; fit/100%; Live View ACK, stale overlay, late/stale-stream rejection; erase demo data/stop demo timer on real connection; 320/390/600/900 px and landscape 844×390; four screenshots.
- Compile against Android API 35 using ECJ 3.39.0, dex/package with build-tools 35.0.0. APK v2/v3 signatures verified.
- Certificate SHA-256 matches published 0.1.1. VersionCode increases 2→3. No private key in Git/source archive.
- No INTERNET, CAMERA, audio, or broad storage permission. App-local WebView HTTPS resources are served by an interceptor; document export uses Android folder selection.

## Not verified

Physical Android or emulator runtime of 0.2.0; actual resolution of the reported one-shot issue; USB permission/OTG/lifecycle; real camera AF/busy/file completion; real multiple JPEG transfers; original JPEG quality; Android document provider behavior and failed-write cleanup; EVF property acceptance/restore; still/movie/REC frame formats; latency/FPS; ML compatibility.

Fake transports and browser bridge mocks are not a camera test. Screenshots contain marked synthetic scenes, not photographs or actual Canon performance measurements. Seven compatibility deprecation warnings remain in the Android Activity; no compiler errors or disabled verification checks.
