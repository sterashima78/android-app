#!/usr/bin/env python3
"""Report high-level APK size components as Markdown."""

from __future__ import annotations

import argparse
import os
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path

DEX_PATTERN = re.compile(r"^classes(?:\d+)?\.dex$")


@dataclass(frozen=True)
class ApkSizeReport:
    apk_bytes: int
    dex_bytes: int
    native_bytes: int
    native_libraries: tuple[tuple[str, int], ...]


def inspect_apk(apk_path: Path) -> ApkSizeReport:
    with zipfile.ZipFile(apk_path) as archive:
        entries = archive.infolist()
        dex_bytes = sum(
            entry.file_size for entry in entries if DEX_PATTERN.fullmatch(entry.filename)
        )
        native_libraries = tuple(
            sorted(
                (
                    (entry.filename, entry.file_size)
                    for entry in entries
                    if entry.filename.startswith("lib/")
                    and entry.filename.endswith(".so")
                ),
                key=lambda item: (-item[1], item[0]),
            )
        )

    return ApkSizeReport(
        apk_bytes=apk_path.stat().st_size,
        dex_bytes=dex_bytes,
        native_bytes=sum(size for _, size in native_libraries),
        native_libraries=native_libraries,
    )


def format_mib(size_bytes: int) -> str:
    return f"{size_bytes / (1024 * 1024):.2f}"


def markdown_escape(value: str) -> str:
    return value.replace("|", r"\|")


def render_markdown(apk_path: Path, report: ApkSizeReport) -> str:
    lines = [
        "## Release APK size",
        "",
        f"`{markdown_escape(apk_path.name)}`",
        "",
        "| Component | Bytes | MiB |",
        "| --- | ---: | ---: |",
        f"| APK file | {report.apk_bytes:,} | {format_mib(report.apk_bytes)} |",
        f"| DEX (uncompressed) | {report.dex_bytes:,} | {format_mib(report.dex_bytes)} |",
        f"| Native `.so` (uncompressed) | {report.native_bytes:,} | {format_mib(report.native_bytes)} |",
    ]

    if report.native_libraries:
        lines.extend(
            [
                "",
                "### Native libraries",
                "",
                "| Library | Bytes | MiB |",
                "| --- | ---: | ---: |",
            ]
        )
        lines.extend(
            f"| `{markdown_escape(name)}` | {size:,} | {format_mib(size)} |"
            for name, size in report.native_libraries
        )

    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()

    report = inspect_apk(args.apk)
    markdown = render_markdown(args.apk, report)
    print(markdown, end="")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as summary:
            summary.write(markdown)


if __name__ == "__main__":
    main()
