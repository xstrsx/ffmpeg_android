#!/usr/bin/env sh
# Gradle wrapper script for Unix-like systems.

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="FFmpeg Terminal Tool"

# Determine the location of the Gradle wrapper JAR file
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    GRADLE_WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
else
    echo "Could not find gradle-wrapper.jar. Please ensure it is present in the gradle/wrapper directory."
    exit 1
fi

# Execute the Gradle wrapper
exec java -jar "$GRADLE_WRAPPER_JAR" "$@"