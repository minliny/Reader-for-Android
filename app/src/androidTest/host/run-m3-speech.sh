#!/usr/bin/env bash
set -euo pipefail
target="${1:?Exact Reader Android emulator serial required}"
evidence="${2:?New absolute evidence output required}"
[[ "$target" =~ ^emulator-[0-9]+$ && "$evidence" = /* ]]
adb="${READER_ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
[[ "$("$adb" -s "$target" emu avd name | head -1 | tr -d '\r')" == Reader-XPLAT-M1-API35-16K ]]
mkdir "$evidence"
umask 077
private="$(mktemp -d /private/tmp/reader-android-speech-tls.XXXXXXXX)"
port=
server=
cleanup() {
  if [[ -n "$port" ]]; then "$adb" -s "$target" reverse --remove "tcp:$port" || true; fi
  if [[ -n "$server" ]]; then kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true; fi
  rm -f "$private"/{fixture.cnf,root.key,root.pem,root.srl,server.csr,server.key,server.pem,root.der}
  rmdir "$private"
}
trap cleanup EXIT
cat > "$private/fixture.cnf" <<'OPENSSL'
[ req ]
distinguished_name = dn
prompt = no
[ dn ]
CN = Reader Wave 3 Android TLS fixture
[ ca ]
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
[ server ]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = IP:127.0.0.1
OPENSSL
openssl req -new -newkey rsa:2048 -nodes -x509 -days 2 -sha256 -config "$private/fixture.cnf" -extensions ca \
  -keyout "$private/root.key" -out "$private/root.pem" > /dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -config "$private/fixture.cnf" \
  -keyout "$private/server.key" -out "$private/server.csr" > /dev/null 2>&1
openssl x509 -req -in "$private/server.csr" -CA "$private/root.pem" -CAkey "$private/root.key" -CAcreateserial \
  -days 2 -sha256 -extfile "$private/fixture.cnf" -extensions server -out "$private/server.pem" > /dev/null 2>&1
cat "$private/server.pem" "$private/root.pem" > "$private/server-chain.pem"
openssl verify -CAfile "$private/root.pem" -purpose sslserver "$private/server.pem" > "$evidence/tls-verify.log"
openssl x509 -in "$private/root.pem" -outform der -out "$private/root.der"
fixture="$(cd "$(dirname "$0")" && pwd)/m3_speech_fixture.py"
python3 "$fixture" --port-file "$evidence/port.txt" --certificate "$private/server-chain.pem" --key "$private/server.key" \
  > "$evidence/https.log" 2>&1 &
server=$!
for unused in {1..60}; do
  [[ ! -s "$evidence/port.txt" ]] || break
  kill -0 "$server"
  sleep .1
done
port="$(cat "$evidence/port.txt")"
[[ "$port" =~ ^[0-9]+$ ]]
[[ "$(curl --fail --silent --show-error --cacert "$private/root.pem" "https://127.0.0.1:$port/ready")" = ready ]]
"$adb" -s "$target" reverse "tcp:$port" "tcp:$port"
test_ca="$(openssl base64 -A -in "$private/root.der")"
runner=io.reader.minliny.rebuild.android.dev.test/androidx.test.runner.AndroidJUnitRunner
test_class=io.reader.rebuild.NativeM3SpeechTest
since="$("$adb" -s "$target" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
[[ "$since" =~ ^[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$ ]]
printf 'adb -s %s shell am instrument -w -r -e class %s <local TLS fixture arguments> %s\n' \
  "$target" "$test_class" "$runner" > "$evidence/command.txt"
status=0
/usr/bin/time -p "$adb" -s "$target" shell am instrument -w -r -e class "$test_class" \
  -e readerSpeechTlsPort "$port" -e readerSpeechCA "$test_ca" "$runner" 2>&1 | tee "$evidence/instrumentation.log" || status=$?
"$adb" -s "$target" shell "logcat -d -T '$since' -v threadtime -s System.out:I AndroidRuntime:E TextToSpeech:W" \
  > "$evidence/logcat.log"
awk '/ANDROID_M3_(SYSTEM|HTTP)_TTS pid=/' "$evidence/logcat.log" | tee "$evidence/markers.log"
test "$status" -eq 0
grep -Fxq 'OK (2 tests)' "$evidence/instrumentation.log"
grep -q 'ANDROID_M3_SYSTEM_TTS .* trueNativeRange=.* staticLayout18to23=true .* retired=true' "$evidence/markers.log"
grep -q 'ANDROID_M3_HTTP_TTS .* tls=true wireRate=1.25 nativeExoPlayer=true .* coreQueueCompleted=true' "$evidence/markers.log"
