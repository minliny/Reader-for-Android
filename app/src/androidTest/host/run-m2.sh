#!/usr/bin/env bash
set -euo pipefail
serial="${1:?Exact approved Android target required}"
run="${2:?Provide a unique instrumentation run key}"
evidence="${3:?Provide a new absolute evidence directory}"
[[ "$run" =~ ^[a-zA-Z0-9_-]{1,64}$ && "$evidence" = /* ]]
mkdir "$evidence"
adb="${READER_ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
fixture="$(cd "$(dirname "$0")" && pwd)/loopback_fixture.py"
target="io.reader.minliny.rebuild.android.dev"
runner="$target.test/androidx.test.runner.AndroidJUnitRunner"
class="io.reader.rebuild.NativeM2JourneyTest"
"$adb" -s "$serial" shell pm path "$target"
"$adb" -s "$serial" shell pm path "$target.test"
python3 "$fixture" --port-file "$evidence/loopback-port.txt" >"$evidence/http.log" 2>&1 &
fixture_pid=$!
port=
cleanup() {
    if [[ -n "$port" ]]; then "$adb" -s "$serial" reverse --remove "tcp:$port" || true; fi
    kill "$fixture_pid" 2>/dev/null || true
    wait "$fixture_pid" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in {1..80}; do
    if [[ -s "$evidence/loopback-port.txt" ]]; then break; fi
    kill -0 "$fixture_pid"
    sleep .1
done
port="$(cat "$evidence/loopback-port.txt")"
[[ "$port" =~ ^[0-9]+$ ]]
curl --fail --silent "http://127.0.0.1:$port/ready"
"$adb" -s "$serial" reverse "tcp:$port" "tcp:$port"

phase() {
    local name="$1" test_class="$2" method="$3" since status=0
    since="$("$adb" -s "$serial" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
    [[ "$since" =~ ^[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$ ]]
    (
        set -x
        /usr/bin/time -p "$adb" -s "$serial" shell am instrument -w -r -e readerM2Port "$port" \
            -e readerM2Run "$run" -e class "$test_class#$method" "$runner"
    ) 2>&1 | tee "$evidence/$name.log" || status=$?
    "$adb" -s "$serial" shell "logcat -d -T '$since' -v threadtime -s System.out:I AndroidRuntime:E" \
        >"$evidence/$name-logcat.log"
    awk '/M2_NATIVE_(TARGET|HOST|HTTP|LOGIN|SOURCE|ONLINE|DOWNLOAD|COLD|REJECT) pid=|M2_NATIVE_DOWNLOAD request=/' \
        "$evidence/$name.log" "$evidence/$name-logcat.log" | tee "$evidence/$name-markers.log"
    [[ "$status" = 0 ]]
    grep -Fxq 'OK (1 test)' "$evidence/$name.log"
}
phase write "$class" writeThroughRealAndroidHostAndCore
phase cold "$class" coldProcessRestoresExactSourceScalarAndOriginalDownload
grep -q 'M2_NATIVE_HOST .*gbk=d6d0cec4 http503=true partial=131080 webEval=true' "$evidence/write-markers.log"
grep -q 'M2_NATIVE_LOGIN .*hostToWeb=true webToOkHttp=true sourceIsolation=true' "$evidence/write-markers.log"
grep -q 'M2_NATIVE_SOURCE .*conditionalConflict=true levels=L1-L5 scopedClear=true subscription=2' "$evidence/write-markers.log"
grep -q 'M2_NATIVE_ONLINE .*searchReferenceDetail=true .*offlineCache=true switchedScalar=14' "$evidence/write-markers.log"
grep -q 'M2_NATIVE_DOWNLOAD .*panelRetiredOwner=true lifecycle=paused,resumed,cancelled' "$evidence/write-markers.log"
grep -q 'M2_NATIVE_COLD .*scalar=14 cache=true .*exactPayload=true' "$evidence/cold-markers.log"
echo 'PASS Android M2 production-host loopback and independent-process readback'
