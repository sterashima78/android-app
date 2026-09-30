# 5. 端末内AI

## 5.1 共通runtime

- LiteRT-LM を利用する端末内AI runtime を共有する。
- モデルのダウンロード、選択、削除、推論設定、端末上のベンチマークを管理できる。
- 非対話型のAI生成・分類・学習は foreground UI へ閉じず、feature 所有の durable background runtime または task queue へ委譲する。UIはtask登録と状態表示だけを行う。
- RSS推薦評価はRSSが所有する記事単位キューで順次実行する。端末内AI選択時は共通のローカルAI一時停止・充電時自動再開・推論直列化に参加し、クラウドAI選択時はクラウドAI一時停止とnetwork constraintに従う。

## 5.2 要約

- コンテンツ本文を取得・前処理したうえで要約を生成する。
- 取得、前処理、推論、metadata生成は分離した処理段階として扱う。
- 要約結果とtask状態は永続化し、失敗taskの再実行や一時停止・再開を行える。
<!-- formal-requirement
id: SUMMARY-TASK-LIFECYCLE-001
models:
  - spec-models/quint/summary_task_lifecycle.qnt
  - spec-models/alloy/summary_task_uniqueness.als
-->
- 要約taskは記事ごとに1つのdurable stateを持ち、処理待ちまたは処理中の同一記事を重複投入しない。停止または失敗したtaskは再開すると処理待ちへ戻り、処理中taskの中断復旧も同じtaskを処理待ちへ戻す。
<!-- /formal-requirement -->
- ブックマークの自動補完、明示的な一括再実行、単記事のタグ再生成指定は Summary が所有する既存の要約キューへ投入する。

形式モデル:

- [Quint: `summary_task_lifecycle.qnt`](../../spec-models/quint/summary_task_lifecycle.qnt) — 要約taskの投入、claim、停止、再開、中断復旧、完了・失敗の状態遷移を検査する。
- [Alloy: `summary_task_uniqueness.als`](../../spec-models/alloy/summary_task_uniqueness.als) — 同一記事が複数のdurable Summary taskを同時に持たないことを検査する。

## 5.3 AIチャット

- 端末内モデルを利用してチャットできる。
- アプリ内情報を参照する場合は、定義済みの読み取り用tool / skillを利用する。
- 任意SQLや任意コード実行をAIへ公開しない。

## 5.4 Knowledge

- 保存済みコンテンツや要約を資料としてKnowledge pageを生成・更新できる。
- 自動生成は永続background taskとして実行し、既存pageの拡張と追加page作成を扱う。
- ユーザーが開始するKnowledge pageの新規生成とAI編集もbackground taskとして実行し、画面を離れても推論実行をViewModel lifetimeへ依存させない。
