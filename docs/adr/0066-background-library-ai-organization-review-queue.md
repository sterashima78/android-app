# ADR-0066: 蔵書の一括AI整理をバックグラウンド実行し未確定候補を永続化する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0108 は蔵書のタグ・コレクション・読書状態を外部同期キャッシュから分離し、AIは整理候補だけを生成してユーザー確認後に反映する方針を定めた。2026-08-16の最初の一括整理実装では、未整理本をViewModel内で逐次解析し、候補をメモリ上に保持して全解析完了後に一括適用する方式を採用した。

しかしローカルLLMで多数の蔵書を逐次解析すると処理時間が長くなる。画面やViewModelの寿命に処理を結び付けると、画面を閉じた場合やプロセスが再生成された場合に解析を継続できず、生成済みの未確定候補も失われる。また、全冊の解析完了を待ってからレビューを始める必要はなく、先に生成された候補から順に仕分けできる方が整理作業を進めやすい。

## Decision

### 1. 一括AI解析をWorkManagerのバックグラウンドキューへ移す

一括AI解析は `LibraryOrganizationViewModel` のCoroutine Jobでは実行しない。`feature:library:data` が `LibraryOrganizationBatchWorker` と `WorkManagerLibraryOrganizationBatchScheduler` を所有し、既存の要約バックグラウンド処理と同様にWorkManagerの `CoroutineWorker` で実行する。

Workerはローカルモデルを同時並列実行せず、対象本を1冊ずつclaimして処理する。AI推論中はforeground workerとして低重要度の進捗通知を表示する。画面を閉じてもWorkManagerとDBのキュー状態によって処理を継続できる。

Workerが中断された場合、`PROCESSING` の候補を次回起動時に `QUEUED` へ戻して再開可能にする。一時停止ではバッチ状態を `PAUSED` にしてWorkManagerのunique workをキャンセルし、再開時に `RUNNING` へ戻して再enqueueする。

### 2. バッチと未確定候補をLibrary-owned DBへ永続化する

次のテーブルを `feature:library:data` のschema contributionへ追加する。

```text
library_organization_batches
- batch_id TEXT PRIMARY KEY
- status RUNNING | PAUSED | COMPLETED
- created_at
- updated_at

library_organization_batch_items
- batch_id
- source
- source_id
- status
- tag_names_json
- collection_names_json
- reason
- error
- created_at
- updated_at
- PRIMARY KEY(batch_id, source, source_id)
```

候補状態は次を持つ。

- `QUEUED`: AI解析待ち
- `PROCESSING`: AI解析中
- `PENDING_REVIEW`: AI候補生成済み・未確認
- `DEFERRED`: ユーザーが保留
- `APPLIED`: ユーザーが採用して実際の整理情報へ反映済み
- `REJECTED`: ユーザーが却下
- `FAILED`: AI解析失敗
- `SKIPPED`: 対象本が見つからない、または別操作ですでに整理済みなどでスキップ

AIが1冊の候補を生成した時点で直ちに `PENDING_REVIEW` として保存する。全冊の解析完了を待たない。

この変更でapp-level database versionを16から17へ上げる。

### 3. 解析と仕分けを独立させる

ユーザーはバッチが `RUNNING` の間でも、すでに `PENDING_REVIEW` になった候補を仕分けできる。

未確認候補には次の操作を提供する。

- 採用
- 候補を編集して採用
- 候補だけ編集して保存
- 保留
- 却下

保留・却下した候補は後から未確認へ戻せる。`FAILED` と `SKIPPED` は再解析するか、「却下して完了」として `REJECTED` へ移せる。`PENDING_REVIEW`、`DEFERRED`、`FAILED`、`SKIPPED` のいずれかが残っている間は次のバッチを開始せず、未確定・未処理候補が最新バッチの表示から隠れないようにする。

AI一括整理は読書状態を推測・変更しない。候補採用時にはその時点の既存読書状態を保持する。

### 4. 採用は候補状態と実整理情報を同じSQLite transactionで確定する

候補採用時は、対象本が依然としてタグ・コレクション未設定であることを確認してから、実際の `library_item_organization_tags` / `library_item_organization_collections` を更新し、候補を `APPLIED` にする。

一括解析開始後に別操作で本が整理済みになっていた場合は、古いAI候補で上書きせずエラーとしてユーザーへ知らせる。

候補の編集だけを行う場合は実整理情報には触れず、候補テーブルのJSONだけを更新する。

### 5. 生成済み未確定候補を後続AI分類のtaxonomy contextへ利用する

各本を解析する際、既存の確定済みタグ・コレクションに加え、同じバッチの `PENDING_REVIEW` と `DEFERRED` 候補に含まれる分類名もAIへ提示する。

これにより、バッチ途中でまだ確定されていない分類についても後続の本が同じ表記を再利用できる。却下済み候補はtaxonomy contextへ含めない。採用済み候補は確定済みタグ・コレクション側から参照される。

### 6. UIはDBの候補キューを定期的に読み直す

整理ワークスペース表示中は最新のバッチsnapshotを定期的に読み直し、Workerが生成した候補を順次表示する。UIのpollingは表示更新だけを担い、バックグラウンド処理の寿命には関与しない。

候補一覧は少なくとも「未確認」「保留」「解析中」「採用済み」「却下」「失敗/スキップ」を切り替えて確認できる。

### 7. 公開リポジトリへユーザーの候補データを出力しない

候補DBは端末ローカルだけに保存する。ログ、fixture、テスト、ADR、PR説明へ実在する蔵書名、ASIN、Personal Document ID、ユーザーのタグ・コレクション、AIが生成した実ユーザー向け候補を含めない。

バックグラウンドAI整理も既存の端末内ローカルモデルだけを利用し、書誌情報や整理候補を外部AIサービスへ送信しない。

## Supersedes

ADR-0108 section 9のうち、次の判断を本ADRで廃止する。

- 一括解析をViewModelの一時状態として実行すること
- 未確定候補を永続化しないこと
- プロセス終了時に候補を失うことを許容すること
- 全候補生成完了後にのみ一括適用すること
- 一括解析でDB schema/versionを変更しないこと

ADR-0108の「AIは候補生成に限定し、自動適用しない」「読書状態をAIで推測しない」「既存分類を優先して再利用する」「ユーザー編集を同期キャッシュから分離する」という判断は維持する。

## Consequences

### Positive

- 整理画面を閉じても長時間の一括AI解析を継続できる。
- プロセス再生成やWorker再実行後も生成済み候補を失わない。
- 全冊の解析完了を待たず、候補ができた本から順に整理を確定できる。
- 保留を使って判断が難しい本だけ後回しにできる。
- 候補編集自体も永続化され、次回画面表示時に続きを仕分けできる。
- AI候補と実際の整理情報を別状態として監査しやすい。

### Negative

- 候補とバッチの永続テーブルおよび状態遷移が増え、実装が複雑になる。
- foreground worker実行中はOSの進捗通知が表示される。
- バッチ履歴を保持するため端末DBサイズがわずかに増える。
- UI表示中はWorker進捗反映のため定期的なDB readが発生する。

## Relationship to existing ADRs

- ADR-0013: `library_items` を再構築可能な同期キャッシュとする方針を維持する。
- ADR-0017: ユーザー編集状態を同期キャッシュから分離する方針を維持する。
- ADR-0030: 既存分類をAIへ提示して分類語の増殖を抑える考え方を継承する。
- ADR-0047: 新テーブルは `feature:library:data` のschema contributionが所有する。
- ADR-0054: Library runtime/UI ownershipを維持する。
- ADR-0056: AI runtimeは汎用推論だけを提供し、蔵書分類policyとキュー状態はLibrary featureが所有する。
- ADR-0108: 単冊整理とAI候補確認の基本方針を維持し、一括処理の実行・永続化方式だけを本ADRで置き換える。
