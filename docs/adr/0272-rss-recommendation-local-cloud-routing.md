# ADR-0272: RSS推薦の実行先を端末内AIとクラウドAIから明示選択する

- Status: Accepted
- Date: 2026-09-26
- Amends: [ADR-0270](0270-rss-recommendation-scoring.md), [ADR-0271](0271-rss-recommendation-background-queue.md)
- Applies: [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md), [ADR-0263](0263-podcast-structured-clustering-output.md)

## Context

RSS推薦はこれまで端末内structured inferenceだけを利用していた。端末内推論は通信を必要としない一方、端末のメモリ制約やmodel runtimeの制約を受ける。

既存のapplication runtimeにはprovider-neutralなstructured inference contractと、端末内・クラウドそれぞれのadapter、background pause制御が存在する。RSS独自のcloud protocolや第二の推論実装を追加せず、既存capabilityを明示的に選択できるようにする。

クラウド実行は外部通信境界を広げるため、利用者の明示選択を必要とし、既定動作と送信データ範囲を固定する必要がある。

## Decision

### RSS Contextが実行providerを所有する

RSS推薦policyへ `RssRecommendationExecutionProvider` を追加し、`LOCAL` / `CLOUD` の2値を持つ。

- 既定値は `LOCAL`
- 自動fallbackは行わない
- provider変更時はpolicy revisionを進める
- 現在未読の記事を新revisionで再評価する
- providerは既存のRSS policyと同じdurable stateとして保存する

既存databaseにはadditive columnを追加し、columnが存在しない場合は `LOCAL` を既定値として収束させる。この変更だけを理由にdatabase compatibility baselineは変更しない。

### provider-neutral structured inferenceを再利用する

RSS Dataは端末内用とクラウド用の `AiStructuredTextInference` をapplication compositionから受け取る。

scoringと除外参考からの条件学習は同じprompt、tool schema、validationを共有し、選択providerに応じてadapterだけを切り替える。

クラウド実行でも通常テキストや自由形式JSONを結果として解析せず、tool call argumentsだけを採用する。

### クラウド送信範囲を限定する

scoringでクラウドへ送信できるのは次だけとする。

- 手動条件と学習条件を結合した除外条件
- 評価対象の記事タイトル

条件学習でクラウドへ送信できるのは次だけとする。

- 手動条件
- 現在の学習条件
- 除外参考の記事タイトル
- 各除外参考の直前評価

RSS推薦では記事URL、feed本文、リンク先本文、保存済み要約をクラウド入力へ追加しない。

### background runtimeをprovider別に切り替える

RSS-owned durable queueとWorker identityは維持する。

`LOCAL` の場合:

- local background pauseに従う
- 充電時自動再開の対象とする
- `LocalAiBackgroundTaskGate` を利用する

`CLOUD` の場合:

- cloud background pauseに従う
- network connectivity constraintを設定する
- 充電時自動再開の対象にはしない
- local AI gateを取得しない

共通AIタスク一覧には現在providerを投影し、Local / Cloudのどちらのglobal pauseを反映するかをproviderから決定する。

### UI

RSS設定の推薦セクションで「端末内AI」「クラウドAI」を明示選択できるようにする。

クラウド選択時は外部送信されるデータ範囲を設定画面内に表示する。端末内選択時は対象データが端末外へ送信されないことを表示する。

## Consequences

- 端末内model runtimeが利用できない、または端末のresource制約が厳しい場合でも、利用者がクラウド実行へ切り替えられる。
- 既定のprivacy behaviorはLOCALのまま維持される。
- provider変更でrevisionが進むため、異なるproviderで生成した評価を現在policyの結果として混在させない。
- RSS queue、assessment、feedbackのownershipは変更しない。
- 外部通信境界は広がるが、送信対象はRSS推薦に必要な最小データへ限定する。
- cloud adapterの認証やmodel選択は既存のprovider設定を利用し、RSS独自のcredential stateを持たない。

## Verification

- domain testでprovider変更時にrevisionが進むことを確認する。
- repository testで既定値LOCALとprovider永続化を確認する。
- routing testでCLOUD選択時にcloud structured inferenceだけが呼ばれることを確認する。
- background / task queue testでLocal / Cloudのpause、表示provider、network constraintを確認する。
- UI testでprovider選択と送信範囲の説明を確認する。
- public repository verification、architecture verification、unit test、lintを実行する。
