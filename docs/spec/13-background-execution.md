# 13. Background execution

- durableなbackground処理にはWorkManagerを利用する。
- feature固有Worker、scheduler/controller、queue state interpretationは原則としてowning featureのdata/runtimeが所有する。
- application-scope の周期更新では通常feed、Reddit、購読型動画Provider、メール等の更新を個別に分離して実行する。購読型動画はVideo-owned provider refresh capabilityを利用し、1件のsubscription失敗で他sourceの更新を中断しない。
- 通常feedの更新完了時は、推薦条件が有効なら対象feedの未読記事から評価が必要な記事をRSS-owned queueへ追加する。推薦評価はRSS-owned Workerが記事単位で順次claimし、画面のlifecycleに依存せず処理する。
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
