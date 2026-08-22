import unittest

from scripts.verify_module_map import (
    feature_modules_from_document,
    feature_modules_from_settings,
    verification_errors,
)


class VerifyModuleMapTest(unittest.TestCase):
    def test_parses_feature_layers_from_settings(self):
        settings = '\n'.join(
            [
                'include(":app")',
                'include(":feature:alpha:ui")',
                'include(":feature:alpha:domain")',
                'include(":feature:alpha:data")',
                'include(":feature:beta:ui")',
            ]
        )

        self.assertEqual(
            {
                "alpha": ("domain", "data", "ui"),
                "beta": ("ui",),
            },
            feature_modules_from_settings(settings),
        )

    def test_parses_only_marked_feature_table(self):
        module_map = """
| Ignore | Row |
| --- | --- |
<!-- feature-modules:start -->
| Feature | Layers |
| --- | --- |
| alpha | domain / data / ui |
| beta | ui |
<!-- feature-modules:end -->
"""

        self.assertEqual(
            {
                "alpha": ("domain", "data", "ui"),
                "beta": ("ui",),
            },
            feature_modules_from_document(module_map),
        )

    def test_reports_missing_stale_and_layer_mismatch(self):
        settings = '\n'.join(
            [
                'include(":feature:alpha:domain")',
                'include(":feature:alpha:ui")',
                'include(":feature:beta:ui")',
            ]
        )
        module_map = """
<!-- feature-modules:start -->
| Feature | Layers |
| --- | --- |
| alpha | domain / data / ui |
| gamma | ui |
<!-- feature-modules:end -->
"""

        self.assertEqual(
            [
                "missing feature row: beta -> ui",
                "stale feature row: gamma -> ui",
                "layer mismatch for alpha: settings=domain / ui, docs=domain / data / ui",
            ],
            verification_errors(settings, module_map),
        )

    def test_rejects_duplicate_feature_rows(self):
        module_map = """
<!-- feature-modules:start -->
| Feature | Layers |
| --- | --- |
| alpha | domain |
| alpha | ui |
<!-- feature-modules:end -->
"""

        with self.assertRaisesRegex(ValueError, "duplicate feature row: alpha"):
            feature_modules_from_document(module_map)


if __name__ == "__main__":
    unittest.main()
