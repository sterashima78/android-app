# ADR-0192: Settings feature が cross-feature presentation を所有する

- Status: Accepted
- Date: 2026-08-27
- Amends: [ADR-0063](0063-feature-ui-ownership-cleanup.md), [ADR-0101](0101-feature-route-and-background-runtime-ownership.md)
- Applies: [ADR-0188](0188-integrated-feature-owns-cross-feature-presentation.md)
- Refines: [ADR-0003](0003-multi-module-architecture.md)

## Context

ADR-0063 では Settings / Summary の feature 固有 UI を owning feature へ戻した一方、既存 composition を安定させるため `:app` に薄い feature UI forwarding adapter を残した。ADR-0101 では Settings の local presentation を `:feature:settings:ui` が所有し、Summary Prompt、AI Task Queue、Drive Backup は別 feature の presentation であることを理由に `:app` の `SettingsFeatureHost` が overlay selection と composition state を所有する判断を採用した。

その後 ADR-0188 で、複数 feature を利用すること自体は app ownership の理由にならず、名前のある feature responsibility に固有の state/action mapping と presentation policy は owning feature が持つ方針を明確化した。

Settings の Summary Prompt、AI Task Queue、Drive Backup も同じ構造である。各 dialog / route の再利用可能 UI と task semantics はそれぞれ Summary、AI Task Queue、Backup feature が所有するが、「Settings のどの overlay を表示しているか」「Settings の操作からどの overlay を開くか」は Settings 画面固有の presentation policy である。これを `:app` に置くと Settings の表示変更理由が composition root に残る。

また `FeatureUiAdapters.kt` は Summary Prompt dialog の単純 forwarding と、Settings の AI progress 型を Summary の表示関数へ渡す変換だけを持つ。前者は ownership 移動後に不要であり、後者も専用 adapter file を維持するほどの独立責務を持たない。

## Decision

### Settings overlay selection は `:feature:settings:ui` が所有する

`SettingsFeatureScreen` が Settings から開く次の presentation の selection state と表示を所有する。

- Models
- ChatGPT Debug
- AI Execution Settings
- Summary Prompt
- AI Task Queue
- Drive Backup

Summary Prompt の dialog 実装は `:feature:summary:ui`、AI Task Queue の route 実装は `:feature:ai-task-queue:ui`、Drive Backup dialog と ViewModel は `:feature:backup:ui` が所有し続ける。Settings UI は sibling feature の public UI / Domain contract を利用して Settings 固有の presentation policy を構成する。

`:feature:settings:ui` から必要な sibling feature UI / Domain module への Gradle dependency を明示する。UI から concrete Data implementation へは依存しない。

### `:app` の Settings adapter は platform wiring に限定する

`:app` の `SettingsRoute` は次だけを担当する。

- `CreateDocument` / `OpenDocument` / `OpenDocumentTree` Activity Result の接続
- backup restore 完了後の app-shell navigation
- application graph が生成した `BackupViewModel`、`AiSettingsViewModel`、`AiTaskQueueRepository` の引き渡し
- biometric lock、background fetch、LAN Web Server など application/platform callback の接続

`SettingsFeatureHost.kt` は削除する。Settings の overlay selection state や sibling feature presentation は `:app` に置かない。

### 汎用名の feature UI forwarding adapter を削除する

`FeatureUiAdapters.kt` は削除する。

Settings の Summary Prompt は `SettingsFeatureScreen` から Summary UI を直接利用する。app-level Summary overlay に必要な progress label は、`FeatureOverlays.kt` が `:feature:summary:ui` の `summaryProgressLabel` を直接呼び、専用 forwarding function は設けない。

### 2026-08-28 refinement: app-level message effects と overlays を物理分割する

ADR-0205 で app-shell presentation が `:app:presentation` へ移った後、`FeatureUiHosts.kt` には navigation destination に応じた snackbar message effect と、Bookmark / Summary の app-level overlay host という異なる変更理由が同居していた。

`FeatureUiHosts.kt` は削除し、次の責務別 source に分割する。

- `FeatureMessageEffects.kt`: `featureMessageSources()` が示す active capability に対する ViewModel resolution と snackbar consumption
- `FeatureOverlays.kt`: Bookmark edit overlay と Summary overlay の app-shell composition

この refinement は presentation ownership、feature UI / Domain dependency、Route contract、ViewModel lifetime を変更しない。Gradle module を追加せず、ADR-0193 の「module 内の局所責務は package / file で分離する」原則に従う。

## Consequences

### Positive

- Settings の変更理由が `:feature:settings:ui` に集約される。
- `:app` の Settings 関連責務が Android Activity Result、navigation、dependency wiring に限定される。
- local overlay と cross-feature overlay の ownership 基準が統一される。
- 実装を持たない `SettingsFeatureHost` / `FeatureUiAdapters` という特例的な app source を削除できる。
- app-level snackbar effect と overlay host の変更理由が source file 単位で分離される。
- ADR-0188 と同じ「cross-feature であることと app ownership を同一視しない」原則を Settings に適用できる。

### Negative

- `:feature:settings:ui` から Summary、AI Task Queue、Backup UI / Domain への明示的な sibling dependency が増える。
- sibling feature の public presentation contract 変更時に Settings UI が追随する必要がある。
- Settings UI が sibling feature の内部実装まで参照しないよう、Data implementation dependency と循環依存を architecture verification で引き続き防ぐ必要がある。
- app-level message effect / overlay の責務間で共通化が必要になった場合でも、汎用 host file に再統合せず ownership を明示する必要がある。

## Verification

- `SettingsCompositionSourceArchitectureTest` で `SettingsRoute` が dialog / route presentation を所有しないことを検証する。
- 同 test で `SettingsFeatureScreen` が6種類の Settings presentation と overlay selection state を所有することを検証する。
- 同 test で `SettingsFeatureHost.kt` と `FeatureUiAdapters.kt` が `:app` に再導入されないことを検証する。
- `AppCompositionSourceArchitectureTest` で `FeatureUiHosts.kt` が戻らず、message effect が centralized navigation capability mapping を利用し続けることを検証する。
- `:feature:settings:ui` の compilation と unit tests を実行する。
- `verifyArchitecture`、app unit tests、release lint を実行する。

## Documentation

- ADR-0063 の app-level forwarding adapter を残す判断を本 ADR で amend する。
- ADR-0101 の Settings cross-feature overlay を `SettingsFeatureHost` が所有する判断を本 ADR で amend する。
- `docs/architecture/module-map.md` の App / Settings ownership 説明を更新する。
- 2026-08-28 refinement では module ownership は変わらないため、module map の追加変更は不要とする。

## Public repository review

本変更は production source、architecture regression test、architecture document のみを変更する。credential、token、OAuth secret、実ユーザーの URL・メールアドレス・健康情報、database / backup / private artifact を追加しない。
