# ADR-0262: Embedded game runtime を 4.7.2 stable へ戻す

- Status: Accepted
- Date: 2026-09-19
- Supersedes: [ADR-0236](0236-godot-android17-version-fallback.md)
- Refines: [ADR-0238](0238-promote-godot-sudoku.md), [ADR-0251](0251-godot-klondike.md)

## Context

ADR-0236 では Android 17 実機での native crash 切り分けのため、embedded game runtime を 4.7.2 stable から 4.6.3 stable へ一時的に下げた。

その後、4.6.3 でも同じ起動 crash が再現し、native tombstone と Java frame から release minification により JNI-visible API の class/member 名が変更されていたことを確認した。runtime dependency を所有する `:feature:game:ui` では、native JNI が名前解決する API を保持する consumer rule を現在の architecture として維持している。

したがって 4.6.3 への fallback は原因回避策ではなく、一時的な切り分け条件として役割を終えた。永続データや schema には関係せず、runtime version の切り替えに migration は不要である。

## Decision

- embedded game runtime baseline を 4.7.2 stable へ戻す。
- packaged project の feature baseline を 4.7 に合わせる。
- release minification では JNI-visible API の class/member 名を保持する既存 consumer rule を維持する。
- dedicated process、Activity orientation、bootstrap による game selection 等の current architecture は変更しない。
- 4.6.3 への一時 fallback は終了し、今後の検証・headless regression は 4.7.2 を基準とする。

## Change Impact Brief

- Changed capability: none。
- Changed Context / ownership: none。
- Changed dependency direction: none。
- Changed durable state / schema: none。
- Changed background execution: none。
- Changed external communication / permission / credential boundary: none。
- Changed compatibility baseline: embedded game runtime 4.6.3 → 4.7.2。
- Rollback: version catalog と packaged project feature version を戻すだけで、data migration は不要。

## Verification

- PR CI と release shrinker が成功すること。
- main の signed release APK を Android 17 実機へ導入し、対象ゲームを複数回起動して native/JNI crash が再現しないこと。
- 数独・クロンダイクで起動、基本入力、画面離脱後の再起動を確認する。
- 問題が再現した場合は runtime version だけを原因と仮定せず、tombstone と release minification の evidence を再確認する。

## Consequences

- temporary fallback を current architecture から除去できる。
- project feature baseline と runtime dependency が同じ 4.7 系へ戻る。
- Android 17 実機確認は main build の release artifact に対して継続する。
