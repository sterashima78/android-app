# ADR-0269: ニュースポッドキャスト生成をdurable foreground workへ統一する

- Status: Accepted
- Date: 2026-09-25
- Refines: [ADR-0253](0253-resume-interrupted-podcast-generation.md), [ADR-0257](0257-podcast-chapter-generation-jobs.md), [ADR-0265](0265-podcast-cloud-chapter-parallelism.md), [ADR-0268](0268-podcast-rebuild-regeneration.md)

## Context

ニュースポッドキャストには、定刻生成では既存のWorkManager Workerを利用する一方、画面からの生成と作り直しはViewModel scopeから直接長時間処理を開始する差がある。後者は画面を離れただけでは直ちに停止しないものの、process終了やUI lifetimeの変化に対してbackground実行を保証できない。

またクラウド生成ではchapterを独立checkpointとして有界並列実行しているが、一時的なtransport / timeout / rate limit / server failureを即座にchapter failureへ確定している。provider adapterは既にretryable failureを安全なtyped taxonomyへ正規化しており、Podcast側でretry policyを所有できる。

## Decision

### 1. 生成開始経路をPodcast-owned WorkManagerへ統一する

画面からの新規生成と作り直しは、ViewModelから生成use caseを直接実行せず、Podcast Contextが所有するcontrollerを通じて即時WorkManager workとして登録する。

定刻生成と手動生成は同じWorker classとapplication-scope dependency graphを利用するが、番組ごとに定刻用と手動用の別unique work identityを持つ。これにより手動操作は将来の定刻workを置き換えず、同じ番組で実行時刻が重なった場合だけ既存のprogram単位generation guardとWorker retryで直列化する。

Workerは入力operationとして通常生成と既存episodeの作り直しを区別する。episode / chapterのdurable state ownershipは従来どおりPodcast Contextに残す。

### 2. Workerをlong-running foreground workとして扱う

Podcast生成Workerは実処理開始前にforegroundへ昇格し、低重要度のongoing notificationを表示する。

episodeが予約済みになった後は、durable chapter checkpointの完了数と総数を通知へ反映する。通知は生成継続のためのforeground service lifetimeを提供するもので、Podcastの新しいsource of truthにはしない。

### 3. retryable cloud failureはchapter内でbounded retryする

クラウドchapter生成では、provider adapterがretryableと分類したfailureだけを同一chapter内で再試行する。

- 初回試行に加えて最大3回再試行する。
- 待機は概ね2秒、5秒、15秒を基準に小さなjitterを加える。
- cancellationはretryせず直ちに伝播する。
- authentication、request rejection、未接続等のnon-retryable failureは再試行しない。
- retryを使い切るまではchapterをFAILEDへ確定しない。

各chapterのretryはepisode内の既存有界並列数の内側で行い、並列数そのものは増やさない。

### 4. retry exhaustion後だけ既存failure checkpointへ収束する

retryable failureが全試行で失敗した場合、従来どおり該当chapterをFAILEDへ保存し、並列中の他chapterは可能な限り完了させる。全chapter処理後にepisode attemptを失敗へ遷移させる。

完成済みREADY checkpointは後続の明示再実行や中断復旧で再生成しない。

### 5. schedule ownershipと起動時復旧は維持する

次回定刻時刻の計算、program単位unique work、application起動時のinterrupted generation recoveryは既存のPodcast ownershipを維持する。新しいscheduler、queue、database tableは追加しない。

## System delta

- Changed capability: Podcast generation request / execution / retry / progress notification
- Changed Context / ownership: none
- Changed dependency direction: Podcast Dataが既存cloud provider adapterのtyped failure contractを利用する
- Changed durable data / schema: none
- Changed background execution: 手動生成をUI coroutineからWorkManager foreground workへ移行
- Changed external communication: none
- New durable state: none
- New scheduler / queue: none
- New Android permission: none
- Cloud request policy: retryable failureだけをchapter内でbounded retry

## Consequences

### Positive

- 画面を離れる、端末をロックする、といったUI lifetime変化から手動生成を切り離せる。
- 一時的な通信・provider failureを短時間の自動再試行で吸収しやすくなる。
- retry中にepisode全体をFAILEDへ確定せず、既存checkpoint semanticsを維持できる。
- 生成中の進捗を通知から確認できる。
- 定刻生成と手動生成で実行基盤が分岐しない。

### Negative

- 手動操作の結果は即時の関数戻り値ではなくdurable episode stateとして観測する必要がある。
- 同じchapterの一時障害時は最大でretry backoff分だけ完了が遅れる。
- Podcast Dataがprovider-neutral inferenceだけでなく、retryability判定のため既存のtyped cloud failure adapterへ依存する。

## Compatibility / rollback

database schemaと保存形式は変更しない。

旧versionが登録済みのPodcast generation Worker class identityは維持し、入力operation未指定の既存scheduled workは通常生成として扱う。rollback時も既存episode / chapter stateはそのまま利用できる。

## Verification

- retryable failureが成功するまで再試行され、成功時はchapterがREADYになることをunit testする。
- retry回数とbackoff sequenceをunit testする。
- non-retryable failureとcancellationを再試行しないことをunit testする。
- retry exhaustion後だけchapter / episodeがFAILEDになることをunit testする。
- 手動生成と作り直しが即時WorkManager workへ変換されることをunit testする。
- operation未指定のworker inputを通常生成として扱うcompatibilityをtestする。
- foreground notificationの初期状態とchapter進捗表示をtest可能な純粋関数へ分離してunit testする。
- architecture verification、unit tests、lint、public repository verificationを実行する。
