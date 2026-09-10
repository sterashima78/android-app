# Podcast architecture

## Responsibility

Podcast Contextはニュースポッドキャストの番組、入力source、episode生成queue、生成時の記事snapshot、番組単位のentry消費状態、生成原稿、定刻設定を所有する。

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
2. 中断済みまたは予約済みepisodeがあれば、そのsnapshotを優先して再開する。
3. 番組の `sourceIds` に対応するPodcast-owned source定義を取得し、欠落があれば設定エラーとする。
4. source URLからRSS / Atom entryのtitle / body / entry URL metadataを取得する。
5. program-scoped `podcast_consumed_articles` に存在しないentryだけを候補にする。
6. 最大記事数単位でepisodeへ分割し、候補の本文とentry URL metadataを `podcast_episode_articles` へsnapshotする。
7. 最初のepisodeを生成し、残りはqueueへ保持する。
8. 生成promptは入力記事1件を1チャプターとして同じ順序で扱い、各チャプター先頭へ `[[CHAPTER:n]]` markerを要求する。entry URLはpromptへ含めない。
9. 生成済み原稿はPodcast側でチャプターへ分割してAudio Contextへ再生委譲する。

process終了やcoroutine cancellationによって `GENERATING` のまま残ったepisodeは、次のapplication background runtime起動時にも同じepisode IDと保存済みsnapshotから自動再開する。起動時再開は `QUEUED` をpromoteせず、新しいfeed候補も予約しない。定刻scheduleのreconciliationとは別のapplication-scope coroutineで実行し、新しいschedulerやdurable queueは追加しない。

Podcast生成や再生はreader側の記事を既読化しない。

## Playback projection

新しく生成した原稿でchapter marker数、番号、記事数が一致する場合、Podcastは1記事分の原稿segmentを1つの `AudioQueueItem` へ投影する。queue itemには記事title、source title、読み上げ本文を渡し、marker自体は読み上げない。

Podcast UIは `:feature:audio:ui` の共通再生controlsを再利用する。このため再生 / 一時停止、前後チャプター移動、15秒戻し、30秒送り、再生速度変更、停止、background playback、通知・lock screen等のMediaSession操作はAudio Contextの既存挙動を利用する。

再生詳細画面にはepisodeの記事をチャプター順で表示し、現在のAudio queue itemに対応するチャプターを強調する。保存済み `article_url` がある記事にはリンクを開く操作を表示する。再生詳細画面を閉じてもAudio playbackは停止しない。

markerがない既存episode、marker数や番号が不正な生成結果は誤った記事対応を作らず、episode原稿全体を1つのAudio queue itemとして従来どおり再生する。この場合、記事一覧は関連記事として表示し、再生位置との対応を主張しない。

## Durable state

Podcast-owned tablesは次のとおり。

- `podcast_sources`: Podcast専用RSS / Atom source catalog
- `podcast_programs`: 番組定義、source ID集合、生成provider、schedule、最大記事数
- `podcast_episodes`: episode lifecycleと生成原稿
- `podcast_episode_articles`: 生成に予約したentry metadata、feed body、entry URL snapshot
- `podcast_consumed_articles`: 番組ごとの消費済みentry identity

これらは通常のdatabase snapshot backup対象である。Audioが生成する再生成可能な音声cacheは対象外とする。

## Compatibility migration

application database version 33ではPodcast番組がRSS readerのfeed IDを直接保持していた。version 34 migrationは次を一度だけ実行する。

- `podcast_sources` を作成する。
- 旧 `podcast_programs.feed_ids` に含まれるIDについて、RSS-owned `feeds` から表示名（custom titleがあればそれを優先）とfeed URLをコピーする。
- Content articleが残っている消費済みentryは `feed_id` / `identity_key` からPodcast-owned stable identityへ変換する。
- `feed_ids` columnを `source_ids` へrenameする。

このmigrationだけは `feeds` と `articles` のforeign-table readをarchitecture allowlistで許可する。runtime readではない。version 33 upgrade baseline退役時にmigrationとallowlist entryを削除する。

application database version 35では `podcast_episode_articles.article_url` をnullable columnとして追加する。version 34までに生成済みのepisodeはURLを持たないまま保持し、再取得で補完しない。新規episodeだけ生成予約時のfeed entry URLをsnapshotする。

## Invariants

- 番組には1つ以上のPodcast sourceが必要である。
- 番組が参照するPodcast sourceは保存時点で存在しなければならない。
- source URLはPodcast Contextのdurable stateとして保持する。
- 同一番組では一度予約したstable entry identityを新規episodeへ再利用しない。
- episodeへ予約したfeed bodyとentry URL metadataは生成時点でsnapshotし、後続のfeed rotationに依存しない。
- entry URLはAI生成promptへ含めず、linked page本文も取得しない。
- 中断された `GENERATING` episodeの再開では同じsnapshotを利用し、新しいfeed候補を予約しない。
- chapter markerと記事snapshotの対応を検証できない場合は全文再生へfallbackし、誤った記事対応を作らない。
- generation providerの自動fallbackを行わない。
- Podcast runtimeはreaderの購読・既読stateへ依存しない。

## Related decisions

- `docs/adr/0249-news-podcast-context.md`
- `docs/adr/0250-podcast-owned-feed-sources.md`
- `docs/adr/0253-resume-interrupted-podcast-generation.md`
- `docs/adr/0254-podcast-playback-chapters.md`
