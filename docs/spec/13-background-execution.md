# 13. Background execution

- durableなbackground処理にはWorkManagerを利用する。
- 自動バックアップはbackup対象データの変更イベントでは起動しない。
<!-- formal-requirement
id: BACKUP-SCHEDULE-002
models:
  - spec-models/quint/backup_schedule.qnt
-->
- Backup Contextが保持する各ローカル時刻ごとにone-shot workを予約し、schedule triggerは次回同時刻を再予約する。
<!-- /formal-requirement -->
- 実バックアップjobは既存のnetwork constraintに従って実行する。
- Chat等の対話sessionを除くone-shot AI生成・分類・学習はowning featureのdurable background taskから実行する。feature UI / ViewModelは推論adapterを直接実行せず、task controller / schedulerへの登録とstate projectionだけを行う。
- Workoutのメニュー提案・完了後レビュー、Knowledgeのユーザー指定ページ生成・AI編集、Libraryの単冊整理候補生成・シリーズ再整理もこのbackground-only境界に従う。
- WorkManager `Data` はID・enum等のbounded metadataに限定する。サイズが入力依存のAI入力・出力や、画面再生成後にも回収すべき未消費結果はowning featureのapp-private durable stateへ保持し、task controllerから再発見できるようにする。transient task stateは端末backupの正本にはしない。
<!-- formal-requirement
id: BACKGROUND-CLEANUP-001
models:
  - spec-models/quint/background_request_cleanup.qnt
-->
- feature-owned request stateの期限掃除では、経過時間だけを理由にENQUEUED / BLOCKED / RUNNING等のactive work入力を削除しない。終了済みまたは対応workが存在しない入力だけを掃除対象とする。
<!-- /formal-requirement -->
- feature固有Worker、scheduler/controller、queue state interpretationは原則としてowning featureのdata/runtimeが所有する。
- application-scope の周期更新では通常feed、Reddit、購読型動画Provider、メール等の更新を個別に分離して実行する。購読型動画はVideo-owned provider refresh capabilityを利用し、1件のsubscription失敗で他sourceの更新を中断しない。
- 通常feedの更新完了時は、推薦条件が有効なら対象feedの未読記事から評価が必要な記事をRSS-owned queueへ追加する。推薦評価はRSS-owned Workerが記事単位で順次claimし、画面のlifecycleに依存せず処理する。長時間の評価中は低重要度のforeground通知を表示する。
- 統合ビューへ遷移する新着通知の件数には購読型動画を含めず、統合ビューで実際に確認できる未読件数と一致させる。
- custom Video Providerのfunction実行はforeground Activityに依存せず、Video-owned runtimeからbackground refreshでも実行する。
- Podcastの定刻生成は番組ごとに次のローカル日時を再計算するone-shot work chainとして実行し、通常の生成失敗後も翌日のscheduleを維持する。
- Podcast画面から開始した新規生成と作り直しも同じPodcast-owned Workerへ即時登録し、画面を離れたり端末をロックしたりしてもUI lifetimeに依存せず生成を継続する。生成中はforeground通知へチャプター進捗を表示する。
- Podcastのクラウドチャプター生成では、一時的な通信・rate limit・server failureとしてretryableに分類された失敗をchapter内で有限回自動再試行し、再試行を使い切った場合だけ既存の失敗checkpointへ確定する。
- Podcast生成taskは記事単位の完了数をAIタスクキューへ投影し、生成中・再生成中または途中失敗したtaskでは全記事数と未完了記事数を確認できる。
- ユーザーが開始したAudioの継続再生はWorkManagerではなくforeground `MediaSessionService`を利用し、durable taskへ変換しない。
- `:app` はbackground business logicの恒久的な所有場所とせず、compositionとframework wiringに限定する。
- Android framework が直接生成し constructor injection を差し込めない entry point だけ、監査済みProvider contractからapplication-level dependencyを取得できる。
- WorkManager Worker は Provider lookup の例外に含めず、owning feature の `WorkerFactory` から constructor injection し、`:app` の WorkerFactory composition が application graph へ接続する。
- frameworkが永続化した旧class nameとの互換が必要な場合だけ、ADRで根拠を持つcompatibility shimを残す。


## 形式モデル

自動バックアップのschedule / triggerと、feature-owned request cleanupの安全条件を次のmodelで検査する。その他のbackground queue / AI lifecycleは、[`../formal-modeling.md`](../formal-modeling.md) の優先順で段階的に形式化する。

- [Quint: `backup_schedule.qnt`](../../spec-models/quint/backup_schedule.qnt) — scheduleの状態遷移と再予約safety。
- [Quint: `background_request_cleanup.qnt`](../../spec-models/quint/background_request_cleanup.qnt) — active workに対応するdurable request inputを期限だけで削除しないことを検査する。
- [Alloy: `backup_schedule.als`](../../spec-models/alloy/backup_schedule.als) — configured timeとscheduled work / automatic jobの構造制約。
