#!/usr/bin/env python3
"""Run the standalone OpenWiki CLI without retaining generated agent instructions."""

from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys

REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
MANAGED_START = "<!-- OPENWIKI:START -->"
MANAGED_END = "<!-- OPENWIKI:END -->"
GENERATED_WORKFLOW = Path(".github/workflows/openwiki-update.yml")


def strip_generated_agent_block(text: str) -> str:
    """Remove only the upstream-managed block, preserving human instructions."""
    if MANAGED_START not in text and MANAGED_END not in text:
        return text
    if text.count(MANAGED_START) != 1 or text.count(MANAGED_END) != 1:
        raise ValueError("Malformed OpenWiki agent instruction markers")

    start = text.index(MANAGED_START)
    end = text.index(MANAGED_END)
    if end < start:
        raise ValueError("OpenWiki managed markers are out of order")

    before = text[:start].rstrip("\r\n")
    after = text[end + len(MANAGED_END) :].lstrip("\r\n")
    if before and after:
        return before + "\n\n" + after
    if before:
        return before + "\n"
    return after


def run(mode: str, root: Path = REPOSITORY_ROOT) -> int:
    """Invoke the native CLI and normalize its generated repository setup."""
    agents_path = root / "AGENTS.md"
    workflow_path = root / GENERATED_WORKFLOW
    workflow_existed = workflow_path.exists()
    command = ["openwiki", "code", "--" + mode, "--print"]
    if mode == "init":
        command.extend(["--language", "ja"])

    try:
        result = subprocess.run(command, cwd=root, check=False)
        return result.returncode
    finally:
        # The upstream CLI always refreshes a block advertising MCP tools.
        # The repository's hand-maintained AGENTS.md is authoritative instead.
        if agents_path.exists():
            content = agents_path.read_text(encoding="utf-8")
            cleaned = strip_generated_agent_block(content)
            if cleaned != content:
                agents_path.write_text(cleaned, encoding="utf-8")
        # Native --init creates a scheduled workflow even though this repo
        # intentionally does not use one. Never remove a pre-existing file.
        if mode == "init" and not workflow_existed:
            workflow_path.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Standalone repository Wiki maintenance")
    parser.add_argument("mode", choices=("init", "update"))
    args = parser.parse_args()
    try:
        return run(args.mode)
    except FileNotFoundError as error:
        print(f"Required CLI executable not found: {error}", file=sys.stderr)
        return 127
    except ValueError as error:
        print(f"Generated setup cleanup failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
