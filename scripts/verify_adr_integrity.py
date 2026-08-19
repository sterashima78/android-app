#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ADR_INDEX_FILENAME = "README.md"
ADR_FILENAME = re.compile(r"^(?P<number>\d{4})-(?P<slug>[a-z0-9][a-z0-9-]*)\.md$")
ADR_HEADING = re.compile(r"^# ADR-(?P<number>\d{4}):\s+\S.*$")
ADR_REFERENCE = re.compile(r"\bADR-(?P<number>\d{4})\b")
ADR_PATH_REFERENCE = re.compile(r"docs/adr/(?P<filename>\d{4}-[a-z0-9][a-z0-9-]*\.md)")
MARKDOWN_LOCAL_ADR_LINK = re.compile(
    r"\]\((?:\./)?(?P<filename>\d{4}-[a-z0-9][a-z0-9-]*\.md)(?:#[^)]+)?\)"
)


def verify_adr_integrity(root: Path) -> list[str]:
    adr_dir = root / "docs" / "adr"
    if not adr_dir.is_dir():
        return [f"ADR directory does not exist: {adr_dir}"]

    violations: list[str] = []
    parsed: list[tuple[Path, str, str]] = []
    paths_by_number: dict[str, list[Path]] = defaultdict(list)

    for path in sorted(adr_dir.glob("*.md")):
        if path.name == ADR_INDEX_FILENAME:
            continue

        match = ADR_FILENAME.fullmatch(path.name)
        if match is None:
            violations.append(
                f"{path.relative_to(root)}: filename must match NNNN-lowercase-kebab-case.md"
            )
            continue

        number = match.group("number")
        text = path.read_text(encoding="utf-8")
        first_line = text.splitlines()[0] if text.splitlines() else ""
        heading = ADR_HEADING.fullmatch(first_line)
        if heading is None:
            violations.append(
                f"{path.relative_to(root)}: first line must be '# ADR-{number}: <title>'"
            )
        elif heading.group("number") != number:
            violations.append(
                f"{path.relative_to(root)}: heading ADR-{heading.group('number')} "
                f"does not match filename ADR-{number}"
            )

        paths_by_number[number].append(path)
        parsed.append((path, number, text))

    for number, paths in sorted(paths_by_number.items()):
        if len(paths) > 1:
            names = ", ".join(str(path.relative_to(root)) for path in paths)
            violations.append(f"ADR-{number} is duplicated: {names}")

    known_numbers = set(paths_by_number)
    known_filenames = {path.name for path, _, _ in parsed}

    for path, _, text in parsed:
        rel = path.relative_to(root)
        for match in ADR_REFERENCE.finditer(text):
            referenced = match.group("number")
            if referenced not in known_numbers:
                violations.append(f"{rel}: ADR-{referenced} does not exist")

        explicit_paths = {
            match.group("filename") for match in ADR_PATH_REFERENCE.finditer(text)
        }
        explicit_paths.update(
            match.group("filename") for match in MARKDOWN_LOCAL_ADR_LINK.finditer(text)
        )
        for filename in sorted(explicit_paths):
            if filename not in known_filenames:
                violations.append(f"{rel}: ADR link target does not exist: {filename}")

    return sorted(set(violations))


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    violations = verify_adr_integrity(root)
    if violations:
        print("ADR integrity verification failed:", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        return 1

    count = len(
        [
            path
            for path in (root / "docs" / "adr").glob("*.md")
            if path.name != ADR_INDEX_FILENAME
        ]
    )
    print(f"ADR integrity verification passed ({count} ADR files).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
