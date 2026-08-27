from pathlib import Path


testing = Path("docs/architecture/testing.md")
text = testing.read_text()
start = text.index("## CI baseline\n")
end = text.index("## Choosing tests for a change\n", start)
replacement = """## CI baseline

Pull Request の品質 gate は `.github/workflows/check.yml` が所有し、次の4 checkを独立して並列実行する。

- `Public repository`: public repository verifier の unit test と tracked content scan
- `Architecture`: module map verifier、ADR integrity verifier、Gradle `verifyArchitecture`
- `Test`: `./gradlew --no-daemon test`
- `Lint`: `./gradlew --no-daemon :app:lintRelease`

Android の3検証は matrix で `fail-fast: false` とし、1つが失敗しても他の結果を取得する。従来の `quality` 集約 job は置かず、GitHub repository ruleset から4 checkを直接 required status checks とする。ADR integrity は path filter 付きの独立 workflow にせず、常時実行される `Architecture` check に含める。

```bash
python3 scripts/test_verify_public_repository.py
python3 scripts/verify_public_repository.py
python3 -m unittest scripts.test_verify_module_map scripts.test_verify_adr_integrity
python3 scripts/verify_module_map.py
python3 scripts/verify_adr_integrity.py
./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture
./gradlew --no-daemon test
./gradlew --no-daemon :app:lintRelease
```

`main` push と手動実行の signed release APK は `.github/workflows/build.yml` が所有する。PR gate を通過した commit を ruleset により `main` へ取り込む前提とし、main build では Architecture / Test / Lint を重複実行しない。repository scan は release keystore を runner へ復元する前に再実行し、その後 APK build / signature verification / artifact upload / `apk/main` commit status publication を行う。

ruleset 移行中は workflow 分離が先行し、direct push の技術的な防止が一時的に弱くなることを ADR-0192 で明示的に受け入れている。最終状態では `main` への Pull Request、4 required checks、force push / deletion / bypass の禁止を repository ruleset で enforcement する。

CI workflow が変更された場合は、この文書のコマンドを正本とせず workflow を優先して本記述を更新する。

Sources: [ADR-0038](../adr/0038-android-test-layers-and-e2e.md), [ADR-0093](../adr/0093-main-apk-build-run-status.md), [ADR-0136](../adr/0136-public-repository-content-verification.md), [ADR-0192](../adr/0192-split-pr-checks-and-main-apk-build.md).

"""
testing.write_text(text[:start] + replacement + text[end:])

adr = Path("docs/adr/0164-p1-owner-boundary-and-main-quality-gate.md")
text = adr.read_text()
marker = "- Date: 2026-08-24\n"
note = "\n> Update (2026-08-27): Decision 3 と、それに対応する main quality gate の consequence / verification は [ADR-0192](0192-split-pr-checks-and-main-apk-build.md) で supersede された。Decision 1 と Decision 2 は引き続き有効。\n"
assert marker in text
if "ADR-0192" not in text:
    text = text.replace(marker, marker + note, 1)
adr.write_text(text)

readme = Path("docs/adr/README.md")
text = readme.read_text()
anchor = "- [ADR-0164: owner boundary と main quality gate の残存 P1 を収束する](0164-p1-owner-boundary-and-main-quality-gate.md)\n"
entry = "- [ADR-0192: PR quality checks と main APK build を分離する](0192-split-pr-checks-and-main-apk-build.md)\n"
assert anchor in text
if entry not in text:
    text = text.replace(anchor, anchor + entry, 1)
readme.write_text(text)
