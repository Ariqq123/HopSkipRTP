#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
JAVA_CMD="$JAVA_HOME/bin/java"
GRADLE_USER_HOME=${GRADLE_USER_HOME:-$APP_HOME/.gradle-user-home}

if [ ! -x "$JAVA_CMD" ]; then
    JAVA_CMD=java
fi

export GRADLE_USER_HOME

exec "$JAVA_CMD" \
    -Dorg.gradle.appname=gradlew \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
