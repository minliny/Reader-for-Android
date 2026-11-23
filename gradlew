#!/bin/sh
set -eu
READER_PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${READER_JAVA_HOME:-}" ]; then
    JAVA_HOME=$READER_JAVA_HOME
elif [ -x /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
elif [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo 'Set READER_JAVA_HOME or JAVA_HOME to the pinned JDK 17.' >&2
    exit 1
fi
export JAVA_HOME
exec "$JAVA_HOME/bin/java" -classpath "$READER_PROJECT_ROOT/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
