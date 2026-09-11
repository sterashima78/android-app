# Podcast architecture

## Responsibility

Podcast Contextはニュースポッドキャストの番組、入力source、episode生成queue、生成時の記事snapshot、記事単位の生成checkpoint、番組単位のentry消費状態、生成原稿、定刻設定を所有する。

RSS readerの購読状態やContentのread / unread stateはPodcastのsource of truthではない。Podcastで利用するRSS / Atom URLは `PodcastSource` としてPodcast Contextへ登録する。

## Source lifecycle

`podcast_sources` は次を保持する。

- Podcast source ID
- 表示名
- RSS / Atom feed URL

同じsourceは複数番組から参照できる。番組は `PodcastProgram.sourceIds` でsourceを選択する。

Podcast画面からsourceを追加できる。どの番組からも参照されていないsourceだけ削除できる。reader側のRSS購読を追加・削除・既読化してもPodcast sourceは変更しない。Podcast sourceを追加・削除してもreader側のRSS購読一覧は変更しない。

番組保存時はすべての `sourceIds` に対応するPodcast sourceが存在することを検証する。互換migrationで旧source metadataを復元できず参照だけが残った番組は、新規feed取得を行わず設定エラーとして扱う。編集画面では復元できないsource IDを選択状態へ持ち越さず、利用可能なsourceを選び直して保存できる。

## Feed-content boundary

Podcast data moduleはRSS domainの `RssFeedContentReader.latestEntriesFromSources` を利用する。このAPIは呼び出し側が渡したsource IDとfeed URLを直接読み、RSS購読登録を要求しない。

RSS data moduleは既存のHTTP transportとRSS / Atom parserを再利用する。entryのtitle、feed-carried body、published time、entry URLを返すが、リンク先ページは取得しない。entry URLは生成時の記事metadataとしてPodcastへ渡し、AI推論材料には含めない。

Podcast側では取得したentryを `sourceId:feedEntryIdentity` 形式のstable identityへ変換する。Podcast runtimeは候補選択のために `FeedRepository`、`ArticleRepository`、`feeds` table、`articles` tableを参照しない。

## Generation lifecycle

1. `GeneratePodcastEpisodeUseCase` が番組を取得する。
2. 中断済みまたは予約済みepisodeがあれば、そのsnapshotと記事checkpointを優先して再開する。
3. 番組の `sourceIds` に対応するPodcast-owned source定義を取得し、欠落があれば設定エラーとする。
4. source URLからRSS / Atom entryのtitle / body / entry URL metadataを取得する。
5. program-scoped `podcast_consumed_articles` に存在しないentryだけを候補にする。
6. 最大記事数単位でepisodeへ分割し、候補の本文とentry URL metadataを `podcast_episode_articles` へsnapshotする。新規記事checkpointは `PENDING` とする。
7. 最初のepisodeを生成し、残りはqueueへ保持する。
8. episode内の記事をsnapshot position順に処理する。`READY` checkpointは再利用し、未完了記事だけを1記事1回のAI推論へ渡す。
9. 記事生成promptでは元記事のtitle / feed bodyだけを根拠に、音声ニュース向けの短い日本語見出しと本文を生成するよう要求する。新形式では見出しを `[[TITLE:...]]` markerとして `chapter_script` 内へ保持する。記事生成開始前にcheckpointを `GENERATING`、成功時に `READY` と `chapter_script`、失敗時に `FAILED` と `chapter_error` へ更新する。promptへentry URLや他の記事本文を含めない。
10. 全記事checkpointが `READY` になったら、Podcast Contextがposition順に `chapter_script` を連結する。`[[CHAPTER:n]]` markerと番組の冒頭・締めはアプリ側で決定的に付与し、episode全体を対象とする追加AI推論は行わない。
11. 完成したepisode原稿をAudio Contextへ再生委譲する。

process終了やcoroutine cancellationによって初回生成の `GENERATING` または再生成の `regeneration_status=RUNNING` が残ったepisodeは、次のapplication background runtime起動時にも同じepisode ID、保存済みsnapshot、checkpointから自動再開する。起動時再開は無関係な `QUEUED` をpromoteせず、新しいfeed候補も予約しない。定刻scheduleのreconciliationとは別のapplication-scope coroutineで実行し、新しいschedulerやdurable queueは追加しない。

`FAILED` episodeの再試行は同じsnapshotを使い、`READY` checkpointを再利用して失敗・未完了chapterだけを続行する。

`READY` episodeの明示的な再生成では、既存のepisode scriptと `READY` 状態を再生可能な正本として保持しながら `regeneration_status=RUNNING` と記事checkpointを使って新しい原稿を構築する。最初の再生成開始時だけ記事checkpointを新しいattemptの `PENDING` へ戻す。途中で失敗した場合は `regeneration_status=FAILED` とし、既存scriptを保持する。再実行では完成済みcheckpointを再利用し、全chapter完成時だけ同じepisode IDのscriptを置き換えて `regeneration_status` を消去する。

`regeneration_status=RUNNING` のepisodeでは、同じepisodeへの重複再生成、archive、deleteを拒否する。これらの操作とcheckpoint更新を同時に進めない。`regeneration_status=FAILED` は再試行可能なattemptとして保持するが、archiveまたはdeleteを選んだ場合はattempt状態を閉じる。archiveでは既存scriptを保持したまま `regeneration_status` と再生成errorを消去する。

Podcast生成や再生はreader側の記事を既読化しない。

## AI task queue projection

Podcastのdurable generation stateはPodcast Contextに残し、`:feature:ai-task-queue` にはPodcast domainの `PodcastGenerationTaskReader` 経由でepisode単位の観測情報だけを投影する。AIタスクキュー側はPodcast-owned tableを直接参照しない。

- `QUEUED` episodeは待機中として表示する。
- 初回生成中または再生成中は実行中として表示する。
- 初回生成失敗または再生成失敗は失敗として表示する。
- 進捗は `READY` checkpoint数 / 全記事数で表示する。
- 実行中は「チャプターを生成中」と表示する。
- providerはローカル / クラウドを表示する。
- 通常の `READY`、`ARCHIVED`、`DELETED` は表示しない。

この統合は観測専用とし、Podcast jobの停止、キャンセル、再開、再生成の操作意味論はPodcast Context側に残す。

## Episode organization lifecycle

`READY` episodeは、再生成中でなければ通常一覧から `ARCHIVED` へ移動でき、`ARCHIVED` episodeは `READY` へ復元できる。再生成失敗後にarchiveした場合は失敗したattempt状態を閉じ、既存の生成原稿と記事snapshotを保持する。アーカイブ中も生成原稿と記事snapshotを保持するため再生可能である。generation queueは `QUEUED` / `GENERATING` と明示的な再生成状態だけを対象にするため、アーカイブ状態は生成予約や中断再開へ影響しない。

`READY` / `ARCHIVED` / `FAILED` episodeは、再生成中でなければ削除できる。削除はepisode rowの物理削除ではなく `DELETED` tombstoneへの不可逆な遷移とする。削除時にはtitle、script、error message、再生成状態、`podcast_episode_articles` snapshotを消去するが、`podcast_consumed_articles` とその参照先として必要なepisode IDは保持する。これにより削除済みepisodeに由来するentryも未消費へ戻らず、新規episodeへ再利用されない。

通常一覧は `ARCHIVED` / `DELETED` を除外し、アーカイブ一覧は `ARCHIVED` だけを表示する。`DELETED` はどの一覧にも表示せず、再生、再生成、復元の対象にしない。

## Playback projection

新しく生成するepisodeではPodcast Contextが記事positionと同じ番号のchapter markerを付与するため、1記事分の原稿segmentを1つの `AudioQueueItem` へ投影する。`[[TITLE:...]]` markerがあるsegmentではそのmarkerから日本語見出しを抽出し、marker自体を読み上げ本文から除外する。1件目は抽出した日本語見出しをqueue titleとして使い、2件目以降は短い音声キュー「続いて。」を先頭へ付けた日本語見出しをqueue titleとして渡す。source titleと本文は従来どおり渡す。

`[[TITLE:...]]` markerがない旧形式chapterは保存済み記事titleをqueue titleとして使う。見出しmarkerの有無は新しいdurable columnを追加せず、既存 `chapter_script` / episode script内の生成形式で表現する。

Podcast UIは `:feature:audio:ui` の共通再生controlsを再利用する。このため再生 / 一時停止、前後チャプター移動、15秒戻し、30秒送り、再生速度変更、停止、background playback、通知・lock screen等のMediaSession操作はAudio Contextの既存挙動を利用する。

再生詳細画面にはepisodeの記事をチャプター順で表示し、現在のAudio queue itemに対応するチャプターを強調する。保存済み `article_url` がある記事にはリンクを開く操作を表示する。再生詳細画面を閉じてもAudio playbackは停止しない。

markerがない既存episode、marker数や番号が不正な既存原稿は誤った記事対応を作らず、episode原稿全体を1つのAudio queue itemとして従来どおり再生する。この場合、記事一覧は関連記事として表示し、再生位置との対応を主張しない。

## Durable state

Podcast-owned tablesは次のとおり。

- `podcast_sources`: Podcast専用RSS / Atom source catalog
- `podcast_programs`: 番組定義、source ID集合、生成provider、schedule、最大記事数
- `podcast_episodes`: episode generation / organization lifecycleと生成原稿。再生成中断状態、`ARCHIVED` / `DELETED` を含む
- `podcast_episode_articles`: 生成に予約したentry metadata、feed body、entry URL snapshotと記事単位の生成checkpoint。`DELETED` episodeでは消去する
- `podcast_consumed_articles`: 番組ごとの消費済みentry identity。episode削除後も再利用防止のため保持する

これらは通常のdatabase snapshot backup対象である。Audioが生成する再生成可能な音声cacheは対象外とする。

## Compatibility migration

application database version 33ではPodcast番組がRSS readerのfeed IDを直接保持していた。version 34 migrationは次を一度だけ実行する。

- `podcast_sources` を作成する。
- 旧 `podcast_programs.feed_ids` に含まれるIDについて、RSS-owned `feeds` から表示名（custom titleがあればそれを優先）とfeed URLをコピーする。
- Content articleが残っている消費済みentryは `feed_id` / `identity_key` からPodcast-owned stable identityへ変換する。
- `feed_ids` columnを `source_ids` へrenameする。

このmigrationだけは `feeds` と `articles` のforeign-table readをarchitecture allowlistで許可する。runtime readではない。version 33 upgrade baseline退役時にmigrationとallowlist entryを削除する。

application database version 35では `podcast_episode_articles.article_url` をnullable columnとして追加する。version 34までに生成済みのepisodeはURLを持たないまま保持し、再取得で補完しない。新規episodeだけ生成予約時のfeed entry URLをsnapshotする。

Episode archive/deleteでは既存 `podcast_episodes.status` のTEXT値だけを拡張するためschema migrationは追加しない。

application database version 36では `podcast_episode_articles` に `chapter_status` / `chapter_script` / `chapter_error`、`podcast_episodes` に `regeneration_status` を追加する。既存 `READY` episodeの記事checkpointは `READY` として移行し、既存episode scriptを正本として保持する。その他の既存記事checkpointは `PENDING` とする。

## Invariants

- 番組には1つ以上のPodcast sourceが必要である。
- 番組が参照するPodcast sourceは保存時点で存在しなければならない。
- source URLはPodcast Contextのdurable stateとして保持する。
- 同一番組では一度予約したstable entry identityを新規episodeへ再利用しない。
- episodeへ予約したfeed bodyとentry URL metadataは生成時点でsnapshotし、後続のfeed rotationに依存しない。
- entry URLはAI生成promptへ含めず、linked page本文も取得しない。
- 1回のAI推論は1記事だけを生成材料とし、別記事の本文を混在させない。
- checkpointが `READY` の記事はretry / interrupted recoveryで再生成しない。
- episode scriptは全checkpoint完成後にposition順で決定的に組み立てる。
- 日本語見出しは記事ごとのAI生成結果から再生時に抽出し、新しい独立したdurable source of truthを追加しない。
- 日本語見出しを抽出できない旧形式chapterは保存済み記事titleへfallbackする。
- 中断された初回生成または再生成は同じsnapshotとcheckpointを利用し、新しいfeed候補を予約しない。
- `READY` episodeの再生成失敗では既存scriptを失わない。
- `regeneration_status=RUNNING` のepisodeへ重複再生成、archive、deleteを行わない。
- `ARCHIVED` episodeは再生可能な原稿とsnapshotを保持し、復元時は同じepisode IDで `READY` へ戻る。
- `DELETED` episodeは原稿と記事snapshotを保持しないが、消費済みentry identityは保持する。
- episode削除によって一度予約したentryを未消費へ戻さない。
- chapter markerと記事snapshotの対応を検証できない既存原稿は全文再生へfallbackし、誤った記事対応を作らない。
- generation providerの自動fallbackを行わない。
- Podcast runtimeはreaderの購読・既読stateへ依存しない。

## Related decisions

- `docs/adr/0249-news-podcast-context.md`
- `docs/adr/0250-podcast-owned-feed-sources.md`
- `docs/adr/0253-resume-interrupted-podcast-generation.md`
- `docs/adr/0255-podcast-playback-chapters.md`
- `docs/adr/0256-podcast-episode-archive-delete.md`
- `docs/adr/0257-podcast-chapter-generation-jobs.md`
