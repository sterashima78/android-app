# ADR-0071: バックグラウンドAIタスクを非プリエンプティブな優先度付きで実行する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0070では、summary と library organization がローカルモデルを同時利用しないよう `LocalAiBackgroundTaskGate` でバックグラウンドAI推論を直列化した。しかし gate は単純な `Mutex` であり、各 worker は permit を取得したまま feature 内の複数タスクを連続処理していた。

この方式では、長い蔵書整理 batch や通常ブックマークのAI整理が先に開始されると、ユーザーが「あとで読む」に追加した記事の要約が多数の既存タスクの後ろで待つ可能性がある。「あとで読む」は近い将来に読む意思を示す操作であり、バックグラウンドの網羅的な整理より早く要約を利用できることを優先したい。

一方、実行中のローカル推論を途中で強制停止すると、モデル生成や feature-owned durable state の中断処理が複雑になり、再実行コストも増える。また ADR-0056 と ADR-0069 に従い、`core:background` は記事、ブックマーク、蔵書といった業務概念を知るべきではない。

## Decision

### 1. `core:background` の実行 gate に汎用の優先度を追加する

`LocalAiBackgroundTaskGate` は `HIGH` / `NORMAL` / `LOW` の技術的な優先度を受け取る。

優先度の意味付けは feature 側が所有し、gate 自体は記事、フォルダ、蔵書などを知らない。permit が空いた時点で待機中の最も高い優先度を選び、同じ優先度では待機開始順の FIFO とする。

### 2. 実行中タスクはプリエンプトしない

優先度は、現在実行中のAIタスクを強制中断するためには使わない。現在の1タスクが完了して permit が返却された時点で、次に実行するタスクを優先度順で選ぶ。

これにより、durable state の中断点を増やさず、生成途中の処理を破棄しない。高優先度タスクが追加された場合の最大待ち時間は、原則として現在実行中の1タスク分となる。

### 3. worker は1タスクごとに permit を返却して再取得する

summary worker と library organization worker は WorkManager job 自体を1タスクごとに作り直さない。既存 worker のループを維持しつつ、feature-owned durable task を1件処理するたびに `LocalAiBackgroundTaskGate` の permit を返却し、次の1件の前に再取得する。

これにより、WorkManager の unique work chain をタスク数に比例して増やさず、feature 間で各タスク境界ごとに優先度を再評価できる。

### 4. 「あとで読む」の要約を高優先度にする

初期の優先度割り当ては次のとおりとする。

- 「あとで読む」システムフォルダに現在所属する記事の summary task: `HIGH`
- その他の summary task（通常ブックマークの未要約・タグ付けを含む）: `NORMAL`
- 蔵書AI整理: `NORMAL`

将来、ユーザー操作に直結しない保守的AI処理などを `LOW` に割り当てられるよう3段階を用意するが、本変更では `LOW` を既存タスクへ割り当てない。

### 5. summary の優先度は投入時ではなく現在のフォルダ状態から動的に判定する

summary task table へ priority 列は追加しない。claim 直前に `article_folders` と `bookmark_folders.system_kind = 'read_later'` を参照し、「あとで読む」を先に claim する。同一優先度内では既存どおり `queued_at` の昇順とする。

この方式により、すでに通常ブックマークとしてAIタスクが待機している記事を後から「あとで読む」へ移した場合も、その既存タスクを再投入せず高優先度へ昇格できる。逆に「あとで読む」から外した場合は通常優先度へ戻る。

DBスキーマ変更と migration は不要である。

### 6. 共通AIタスクキューへ優先度を投影する

`AiTaskQueueItem` に汎用優先度を追加し、summary の動的優先度を共通キューへ投影する。library organization は初期値として `NORMAL` とする。

一覧は従来どおり状態を第一キーとして「実行中、待機中、一時停止、その他」の順を維持し、同じ状態の中では `HIGH`、`NORMAL`、`LOW` の順に表示する。各行には優先度も表示する。

### 7. 公開リポジトリへユーザーデータを追加しない

優先度判定はシステムフォルダの種別 `read_later` だけを参照し、実在する記事タイトル、URL、タグ、蔵書名、ASIN、Personal Document ID、認証情報などをコード、テスト、ADRへ固定値として追加しない。テストデータは `example.com` と架空IDのみを使用する。

## Consequences

### Positive

- 「あとで読む」の要約が、大量の通常ブックマーク整理や蔵書整理の後ろで長時間待ちにくくなる。
- 実行中推論を破棄せず、次のタスク境界で安全に高優先度へ切り替えられる。
- すでに待機中のブックマークを後から「あとで読む」へ移した場合も即座に優先度へ反映できる。
- feature-owned durable queue を統合せず、ADR-0056 / ADR-0069 の責務境界を維持できる。
- WorkManager job をタスク数だけ細分化せずに feature 間の優先制御を行える。
- 将来のAI機能も業務意味を feature 側で定義し、共通 gate の3段階優先度を再利用できる。

### Negative

- 高優先度タスクも、すでに実行中の1タスクが終わるまでは待つ。
- `HIGH` が継続的に追加され続けると `NORMAL` / `LOW` が長時間待つ可能性がある。現時点では「あとで読む」のユーザー意図を優先し、aging は導入しない。
- summary の優先度判定時にブックマークフォルダテーブルを参照するDB読み取りが増える。
- summary task は要約とブックマークAIメタデータ生成を同じdurable taskとして扱うため、「あとで読む」の高優先度はそのタスク全体に適用される。

## Supersedes / Updates

ADR-0070 のバックグラウンドAI推論を直列化する判断は維持する。ただし、worker が permit 内で feature の複数タスクを連続処理し得る構造と、「公平性より端末負荷を優先し、片方が長く gate を保持し得る」という結果を本ADRで更新する。以後は1タスクごとに permit を返却し、優先度付きで次の実行権を選ぶ。

## Relationship to existing ADRs

- ADR-0030: 通常ブックマークの要約・タグ付けは既存 summary queue を利用する方針を維持する。
- ADR-0056: feature 固有のAI意味論を `core` へ持ち込まず、`core:background` は汎用優先度と排他制御だけを担当する。
- ADR-0069: feature-owned durable queue を共通表示・実行ポリシーへ投影する方針を維持し、表示 contract に優先度を追加する。
- ADR-0070: ローカルAIの直列実行を維持し、permit の保持粒度と次タスク選択を優先度付きへ更新する。
