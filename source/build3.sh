#!/bin/bash
# Builds bible-corner-kjt-<ver>.apk: the native app (java3) + the website window and services (java), against the real Android library.
set -e
VER=${1:-3.0}; CODE=${2:-30}
A=/root/apkb/dl/aapt/bin/x64/linux/aapt2; JAR=/root/apkb/sdk/android-33.jar; N=/root/apkb/native; APP=/root/apkb/wvapp
cd $N && rm -rf build gen out dex && mkdir -p build gen out dex
$A compile --dir res -o build/res.zip
$A link -I /root/apkb/android-framework.jar --manifest AndroidManifest.xml -o build/base.apk build/res.zip -A $APP/assets --java gen --min-sdk-version 24 --target-sdk-version 34 --version-code $CODE --version-name $VER
javac --release 8 -nowarn -Xlint:none -cp $JAR -d out java/*.java java3/*.java gen/today/knowingjesus/biblecorner/R.java 2> /tmp/app3.err || { grep -v JAVA_TOOL /tmp/app3.err | grep -v "^Note:" | head -60; echo 'COMPILE FAILED'; exit 1; }
python3 strip.py out
(cd out && rm -f ../app.jar && zip -q -r ../app.jar .)
java -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -XX:-BytecodeVerificationLocal -cp /root/apkb/d8jvm/d8all.jar com.android.tools.r8.D8 --release --min-api 24 --lib $JAR --output dex app.jar 2> /tmp/d83.err; [ -s dex/classes.dex ] || { grep -v JAVA_TOOL /tmp/d83.err | head; exit 1; }
python3 /root/apkb/tool/pack.py build/base.apk dex/classes.dex build/unsigned.apk
cd /root/apkb/signer && node sign.mjs $N/build/unsigned.apk $N/build/bible-corner-kjt-$VER.apk /root/apkb/keys/key.pem /root/apkb/keys/cert.pem
/root/apkb/venv/bin/apksigtool verify $N/build/bible-corner-kjt-$VER.apk 2>&1 | grep verified
