#!/bin/bash
# Builds bible-corner-kjt-<ver>.apk from: wvapp (manifest, res, assets/www) + wv/src (Java) + wv/stubs (compile-only Android stand-ins)
set -e
VER=${1:-1.8}; CODE=${2:-9}
A=/root/apkb/dl/aapt/bin/x64/linux/aapt2; WV=/root/apkb/wv; APP=/root/apkb/wvapp
cd $WV/stubs && rm -rf classes && mkdir classes && javac --release 8 -nowarn -d classes $(find src -name "*.java") 2> /tmp/stubs.err || { grep -v JAVA_TOOL /tmp/stubs.err; exit 1; }
cd $APP && rm -rf build gen && mkdir -p build gen
$A compile --dir res -o build/res.zip
$A link -I /root/apkb/android-framework.jar --manifest AndroidManifest.xml -o build/base.apk build/res.zip -A assets --java gen --min-sdk-version 24 --target-sdk-version 34 --version-code $CODE --version-name $VER
cd $WV && rm -rf out dex && mkdir out dex
javac --release 8 -nowarn -cp stubs/classes -d out src/today/knowingjesus/biblecorner/*.java $APP/gen/today/knowingjesus/biblecorner/R.java 2> /tmp/app.err || { grep -v JAVA_TOOL /tmp/app.err; echo 'COMPILE FAILED'; exit 1; }
for c in LauncherActivity MediaService Packed Js NoticeJob VerseWidget Remind Updater Speech; do [ -f out/today/knowingjesus/biblecorner/$c.class ] || { echo "missing $c"; exit 1; }; done
python3 strip.py out
(cd out && rm -f ../app.jar && zip -q -r ../app.jar .)
java -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -XX:-BytecodeVerificationLocal -cp /root/apkb/d8jvm/d8all.jar com.android.tools.r8.D8 --release --min-api 24 --no-desugaring --output dex app.jar 2> /tmp/d8.err; [ -s dex/classes.dex ] || { grep -v JAVA_TOOL /tmp/d8.err; exit 1; }
python3 /root/apkb/tool/pack.py $APP/build/base.apk dex/classes.dex $APP/build/unsigned.apk
cd /root/apkb/signer && node sign.mjs $APP/build/unsigned.apk $APP/build/bible-corner-kjt-$VER.apk /root/apkb/keys/key.pem /root/apkb/keys/cert.pem
/root/apkb/venv/bin/apksigtool verify $APP/build/bible-corner-kjt-$VER.apk 2>&1 | grep verified
