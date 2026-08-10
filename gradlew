#!/usr/bin/env sh
set -eu
GRADLE_VERSION=9.5.0
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/native-wrapper"
GRADLE_HOME="$CACHE_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    curl --fail --location --retry 3 --output "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  fi
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP" -d "$CACHE_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
