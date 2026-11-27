#!/usr/bin/env bash
set -euo pipefail
serial="${1:?Exact approved Android AVD serial required}"
run="${2:?New run key required}"
evidence="${3:?New absolute evidence directory required}"
[[ "$run" =~ ^[a-zA-Z0-9_-]{1,64}$ && "$evidence" = /* ]]
mkdir "$evidence"
adb="${READER_ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
fixture="$(cd "$(dirname "$0")" && pwd)/m3_manga_fixture.py"
target="io.reader.minliny.rebuild.android.dev"
runner="$target.test/androidx.test.runner.AndroidJUnitRunner"
class="io.reader.rebuild.NativeM3MangaTest"
[[ "$("$adb" -s "$serial" emu avd name | head -1 | tr -d '\r')" = Reader-XPLAT-M1-API35-16K ]]
[[ "$("$adb" -s "$serial" shell getconf PAGE_SIZE | tr -d '\r')" = 16384 ]]
"$adb" -s "$serial" shell pm path "$target"
"$adb" -s "$serial" shell pm path "$target.test"
python3 "$fixture" --port-file "$evidence/port" > "$evidence/http.log" 2>&1 &
fixture_pid=$!
port=
cleanup() {
    if [[ -n "$port" ]]; then "$adb" -s "$serial" reverse --remove "tcp:$port" || true; fi
    kill "$fixture_pid" 2>/dev/null || true
    wait "$fixture_pid" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in {1..80}; do
    if [[ -s "$evidence/port" ]]; then break; fi
    kill -0 "$fixture_pid"
    sleep .1
done
port="$(cat "$evidence/port")"
[[ "$port" =~ ^[0-9]+$ ]]
curl --fail --silent "http://127.0.0.1:$port/ready"
"$adb" -s "$serial" reverse "tcp:$port" "tcp:$port"

phase() {
    local name="$1" method="$2" since status=0
    since="$("$adb" -s "$serial" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
    [[ "$since" =~ ^[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}$ ]]
    (
        set -x
        /usr/bin/time -p "$adb" -s "$serial" shell am instrument -w -r -e readerM3MangaPort "$port" \
            -e readerM3MangaRun "$run" -e class "$class#$method" "$runner"
    ) 2>&1 | tee "$evidence/$name.log" || status=$?
    "$adb" -s "$serial" shell "logcat -d -T '$since' -v threadtime -s System.out:I AndroidRuntime:E ReaderM3:E" > "$evidence/$name-logcat.log"
    grep 'M3_ANDROID_MANGA_' "$evidence/$name-logcat.log" | tee "$evidence/$name-markers.log" || true
    [[ "$status" = 0 ]]
    grep -Fxq 'OK (1 test)' "$evidence/$name.log"
}
phase write writeTilesRetryBudgetAndPosition
phase cold readPositionInIndependentProcess
grep -q 'M3_ANDROID_MANGA_TILE .*retry=PASS draw=PASS' "$evidence/write-markers.log"
grep -q 'M3_ANDROID_MANGA_WRITE .*sourcePixelBudget=PASS scratchReclaim=PASS' "$evidence/write-markers.log"
grep -q 'M3_ANDROID_MANGA_COLD .*staleWrite=REJECTED refetch=0' "$evidence/cold-markers.log"
echo 'PASS Android M3 manga write and independent-process cold readback'
