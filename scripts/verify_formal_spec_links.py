#!/usr/bin/env python3

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SPEC_DIR = ROOT / "docs" / "spec"
MODEL_ROOT = ROOT / "spec-models"
MODEL_SUFFIXES = {".qnt", ".als"}

model_files = {
    path.relative_to(ROOT).as_posix()
    for path in MODEL_ROOT.rglob("*")
    if path.is_file() and path.suffix in MODEL_SUFFIXES
}

referenced_models: set[str] = set()
errors: list[str] = []

link_pattern = re.compile(r"\]\(([^)#]*spec-models/(?:quint|alloy)/[^)#]+)\)")

for spec in sorted(SPEC_DIR.glob("*.md")):
    content = spec.read_text(encoding="utf-8")
    for raw_target in link_pattern.findall(content):
        target = (spec.parent / raw_target).resolve()
        try:
            relative = target.relative_to(ROOT).as_posix()
        except ValueError:
            errors.append(f"{spec.relative_to(ROOT)}: model link escapes repository: {raw_target}")
            continue

        if relative not in model_files:
            errors.append(f"{spec.relative_to(ROOT)}: missing formal model: {raw_target}")
            continue

        referenced_models.add(relative)

for model in sorted(model_files):
    if model not in referenced_models:
        errors.append(f"{model}: no docs/spec/*.md document links to this model")

    model_path = ROOT / model
    model_content = model_path.read_text(encoding="utf-8")
    declared_specs = re.findall(
        r"^// - (docs/spec/[A-Za-z0-9._/-]+[.]md)$",
        model_content,
        flags=re.MULTILINE,
    )
    if not declared_specs:
        errors.append(f"{model}: missing natural-language specification declaration")
        continue

    for declared_spec in declared_specs:
        declared_path = ROOT / declared_spec
        if not declared_path.is_file():
            errors.append(f"{model}: declared specification does not exist: {declared_spec}")
            continue

        model_target_from_spec = Path(model).as_posix()
        spec_content = declared_path.read_text(encoding="utf-8")
        if model_target_from_spec not in spec_content:
            errors.append(
                f"{model}: declared specification does not link back to this model: "
                f"{declared_spec}"
            )

if errors:
    print("Formal specification traceability verification failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    f"Formal specification traceability verified: "
    f"{len(model_files)} model(s), {len(referenced_models)} referenced model(s)."
)
