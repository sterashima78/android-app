from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.verify_adr_integrity import verify_adr_integrity


class VerifyAdrIntegrityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        (self.root / "docs" / "adr").mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write_adr(self, filename: str, content: str) -> None:
        (self.root / "docs" / "adr" / filename).write_text(content, encoding="utf-8")

    def test_accepts_unique_consistent_adrs_and_existing_references(self) -> None:
        self.write_adr("0001-first.md", "# ADR-0001: First\n\nSee ADR-0002.\n")
        self.write_adr("0002-second.md", "# ADR-0002: Second\n\nSee [first](0001-first.md).\n")

        self.assertEqual([], verify_adr_integrity(self.root))

    def test_rejects_duplicate_numbers(self) -> None:
        self.write_adr("0001-first.md", "# ADR-0001: First\n")
        self.write_adr("0001-second.md", "# ADR-0001: Second\n")

        violations = verify_adr_integrity(self.root)

        self.assertTrue(any("ADR-0001 is duplicated" in item for item in violations))

    def test_rejects_heading_number_mismatch(self) -> None:
        self.write_adr("0001-first.md", "# ADR-0002: First\n")

        violations = verify_adr_integrity(self.root)

        self.assertTrue(any("does not match filename ADR-0001" in item for item in violations))

    def test_rejects_missing_numeric_reference(self) -> None:
        self.write_adr("0001-first.md", "# ADR-0001: First\n\nSee ADR-9999.\n")

        violations = verify_adr_integrity(self.root)

        self.assertIn("docs/adr/0001-first.md: ADR-9999 does not exist", violations)

    def test_rejects_missing_explicit_link_target(self) -> None:
        self.write_adr(
            "0001-first.md",
            "# ADR-0001: First\n\nSee [missing](9999-missing.md).\n",
        )

        violations = verify_adr_integrity(self.root)

        self.assertIn(
            "docs/adr/0001-first.md: ADR link target does not exist: 9999-missing.md",
            violations,
        )

    def test_rejects_malformed_filename(self) -> None:
        self.write_adr("1-not-padded.md", "# ADR-0001: First\n")

        violations = verify_adr_integrity(self.root)

        self.assertTrue(any("filename must match" in item for item in violations))


if __name__ == "__main__":
    unittest.main()
