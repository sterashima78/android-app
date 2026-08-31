# ADR-0229: Knowledge 再構築の runtime pressure を制限する

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0104](0104-ai-task-queue-feature-ownership.md), [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md), [ADR-0227](0227-partition-auto-wiki-generation-by-topic.md)

## Context

ADR-0227 では、自動Wiki再構築を計画Workerとトピック単位Workerへ分割した。1個の長時間Workerへ複数ページ生成を集約しない点は維持したい一方、Local providerで多数の変更トピックがある場合に次の負荷が発生した。

- 計画完了後、全 `KnowledgeTopicBuildWorker` がWorkManagerへ登録される。
- Local workerは `LocalAiBackgroundTaskGate` でLLM推論自体は直列化されるが、permit待ちへ入る前に各workerがforeground化されていたため、多数workerが同時にforeground executionとして立ち上がり得た。
- 計画WorkerはトピックWorker登録後に完了するため、再構築要求自体がまだ継続中でも通常の `kick()` から同じ `requestId` の計画Workerを再投入できる。AIタスクキューを開く処理も `kick()` を行うため、同一トピックWorker群を重複投入し得る。
- AIタスクキューは1秒周期でKnowledgeの `WORK_TAG` に属する全 `WorkInfo` を列挙して状態を求めていた。トピック数や重複Workが増えるほど、監視UI自体がWorkManager DB負荷を増幅する。
- AIタスクキュー全体でも、一覧取得後に件数計算のため再度 `listTasks()` 相当の処理を行っていた。

実機では自動Wikiの「再作成」を押した後、Knowledge画面だけでなくアプリ全体の操作が遅くなる症状として観測された。

## Decision

### 1. 計画済みの再構築要求へ通常 `kick()` から再計画しない

Knowledge queue stateのpending topic集合を、現在generationの計画が既に成立していることを示すdurable stateとして利用する。

- `requested=true` かつpending topicが存在する間、通常の `kick()` は新しい `KnowledgeBuildWorker` を投入しない。
- 明示的な再作成要求やprovider変更は従来どおり新しい `requestId` を発行して再計画する。
- Global pauseでは現在generationのWorkをキャンセルした後、pending topic集合だけを破棄する。再開時は保存済みデータから再計画し、すでに生成済みのページはfingerprint比較で再利用する。

これにより、画面遷移やruntime復帰目的の `kick()` を冪等なwake-up操作として扱い、計画済みWorkの重複投入を避ける。

### 2. Local topic workerはpermit取得後にだけforeground化する

ADR-0227の「変更トピックごとに独立したWorkRequestを登録する」方針を維持する。Local providerでは各 `KnowledgeTopicBuildWorker` が `LocalAiBackgroundTaskGate` のpermitを取得してからforeground化し、LLM生成へ進む。

- permit待ちのLocal workerはforeground serviceを開始しない。
- 実際に高コストなLocal AI処理を行うworkerだけがforegroundになる。
- 各topic workerはWorkManager上では独立したままとし、1トピックの失敗が別トピックの実行可否へ依存関係を作らない。
- `LocalAiBackgroundTaskGate` はSummary・Library等を含むfeature横断の高コストLocal AI処理との排他・優先度制御を引き続き担当する。

ChatGPT providerはADR-0227の方針を維持し、各topic workerを独立実行する。network constraintとretry/backoffへ委譲する。

### 3. AIタスクキューのKnowledge状態取得で全WorkInfoを列挙しない

Knowledge buildのUI投影は、既存のdurable queue stateを利用する。

- pause / stopped / failed は既存queue stateとprovider pause状態から判定する。
- pending topicが存在する場合は `RUNNING` として投影する。
- pending topicがまだ計画されていない要求は `QUEUED` とする。

この投影では個々のWorkManager rowを毎秒列挙しない。UI表示のためだけにWorkManager DBのtask数へ比例する読み取りを発生させないことを優先する。

### 4. AIタスクキューの1回のrefreshでtask集合を1回だけ取得する

AIタスクキューは `repository.listTasks()` の結果からrunning / queued / paused-or-stopped件数を計算する。件数表示のために同じComposite queueを再度構築しない。

初回表示ではまずsnapshotを読み込み、その後にwake-up目的の `kick()` を行う。これにより `kick()` の処理時間が初回ローディング表示を直接延長しない。

## Consequences

### Positive

- AIタスクキューを開くなど通常のwake-up操作で同一generationを重複計画しない。
- Local自動Wiki再作成でpermit待ちのtopic workerまで一斉にforeground化することを避けられる。
- Knowledge task数が増えてもAIタスクキューの1秒pollingが全 `WorkInfo` 数に比例しない。
- AIタスクキューのComposite task構築回数を1refreshあたり2回から1回へ減らす。
- ADR-0227の「1 worker = 1 topic」と「1 topicの失敗が他topicを停止させない」というdurability境界を維持する。

### Negative

- WorkManagerには変更topic数分の独立WorkRequestを登録するため、WorkManager row数自体はADR-0227と同じだけ増える。
- permit待ちのLocal CoroutineWorkerは存在するため、worker object / coroutineの待機コストを完全には除去しない。
- Knowledgeの `RUNNING` 表示はpending topic集合を基準にするため、個々のworkerがscheduler待ちしている瞬間まで厳密には区別しない。
- 各topic workerが最新source snapshotから対象topicを再構築するADR-0227のコストは今回変更しない。保存済み資料数が非常に大きい場合のsource snapshot最適化は別の計測結果に基づいて扱う。

## Alternatives considered

### WorkManager dependency chainでLocal topicを直列化する

同時runnable worker数を強く制限できるが、通常のWorkManager dependency chainでは先行workerの失敗が後続workerへ伝播し、ADR-0227の「1トピックの失敗が他トピックの生成を止めない」性質を損なうため採用しない。

### ADR-0227以前の単一長時間Workerへ戻す

runtime pressureは小さくなるが、プロセス終了耐性、トピック単位retry、8件超の再構築を改善したADR-0227の利点を失うため採用しない。

### topic workerごとに最新WorkInfoをUIから監視する

表示は厳密になるが、監視UIの読み取りコストがtask数に比例する問題を残すため採用しない。

## Verification

- pending topicがある通常 `kick()` は再計画をskipし、明示的なforce rescheduleはskipしないpolicyをunit testする。
- pending topicの有無からKnowledge queueの `RUNNING` / `QUEUED` 投影をunit testする。
- AIタスクキューの件数を取得済みtask集合から計算することをunit testする。
- Knowledge data / AI task queue UIのunit test、architecture checks、public repository checks、lintをPR CIで実行する。
- 実機では多数topicの再作成中に画面遷移、AIタスクキュー表示、通常スクロールの応答性を確認する。

## Documentation

- `docs/architecture/knowledge.md` のbackground rebuild現在形を更新する。
- ADR-0227は履歴として維持し、本ADRをruntime pressure制御の後続判断とする。
