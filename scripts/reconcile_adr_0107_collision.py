#!/usr/bin/env python3
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADR = ROOT / "docs" / "adr"


def read_main(path: str) -> str:
    return subprocess.check_output(
        ["git", "show", f"origin/main:{path}"],
        cwd=ROOT,
        text=True,
        encoding="utf-8",
    )


def replace(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated = text.replace(old, new)
    if updated != text:
        path.write_text(updated, encoding="utf-8")


def main() -> None:
    old_x = ADR / "0107-x-webview-css-customization.md"
    new_x = ADR / "0115-x-webview-css-customization.md"
    if old_x.exists():
        if new_x.exists():
            raise RuntimeError(f"destination already exists: {new_x}")
        old_x.rename(new_x)
    elif not new_x.exists():
        raise RuntimeError(f"X WebView ADR is missing: {old_x}")
    replace(new_x, "ADR-0107", "ADR-0115")

    for name in [
        "0011-mail-html-rendering.md",
        "0012-navigation-drawer-explicit-open.md",
        "0022-x-webview-css-sets.md",
        "0045-x-webview-external-link-routing.md",
        "0050-x-css-settings-bottom-sheet.md",
    ]:
        replace(ADR / name, "ADR-0107", "ADR-0115")

    # Rebase the semantically overlapping ADR-0102 document onto latest main.
    # Keep the newly-added ADR-0107 amendment while pointing its original X
    # WebView/CSS antecedent at the newly renumbered ADR-0115.
    latest_0102 = read_main("docs/adr/0102-x-css-settings-layer-ownership.md")
    latest_0102 = latest_0102.replace("Amends: ADR-0047", "Amends: ADR-0115")
    latest_0102 = latest_0102.replace("ADR-0047 では X feature", "ADR-0115 では X feature")
    (ADR / "0102-x-css-settings-layer-ownership.md").write_text(latest_0102, encoding="utf-8")

    # Include the latest-main ADR that claimed 0107 while this branch was open so
    # branch-local integrity validation sees the same ADR namespace as the merge.
    latest_0107 = read_main("docs/adr/0107-x-css-explicit-repository-injection.md")
    (ADR / "0107-x-css-explicit-repository-injection.md").write_text(latest_0107, encoding="utf-8")

    policy = ADR / "0055-adr-numbering-policy.md"
    replace(policy, "X WebView / CSS: ADR-0047 → ADR-0107。", "X WebView / CSS: ADR-0047 → ADR-0115。")
    replace(
        policy,
        "既存番号の意味を可能な限り維持するため、参照が多い側または後続 ADR の基準として使われている側を旧番号に残し、衝突した別の設計判断だけを現在の最大番号 0106 より後ろへ再採番する。",
        "既存番号の意味を可能な限り維持するため、参照が多い側または後続 ADR の基準として使われている側を旧番号に残す。作業中に最新 `main` へ ADR-0107 が追加されたため、その番号も維持し、衝突側には ADR-0108〜0115 を割り当てる。",
    )


if __name__ == "__main__":
    main()
