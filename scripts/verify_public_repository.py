#!/usr/bin/env python3
"""Fail CI when tracked repository content contains high-confidence private artifacts."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ALLOWED_ENV_FILES = {".env.example", ".env.sample", ".env.template"}
SENSITIVE_SUFFIXES = {
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".key",
    ".db",
    ".sqlite",
    ".sqlite3",
    ".hprof",
    ".perfetto-trace",
    ".perfetto-trace-unredacted",
    ".heapprofile",
    ".heapdump",
    ".heapsnapshot",
}

SECRET_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("Google API key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("Google OAuth client secret", re.compile(r"\bGOCSPX-[0-9A-Za-z_-]{20,}\b")),
    ("GitHub token", re.compile(r"\bgh[pousr]_[0-9A-Za-z]{36,255}\b")),
    ("GitHub fine-grained token", re.compile(r"\bgithub_pat_[0-9A-Za-z_]{20,255}\b")),
    ("AWS access key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("Slack token", re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{10,}\b")),
    ("OpenAI API key", re.compile(r"\bsk-(?:proj-)?[0-9A-Za-z_-]{20,}\b")),
    ("Stripe live secret", re.compile(r"\bsk_live_[0-9A-Za-z]{16,}\b")),
)


def sensitive_path_reason(relative_path: str) -> str | None:
    path = Path(relative_path)
    name = path.name.lower()
    suffix = path.suffix.lower()

    if name == ".env" or (name.startswith(".env.") and name not in ALLOWED_ENV_FILES):
        return "environment file"
    if name == "google-services.json":
        return "Google services configuration"
    if name.startswith("client_secret") and suffix == ".json":
        return "OAuth client secret file"
    if name in {"credentials.json", "service-account.json"}:
        return "credential file"
    if any(name.endswith(sensitive_suffix) for sensitive_suffix in SENSITIVE_SUFFIXES):
        matched = next(
            sensitive_suffix
            for sensitive_suffix in SENSITIVE_SUFFIXES
            if name.endswith(sensitive_suffix)
        )
        return f"sensitive {matched} file"
    if suffix == ".zip" and ("backup" in name or "export" in name):
        return "backup/export archive"
    return None


def secret_reasons(text: str) -> list[str]:
    return [name for name, pattern in SECRET_PATTERNS if pattern.search(text)]


def tracked_paths() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [entry.decode("utf-8") for entry in result.stdout.split(b"\0") if entry]


def main() -> int:
    violations: list[tuple[str, str]] = []

    for relative_path in tracked_paths():
        reason = sensitive_path_reason(relative_path)
        if reason is not None:
            violations.append((relative_path, reason))
            continue

        path = ROOT / relative_path
        try:
            raw = path.read_bytes()
        except OSError as error:
            violations.append((relative_path, f"cannot inspect tracked file: {error}"))
            continue

        if b"\0" in raw:
            continue

        text = raw.decode("utf-8", errors="ignore")
        for secret_reason in secret_reasons(text):
            violations.append((relative_path, secret_reason))

    if violations:
        print("Public repository verification failed:", file=sys.stderr)
        for relative_path, reason in sorted(set(violations)):
            # Never print the matching value; CI logs must not echo a leaked credential.
            print(f"- {relative_path}: {reason}", file=sys.stderr)
        return 1

    print(f"Public repository verification passed for {len(tracked_paths())} tracked files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
