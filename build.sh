#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/lib/android-sdk}}"
ANDROID_JAR="${ANDROID_JAR:-$SDK_ROOT/platforms/android-23/android.jar}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
R8_JAR="${R8_JAR:?Set R8_JAR to your local R8 jar}"
mkdir -p build/classes build/dex dist
find build/classes -type f -name '*.class' -delete
find build/dex -type f -name '*.dex' -delete
aapt package -f -M AndroidManifest.xml -A assets -S res -I "$ANDROID_JAR" -F build/resources.apk
"${JAVA_BIN}javac" -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d build/classes src/com/codex/ringlab/*.java
"${JAVA_BIN}jar" cf build/classes.jar -C build/classes .
"${JAVA_BIN}java" -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 23 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
python3 - <<'PY'
from pathlib import Path
import zipfile
with zipfile.ZipFile('build/resources.apk') as src,zipfile.ZipFile('build/unsigned.apk','w') as dst:
    for item in src.infolist():dst.writestr(item,src.read(item.filename))
    for dex in Path('build/dex').glob('*.dex'):dst.write(dex,dex.name)
PY
zipalign -f 4 build/unsigned.apk build/aligned.apk
APK=dist/N5D-RingEffects-1.1.apk
if [[ -n "${KEYSTORE:-}" ]]; then
    : "${KS_ALIAS:?Set KS_ALIAS}" "${KS_PASSWORD:?Set KS_PASSWORD}" "${KEY_PASSWORD:?Set KEY_PASSWORD}"
    apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" --ks-pass env:KS_PASSWORD --key-pass env:KEY_PASSWORD --out "$APK" build/aligned.apk
    apksigner verify --verbose "$APK"
    (cd dist && sha256sum N5D-RingEffects-1.1.apk) > dist/SHA256SUMS.txt
else
    cp build/aligned.apk dist/N5D-RingEffects-1.1-unsigned.apk
    printf '%s\n' 'Unsigned APK: dist/N5D-RingEffects-1.1-unsigned.apk'
fi
