#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export JAVA_HOME ANDROID_HOME PATH="$JAVA_HOME/bin:$PATH"
gradle_bin=${READER_GRADLE_BIN:-$(command -v gradle)}
"$gradle_bin" --version | grep -Eq '^Gradle 8\.13$' || { echo 'Gradle 8.13 is required' >&2; exit 2; }
exec "$gradle_bin" --no-daemon "$@"
