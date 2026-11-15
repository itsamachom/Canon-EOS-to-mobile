# Validation — 15 November 2026 / 0.1.1-prototype

Standalone migration build; reconstructed from the previous conversation after the unsent local workspace was lost. See MIGRATION.md.

## Passed on the build computer

- ECJ 3.39.0 compiles all Java sources against Android API 35; Android build-tools 35.0.0 builds an installable APK package.
- apksigner verifies APK v2/v3 signatures. Application ID art.photoui.companion; versionCode 2; min SDK 26, target SDK 35.
- No INTERNET, CAMERA, or broad storage permissions declared. USB permission is requested through Android USB Host; JPEG export uses the document picker.
- 37 PTP assertions: unsigned values, fragmented/coalesced input, zero-length packet handling, partial writes, streaming payload, response rejection, transaction mismatch, size limits, disconnect mid-header, OpenSession ID zero, dataset bounds, EOS event parsing, filenames, capture guards, and full/half shutter release after rejection without capture retry.
- 2,000 deterministic malformed parser datasets handled without unchecked bounds failures.
- Headless Chromium/Playwright checks: demo navigation/capture/photos, simulated Java bridge calls, absent capture capability, busy/disconnect state, late values ignored after disconnect, untrusted filenames rendered as text, overflow checks at 320/390/600/900 px, shutter visible above bottom navigation at 390×844.
- Four screenshots regenerated from the same bundled HTML. These are browser screenshots; values and cup illustration in demo mode are synthetic.

## Not yet tested

- Installation and runtime on Android hardware or Android emulator.
- USB permission/attach/detach lifecycle, WebView-to-Java integration, and Android document export on a physical phone.
- Real 600D 1.0.2 commands/property events, one exposure per request, actual on-card write completion, JPEG integrity and transfer speed.
- Reconnect, background/termination, sleep, cable loss during transfers, multiple phone/OTG combinations, and coexistence with Magic Lantern.

Compilation reports seven Android compatibility deprecation warnings; no errors. No verification checks were disabled. Tests on the computer do not establish camera compatibility.
