#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TOOLCHAIN="${TOOLCHAIN:-$PWD/build/toolchain}"
SDK_PLATFORM="${SDK_PLATFORM:-$TOOLCHAIN/android-35}"
BUILD_TOOLS="${BUILD_TOOLS:-$TOOLCHAIN/android-15}"
ECJ_JAR="${ECJ_JAR:-$TOOLCHAIN/ecj.jar}"
OUT="$PWD/build"
rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/classes" "$OUT/dex"
rm -f "$OUT/resources.zip" "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$OUT/unsigned.apk" --manifest app/src/main/AndroidManifest.xml -I "$SDK_PLATFORM/android.jar" -A app/src/main/assets "$OUT/resources.zip"
mapfile -t sources < <(find app/src/main/java -name '*.java' | sort)
java -jar "$ECJ_JAR" -1.8 -bootclasspath "$SDK_PLATFORM/android.jar" -encoding UTF-8 -d "$OUT/classes" "${sources[@]}"
mapfile -t classes < <(find "$OUT/classes" -name '*.class' | sort)
"$BUILD_TOOLS/d8" --lib "$SDK_PLATFORM/android.jar" --min-api 26 --output "$OUT/dex" "${classes[@]}"
python3 - "$OUT" <<'PY'
import sys,zipfile,pathlib
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for dex in (p/'dex').glob('*.dex'): z.write(dex,dex.name)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [[ ! -f "$OUT/prototype.keystore" ]]; then
  keytool -genkeypair -keystore "$OUT/prototype.keystore" -storepass android -keypass android -alias prototype -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=600D Companion Prototype' >/dev/null 2>&1
fi
"$BUILD_TOOLS/apksigner" sign --ks "$OUT/prototype.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias prototype --out "$OUT/600D-Companion-0.4.0-prototype.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/600D-Companion-0.4.0-prototype.apk"
"$BUILD_TOOLS/aapt2" dump badging "$OUT/600D-Companion-0.4.0-prototype.apk" | head -6
