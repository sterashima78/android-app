#!/usr/bin/env sh
set -eu

GRADLE_VERSION=9.8.1
GRADLE_SHA256=dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/native-wrapper"
GRADLE_HOME="$CACHE_DIR/gradle-$GRADLE_VERSION"

verify_zip() {
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$1" | cut -d ' ' -f 1)
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "$1" | cut -d ' ' -f 1)
  else
    echo "A SHA-256 command (sha256sum or shasum) is required." >&2
    exit 1
  fi
  if [ "$actual" != "$GRADLE_SHA256" ]; then
    echo "Gradle distribution checksum mismatch; refusing to execute unverified files." >&2
    exit 1
  fi
}

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    TMP_ZIP="$ZIP.tmp.$$"
    trap 'rm -f "$TMP_ZIP"' EXIT HUP INT TERM
    curl --fail --location --retry 3 --output "$TMP_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    verify_zip "$TMP_ZIP"
    mv "$TMP_ZIP" "$ZIP"
    trap - EXIT HUP INT TERM
  fi
  verify_zip "$ZIP"
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP" -d "$CACHE_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
