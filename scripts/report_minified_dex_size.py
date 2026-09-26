#!/usr/bin/env python3
"""Report minified DEX size and warn on large regressions."""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class DexSizeReport:
    total_bytes: int
    files: tuple[tuple[str, int], ...]


@dataclass(frozen=True)
class DexSizeComparison:
    baseline_bytes: int
    current_bytes: int
    delta_bytes: int
    delta_percent: float | None

    def exceeds(self, warning_percent: float) -> bool:
        return self.delta_percent is not None and self.delta_percent >= warning_percent


def inspect_dex(root: Path) -> DexSizeReport:
    dex_files = sorted(path for path in root.rglob("*.dex") if path.is_file())
    if not dex_files:
        raise ValueError(f"No DEX files found under {root}")

    rows = tuple((path.relative_to(root).as_posix(), path.stat().st_size) for path in dex_files)
    return DexSizeReport(
        total_bytes=sum(size for _, size in rows),
        files=rows,
    )


def compare_sizes(baseline: DexSizeReport, current: DexSizeReport) -> DexSizeComparison:
    delta = current.total_bytes - baseline.total_bytes
    percent = None
    if baseline.total_bytes > 0:
        percent = delta * 100.0 / baseline.total_bytes
    return DexSizeComparison(
        baseline_bytes=baseline.total_bytes,
        current_bytes=current.total_bytes,
        delta_bytes=delta,
        delta_percent=percent,
    )


def format_percent(value: float | None) -> str:
    return "n/a" if value is None else f"{value:+.2f}%"


def render_markdown(
    current: DexSizeReport,
    baseline: DexSizeReport | None = None,
    warning_percent: float = 10.0,
) -> str:
    lines = [
        "### Minified DEX size",
        "",
        f"Current total: `{current.total_bytes:,}` bytes",
        "",
        "| File | Bytes |",
        "| --- | ---: |",
    ]
    lines.extend(f"| `{name}` | {size:,} |" for name, size in current.files)

    if baseline is not None:
        comparison = compare_sizes(baseline, current)
        lines.extend(
            [
                "",
                "#### Base comparison",
                "",
                "| Metric | Value |",
                "| --- | ---: |",
                f"| Base total | {comparison.baseline_bytes:,} bytes |",
                f"| Current total | {comparison.current_bytes:,} bytes |",
                f"| Delta | {comparison.delta_bytes:+,} bytes |",
                f"| Delta percent | {format_percent(comparison.delta_percent)} |",
                f"| Warning threshold | +{warning_percent:.2f}% |",
            ]
        )
        if comparison.exceeds(warning_percent):
            lines.extend(
                [
                    "",
                    f"> Warning: minified DEX size increased by {format_percent(comparison.delta_percent)} against the base branch.",
                ]
            )

    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("current_root", type=Path)
    parser.add_argument("--baseline-root", type=Path)
    parser.add_argument("--warning-percent", type=float, default=10.0)
    args = parser.parse_args()

    if args.warning_percent < 0:
        parser.error("--warning-percent must be non-negative")

    current = inspect_dex(args.current_root)
    baseline = inspect_dex(args.baseline_root) if args.baseline_root else None
    markdown = render_markdown(
        current=current,
        baseline=baseline,
        warning_percent=args.warning_percent,
    )
    print(markdown, end="")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as summary:
            summary.write(markdown)

    if baseline is not None:
        comparison = compare_sizes(baseline, current)
        if comparison.exceeds(args.warning_percent):
            message = (
                "Minified DEX size increased by "
                f"{format_percent(comparison.delta_percent)} "
                f"({comparison.delta_bytes:+,} bytes) against the base branch."
            )
            print(f"WARNING: {message}")
            if os.environ.get("GITHUB_ACTIONS") == "true":
                print(f"::warning::{message}")


if __name__ == "__main__":
    main()
