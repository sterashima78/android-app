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

"$godot" --headless --path "$project_dir" --script res://game_bootstrap.gd --check-only
"$godot" --headless --path "$project_dir" --script res://sudoku.gd --check-only
"$godot" --headless --path "$project_dir" --script res://klondike_model.gd --check-only
"$godot" --headless --path "$project_dir" --script res://klondike.gd --check-only
"$godot" --headless --editor --path "$project_dir" --quit

sudoku_output="$("$godot" --headless --path "$project_dir" --quit-after 2 2>&1)"
printf '%s\n' "$sudoku_output"
grep -Fq "Embedded game bootstrap: sudoku" <<<"$sudoku_output"

klondike_output="$("$godot" --headless --path "$project_dir" --quit-after 2 -- --game=klondike 2>&1)"
printf '%s\n' "$klondike_output"
grep -Fq "Embedded game bootstrap: klondike" <<<"$klondike_output"

"$godot" --headless --path "$project_dir" --script res://.klondike_model_test.gd
