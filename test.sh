#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
java -jar "${ECJ_JAR:-${TOOLCHAIN:-$PWD/build/toolchain}/ecj.jar}" -1.8 -encoding UTF-8 -d build/tests app/src/main/java/art/photoui/companion/Ptp.java app/src/main/java/art/photoui/companion/CameraRules.java tests/PtpTest.java
java -cp build/tests art.photoui.companion.PtpTest
