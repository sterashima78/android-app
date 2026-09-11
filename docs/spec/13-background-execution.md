# 13. Background execution

- durableなbackground処理にはWorkManagerを利用する。
- feature固有Worker、scheduler/controller、queue state interpretationは原則としてowning featureのdata/runtimeが所有する。
- application-scope の周期更新では通常feed、Reddit、購読型動画Provider、メール等の更新を個別に分離して実行する。購読型動画はVideo-owned provider refresh capabilityを利用し、1件のsubscription失敗で他sourceの更新を中断しない。
- 統合ビューへ遷移する新着通知の件数には購読型動画を含めず、統合ビューで実際に確認できる未読件数と一致させる。
- custom Video Providerのfunction実行はforeground Activityに依存せず、Video-owned runtimeからbackground refreshでも実行する。
- Podcastの定刻生成は番組ごとに次のローカル日時を再計算するone-shot work chainとして実行し、通常の生成失敗後も翌日のscheduleを維持する。
- ユーザーが開始したAudioの継続再生はWorkManagerではなくforeground `MediaSessionService`を利用し、durable taskへ変換しない。
- `:app` はbackground business logicの恒久的な所有場所とせず、compositionとframework wiringに限定する。
- Android framework が直接生成し constructor injection を差し込めない entry point だけ、監査済みProvider contractからapplication-level dependencyを取得できる。
- WorkManager Worker は Provider lookup の例外に含めず、owning feature の `WorkerFactory` から constructor injection し、`:app` の WorkerFactory composition が application graph へ接続する。
- frameworkが永続化した旧class nameとの互換が必要な場合だけ、ADRで根拠を持つcompatibility shimを残す。
