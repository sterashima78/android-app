# ADR-0273: 非対話型AI推論をfeature-owned durable background workから実行する

- Status: Accepted
- Date: 2026-09-27
- Amends: [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0074](0074-library-metadata-management-and-series-reorganization.md), [ADR-0101](0101-feature-route-and-background-runtime-ownership.md), [ADR-0194](0194-workout-ai-advisor.md), [ADR-0271](0271-rss-recommendation-background-queue.md)
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
- WorkManager `Data` はboundedなID・enum・小さいmetadataだけに使い、サイズが入力依存のprompt / response / structured resultはfeature-owned app-private stateへ保存する。これらのtransient task stateは端末backupの正本にはしない。
- 既存のdurable domain stateだけで再構成できる場合は、task tableを重複して追加しない。
- provider選択、network constraint、Local / Cloud pause、charging resume等のruntime条件はscheduler / Worker側で解決し、UI coroutineへ持ち込まない。
- provider変更時は古い実行を停止し、同じdurable inputから新provider向けworkを再構成する。
- 推論失敗時は入力を消費せず、feature固有のbounded retry / failure policyへ収束させる。

### 対話型推論は例外とする

Chat、streaming conversation、保存前の明示的な推論テストなど、画面上の対話session自体が処理単位であるものはこのbackground-only制約の対象外とする。

ただし、対話型処理を理由に非対話型の生成・分類・学習をViewModelへ戻してはならない。

### 推論packageの公開APIでbackground executionを強制する

設計文書だけに依存せず、`:core:ai-inference` の公開contract自体をbackground用途へ限定する。

- model選択、token count、progress等のread-only capabilityは `AiTextInferenceModelReader` として生成権限から分離する。
- one-shot text生成は `BackgroundAiTextInference`、structured tool outputは `BackgroundAiStructuredTextInference` として公開する。
- production adapterは生成開始時にbackground inference execution contextを検証し、通常のUI coroutine等から呼ばれた場合は推論を開始しない。
- background inference execution contextは `CoroutineWorker.withAiBackgroundInference` からだけ開始する。Worker内で `withContext` 等へ移ってもcoroutine contextとして引き継ぐ。
- feature UI / ViewModelには上記background inference capabilityを注入せず、feature-owned task controller / schedulerとstate projectionだけを渡す。
- architecture testでfeature UIからbackground inference capabilityまたはexecution scopeへの依存を禁止する。

これにより、誤ってViewModelからfeature serviceを経由して推論adapterを呼ぶ実装もproduction実行時に拒否され、通常のcompositionではそもそもUIへ生成capabilityが到達しない。

### RSS推薦の除外参考学習をbackground workへ移す

RSS推薦では `rss_recommendation_feedback` を学習入力のdurable source of truthとして維持する。学習専用の第二task tableは追加しない。

「除外参考」追加時はRSS schedulerへ学習workの登録を依頼する。最後のfeedback追加から約30秒のdebounceをWorkManagerのunique workとして表現し、新しいfeedbackが追加された場合はdelayを更新する。

Workerは現在policyの `executionProvider` を利用する。

- LOCAL: 端末内structured inferenceとlocal background pause / charging resumeに従う。
- CLOUD: cloud structured inferenceを利用し、network接続constraintとcloud background pauseに従う。

記事評価と除外参考学習は同じRSS policyのprovider選択を共有し、自動fallbackしない。

学習成功時だけlearned condition更新と対象feedback消費をtransactionで行う。推論、tool call検証、provider変更等で完了できない場合はfeedbackを保持する。

## Consequences

- Workout のメニュー提案・完了後レビューはWorkout-owned task controllerからWorkManagerへ登録する。生成本文はWorkManager `Data` に載せずapp-private no-backup task stateへ保存し、ViewModel / process再生成後も未消費taskを再発見して結果へ接続する。
- Knowledge のユーザー指定ページ作成・AI編集はKnowledge-owned background taskから実行し、入力本文はWorker再実行に耐えるfeature-owned request stateとして保持する。期限による掃除は対応Workが終了済みまたは存在しない入力だけを対象とし、pause / retry中の入力は保持する。未消費task参照もapp-private no-backup stateへ保持し、ViewModel / process再生成後に実行中または完了済みtaskへ再接続する。
- Library の単冊整理候補生成・シリーズ再整理もLibrary-owned background taskへ移し、既存の一括整理workerと同じlocal inference gateへ参加する。単発taskの参照と未消費結果はapp-private no-backup stateへ保持し、ViewModel / process再生成後に再接続する。単冊整理候補は複数冊分の未消費結果を独立して保持し、保存時に対応するtaskだけを消費する。
- RSS画面を離れても除外参考からの条件学習が継続できる。
- ViewModelはAI実行lifetimeを所有せず、RSS stateの表示とtask登録だけを担当する。
- RSS scoringとlearningでLocal / Cloud provider、pause、network policyが同じbackground boundaryへ揃う。
- feedback tableを既存のdurable入力として再利用するため、schemaとbackup source of truthは増えない。
- 今後新しい非対話型AI機能を追加するとき、UI coroutineから直接推論する実装はarchitecture driftとして扱う。

## Verification

- feature UI source testでbackground inference capabilityとexecution scopeへの依存がないことを確認する。
- Workout / Knowledge / Library のViewModel testでAI処理が直接生成ではなくtask登録へ委譲されることを確認する。
- Workout / Knowledge / Library の未消費task再接続、Knowledge のactive request retention、Library の複数未消費候補の独立消費を検証する。
- RSS domain testで学習成功時だけfeedbackを消費し、provider変更や失敗時は保持することを確認する。
- RSS background testで30秒debounce計算とbackground scheduling helperを確認する。
- UIから学習推論を実行するcoroutineが残っていないことをレビューする。
- current architecture / specへbackground-only ruleとRSS provider共有を反映する。
- public repository verification、architecture verification、unit test、lintを実行する。
