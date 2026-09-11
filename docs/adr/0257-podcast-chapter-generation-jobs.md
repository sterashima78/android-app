# ADR-0257: ニュースポッドキャスト原稿を記事単位でcheckpoint生成する

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0249](0249-news-podcast-context.md), [ADR-0253](0253-resume-interrupted-podcast-generation.md), [ADR-0255](0255-podcast-playback-chapters.md)
- Related: [ADR-0104](0104-ai-task-queue-feature-ownership.md), [ADR-0256](0256-podcast-episode-archive-delete.md)

## Context

ニュースポッドキャストは、1 episode に含まれる全記事を1回のAI推論へ渡して原稿全体を生成している。記事数が多いepisodeでは入力が大きくなり、1回の推論時間も長くなるため、通信タイムアウトや一時的な推論失敗によってepisode全体を再生成する必要がある。

Podcastは生成開始前に各記事のtitle、feed-carried body、entry URL metadataを `podcast_episode_articles` へsnapshotしている。また再生時には1記事を1チャプターとして扱う契約が既にある。この境界を利用すれば、1記事分の原稿を独立したcheckpointとして保存し、完了済み記事を再推論せずに生成を継続できる。

AIタスクキューは複数featureのdurable background taskを共通画面へ投影する。Podcastの生成も同じ画面から進捗と失敗を確認できる必要があるが、Podcastのdurable stateや実行意味論をAIタスクキューへ移してはならない。

## Decision

### 1. 1記事を1回の原稿生成単位とする

1 episode の生成は保存済み記事snapshotをposition順に処理し、1記事ごとにAI推論を1回実行する。各推論はその記事だけを根拠に、読み上げ用の短いチャプター本文を生成する。

全記事を同時にモデルへ渡して重要度順へ再配置する処理は行わない。episode内の再生順はsnapshotのposition順とする。番組全体を再度AIへ渡す統合処理も行わない。

### 2. 記事snapshotへ生成checkpointを保持する

`podcast_episode_articles` に次を追加する。

- `chapter_status`: `PENDING` / `GENERATING` / `READY` / `FAILED`
- `chapter_script`: 生成済みチャプター本文
- `chapter_error`: 直近の失敗理由

記事生成開始前に `GENERATING`、成功時に `READY` と本文、失敗時に `FAILED` と失敗理由を保存する。後続のretry / process再開では `READY` の記事を再生成せず、未完了記事だけを処理する。

既存の `READY` episodeはmigration時に記事checkpointを `READY` とみなし、既存のepisode scriptをそのまま再生可能な正本として保持する。既存の未完了episodeは記事checkpointを `PENDING` として新方式で生成できるようにする。

### 3. episode scriptは決定的に組み立てる

全記事checkpointが `READY` になった時点で、Podcast Contextが各 `chapter_script` をposition順に連結する。各チャプター先頭の `[[CHAPTER:n]]` markerはアプリ側で付与し、AIには生成させない。

冒頭と締めはepisode組み立て時にアプリ側で付加する。これにより記事単位生成の結果だけから再現可能なepisode scriptを作り、全記事を対象とする追加推論を不要にする。

### 4. 生成済みepisodeの再生成でも既存scriptを保持する

`READY` episodeの明示的な再生成では、既存のepisode scriptを再生可能なまま保持しつつ、記事checkpointを新しい生成attemptとして処理する。

再生成中断を識別するため、`podcast_episodes` に `regeneration_status` を追加する。値は `RUNNING` / `FAILED` または未設定とする。

- 新しい再生成開始時は記事checkpointを `PENDING` に戻し、`regeneration_status=RUNNING` とする。
- 再生成に失敗した場合は `regeneration_status=FAILED` とし、既存episode scriptと `READY` 状態を保持する。
- 再実行では成功済みchapterを再利用し、失敗・未完了chapterだけを続行する。
- 全chapter完成時だけepisode scriptを置き換え、`regeneration_status` を消去する。
- process終了時に `RUNNING` が残っていれば、起動時復旧で同じsnapshotとcheckpointから再開する。
- `RUNNING` の間は同じepisodeの重複再生成、archive、deleteを拒否し、実行中checkpointとepisode lifecycleを競合させない。
- `FAILED` の再生成attemptはretry対象として保持するが、archiveまたはdeleteを選択した場合はattempt状態を閉じる。archiveでは既存scriptを保持し、再生成errorと `regeneration_status` を消去する。

### 5. 既存のepisode単位background executionを維持する

記事ごとに新しいschedulerや独立したsystem jobを作らない。既存のPodcast生成worker / application-scope recoveryが1 episodeを担当し、その内部で記事checkpointを順次処理する。

これによりsystem job数を記事数に比例して増やさず、Podcast Contextが所有するqueueとscheduleを維持する。

### 6. AIタスクキューにはepisode単位のjobとして投影する

`:feature:ai-task-queue:data` にPodcast domain contractを利用するadapterを追加し、Podcastのdurable stateからepisode単位の `AiTaskQueueItem` を作る。

- `QUEUED` episodeは待機中
- 初回生成中または再生成中は実行中
- 初回生成失敗または再生成失敗は失敗
- 完了済み、アーカイブ済み、削除済みで再生成attemptのないepisodeは一覧へ出さない
- progressは `READY` chapter数 / 全記事数
- 実行中のprogress labelは「チャプターを生成中」とする
- provider種別はローカル / クラウドとして表示する

AIタスクキューはPodcast tableを直接読まない。Podcast domain contractをadapterへ渡し、durable stateのownershipはPodcast Contextに残す。

今回の統合は観測を目的とし、AIタスクキューからPodcast jobを停止・キャンセル・再開する操作は追加しない。Podcast固有の生成操作は引き続きPodcast画面が所有する。

## Durable state / migration

application database versionを35から36へ進める。

- `podcast_episode_articles.chapter_status`
- `podcast_episode_articles.chapter_script`
- `podcast_episode_articles.chapter_error`
- `podcast_episodes.regeneration_status`

を追加する。新しいtableや新しいContext-owned source of truthは追加しない。

## Consequences

- 1回のAI推論入力が1記事へ限定され、長いepisodeでも単一リクエストの処理時間と失敗範囲を小さくできる。
- 途中まで完成したchapterをdurableに再利用でき、process終了や一時的な失敗後に未完了部分だけを続行できる。
- 記事数が多いepisodeでは推論request数が増えるが、各requestは小さく独立する。
- AIが番組全体を見て重要度順を決めることはなくなり、再生順はsnapshot positionに固定される。
- AIタスクキューから「生成中 17/50」のような進捗と失敗理由を確認できる。
- Podcastのdurable ownership、schedule ownership、episode archive/delete lifecycle、Audioへの再生委譲は変更しない。

## Verification

- 複数記事のepisodeで記事数と同じ回数だけAI推論し、各成功結果をcheckpointすることをunit testする。
- 途中失敗後のretryで完成済みchapterを再生成しないことをunit testする。
- process中断後に `GENERATING` chapterから同じepisodeを再開できることをunit testする。
- `READY` episode再生成の失敗時に既存scriptを保持し、次回は未完了chapterだけを続行することをunit testする。
- 再生成中は重複再生成、archive、deleteを拒否し、再生成失敗後のarchiveでattempt状態を閉じることをtestする。
- version 35から36へのmigrationで既存episodeを保持し、`READY` episodeのcheckpointを完了扱いにすることをtestする。
- AIタスクキューadapterが待機・実行・失敗とchapter進捗を正しく投影することをunit testする。
- archive/delete済みepisodeがAIタスクキューへ投影されないことをtestする。
- architecture verificationでAIタスクキューがPodcast concrete DataやPodcast-owned tableへ直接依存しないことを確認する。
