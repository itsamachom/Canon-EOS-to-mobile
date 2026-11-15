#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
java -jar "${ECJ_JAR:-${TOOLCHAIN:-$PWD/build/toolchain}/ecj.jar}" -1.8 -encoding UTF-8 -d build/tests app/src/main/java/art/photoui/companion/Ptp.java app/src/main/java/art/photoui/companion/CameraRules.java app/src/main/java/art/photoui/companion/EosCamera.java app/src/main/java/art/photoui/companion/AppMedia.java app/src/main/java/art/photoui/companion/ExposureValues.java app/src/main/java/art/photoui/companion/LatestFrames.java tests/LiveSettingsTest.java tests/PtpTest.java tests/EosCameraTest.java
java -cp build/tests art.photoui.companion.PtpTest | tee build/ptp-tests.txt

java -cp build/tests art.photoui.companion.EosCameraTest | tee build/eos-tests.txt

java -cp build/tests art.photoui.companion.LiveSettingsTest | tee build/live-settings-tests.txt

node tests/monitor-analysis.cjs | tee build/monitor-analysis-tests.txt
