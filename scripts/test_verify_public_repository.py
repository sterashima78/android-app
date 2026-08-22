#!/usr/bin/env python3

import unittest

from verify_public_repository import secret_reasons, sensitive_path_reason


class PublicRepositoryVerifierTest(unittest.TestCase):
    def test_rejects_sensitive_files(self) -> None:
        self.assertIsNotNone(sensitive_path_reason(".env.local"))
        self.assertIsNotNone(sensitive_path_reason("release/app.keystore"))
        self.assertIsNotNone(sensitive_path_reason("fixtures/user-backup.zip"))
        self.assertIsNotNone(sensitive_path_reason("cache/example.db"))
        self.assertIsNone(sensitive_path_reason(".env.example"))
        self.assertIsNone(sensitive_path_reason("fixtures/library.json"))

    def test_detects_high_confidence_credentials(self) -> None:
        google_key = "AIza" + "A" * 35
        github_token = "ghp_" + "A" * 36
        private_key = "-----BEGIN " + "PRIVATE KEY-----"

        self.assertIn("Google API key", secret_reasons(google_key))
        self.assertIn("GitHub token", secret_reasons(github_token))
        self.assertIn("private key", secret_reasons(private_key))

    def test_does_not_flag_environment_variable_names(self) -> None:
        text = "ANDROID_SIGNING_STORE_PASSWORD=${ANDROID_SIGNING_STORE_PASSWORD}"
        self.assertEqual([], secret_reasons(text))


if __name__ == "__main__":
    unittest.main()
