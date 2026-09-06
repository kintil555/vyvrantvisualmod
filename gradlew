#!/bin/sh
set -e
DIR="$(cd "$(dirname "$0")" >/dev/null && pwd)"
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" ${JAVA_OPTS} -cp "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
