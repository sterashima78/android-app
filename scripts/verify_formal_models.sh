#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_ROOT="${FORMAL_TOOL_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/mosaic-formal}"

QUINT_VERSION="0.33.0"
QUINT_SHA256="79eaa33aeb28a72681122656974557db553f4d01b792e22727075b41806db4d3"
QUINT_BIN="$CACHE_ROOT/quint-$QUINT_VERSION"

ALLOY_VERSION="6.2.0"
ALLOY_SHA256="6b8c1cb5bc93bedfc7c61435c4e1ab6e688a242dc702a394628d9a9801edb78d"
ALLOY_JAR="$CACHE_ROOT/alloy-$ALLOY_VERSION.jar"

mkdir -p "$CACHE_ROOT"

download_verified() {
  local url="$1"
  local target="$2"
  local expected_sha="$3"
  local executable="${4:-false}"

  if [[ -f "$target" ]]; then
    local existing_sha
    existing_sha="$(sha256sum "$target" | awk '{print $1}')"
    if [[ "$existing_sha" == "$expected_sha" ]]; then
      if [[ "$executable" == "true" ]]; then
        chmod +x "$target"
      fi
      return 0
    fi
    rm -f "$target"
  fi

  local tmp
  tmp="$(mktemp "$CACHE_ROOT/download.XXXXXX")"
  trap 'rm -f "$tmp"' RETURN

  curl --fail --location --silent --show-error --retry 3     "$url"     --output "$tmp"

  echo "$expected_sha  $tmp" | sha256sum --check --status
  mv "$tmp" "$target"
  [[ "$executable" == "true" ]] && chmod +x "$target"

  trap - RETURN
}

download_verified   "https://github.com/quint-co/quint/releases/download/v$QUINT_VERSION/quint-linux-amd64"   "$QUINT_BIN"   "$QUINT_SHA256"   true

download_verified   "https://github.com/AlloyTools/org.alloytools.alloy/releases/download/v$ALLOY_VERSION/org.alloytools.alloy.dist.jar"   "$ALLOY_JAR"   "$ALLOY_SHA256"

cd "$ROOT"

python3 scripts/verify_formal_spec_links.py

mapfile -t quint_models < <(find spec-models/quint -type f -name '*.qnt' | sort)
if [[ "${#quint_models[@]}" -eq 0 ]]; then
  echo "No Quint models found" >&2
  exit 1
fi

for model in "${quint_models[@]}"; do
  echo "Typechecking Quint model: $model"
  "$QUINT_BIN" typecheck "$model"

  echo "Verifying Quint safety invariant with TLC: $model"
  "$QUINT_BIN" verify --backend=tlc --invariant=safety "$model"
done

mapfile -t alloy_models < <(find spec-models/alloy -type f -name '*.als' | sort)
if [[ "${#alloy_models[@]}" -eq 0 ]]; then
  echo "No Alloy models found" >&2
  exit 1
fi

for model in "${alloy_models[@]}"; do
  echo "Checking Alloy model: $model"
  java -Djava.awt.headless=true -jar "$ALLOY_JAR"     exec -q -o - -t none "$model"
done

echo "Formal specification verification passed."
