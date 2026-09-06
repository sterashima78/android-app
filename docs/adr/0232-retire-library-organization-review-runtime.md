# ADR-0232: 一括蔵書AI整理の旧レビュー状態と操作APIを退役する

- Status: Accepted
- Date: 2026-09-06
- Supersedes in part: [ADR-0072](0072-remove-library-organization-review-ui.md) Decision 4, [ADR-0111](0111-auto-apply-validated-series-aware-library-organization.md) Decision 1 / Decision 2, [ADR-0070](0070-per-book-library-ai-tasks-and-serialized-background-inference.md) の旧レビュー状態投影
- Refines: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0060](0060-converge-to-current-persisted-data-formats.md)

## Context

ADR-0066 は一括蔵書AI整理に `PENDING_REVIEW` / `DEFERRED` を持たせ、候補の編集・採用・保留・再開を行うレビューworkflowを導入した。

ADR-0072 でそのレビューUIを廃止し、ADR-0111 では構造化出力を検証した一括整理結果を自動反映する方式へ変更した。しかしruntimeには次が残っていた。

- `LibraryOrganizationCandidateStatus.PENDING_REVIEW`
- `LibraryOrganizationCandidateStatus.DEFERRED`
- `LibraryOrganizationBatchSnapshot.pendingReview` / `deferred`
- `updateCandidate` / `acceptCandidate` / `deferCandidate` / `reopenCandidate`
- Worker の `PROCESSING -> PENDING_REVIEW -> APPLIED` 二段階永続化
- pending/deferred候補を次のtaxonomy contextへ混ぜる処理

現在の操作モデルには一括候補レビュー画面がなく、正常な一括整理は自動反映される。これらは現行機能ではなく、退役済みworkflowのruntime表現になっている。

一方、SMB書誌正規化には現在も明示的な候補レビューUIがあり、同名の `PENDING_REVIEW` / `DEFERRED` は現行機能である。本ADRの対象は `LibraryOrganization*` の一括蔵書AI整理だけとする。

## Decision

### 1. 一括蔵書AI整理のレビュー状態をdomain modelから削除する

`LibraryOrganizationCandidateStatus` は次だけを現行状態とする。

- `QUEUED`
- `PROCESSING`
- `APPLIED`
- `REJECTED`
- `FAILED`
- `SKIPPED`

`PENDING_REVIEW` / `DEFERRED` と、それらの件数を公開するsnapshot propertyを削除する。

### 2. レビュー操作APIを削除する

`LibraryOrganizationRepository` から次を削除する。

- `updateCandidate`
- `acceptCandidate`
- `deferCandidate`
- `reopenCandidate`

失敗・対象外タスクの終了と再試行に必要な `rejectCandidate` / `retryCandidate` は維持する。

単冊の「AIで整理候補を作る」操作はbatch候補レビューAPIを利用していないため維持する。

### 3. AI生成結果の保存と適用を1 transactionへ統合する

Workerが1冊の候補を生成した後は `applyGeneratedSuggestion` を1回呼び、同じSQLite transactionで次を行う。

1. claimed itemが `PROCESSING` であることを確認する。
2. AI出力をsanitizationする。
3. その本が手動操作で既に分類済みか再確認する。
4. 未分類なら現在の読書状態を保持したままタグ・コレクションを保存する。
5. batch itemへ生成tags / collections / reasonを保存し、`APPLIED` にする。
6. 手動分類が先に入っていれば既存分類を変更せず、batch itemを `REJECTED` にする。

これにより正常経路は `PROCESSING -> APPLIED`、手動編集競合は `PROCESSING -> REJECTED` となる。候補だけが `PENDING_REVIEW` として残る中間commitを作らない。

Workerがtransaction開始前にcancelされた場合は従来どおりclaimed itemを `QUEUED` へ戻す。transaction途中の失敗はSQLite transaction全体をrollbackする。

### 4. 旧DB文字列はworkflowを復活させず終了状態として読む

現在配布中の更新ベースラインでは旧レビュー候補を利用しない。DBに過去の `PENDING_REVIEW` / `DEFERRED` 文字列が残っていた場合はdomain enumへ復活させず、data adapterで `REJECTED` としてdecodeする。

この互換処理は旧機能を動かすものではなく、現行enumで過去rowを安全に読み飛ばすための境界変換である。新規書き込みでは旧文字列を生成しない。

この整理だけを目的とするdatabase version bumpや destructive migrationは行わない。batch item table自体は生成結果、失敗、再試行、タスク履歴を保持する現行durable recordとして維持する。

### 5. taxonomy contextは確定済み分類だけから作る

一括処理中のtaxonomy contextは `LibraryOrganizationSnapshot` の確定済みtag / collectionだけから構築する。

旧 `PENDING_REVIEW` / `DEFERRED` rowを追加文脈として読む処理は削除する。先に `APPLIED` となった本は通常snapshotへ反映済みなので、後続タスクから引き続き再利用できる。

## Consequences

### Positive

- UIから消えたレビューworkflowがdomain / data / workerへ残らない。
- 正常な自動反映に一時的な `PENDING_REVIEW` commitがなくなる。
- AI生成と適用の間でcancelされ、未確認候補だけが残る状態を作らない。
- 手動整理との競合判定とAI適用が1 transaction内で完結する。
- repository interfaceとfake/test実装が現行操作だけを表す。
- current-version compatibility baselineに沿ってsteady-state実装を単純化できる。

### Negative

- 過去DBに残る未確認・保留候補はレビュー対象として復元できず、`REJECTED` として扱う。
- AI自動適用後に内容を修正する場合は通常のLibrary編集機能を使う。

## Verification

- 生成候補の適用でタグ・コレクションとcandidate `APPLIED` が同時に確定すること。
- 既存の読書状態を維持すること。
- AI生成中に手動分類が入った場合、その分類を上書きせずcandidateを `REJECTED` にすること。
- raw `PENDING_REVIEW` / `DEFERRED` rowを `REJECTED` としてdecodeすること。
- batch進捗は `QUEUED` / `PROCESSING` だけを未完了として扱うこと。
- AI task queueでは `APPLIED` / `REJECTED` を完了、`FAILED` / `SKIPPED` を再試行可能な失敗として扱うこと。
- SMB書誌正規化のレビューworkflowには変更がないこと。
- Architecture / Test / Lint / R8 / public repository verificationを実行すること。

## Public repository review

本変更は既存のdomain状態・repository API・SQLite状態遷移を削減する。新しい権限、外部通信、credential、ユーザーデータfixtureを追加しない。テストは架空の書名・分類名だけを使用する。

## References

- [ADR-0059](0059-current-version-compatibility-baseline.md)
- [ADR-0060](0060-converge-to-current-persisted-data-formats.md)
- [ADR-0066](0066-background-library-ai-organization-review-queue.md)
- [ADR-0070](0070-per-book-library-ai-tasks-and-serialized-background-inference.md)
- [ADR-0072](0072-remove-library-organization-review-ui.md)
- [ADR-0111](0111-auto-apply-validated-series-aware-library-organization.md)
