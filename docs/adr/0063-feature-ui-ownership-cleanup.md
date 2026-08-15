# ADR-0063: app に残った feature 固有 UI を所有 feature へ戻す

- Status: Accepted
- Date: 2026-08-15

## Context

ADR-0003 では `:app` を composition root と navigation に限定し、feature 固有の UI state と再利用可能な UI 実装は各 feature が所有することを決めている。ADR-0062 ではこの方針に従い、統合ビューの再利用可能 UI を `:feature:integrated:ui` へ移した。

その後の確認で、`app/src/main/.../ui` に次の実装が残っていた。

- AI モデル管理ダイアログ
- 要約プロンプト編集ダイアログ
- 要約進捗の表示文言生成
- 過去実装由来で参照されていない AI 進捗ラベル関数

これらは navigation や dependency wiring ではなく Settings / Summary の表示仕様そのものであり、app が所有する理由がない。

また app 側の Settings route が、バックグラウンド取得の Wi-Fi 限定設定について表示用の Compose state まで保持していた。設定値の永続化や Mail の再スケジュールは app-level composition で接続してよいが、画面状態は Settings UI が所有すべきである。

## Decision

AI モデル管理ダイアログを `:feature:settings:ui` へ移し、要約プロンプト編集ダイアログと要約進捗ラベル生成を `:feature:summary:ui` へ移す。

`:app` には既存の `YomitoriApp` composition を安定させるため、feature UI への委譲と Settings の進捗型から Summary 表示引数への変換だけを行う薄い adapter を置く。この adapter は UI 実装を持たず、ADR-0003 が許容する composition adapter として扱う。

参照されていない旧 `progressLabel` は移行せず削除する。

バックグラウンド取得の Wi-Fi 限定設定については、初期値と変更 callback を app route から Settings UI に渡し、表示中の state は `SettingsFeatureScreen` が所有する。app route は次に限定する。

- `BackgroundDataFetchPreferences` から初期値を取得する
- 変更された値を永続化する
- Mail の periodic sync policy を再適用する

この整理では永続化形式やユーザー操作の意味を変更しないため、データ移行や互換 shim は追加しない。

## Consequences

### Positive

- Settings / Summary の表示仕様が所有 feature に集約される
- app の UI package に feature 固有の Compose 実装を増やさない方針が明確になる
- Settings route の feature 固有 state が減り、composition と platform wiring に集中する
- 未使用の過去実装を削除できる
- feature UI を単独でテストしやすくなる

### Negative

- `YomitoriApp` の既存構造を維持するため、小さな app-level adapter が1つ残る
- Settings の Wi-Fi 設定変更は複数 feature の background policy を接続するため、app route に wiring callback が残る

## Relationship to existing ADRs

- ADR-0001 の UI state ownership と Repository / platform detail の分離方針を具体化する
- ADR-0003 の app を composition root とする方針に従う
- ADR-0062 の統合ビュー UI 分離と同じ ownership 原則を残存 UI に適用する
- ADR-0046 の自動検証では意味上の ownership を完全には判定できないため、本 ADR の境界はレビューでも確認する
