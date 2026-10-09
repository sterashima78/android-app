"""Regression tests for the standalone OpenWiki CLI wrapper."""

from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import openwiki_cli


class OpenWikiCliTests(unittest.TestCase):
    def test_removes_only_managed_section(self):
        text = (
            "# Agent rules\n\nKeep manual rule.\n\n"
            "<!-- OPENWIKI:START -->\nUse MCP tools.\n<!-- OPENWIKI:END -->\n\n"
            "## Other rule\nKeep this too.\n"
        )
        self.assertEqual(
            openwiki_cli.strip_generated_agent_block(text),
            "# Agent rules\n\nKeep manual rule.\n\n## Other rule\nKeep this too.\n",
        )

    def test_no_markers_are_unchanged(self):
        text = "# Manual guidance\n\nUse the CLI.\n"
        self.assertEqual(openwiki_cli.strip_generated_agent_block(text), text)

    def test_malformed_markers_are_rejected(self):
        with self.assertRaises(ValueError):
            openwiki_cli.strip_generated_agent_block(
                "# Rules\n<!-- OPENWIKI:START -->\nmissing end\n"
            )

    def test_update_runs_native_cli_and_cleans_generated_block_even_on_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = root / "AGENTS.md"
            agents.write_text("# Human rule\n", encoding="utf-8")

            def fake_run(command, **kwargs):
                self.assertEqual(command, ["openwiki", "code", "--update"])
                self.assertEqual(kwargs["cwd"], root)
                agents.write_text(
                    "# Human rule\n\n<!-- OPENWIKI:START -->\nMCP\n"
                    "<!-- OPENWIKI:END -->\n",
                    encoding="utf-8",
                )
                return subprocess.CompletedProcess(command, 3)

            with patch.object(openwiki_cli.subprocess, "run", side_effect=fake_run):
                self.assertEqual(openwiki_cli.run("update", root), 3)
            self.assertEqual(agents.read_text(encoding="utf-8"), "# Human rule\n")

    def test_init_sets_japanese_and_removes_only_new_workflow(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents = root / "AGENTS.md"
            agents.write_text("# Human rule\n", encoding="utf-8")
            workflow = root / ".github/workflows/openwiki-update.yml"

            def fake_run(command, **kwargs):
                self.assertEqual(
                    command,
                    ["openwiki", "code", "--init", "--language", "ja"],
                )
                workflow.parent.mkdir(parents=True, exist_ok=True)
                workflow.write_text("generated", encoding="utf-8")
                return subprocess.CompletedProcess(command, 0)

            with patch.object(openwiki_cli.subprocess, "run", side_effect=fake_run):
                self.assertEqual(openwiki_cli.run("init", root), 0)
            self.assertFalse(workflow.exists())
            self.assertEqual(agents.read_text(encoding="utf-8"), "# Human rule\n")

    def test_init_preserves_existing_workflow(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            workflow = root / ".github/workflows/openwiki-update.yml"
            workflow.parent.mkdir(parents=True, exist_ok=True)
            workflow.write_text("existing", encoding="utf-8")
            with patch.object(
                openwiki_cli.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 0),
            ):
                self.assertEqual(openwiki_cli.run("init", root), 0)
            self.assertEqual(workflow.read_text(encoding="utf-8"), "existing")


if __name__ == "__main__":
    unittest.main()
