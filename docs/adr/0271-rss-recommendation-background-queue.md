# ADR-0271: RSS推薦スコアリングを記事単位のバックグラウンドキューで実行する

- Status: Accepted
- Date: 2026-09-25
- Amends: [ADR-0270](0270-rss-recommendation-scoring.md)
- Applies: [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0101](0101-feature-route-and-background-runtime-ownership.md), [ADR-0146](0146-workmanager-worker-factory-injection.md)

## Context

ADR-0270 では RSS の推薦評価、条件revision、構造化推論境界を RSS Context が所有することを決めた。初期実装では未読一覧を読み込んだ ViewModel が coroutine を起動して未評価記事を採点していたため、画面を離れると実行 lifetime が UI に依存し、RSS 更新で取得した記事を画面を開かずに評価できなかった。

推薦評価は記事本文を取得せずタイトルだけを端末内AIへ渡すが、記事数が増えると複数回の推論になる。既存の要約、蔵書整理、Knowledge などと同じ端末内AI runtimeを共有するため、RSSだけが連続してruntimeを占有しない実行境界も必要になる。

## Decision

### RSS Context が記事単位の durable task を所有する

RSS Data は `rss_recommendation_tasks` を所有し、現在条件で評価が必要な未読記事を記事単位のtaskとして保持する。

taskには次を保存する。

- article ID
- UI表示と推論再開に必要なtitle snapshot
- 条件revision
- QUEUED / RUNNING
- queued / started timestamp

taskは評価結果そのものではない。評価の正本は従来どおり `rss_recommendation_assessments` とし、task完了後はqueue rowを削除する。queue mutationはbackup schedulingの契機にしない。database snapshotには一時tableが含まれ得るため、restore後はRSS-owned `RssRecommendationBackupRestoreInitializer` がqueueだけを破棄し、評価・条件・feedbackは維持する。

条件revisionが変わった場合は旧revisionの待機taskを破棄し、現在未読の記事から必要なtaskを再構成する。推論中断時のRUNNING taskは次回起動時にQUEUEDへ戻す。

### RSS 更新時に現在未読の未評価記事をqueueへ投入する

通常feedの追加または更新が完了した時点で、そのfeedに属する現在未読記事を確認する。

次のいずれかに該当する記事だけをqueueへ投入する。

- 現在revisionの評価がない
- 評価revisionが古い
- 直前評価が推論失敗

現在revisionのscored / 情報不足unscoredは再投入しない。同じarticle IDのtaskは重複登録しない。

画面表示時や条件変更時にも同じschedulerへ `enqueueUnread` を依頼できる。これは既存データや更新経路の差を補うためのidempotentなkickであり、ViewModel自身は推論を実行しない。

### 1本のWorkerがtaskを1件ずつclaimして順次処理する

RSS-owned Workerはqueueから最古のQUEUED taskを1件だけclaimし、その記事を現在も評価可能か確認してから1件分のstructured inferenceを実行する。完了後に次taskをclaimする。

記事ごとに `LocalAiBackgroundTaskGate` のpermitを取得・返却する。これによりRSSのqueue自体は順次処理しつつ、記事と記事の間で他featureの高優先度ローカルAI taskへ実行機会を渡す。

Workerはowning featureのWorkerFactoryからapplication-scopeのArticle Repository、RSS Recommendation Repository / Serviceをconstructor injectionされる。Worker内で並行する第二repository graphを構築しない。複数記事の処理がAndroidの通常Worker実行時間を超えても継続できるよう、実行中は既存のlong-running AI workerと同じspecial-use foreground workとして低重要度通知を表示する。

### 共通ローカルAI実行制御へ参加する

RSS推薦taskは既存のローカルAI一時停止と充電時自動再開に従う。

global pause時は実行中Workを停止し、RUNNING taskをQUEUEDへ戻す。再開時は同じqueueをkickする。

RSS推薦の推論先は端末内structured inferenceのままとし、cloud routing、network requirement、permission boundaryは追加しない。

### 共通AIタスクキューにはRSS-owned stateを投影する

共通AIタスクキューへ `RSS_RECOMMENDATION` kindを追加し、RSS domain contract経由で記事単位taskを表示する。

共通AIタスクキューはRSS tableを直接読まず、task lifecycleや評価結果を所有しない。RSS-owned reader / schedulerをadapterから利用し、全体pause / resume / charging resumeだけを既存横断制御へ接続する。

### UIは評価待ちを明示する

推薦条件が有効で現在revisionの評価がまだない未読記事は、RSS一覧と統合ビューで「推薦: 評価待ち」と表示する。

background taskが評価を保存するとRSS-owned change streamからpresentation stateを更新し、スコアまたは未評価理由へ置き換える。画面が閉じていても評価は継続し、次回表示時は保存済み評価を使用する。

## Consequences

- RSS画面や統合ビューを開いていなくても、RSS更新後の推薦評価が進む。
- queueと評価のownershipはRSS内に留まり、Content tableや共通AIタスクキューを第二のsource of truthにしない。
- article単位でglobal local-AI permitを返すため、長いRSS queueが他featureを一括で占有しない。
- task tableが1つ増えるが、schemaは既存RSS initializerからadditiveに作成し、この変更だけを理由にdatabase compatibility baselineを変更しない。
- 推論失敗は従来どおり評価reasonとして保存し、そのtaskは完了させる。次回のRSS更新またはidempotent enqueueで再評価候補になるため、同一Worker内で無限再試行しない。

## Verification

- repository testでenqueue、FIFO claim、中断taskのrequeue、revision変更時の旧task破棄、restore時のqueue破棄を確認する。
- scheduler / Worker testで既評価記事を再投入しないこと、1記事ずつ順次処理すること、既読化された記事をskipすることを確認する。
- UI / projection testで評価待ち、scored、情報不足、推論失敗表示を確認する。
- 共通AIタスクキューadapter testでRSS taskの状態とglobal pause projectionを確認する。
- fresh database schema、table ownership、architecture verificationを更新する。
