#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

FEATURE_TABLE_START = "<!-- feature-modules:start -->"
FEATURE_TABLE_END = "<!-- feature-modules:end -->"
LAYER_ORDER = ("domain", "data", "ui")


def feature_modules_from_settings(settings_text: str) -> dict[str, tuple[str, ...]]:
    modules: dict[str, set[str]] = {}
    for project_path in re.findall(r'include\("(:feature:[^"]+)"\)', settings_text):
        parts = project_path.split(":")
        if len(parts) != 4 or parts[1] != "feature":
            raise ValueError(f"unsupported feature module path: {project_path}")
        feature, layer = parts[2], parts[3]
        modules.setdefault(feature, set()).add(layer)

    def ordered(layers: set[str]) -> tuple[str, ...]:
        known = [layer for layer in LAYER_ORDER if layer in layers]
        unknown = sorted(layers.difference(LAYER_ORDER))
        return tuple(known + unknown)

    return {feature: ordered(layers) for feature, layers in sorted(modules.items())}


def feature_modules_from_document(module_map_text: str) -> dict[str, tuple[str, ...]]:
    if FEATURE_TABLE_START not in module_map_text or FEATURE_TABLE_END not in module_map_text:
        raise ValueError("feature module table markers are missing")
    block = module_map_text.split(FEATURE_TABLE_START, 1)[1].split(FEATURE_TABLE_END, 1)[0]
    modules: dict[str, tuple[str, ...]] = {}
    for match in re.finditer(r"(?m)^\|\s*([a-z0-9][a-z0-9-]*)\s*\|\s*([^|]+?)\s*\|$", block):
        feature = match.group(1)
        layers = tuple(part.strip() for part in match.group(2).split("/") if part.strip())
        if feature in modules:
            raise ValueError(f"duplicate feature row: {feature}")
        modules[feature] = layers
    return modules


def verification_errors(settings_text: str, module_map_text: str) -> list[str]:
    expected = feature_modules_from_settings(settings_text)
    documented = feature_modules_from_document(module_map_text)
    errors: list[str] = []

    for feature in sorted(expected.keys() - documented.keys()):
        errors.append(f"missing feature row: {feature} -> {' / '.join(expected[feature])}")
    for feature in sorted(documented.keys() - expected.keys()):
        errors.append(f"stale feature row: {feature} -> {' / '.join(documented[feature])}")
    for feature in sorted(expected.keys() & documented.keys()):
        if expected[feature] != documented[feature]:
            errors.append(
                f"layer mismatch for {feature}: settings={' / '.join(expected[feature])}, "
                f"docs={' / '.join(documented[feature])}"
            )
    return errors


def main() -> int:
    repository_root = Path(__file__).resolve().parent.parent
    settings_text = (repository_root / "settings.gradle.kts").read_text(encoding="utf-8")
    module_map_text = (repository_root / "docs/architecture/module-map.md").read_text(encoding="utf-8")
    try:
        errors = verification_errors(settings_text, module_map_text)
    except ValueError as error:
        print(f"Module map verification failed:\n- {error}", file=sys.stderr)
        return 1

    if errors:
        print("Module map verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Module map verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
