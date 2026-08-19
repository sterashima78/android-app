#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADR_DIR = ROOT / "docs" / "adr"

RENAMES = {
    "0047-x-webview-css-customization.md": "0107-x-webview-css-customization.md",
    "0065-library-organization-and-ai-suggestions.md": "0108-library-organization-and-ai-suggestions.md",
    "0066-generated-knowledge-wiki.md": "0109-generated-knowledge-wiki.md",
    "0067-smb-library-deduplication.md": "0110-smb-library-deduplication.md",
    "0071-auto-apply-validated-series-aware-library-organization.md": "0111-auto-apply-validated-series-aware-library-organization.md",
    "0075-gemma-artifact-revisions-and-speculative-decoding.md": "0112-gemma-artifact-revisions-and-speculative-decoding.md",
    "0075-knowledge-page-lifecycle-management.md": "0113-knowledge-page-lifecycle-management.md",
    "0086-solitaire-board-first-visual-feedback.md": "0114-solitaire-board-first-visual-feedback.md",
}

# Each replacement is intentionally scoped to the ADR where the old number refers
# to the collided decision. References to the decision that retains the old number
# are deliberately left untouched.
CONTENT_REPLACEMENTS = {
    "0107-x-webview-css-customization.md": {"ADR-0047": "ADR-0107"},
    "0108-library-organization-and-ai-suggestions.md": {"ADR-0065": "ADR-0108"},
    "0109-generated-knowledge-wiki.md": {"ADR-0066": "ADR-0109"},
    "0110-smb-library-deduplication.md": {"ADR-0067": "ADR-0110"},
    "0111-auto-apply-validated-series-aware-library-organization.md": {
        "ADR-0071": "ADR-0111",
        "ADR-0065": "ADR-0108",
    },
    "0112-gemma-artifact-revisions-and-speculative-decoding.md": {"ADR-0075": "ADR-0112"},
    "0113-knowledge-page-lifecycle-management.md": {
        "ADR-0075": "ADR-0113",
        "ADR-0066": "ADR-0109",
    },
    "0114-solitaire-board-first-visual-feedback.md": {"ADR-0086": "ADR-0114"},
    "0011-mail-html-rendering.md": {"ADR-0047": "ADR-0107"},
    "0012-navigation-drawer-explicit-open.md": {"ADR-0047": "ADR-0107"},
    "0022-x-webview-css-sets.md": {"ADR-0047": "ADR-0107"},
    "0045-x-webview-external-link-routing.md": {"ADR-0047": "ADR-0107"},
    "0050-x-css-settings-bottom-sheet.md": {"ADR-0047": "ADR-0107"},
    "0102-x-css-settings-layer-ownership.md": {"ADR-0047": "ADR-0107"},
    "0066-background-library-ai-organization-review-queue.md": {"ADR-0065": "ADR-0108"},
    "0072-remove-library-organization-review-ui.md": {"ADR-0071": "ADR-0111"},
    "0074-library-metadata-management-and-series-reorganization.md": {
        "ADR-0065": "ADR-0108",
        "ADR-0071": "ADR-0111",
    },
    "0075-background-knowledge-wiki-build-queue.md": {"ADR-0066": "ADR-0109"},
    "0077-on-device-ai-benchmark.md": {"ADR-0075": "ADR-0112"},
}


def replace_text(path: Path, replacements: dict[str, str]) -> None:
    text = path.read_text(encoding="utf-8")
    updated = text
    for old, new in replacements.items():
        updated = updated.replace(old, new)
    if updated != text:
        path.write_text(updated, encoding="utf-8")


def main() -> None:
    for old_name, new_name in RENAMES.items():
        old_path = ADR_DIR / old_name
        new_path = ADR_DIR / new_name
        if old_path.exists():
            if new_path.exists():
                raise RuntimeError(f"rename destination already exists: {new_path}")
            old_path.rename(new_path)
        elif not new_path.exists():
            raise RuntimeError(f"ADR to rename is missing: {old_path}")

    for name, replacements in CONTENT_REPLACEMENTS.items():
        path = ADR_DIR / name
        if not path.exists():
            raise RuntimeError(f"ADR for reference update is missing: {path}")
        replace_text(path, replacements)

    # Keep explicit filename/path references valid without changing numeric prose
    # whose meaning may refer to the ADR that retained the old number.
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or path == Path(__file__):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = text
        for old_name, new_name in RENAMES.items():
            updated = updated.replace(old_name, new_name)
            updated = updated.replace(f"docs/adr/{old_name}", f"docs/adr/{new_name}")
        if updated != text:
            path.write_text(updated, encoding="utf-8")


if __name__ == "__main__":
    main()
