# ADR-0273: 非対話型AI推論をfeature-owned durable background workから実行する

- Status: Accepted
- Date: 2026-09-27
- Amends: [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0101](0101-feature-route-and-background-runtime-ownership.md), [ADR-0271](0271-rss-recommendation-background-queue.md)
- Refines: [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md), [ADR-0269](0269-podcast-durable-foreground-generation.md)

## Context

AI推論をViewModelのcoroutine lifetimeへ結び付けると、画面遷移、ViewModel破棄、process再生成などで入力だけが永続化され、推論開始または完了が失われることがある。

RSS推薦では記事スコアリングをADR-0271でbackground queueへ移した一方、除外参考から学習条件を更新する処理はViewModelの30秒debounce coroutineに残っていた。このため除外参考feedbackが永続化されても、画面lifetimeによって学習が進まない構造が残っていた。

Summary、Knowledge、Library、Podcastなど既存の長時間AI処理もfeature-owned queue / controller / Workerへ収束している。再発を防ぐため、単発の不具合修正ではなくAI推論のlifetime境界を明示する。

## Decision

### 非対話型推論はUI lifetimeで実行しない

結果を開始画面のlifetimeに依存せず後から利用できるAI推論を「非対話型推論」とする。

非対話型推論では次を必須とする。

- Screen / Route / ViewModelは推論adapterを直接実行せず、owning featureのscheduler / controller / application capabilityへtask登録を依頼する。
- 実際のinference callはfeature-owned background runtimeからだけ行う。
- process deathや画面離脱後も再構成できるdurable input / task stateをowning featureが保持する。
- 既存のdurable domain stateだけで再構成できる場合は、task tableを重複して追加しない。
- provider選択、network constraint、Local / Cloud pause、charging resume等のruntime条件はscheduler / Worker側で解決し、UI coroutineへ持ち込まない。
- provider変更時は古い実行を停止し、同じdurable inputから新provider向けworkを再構成する。
- 推論失敗時は入力を消費せず、feature固有のbounded retry / failure policyへ収束させる。

### 対話型推論は例外とする

Chat、streaming conversation、保存前の明示的な推論テストなど、画面上の対話session自体が処理単位であるものはこのbackground-only制約の対象外とする。

ただし、対話型処理を理由に非対話型の生成・分類・学習をViewModelへ戻してはならない。

### RSS推薦の除外参考学習をbackground workへ移す

RSS推薦では `rss_recommendation_feedback` を学習入力のdurable source of truthとして維持する。学習専用の第二task tableは追加しない。

「除外参考」追加時はRSS schedulerへ学習workの登録を依頼する。最後のfeedback追加から約30秒のdebounceをWorkManagerのunique workとして表現し、新しいfeedbackが追加された場合はdelayを更新する。

Workerは現在policyの `executionProvider` を利用する。

- LOCAL: 端末内structured inferenceとlocal background pause / charging resumeに従う。
- CLOUD: cloud structured inferenceを利用し、network接続constraintとcloud background pauseに従う。

記事評価と除外参考学習は同じRSS policyのprovider選択を共有し、自動fallbackしない。

学習成功時だけlearned condition更新と対象feedback消費をtransactionで行う。推論、tool call検証、provider変更等で完了できない場合はfeedbackを保持する。

## Consequences

- RSS画面を離れても除外参考からの条件学習が継続できる。
- ViewModelはAI実行lifetimeを所有せず、RSS stateの表示とtask登録だけを担当する。
- RSS scoringとlearningでLocal / Cloud provider、pause、network policyが同じbackground boundaryへ揃う。
- feedback tableを既存のdurable入力として再利用するため、schemaとbackup source of truthは増えない。
- 今後新しい非対話型AI機能を追加するとき、UI coroutineから直接推論する実装はarchitecture driftとして扱う。

## Verification

- RSS domain testで学習成功時だけfeedbackを消費し、provider変更や失敗時は保持することを確認する。
- RSS background testで30秒debounce計算とbackground scheduling helperを確認する。
- UIから学習推論を実行するcoroutineが残っていないことをレビューする。
- current architecture / specへbackground-only ruleとRSS provider共有を反映する。
- public repository verification、architecture verification、unit test、lintを実行する。
