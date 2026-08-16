# ADR-0069: AIモデル設定とバックグラウンドAIタスクキューを機能横断で統合する

- Status: Accepted
- Date: 2026-08-16

## Context

ローカルAIは記事要約だけでなく、ブックマークの要約・分類、蔵書整理、チャット、ナレッジ生成など複数 feature から利用されるようになった。実装上はすでに `LocalModelManager` を application scope で共有している一方、設定画面では「要約モデル」と表示されており、利用範囲とユーザー向けの意味が一致していなかった。

バックグラウンド処理も同様である。ADR-0068 では要約・ブックマークAI処理を行う summary queue だけに、キュー全体の一時停止と充電時自動再開を追加した。一方、ADR-0066 の蔵書AI整理も WorkManager とDB上の durable batch を持ち、端末内LLMを連続利用する。電池消費を抑えるという実行方針は両者に共通するが、停止状態と設定画面が summary feature に閉じていた。

ただし ADR-0056 により、`core:ai-runtime` は汎用推論 capability に限定し、要約・蔵書整理など feature 固有のタスク意味論を持たせない。また ADR-0063 により、`:app` は composition adapter に限定し、UI実装とUI state は feature が所有する必要がある。

そのため、feature 固有の durable state を1つの巨大な共通テーブルへ移すのではなく、共通のユーザー操作と実行ポリシーだけを統合する必要がある。

## Decision

### 1. モデル選択はアプリ全体の「AIモデル設定」として扱う

設定画面の「要約」セクションを「AI」へ変更し、「要約モデル」を「AIモデル」と表示する。

モデルの実体は従来どおり application scope の `LocalModelManager` を共有する。要約、ブックマーク整理、蔵書整理、チャットなどは同じ選択済みモデルを利用する。

一方、要約プロンプトのように feature 固有の設定は共通化しない。「AI」セクション内に置いても、利用対象が要約だけであることを明示する。

### 2. 共通AIタスクキューは feature-owned queue を集約する view contract とする

`:feature:settings:domain` に `AiTaskQueueRepository` と共通表示モデルを置く。これは feature の永続タスクを所有せず、次の共通操作だけを表現する。

- AIタスク一覧の取得
- AIバックグラウンド実行全体の一時停止・再開
- 充電時自動再開設定
- 各タスクが対応している場合の停止・キャンセル・再開

`:feature:settings:ui` が `AiTaskQueueScreen` とその ViewModel を所有する。`:app` は `CompositeAiTaskQueueRepository` で summary と library の既存 domain contract を束ねるだけとし、Compose UI や feature 固有 UI state を持たない。

初期の集約対象は次の2種類とする。

- summary queue: 記事要約・ブックマーク要約/タグ付けを個別タスクとして表示する
- library organization queue: 1回の durable batch を「蔵書のAI整理」という1タスクとして表示し、解析済み件数/総件数と確認待ち件数を表示する

将来のAI feature は自身の durable state を維持したまま、composition adapter から共通 contract へ投影して追加できる。

### 3. feature 固有のタスク状態と操作意味論は各 feature に残す

summary の `summary_tasks`、停止/キャンセル/再開状態、進捗は `:feature:summary` が引き続き所有する。

library の batch/candidate table、候補レビュー、採用/保留/却下状態は `:feature:library` が引き続き所有する。共通キューから library batch の個別「一時停止」を選んだ場合は既存の `pauseBatch()` と WorkManager cancel を使い、「再開」は `resumeBatch()` と scheduler kick を使う。

したがって共通キューは feature-specific database を置き換えず、複数 queue を一貫して操作・観測するための集約 view である。

### 4. AIバックグラウンド実行ポリシーだけを `core:background` で共有する

要約キュー専用だった `paused` と `resume_when_charging` は、`LocalAiBackgroundExecutionPreferences` として `:core:background` へ移す。

この型はAIタスクの内容、記事、ブック、要約などを知らず、次の技術的な実行ゲートだけを保持する。

- `paused`: ローカルAIを使うバックグラウンド処理を開始してよいか
- `resume_when_charging`: 一時停止後、充電状態を再開契機にしてよいか

既存ユーザーの設定を維持するため、初回アクセス時に旧 `summary_queue_execution` SharedPreferences から値を移行する。DBスキーマ変更は行わない。

### 5. Summary と Library の両 worker が共通実行ゲートを守る

summary queue は従来の worker-side pause gate を共通 preference 経由で読む。

library organization scheduler も共通 gate が paused の場合、新しい解析 worker を開始しない。全体のAIタスクを一時停止すると、実行中の library unique work はキャンセルする。キャンセルされた processing item は既存のキャンセル処理で queued に戻るため候補データを失わない。

この全体停止では library batch 自体の durable `PAUSED` 状態には変更しない。batch が `RUNNING` のまま共有実行ゲートによって止まっている場合、共通AIタスクキューでは実効状態を「一時停止中」と表示する。これにより、ユーザーが library batch を個別操作で `PAUSED` にしていたのか、AI全体の実行ゲートで一時的に止まっているだけなのかを区別できる。

library にも充電制約付き resume worker を追加する。これは全体停止時に `RUNNING` だった library batch に対してだけ登録する。summary と library の charging worker は同じ preference を参照するため、どちらが先に実行されても安全な idempotent 操作とする。充電時には共有 gate を開き、library batch が引き続き `RUNNING` なら worker を kick する。個別操作で `PAUSED` の batch は充電だけでは再開しない。

充電は再開の契機であり、電源から外れた際の自動再停止は行わない。

共通キューの全体停止は summary と、実行中の library batch のバックグラウンド実行を止める。全体再開は summary と、全体停止前から `RUNNING` だった library batch の実行を再開する。個別に `PAUSED` の library batch は全体再開でも停止状態を維持し、明示的な個別「再開」でのみ `RUNNING` に戻す。

### 6. 公開リポジトリにユーザーデータを追加しない

共通キューのテスト、fixture、ADR には実在する書籍名、ASIN、Personal Document ID、ユーザー固有タグ/分類名などを入れない。実行時の端末内データをUIに表示することと、ソースリポジトリへ固定値として保存することを区別する。

## Consequences

### Positive

- 設定画面のモデル名称が実際の共有範囲と一致する。
- 要約と蔵書整理を同じAIタスクキューから観測・停止・再開できる。
- 電池消費を抑える実行ポリシーが feature ごとに食い違わない。
- 全体停止と個別停止の状態を混同せず、個別停止した蔵書整理を意図せず再開しない。
- feature 固有の durable state とレビュー意味論を維持できる。
- `core:ai-runtime` に業務概念を持ち込まず、ADR-0056 の境界を保てる。
- app は adapter に限定され、ADR-0063 のUI ownershipを保てる。

### Negative

- 共通キューは複数 feature repository を読むため、完全に1つのDB queryだけでスナップショットを作る構造ではない。
- charging resume worker は各 feature が自身の WorkManager job を再開するため複数存在する。共有 preference と idempotent な再開処理で競合を吸収する必要がある。
- library batch は共有 gate により停止中でも durable status 自体は `RUNNING` のため、共通キュー側で実効状態を投影する必要がある。
- library batch の個別「一時停止」は summary の個別 stopped state と異なり、batch 全体の paused state へ写像される。共通 contract はこの差異を抽象化する。

## Relationship to existing ADRs

- ADR-0056: feature 固有AIポリシーと汎用AI runtime の境界を維持する。本ADRは task semantics ではなく横断的な実行ゲートと設定UIを追加する。
- ADR-0063: 共通キューUIを `:feature:settings:ui` に置き、`:app` は composition adapter に限定する。
- ADR-0066: durable な蔵書AI整理 batch/candidate とレビュー方式を維持し、共通AIタスクキューからその batch を観測・停止・再開できるよう拡張する。
- ADR-0068: summary 専用として定義したキュー全体の execution policy と設定UIを本ADRで機能横断へ一般化する。ADR-0068 は本ADRにより superseded とする。
