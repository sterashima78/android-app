# ADR-0060: 現行データ形式へ互換処理を収束させる

- Status: Accepted
- Date: 2026-08-14
- Amends: ADR-0022, ADR-0056, ADR-0059
- Amended by: [ADR-0148](0148-retire-local-model-revision-marker-migration.md)

## Context

ADR-0059 では現在配布中の最新版を更新互換性のベースラインとし、過去 database migration や Expo 時代の database relocation を削除した。

その後も、database schema 以外に過去実装由来の互換処理が残っていた。

- X WebView の旧単一 CSS 設定 `custom_css` を3セット形式へ読み替える処理
- ブックマークの旧「あとで読む」タグをシステムフォルダへ移す初期化処理
- app shell が旧 `SummaryProgress` 型を参照するためだけの source compatibility shim と重複 helper
- 要約プロンプトが旧 `LocalModelManager` の SharedPreferences `local_summary_models` を使い続ける暫定措置

このアプリの利用者は1人で、現在配布中の最新版を利用している。完了済みの互換処理を恒久的に保持すると、現在のデータモデルと過去のデータモデルを同時に理解する必要があり、保守コストが増える。

要約プロンプトについては、保存場所を変更する際にユーザーが編集した値を失わないよう、`local_summary_models` から `summary_preferences` への一度限りの migration を導入した。その migration を含む最新版で現行保存先への収束が完了したため、旧保存先を参照する runtime migration も不要になった。

## Decision

### X CSS

`XViewerCssPreferences` は3セット形式だけを読み書きする。旧 `custom_css` キーからの読み替えは削除する。

現行ベースラインでは3セット形式を利用済みであることを前提とし、旧単一キーだけを持つ状態からの直接更新は保証しない。

### ブックマークの「あとで読む」

「あとで読む」は `bookmark_folders` のシステムフォルダを唯一の現行表現とする。

起動時に旧タグを検索してフォルダへ移す `migrateLegacyReadLaterTag` は削除する。システムフォルダ自体を保証する `ensureReadLaterFolder` は現行機能に必要なため維持する。

### 要約進捗

app shell は `feature:settings` が公開する現行の要約進捗型だけを利用する。

旧 `core.airuntime.SummaryProgress` の source compatibility shim と、app shell 内に残っていた同型向けの重複 `summaryProgressLabel` helper は削除する。

### 要約プロンプト

要約プロンプトの現行保存先は feature 固有の SharedPreferences `summary_preferences` とし、`summary_prompt` を唯一の現行キーとする。

旧 `local_summary_models` からの migration は、移行を含む最新版で現行保存先への収束が完了したため削除する。`SummaryPromptStore` は旧 SharedPreferences 名や旧保存領域を参照しない。

旧 `local_summary_models` にだけ `summary_prompt` が存在する状態から現行版へ直接更新する互換性は保証しない。

## Consequences

### Positive

- 現在利用していない X CSS、ブックマーク、要約進捗、要約プロンプトの互換コードを削減できる。
- `feature:summary:data` の永続化責務が旧 `LocalModelManager` の保存領域から完全に分離される。
- `SummaryPromptStore` は現行保存形式だけを理解すればよくなる。
- 新旧データモデルが runtime で並存しない。

### Negative

- 旧単一 CSS キーだけを持つ状態や、旧「あとで読む」タグだけを持つ状態から最新版へ直接更新する互換性は保証しない。
- 旧 `local_summary_models` にだけ要約プロンプトが残っている古い状態からの直接更新では、カスタムプロンプトを引き継がない。

## Relationship to other ADRs

- ADR-0022 の3セット CSS モデルは維持し、旧単一 CSS キーの互換読み取りだけを廃止する。
- ADR-0056 の feature-owned local AI 方針を維持し、要約プロンプトの永続化を feature 固有保存先へ完全に収束させる。
- ADR-0059 の「現在配布中の最新版を更新互換性のベースラインとする」方針を database schema 以外の永続データ・source compatibility にも適用する。
