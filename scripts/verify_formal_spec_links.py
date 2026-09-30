#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import re
import sys

MODEL_SUFFIXES = {".qnt", ".als"}
REQUIREMENT_BLOCK_PATTERN = re.compile(
    r"<!-- formal-requirement\s*\n(?P<meta>.*?)\n-->\s*\n(?P<body>.*?)\n<!-- /formal-requirement -->",
    flags=re.DOTALL,
)
REQUIREMENT_ID_PATTERN = re.compile(r"^[A-Z][A-Z0-9-]*$")
COVERAGE_PATTERN = re.compile(
    r"^// - (?P<id>[A-Z][A-Z0-9-]*)@sha256:(?P<hash>[0-9a-f]{64})"
    r" -> (?P<symbol>[A-Za-z_][A-Za-z0-9_]*)$",
    flags=re.MULTILINE,
)
COVERAGE_CANDIDATE_PATTERN = re.compile(
    r"^// - [A-Z][A-Z0-9-]*@.* -> [A-Za-z_][A-Za-z0-9_]*$",
    flags=re.MULTILINE,
)
DECLARED_SPEC_PATTERN = re.compile(
    r"^// - (docs/spec/[A-Za-z0-9._/-]+[.]md)$",
    flags=re.MULTILINE,
)
MODEL_LINK_PATTERN = re.compile(
    r"\]\(([^)#]*spec-models/(?:quint|alloy)/[^)#]+)\)"
)


@dataclass(frozen=True)
class Requirement:
    requirement_id: str
    models: tuple[str, ...]
    body: str
    body_hash: str
    spec_path: str


def normalize_requirement_body(body: str) -> str:
    normalized_newlines = body.replace("\r\n", "\n").replace("\r", "\n")
    lines = [line.rstrip() for line in normalized_newlines.split("\n")]
    while lines and lines[0] == "":
        lines.pop(0)
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


def requirement_hash(body: str) -> str:
    normalized = normalize_requirement_body(body)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def parse_requirements(
    content: str,
    spec_path: str,
    errors: list[str],
) -> list[Requirement]:
    requirements: list[Requirement] = []
    matches = list(REQUIREMENT_BLOCK_PATTERN.finditer(content))
    opening_markers = content.count("<!-- formal-requirement")
    closing_markers = content.count("<!-- /formal-requirement -->")

    if opening_markers != closing_markers or len(matches) != opening_markers:
        errors.append(f"{spec_path}: malformed formal requirement block")

    for match in matches:
        meta = match.group("meta")
        body = normalize_requirement_body(match.group("body"))
        requirement_id: str | None = None
        models: list[str] = []
        reading_models = False

        for raw_line in meta.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if line.startswith("id:"):
                requirement_id = line.removeprefix("id:").strip()
                reading_models = False
            elif line == "models:":
                reading_models = True
            elif reading_models and line.startswith("- "):
                models.append(line.removeprefix("- ").strip())
            else:
                errors.append(
                    f"{spec_path}: unrecognized formal requirement metadata: {raw_line}"
                )

        if not requirement_id or not REQUIREMENT_ID_PATTERN.fullmatch(requirement_id):
            errors.append(f"{spec_path}: invalid or missing formal requirement id")
            continue
        if not models:
            errors.append(f"{spec_path}: {requirement_id}: models must not be empty")
            continue
        if len(models) != len(set(models)):
            errors.append(f"{spec_path}: {requirement_id}: duplicate model path")
            continue
        if not body:
            errors.append(f"{spec_path}: {requirement_id}: requirement body is empty")
            continue

        requirements.append(
            Requirement(
                requirement_id=requirement_id,
                models=tuple(models),
                body=body,
                body_hash=requirement_hash(body),
                spec_path=spec_path,
            )
        )

    return requirements


def symbol_exists(model_content: str, symbol: str) -> bool:
    return (
        re.search(
            rf"^\s*(?:val|action|def|assert|pred|fun|fact)\s+{re.escape(symbol)}\b",
            model_content,
            flags=re.MULTILINE,
        )
        is not None
    )


def verify(root: Path) -> list[str]:
    spec_dir = root / "docs" / "spec"
    model_root = root / "spec-models"
    errors: list[str] = []

    model_files = {
        path.relative_to(root).as_posix()
        for path in model_root.rglob("*")
        if path.is_file() and path.suffix in MODEL_SUFFIXES
    }

    spec_linked_models: dict[str, set[str]] = {}
    requirements: dict[str, Requirement] = {}
    referenced_models: set[str] = set()

    for spec in sorted(spec_dir.glob("*.md")):
        spec_path = spec.relative_to(root).as_posix()
        content = spec.read_text(encoding="utf-8")
        linked_models: set[str] = set()

        for raw_target in MODEL_LINK_PATTERN.findall(content):
            target = (spec.parent / raw_target).resolve()
            try:
                relative = target.relative_to(root).as_posix()
            except ValueError:
                errors.append(f"{spec_path}: model link escapes repository: {raw_target}")
                continue

            if relative not in model_files:
                errors.append(f"{spec_path}: missing formal model: {raw_target}")
                continue

            linked_models.add(relative)
            referenced_models.add(relative)

        spec_linked_models[spec_path] = linked_models

        for requirement in parse_requirements(content, spec_path, errors):
            if requirement.requirement_id in requirements:
                previous = requirements[requirement.requirement_id]
                errors.append(
                    f"{spec_path}: duplicate formal requirement id "
                    f"{requirement.requirement_id}; already defined in {previous.spec_path}"
                )
                continue
            requirements[requirement.requirement_id] = requirement

    model_declared_specs: dict[str, set[str]] = {}
    model_coverages: dict[str, list[tuple[str, str, str]]] = {}

    for model in sorted(model_files):
        if model not in referenced_models:
            errors.append(f"{model}: no docs/spec/*.md document links to this model")

        model_content = (root / model).read_text(encoding="utf-8")
        declared_specs = set(DECLARED_SPEC_PATTERN.findall(model_content))
        model_declared_specs[model] = declared_specs

        if not declared_specs:
            errors.append(f"{model}: missing natural-language specification declaration")

        for declared_spec in sorted(declared_specs):
            declared_path = root / declared_spec
            if not declared_path.is_file():
                errors.append(
                    f"{model}: declared specification does not exist: {declared_spec}"
                )
                continue
            if model not in spec_linked_models.get(declared_spec, set()):
                errors.append(
                    f"{model}: declared specification does not link back to this model: "
                    f"{declared_spec}"
                )

        valid_coverage_lines = {match.group(0) for match in COVERAGE_PATTERN.finditer(model_content)}
        for candidate in COVERAGE_CANDIDATE_PATTERN.findall(model_content):
            if candidate not in valid_coverage_lines:
                errors.append(f"{model}: malformed formal requirement coverage: {candidate}")

        coverages = [
            (match.group("id"), match.group("hash"), match.group("symbol"))
            for match in COVERAGE_PATTERN.finditer(model_content)
        ]
        model_coverages[model] = coverages

        if not coverages:
            errors.append(f"{model}: missing formal requirement coverage declaration")

        for requirement_id, acknowledged_hash, symbol in coverages:
            requirement = requirements.get(requirement_id)
            if requirement is None:
                errors.append(
                    f"{model}: coverage references unknown requirement: "
                    f"{requirement_id}@sha256:{acknowledged_hash}"
                )
                continue
            if model not in requirement.models:
                errors.append(
                    f"{model}: coverage for {requirement_id} is not listed by "
                    f"{requirement.spec_path}"
                )
            if acknowledged_hash != requirement.body_hash:
                errors.append(
                    f"{model}: stale requirement hash for {requirement_id}; "
                    f"expected sha256:{requirement.body_hash}, "
                    f"found sha256:{acknowledged_hash}"
                )
            if requirement.spec_path not in declared_specs:
                errors.append(
                    f"{model}: {requirement_id} is defined in {requirement.spec_path}, "
                    "but that specification is not declared by the model"
                )
            if not symbol_exists(model_content, symbol):
                errors.append(
                    f"{model}: coverage target does not exist: "
                    f"{requirement_id}@sha256:{acknowledged_hash} -> {symbol}"
                )

    for requirement in requirements.values():
        for model in requirement.models:
            if model not in model_files:
                errors.append(
                    f"{requirement.spec_path}: {requirement.requirement_id}: "
                    f"missing formal model: {model}"
                )
                continue
            if model not in spec_linked_models.get(requirement.spec_path, set()):
                errors.append(
                    f"{requirement.spec_path}: {requirement.requirement_id}: "
                    f"model is not directly linked from the specification: {model}"
                )
            if requirement.spec_path not in model_declared_specs.get(model, set()):
                errors.append(
                    f"{requirement.spec_path}: {requirement.requirement_id}: "
                    f"model does not declare this specification: {model}"
                )

            matching = [
                coverage
                for coverage in model_coverages.get(model, [])
                if coverage[0] == requirement.requirement_id
                and coverage[1] == requirement.body_hash
            ]
            if not matching:
                errors.append(
                    f"{model}: does not acknowledge current requirement "
                    f"{requirement.requirement_id}@sha256:{requirement.body_hash}"
                )

    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors = verify(root)

    if errors:
        print("Formal specification traceability verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    requirement_count = sum(
        len(
            parse_requirements(
                spec.read_text(encoding="utf-8"),
                spec.relative_to(root).as_posix(),
                [],
            )
        )
        for spec in (root / "docs" / "spec").glob("*.md")
    )
    model_count = sum(
        1
        for path in (root / "spec-models").rglob("*")
        if path.is_file() and path.suffix in MODEL_SUFFIXES
    )
    print(
        "Formal specification traceability verified: "
        f"{requirement_count} requirement(s), {model_count} model(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
