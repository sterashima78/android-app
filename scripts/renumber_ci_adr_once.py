from pathlib import Path

old = Path("docs/adr/0192-split-pr-checks-and-main-apk-build.md")
new = Path("docs/adr/0197-split-pr-checks-and-main-apk-build.md")
text = old.read_text()
assert text.startswith("# ADR-0192:")
assert not new.exists()
new.write_text(text.replace("# ADR-0192:", "# ADR-0197:", 1))
old.unlink()

p = Path("docs/adr/0164-p1-owner-boundary-and-main-quality-gate.md")
s = p.read_text()
old_ref = "[ADR-0192](0192-split-pr-checks-and-main-apk-build.md)"
new_ref = "[ADR-0197](0197-split-pr-checks-and-main-apk-build.md)"
assert old_ref in s
p.write_text(s.replace(old_ref, new_ref))

p = Path("docs/architecture/testing.md")
s = p.read_text()
assert "ADR-0192 で明示的に受け入れている" in s
assert "[ADR-0192](../adr/0192-split-pr-checks-and-main-apk-build.md)" in s
s = s.replace("ADR-0192 で明示的に受け入れている", "ADR-0197 で明示的に受け入れている")
s = s.replace(
    "[ADR-0192](../adr/0192-split-pr-checks-and-main-apk-build.md)",
    "[ADR-0197](../adr/0197-split-pr-checks-and-main-apk-build.md)",
)
p.write_text(s)

p = Path("docs/adr/README.md")
s = p.read_text()
old_entry = "[ADR-0192: PR quality checks と main APK build を分離する](0192-split-pr-checks-and-main-apk-build.md)"
new_entry = "[ADR-0197: PR quality checks と main APK build を分離する](0197-split-pr-checks-and-main-apk-build.md)"
assert old_entry in s
p.write_text(s.replace(old_entry, new_entry))
