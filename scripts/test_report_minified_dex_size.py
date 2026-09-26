#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from report_minified_dex_size import (
    compare_sizes,
    inspect_dex,
    render_markdown,
)


class ReportMinifiedDexSizeTest(unittest.TestCase):
    def test_inspects_nested_dex_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "classes.dex").write_bytes(b"a" * 100)
            nested = root / "nested"
            nested.mkdir()
            (nested / "classes2.dex").write_bytes(b"b" * 50)
            (nested / "ignored.txt").write_bytes(b"c" * 500)

            report = inspect_dex(root)

            self.assertEqual(150, report.total_bytes)
            self.assertEqual(
                (
                    ("classes.dex", 100),
                    ("nested/classes2.dex", 50),
                ),
                report.files,
            )

    def test_rejects_directory_without_dex(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaisesRegex(ValueError, "No DEX files found"):
                inspect_dex(Path(temp_dir))

    def test_compares_against_base_and_warns_at_threshold(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            baseline_root = root / "base"
            current_root = root / "current"
            baseline_root.mkdir()
            current_root.mkdir()
            (baseline_root / "classes.dex").write_bytes(b"a" * 100)
            (current_root / "classes.dex").write_bytes(b"a" * 110)

            baseline = inspect_dex(baseline_root)
            current = inspect_dex(current_root)
            comparison = compare_sizes(baseline, current)

            self.assertEqual(10, comparison.delta_bytes)
            self.assertEqual(10.0, comparison.delta_percent)
            self.assertTrue(comparison.exceeds(10.0))
            self.assertFalse(comparison.exceeds(10.1))

            markdown = render_markdown(
                current=current,
                baseline=baseline,
                warning_percent=10.0,
            )
            self.assertIn("| Delta | +10 bytes |", markdown)
            self.assertIn("| Delta percent | +10.00% |", markdown)
            self.assertIn("Warning: minified DEX size increased", markdown)

    def test_decrease_does_not_warn(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            baseline_root = root / "base"
            current_root = root / "current"
            baseline_root.mkdir()
            current_root.mkdir()
            (baseline_root / "classes.dex").write_bytes(b"a" * 100)
            (current_root / "classes.dex").write_bytes(b"a" * 90)

            comparison = compare_sizes(
                inspect_dex(baseline_root),
                inspect_dex(current_root),
            )

            self.assertEqual(-10.0, comparison.delta_percent)
            self.assertFalse(comparison.exceeds(10.0))


if __name__ == "__main__":
    unittest.main()
