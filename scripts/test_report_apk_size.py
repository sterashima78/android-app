#!/usr/bin/env python3

import tempfile
import unittest
import zipfile
from pathlib import Path

from report_apk_size import inspect_apk, render_markdown


class ReportApkSizeTest(unittest.TestCase):
    def test_reports_apk_dex_and_native_sizes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            apk_path = Path(temp_dir) / "sample.apk"
            with zipfile.ZipFile(apk_path, "w") as archive:
                archive.writestr("classes.dex", b"a" * 100)
                archive.writestr("classes2.dex", b"b" * 50)
                archive.writestr("lib/arm64-v8a/liblarge.so", b"c" * 200)
                archive.writestr("lib/arm64-v8a/libsmall.so", b"d" * 25)
                archive.writestr("res/raw/data.bin", b"e" * 10)

            report = inspect_apk(apk_path)

            self.assertEqual(apk_path.stat().st_size, report.apk_bytes)
            self.assertEqual(150, report.dex_bytes)
            self.assertEqual(225, report.native_bytes)
            self.assertEqual(
                (
                    ("lib/arm64-v8a/liblarge.so", 200),
                    ("lib/arm64-v8a/libsmall.so", 25),
                ),
                report.native_libraries,
            )

            markdown = render_markdown(apk_path, report)
            self.assertIn("| DEX (uncompressed) | 150 | 0.00 |", markdown)
            self.assertIn("| Native `.so` (uncompressed) | 225 | 0.00 |", markdown)
            self.assertIn("`lib/arm64-v8a/liblarge.so`", markdown)


if __name__ == "__main__":
    unittest.main()
