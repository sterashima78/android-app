#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "verify_formal_spec_links.py"
SPEC = importlib.util.spec_from_file_location("verify_formal_spec_links", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def write_fixture(
    root: Path,
    *,
    revision: int = 1,
    body: str = "- Requirement body.",
    model_revision: int | None = None,
    quint_symbol: str = "safety",
) -> None:
    model_revision = revision if model_revision is None else model_revision
    (root / "docs/spec").mkdir(parents=True, exist_ok=True)
    (root / "spec-models/quint").mkdir(parents=True, exist_ok=True)
    (root / "spec-models/alloy").mkdir(parents=True, exist_ok=True)

    (root / "docs/spec/example.md").write_text(
        f"""# Example

<!-- formal-requirement
id: EXAMPLE-001
revision: {revision}
models:
  - spec-models/quint/example.qnt
  - spec-models/alloy/example.als
-->
{body}
<!-- /formal-requirement -->

- [Quint](../../spec-models/quint/example.qnt)
- [Alloy](../../spec-models/alloy/example.als)
""",
        encoding="utf-8",
    )
    (root / "spec-models/quint/example.qnt").write_text(
        f"""// Natural-language specifications:
// - docs/spec/example.md
// Covers:
// - EXAMPLE-001@{model_revision} -> {quint_symbol}

module example {{
  val safety = true
}}
""",
        encoding="utf-8",
    )
    (root / "spec-models/alloy/example.als").write_text(
        f"""// Natural-language specifications:
// - docs/spec/example.md
// Covers:
// - EXAMPLE-001@{model_revision} -> Good

module example

assert Good {{}}
check Good expect 0
""",
        encoding="utf-8",
    )


def commit_fixture(root: Path) -> str:
    subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
    subprocess.run(
        ["git", "config", "user.email", "formal-test@example.invalid"],
        cwd=root,
        check=True,
    )
    subprocess.run(
        ["git", "config", "user.name", "Formal Test"],
        cwd=root,
        check=True,
    )
    subprocess.run(["git", "add", "."], cwd=root, check=True)
    subprocess.run(
        ["git", "commit", "-m", "base"],
        cwd=root,
        check=True,
        capture_output=True,
    )
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        text=True,
    ).strip()


class FormalSpecTraceabilityTest(unittest.TestCase):
    def test_valid_requirement_and_model_coverage_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)

            self.assertEqual([], MODULE.verify(root))

    def test_requirement_body_change_requires_revision_bump(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)
            base = commit_fixture(root)

            write_fixture(root, body="- Changed requirement body.")

            errors = MODULE.verify(root, base)
            self.assertTrue(
                any("without increasing revision" in error for error in errors),
                errors,
            )

    def test_revision_bump_requires_model_acknowledgement(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)
            base = commit_fixture(root)

            write_fixture(
                root,
                revision=2,
                body="- Changed requirement body.",
                model_revision=1,
            )

            errors = MODULE.verify(root, base)
            self.assertTrue(
                any("stale requirement revision" in error for error in errors),
                errors,
            )
            self.assertTrue(
                any("does not acknowledge current requirement" in error for error in errors),
                errors,
            )

    def test_revision_bump_with_model_acknowledgement_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)
            base = commit_fixture(root)

            write_fixture(
                root,
                revision=2,
                body="- Changed requirement body.",
                model_revision=2,
            )

            self.assertEqual([], MODULE.verify(root, base))

    def test_coverage_target_must_exist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root, quint_symbol="missingSymbol")

            errors = MODULE.verify(root)
            self.assertTrue(
                any("coverage target does not exist" in error for error in errors),
                errors,
            )


if __name__ == "__main__":
    unittest.main()
