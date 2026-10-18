#!/bin/bash
# Builds robot-arkadas.apk without Gradle: javac -> dx -> aapt2 -> align -> apksig.
set -euo pipefail
cd "$(dirname "$0")"
SDK_JAR=${ANDROID_JAR:-/root/android-sdk/platforms/android-34/android.jar}
T=${TOOLS:-/root/tools}
B=build
rm -rf $B && mkdir -p $B/classes $B/res $B/dex $B/gen

# 1. resources -> R.java + resource APK, then Java -> .class (Java 8 bytecode, downgraded to v50 for the old dx)
$T/aapt2 compile --dir res -o $B/res/res.zip
$T/aapt2 link -I "$SDK_JAR" --manifest AndroidManifest.xml -A assets --java $B/gen -o $B/unsigned.apk $B/res/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$SDK_JAR" -d $B/classes $(find src $B/gen -name '*.java')
python3 - "$B/classes" <<'PY'
import sys, pathlib
for p in pathlib.Path(sys.argv[1]).rglob("*.class"):
    b = bytearray(p.read_bytes()); b[6:8] = (50).to_bytes(2, "big"); p.write_bytes(b)
PY

# 2. .class -> classes.dex
java -cp $T/dx-1.7.jar com.android.dx.command.Main --dex --output=$B/dex/classes.dex $B/classes

# 3. add code to the resource APK
(cd $B/dex && zip -q -j ../unsigned.apk classes.dex)

# 4. align + sign
python3 tools/align.py $B/unsigned.apk $B/aligned.apk
KS=${KEYSTORE:-release.p12}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -storetype PKCS12 -keystore "$KS" -storepass robot123 -alias robot \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Robot Arkadas" >/dev/null 2>&1
fi
mkdir -p $B/signer && javac -nowarn -cp $T/apksig-2.3.0.jar -d $B/signer tools/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED -cp $T/apksig-2.3.0.jar:$B/signer Sign $B/aligned.apk robot-arkadas.apk "$KS" robot123 robot
ls -la robot-arkadas.apk
