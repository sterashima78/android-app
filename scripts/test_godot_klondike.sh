#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: bash scripts/test_godot_klondike.sh /path/to/godot" >&2
  exit 2
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project_dir="$root_dir/feature/game/ui/src/main/assets"
test_source="$root_dir/scripts/godot/klondike_model_test.gd"
test_target="$project_dir/.klondike_model_test.gd"
godot="$1"

cleanup() {
  rm -f "$test_target"
}
trap cleanup EXIT

cp "$test_source" "$test_target"

"$godot" --headless --path "$project_dir" --script res://sudoku.gd --check-only
"$godot" --headless --path "$project_dir" --script res://klondike_model.gd --check-only
"$godot" --headless --path "$project_dir" --script res://klondike.gd --check-only
"$godot" --headless --editor --path "$project_dir" --quit
"$godot" --headless --path "$project_dir" --scene res://klondike.tscn --quit-after 2
"$godot" --headless --path "$project_dir" --script res://.klondike_model_test.gd
