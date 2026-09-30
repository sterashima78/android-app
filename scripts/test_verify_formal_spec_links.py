#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "verify_formal_spec_links.py"
SPEC = importlib.util.spec_from_file_location("verify_formal_spec_links", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write_fixture(
    root: Path,
    *,
    body: str = "- Requirement body.",
    acknowledged_hash: str | None = None,
    quint_symbol: str = "safety",
    malformed_coverage: bool = False,
) -> None:
    if acknowledged_hash is None:
        acknowledged_hash = MODULE.requirement_hash(body)

    (root / "docs/spec").mkdir(parents=True, exist_ok=True)
    (root / "spec-models/quint").mkdir(parents=True, exist_ok=True)
    (root / "spec-models/alloy").mkdir(parents=True, exist_ok=True)

    (root / "docs/spec/example.md").write_text(
        f"""# Example

<!-- formal-requirement
id: EXAMPLE-001
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

    quint_hash = acknowledged_hash
    if malformed_coverage:
        quint_hash = "not-a-sha256"

    (root / "spec-models/quint/example.qnt").write_text(
        f"""// Natural-language specifications:
// - docs/spec/example.md
// Covers:
// - EXAMPLE-001@sha256:{quint_hash} -> {quint_symbol}

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
// - EXAMPLE-001@sha256:{acknowledged_hash} -> Good

module example

assert Good {{}}
check Good expect 0
""",
        encoding="utf-8",
    )


class FormalSpecTraceabilityTest(unittest.TestCase):
    def test_valid_requirement_and_model_coverage_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)

            self.assertEqual([], MODULE.verify(root))

    def test_requirement_body_change_invalidates_model_acknowledgement(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original_body = "- Requirement body."
            original_hash = MODULE.requirement_hash(original_body)
            write_fixture(
                root,
                body="- Changed requirement body.",
                acknowledged_hash=original_hash,
            )

            errors = MODULE.verify(root)
            self.assertTrue(
                any("stale requirement hash" in error for error in errors),
                errors,
            )
            self.assertTrue(
                any("does not acknowledge current requirement" in error for error in errors),
                errors,
            )

    def test_changed_body_with_current_hash_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            changed_body = "- Changed requirement body."
            write_fixture(root, body=changed_body)

            self.assertEqual([], MODULE.verify(root))

    def test_coverage_target_must_exist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root, quint_symbol="missingSymbol")

            errors = MODULE.verify(root)
            self.assertTrue(
                any("coverage target does not exist" in error for error in errors),
                errors,
            )

    def test_malformed_requirement_block_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root)
            spec_path = root / "docs/spec/example.md"
            spec_path.write_text(
                spec_path.read_text(encoding="utf-8").replace(
                    "<!-- /formal-requirement -->",
                    "<!-- malformed-end -->",
                ),
                encoding="utf-8",
            )

            errors = MODULE.verify(root)
            self.assertTrue(
                any("malformed formal requirement block" in error for error in errors),
                errors,
            )

    def test_malformed_coverage_hash_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_fixture(root, malformed_coverage=True)

            errors = MODULE.verify(root)
            self.assertTrue(
                any("malformed formal requirement coverage" in error for error in errors),
                errors,
            )

    def test_hash_normalization_ignores_newline_style_and_trailing_space(self) -> None:
        unix = "- First line\n- Second line"
        windows_with_trailing_space = "\r\n- First line   \r\n- Second line\t\r\n"

        self.assertEqual(
            MODULE.requirement_hash(unix),
            MODULE.requirement_hash(windows_with_trailing_space),
        )


if __name__ == "__main__":
    unittest.main()
