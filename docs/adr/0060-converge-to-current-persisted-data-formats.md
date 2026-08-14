# ADR-0060: 現行データ形式へ互換処理を収束させる

- Status: Accepted
- Date: 2026-08-14
- Amends: ADR-0022, ADR-0056, ADR-0059

## Context

ADR-0059 では現在配布中の最新版を更新互換性のベースラインとし、過去 database migration や Expo 時代の database relocation を削除した。

その後も、database schema 以外に過去実装由来の互換処理が残っていた。

- X WebView の旧単一 CSS 設定 `custom_css` を3セット形式へ読み替える処理
- ブックマークの旧「あとで読む」タグをシステムフォルダへ移す初期化処理
- app shell が旧 `SummaryProgress` 型を参照するためだけの source compatibility shim と重複 helper
- 要約プロンプトが旧 `LocalModelManager` の SharedPreferences `local_summary_models` を使い続ける暫定措置

このアプリの利用者は1人で、現在配布中の最新版を利用している。完了済みの互換処理を恒久的に保持すると、現在のデータモデルと過去のデータモデルを同時に理解する必要があり、保守コストが増える。

一方、要約プロンプトはユーザーが明示的に編集した設定であり、保存場所を変更するだけで内容を失うべきではない。これは古い形式を継続サポートするのではなく、現行形式へ一度だけ収束させる migration として扱う。

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

要約プロンプトの保存先を `local_summary_models` から feature 固有の `summary_preferences` へ変更する。

`SummaryPromptStore` の初回アクセス時に次の順で自動移行する。

1. 新保存先に `summary_prompt` が既に存在する場合は新保存先を正とし、旧キーを削除する。
2. 新保存先に値がなく旧保存先に値がある場合は、現在の検証規則で正規化する。
3. 新保存先への同期書き込みが成功した場合だけ旧キーを削除する。
4. 旧値が現在の検証規則を満たさない場合は、従来どおり既定プロンプトを利用し、無効な旧キーを削除する。

これにより端末側で事前操作を要求せず、1回の自動移行後は feature 固有の保存先だけを利用する。

## Consequences

### Positive

- 現在利用していない X CSS、ブックマーク、要約進捗の互換コードを削減できる。
- `feature:summary:data` の永続化責務が旧 `LocalModelManager` の保存領域から分離される。
- 要約プロンプトはユーザー操作なしで新保存先へ移行される。
- 新旧データモデルが並存する期間を限定できる。

### Negative

- 旧単一 CSS キーだけを持つ状態や、旧「あとで読む」タグだけを持つ状態から最新版へ直接更新する互換性は保証しない。
- 要約プロンプト移行のため、`SummaryPromptStore` は当面旧 SharedPreferences 名と key を一度だけ知る必要がある。
- 新保存先への書き込みに失敗した場合は旧キーを残し、次回アクセス時に再試行するため、移行完了まで一時的に両方の保存場所が存在し得る。

## Relationship to other ADRs

- ADR-0022 の3セット CSS モデルは維持し、旧単一 CSS キーの互換読み取りだけを廃止する。
- ADR-0056 の feature-owned local AI 方針を維持し、要約プロンプトの永続化を feature 固有保存先へ収束させ、予定されていた `SummaryProgress` shim を削除する。
- ADR-0059 の「現在配布中の最新版を更新互換性のベースラインとする」方針を database schema 以外の永続データ・source compatibility にも適用する。
