# ADR-0137: 旧 Summary 実行設定の一度限り migration を終了する

- Status: Accepted
- Date: 2026-08-22
- Amends: ADR-0069
- Applies: ADR-0059, ADR-0060

## Context

ADR-0069 では AI background execution policy を Summary 固有設定から `core:background` の `LocalAiBackgroundExecutionPreferences` へ統合した。その際、既存ユーザーの `paused` と `resume_when_charging` を維持するため、旧 SharedPreferences `summary_queue_execution` から現行 `local_ai_background_execution` へ初回アクセス時にコピーする migration を導入した。

この migration は PR #118 で 2026-08-16 に main へ入り、その後の配布版に継続して含まれている。ADR-0059 の更新互換性方針では現在配布中の最新版を次版への baseline とし、利用者はその系列へ継続更新する。旧 SharedPreferences 名は backup / restore の入力にも含まれておらず、現行版へ収束した端末へ旧値だけが再流入する経路はない。

migration 完了後も runtime が旧保存名、移行marker、移行testを保持すると、現在存在しない永続形式を理解し続ける必要がある。

## Decision

`LocalAiBackgroundExecutionPreferences` は現行 SharedPreferences `local_ai_background_execution` だけを読み書きする。

次を削除する。

- `summary_queue_execution` の参照
- `migrated_from_summary_queue_execution` marker
- 初回アクセス時の migration function
- 旧形式 migration 専用 test

`paused` と `resume_when_charging` の現行default・永続化testは維持する。

旧 `summary_queue_execution` だけを持つ 2026-08-16 より前の状態から最新版への直接更新は保証しない。必要な場合は migration を含む中間版を経由する。

## Consequences

### Positive

- `core:background` が現在の保存形式だけを理解すればよくなる。
- 一度限り migration のmarkerと旧SharedPreferences名をproduction codeから除去できる。
- ADR-0059 / ADR-0060 の「現行形式へ収束後、互換処理を削除する」方針と一致する。

### Negative

- migration 導入前の古いアプリ状態から最新版へ直接更新した場合、旧停止設定は引き継がない。

## Relationship to ADR-0069

AI background execution policyを機能横断で共有する判断は変更しない。本ADRは ADR-0069 が導入した一度限りの旧設定migrationについて、配布baselineへの収束完了を記録してruntime compatibilityを終了する。
