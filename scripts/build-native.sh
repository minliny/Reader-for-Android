#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
workspace=$(cd "$root/../.." && pwd)
core_pin=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["core"])' "$root/pins.json")
core=${READER_CORE_SOURCE:-$root/.build/core-source/$core_pin}
mode=${2:-android}
default_output="$root/.build/native-m3"
[[ "$mode" != host ]] || default_output="$root/.build/native-m3-host"
output=${1:-$default_output}
if [[ -z ${READER_CORE_SOURCE:-} && ! -e "$core/.git" ]]; then
  mkdir -p "$(dirname "$core")"
  git clone --quiet --shared --no-checkout "$workspace/Reader-Core" "$core"
  git -C "$core" checkout --quiet --detach "$core_pin"
  git -C "$core" remote remove origin
fi
[[ $(git -C "$core" rev-parse HEAD) == "$core_pin" ]]
[[ -z $(git -C "$core" status --porcelain) ]]
mkdir -p "$output" "$root/.build/cargo"
output=$(cd "$output" && pwd)
[[ "$mode" == android || "$mode" == host ]]
set -x
cd "$core"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
export CARGO_TARGET_DIR="${READER_CARGO_TARGET_DIR:-$root/.build/cargo}" CARGO_NET_OFFLINE=true CARGO_BUILD_JOBS="${READER_CARGO_BUILD_JOBS:-3}"
export READER_CORE_BUILD_INPUT_SNAPSHOT="$output/core-inputs-$mode.json"
export READER_CORE_BUILD_IDENTITY_OUTPUT="$output/core-identity-$mode.json"
node scripts/build-input-snapshot.mjs capture "$READER_CORE_BUILD_INPUT_SNAPSHOT"
if [[ "$mode" == host ]]; then
  cargo rustc --locked -p reader-ffi --lib --crate-type cdylib
  lib="$CARGO_TARGET_DIR/debug/libreader_core.dylib"
  bash bindings/android/tests/run-queued-jni-regression.sh "$lib" "$output/host-jni"
  shasum -a 256 "$lib" "$output/host-jni/lib/libreader_core_jni.dylib"
else
  : "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
  export ANDROID_HOME ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.3.11579264"
  export ANDROID_ABIS=arm64-v8a ANDROID_API=26
  export ANDROID_JNI_BUILD_ROOT="$output/android-jni"
  export CMAKE="$ANDROID_HOME/cmake/3.22.1/bin/cmake" CMAKE_BUILD_PARALLEL_LEVEL=3
  export LIBCLANG_PATH="$(xcode-select -p)/Toolchains/XcodeDefault.xctoolchain/usr/lib"
  export CC_SHELL_ESCAPED_FLAGS=1
  bash build-android-jni.sh
  lib="$output/android-jni/libs/arm64-v8a/libreader_core_jni.so"
  readelf=$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -path '*/bin/llvm-readelf' | head -1)
  "$readelf" -lW "$lib" | tee "$output/elf-load.txt"
  awk '$1=="LOAD"{found++; if ($NF != "0x4000") bad++} END {exit (found != 3 || bad)}' "$output/elf-load.txt"
  "$readelf" --dyn-syms -W "$lib" | awk '/flock@LIBC/ {print; found=1} END {exit !found}' \
    | tee "$output/elf-flock.txt"
  shasum -a 256 "$lib"
fi
node scripts/build-input-snapshot.mjs verify "$READER_CORE_BUILD_INPUT_SNAPSHOT"
python3 - "$READER_CORE_BUILD_IDENTITY_OUTPUT" "$core_pin" <<'PY'
import json,sys
identity=json.load(open(sys.argv[1]))
assert identity['gitCommit']==sys.argv[2] and identity['gitDirty'] is False, identity
print(identity)
PY
