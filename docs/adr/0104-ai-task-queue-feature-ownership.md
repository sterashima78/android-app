# ADR-0104: AIタスクキューを独立 feature として所有する

- Status: Accepted
- Date: 2026-08-19

## Context

ADR-0069 では、複数 feature のバックグラウンドAI処理を共通画面から観測・操作するため、`AiTaskQueueRepository` と画面を `:feature:settings` に置き、`:app` の `CompositeAiTaskQueueRepository` が Summary と Library の queue を投影する構成を採用した。その後 Knowledge のバックグラウンド生成も統合対象となり、共通キューは単なる設定画面の一部ではなく、複数 feature のタスク状態を統合するアプリケーション固有の機能へ成長した。

また `CompositeAiTaskQueueRepository` は表示モデルへの変換だけでなく、次の横断的な実行調停を担うようになった。

- Summary / Library / Knowledge の全体停止と再開
- 充電時自動再開の各 feature への反映
- 一部の操作に失敗した場合の rollback
- feature ごとに異なる停止・再開意味論の共通タスク操作への投影

この責務を `:app` に置き続けると、AIタスクキューの仕様変更が composition root の変更理由になる。また、AIタスクキューの UI / Domain を Settings が所有すると、Settings が単なる導線であるにもかかわらず、タスク管理機能そのものの変更理由を持つ。

ADR-0003 では feature を1画面ではなくアプリケーション固有の ownership namespace とし、第一の分割軸を変更理由と ownership としている。ADR-0101 でも `:app` は feature の dependency wiring に限定する方針を再確認している。

## Decision

### 1. AIタスクキューを独立 feature とする

次のモジュールを追加する。

```text
:feature:ai-task-queue:domain
:feature:ai-task-queue:data
:feature:ai-task-queue:ui
```

既存の `:feature:task` はユーザーが管理する Todo / 階層タスクの feature であり、バックグラウンドAI処理とは変更理由も意味も異なるため統合しない。

### 2. Domain は共通キューの表示・操作 contract を所有する

`:feature:ai-task-queue:domain` は次を所有する。

- 共通タスク item / state / priority / progress 表現
- 共通キューの件数と実行状態
- `AiTaskQueueRepository`

この domain は Summary、Library、Knowledge の永続テーブルや WorkManager 実装を所有しない。各 feature が自身の durable state とタスク意味論を引き続き所有する。

### 3. Data は feature-owned queue の adapter と横断オーケストレーションを所有する

`:feature:ai-task-queue:data` は Summary、Library、Knowledge の domain contract を利用し、feature ごとの差を次の adapter に閉じ込める。

- `SummaryTaskQueueAdapter`
- `LibraryTaskQueueAdapter`
- `KnowledgeTaskQueueAdapter`

`CompositeAiTaskQueueRepository` はこれらを束ね、全体停止・再開・充電時再開・失敗時 rollback の横断的な実行調停を担当する。

Data module は各 feature の concrete Data implementation や WorkManager 型を直接依存先にしない。concrete implementation の生成は `:app` の composition root が担当し、domain contract として渡す。

### 4. Knowledge の background task contract を Domain に置く

AIタスクキューが Knowledge の WorkManager 実装を知る必要をなくすため、Knowledge の background task に必要な状態と操作を `:feature:knowledge:domain` の `KnowledgeBuildTaskController` として表現する。

`WorkManagerKnowledgeBuildTaskController` は `:feature:knowledge:data` に残り、この contract を実装する。Knowledge の Worker、queue state store、WorkManager 固有処理も引き続き Knowledge Data が所有する。

### 5. UI は AIタスクキュー feature が所有する

`AiTaskQueueScreen`、`AiTaskQueueViewModel`、表示順・進捗表示の presentation logic は `:feature:ai-task-queue:ui` が所有する。

`:feature:settings:ui` は「AIタスクキューを開く」という callback のみを公開し、AIタスクキュー画面の表示 state や ViewModel を所有しない。

### 6. app は composition と route 接続だけを担当する

`:app` は次だけを担当する。

- `AppContainer` で Summary / Library / Knowledge の implementation を生成して `CompositeAiTaskQueueRepository` に渡す
- Settings の導線と `AiTaskQueueScreen` を薄い route adapter で接続する

`:app` に共通タスクへの変換、停止・再開規則、feature 固有 task ID の解釈を置かない。

### 7. 公開リポジトリへユーザー固有情報を追加しない

テスト・fixture・ADR では実在する書籍名、ASIN、Personal Document ID、ユーザー固有タグ、認証情報などを使用しない。AIタスクキューのテストデータは一般化した固定値だけを使用する。

## Consequences

### Positive

- AIタスクキューの変更理由が Settings と app から独立する
- Summary / Library / Knowledge の差異を adapter 単位で追跡できる
- 新しいAIバックグラウンド feature を追加するとき、app に task semantics を増やさずに済む
- Knowledge の WorkManager implementation が共通キューへ漏れない
- UI / Domain / Data の ownership が ADR-0003 の feature-first 構成と一致する
- `:feature:task` とAIバックグラウンドキューの意味の衝突を避けられる

### Negative

- Gradle module が3つ増える
- 共通キュー Data は複数 feature の Domain に依存する
- 全体停止・再開は複数 feature への操作であるため、完全なtransactionにはできず rollback の考慮が引き続き必要になる

## Relationship to existing ADRs

- ADR-0003 の変更理由・ownership を第一の分割軸とする方針を適用する。
- ADR-0069 の共通キューの実行意味論、feature-owned durable state、共有実行ゲートの判断は維持する。一方、`AiTaskQueueRepository` / UI を Settings が所有し、Composite adapter を app が所有するというモジュール配置は本ADRで置き換える。
- ADR-0101 の `:app` を dependency wiring に限定する判断をさらに進め、AIタスクキューの横断オーケストレーションも独立 feature へ移す。
