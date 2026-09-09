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

RSS data moduleは既存のHTTP transportとRSS / Atom parserを再利用する。リンク先ページは取得しない。

Podcast側では取得したentryを `sourceId:feedEntryIdentity` 形式のstable identityへ変換する。Podcast runtimeは候補選択のために `FeedRepository`、`ArticleRepository`、`feeds` table、`articles` tableを参照しない。

## Generation lifecycle

1. `GeneratePodcastEpisodeUseCase` が番組を取得する。
2. 中断済みまたは予約済みepisodeがあれば、そのsnapshotを優先して再開する。
3. 番組の `sourceIds` に対応するPodcast-owned source定義を取得し、欠落があれば設定エラーとする。
4. source URLからRSS / Atom entryのtitle / bodyを取得する。
5. program-scoped `podcast_consumed_articles` に存在しないentryだけを候補にする。
6. 最大記事数単位でepisodeへ分割し、候補の本文を `podcast_episode_articles` へsnapshotする。
7. 最初のepisodeを生成し、残りはqueueへ保持する。
8. 生成済み原稿はAudio Contextへ再生委譲する。

Podcast生成や再生はreader側の記事を既読化しない。

## Durable state

Podcast-owned tablesは次のとおり。

- `podcast_sources`: Podcast専用RSS / Atom source catalog
- `podcast_programs`: 番組定義、source ID集合、生成provider、schedule、最大記事数
- `podcast_episodes`: episode lifecycleと生成原稿
- `podcast_episode_articles`: 生成に予約したentry metadata / feed body snapshot
- `podcast_consumed_articles`: 番組ごとの消費済みentry identity

これらは通常のdatabase snapshot backup対象である。Audioが生成する再生成可能な音声cacheは対象外とする。

## Compatibility migration

application database version 33ではPodcast番組がRSS readerのfeed IDを直接保持していた。version 34 migrationは次を一度だけ実行する。

- `podcast_sources` を作成する。
- 旧 `podcast_programs.feed_ids` に含まれるIDについて、RSS-owned `feeds` から表示名（custom titleがあればそれを優先）とfeed URLをコピーする。
- Content articleが残っている消費済みentryは `feed_id` / `identity_key` からPodcast-owned stable identityへ変換する。
- `feed_ids` columnを `source_ids` へrenameする。

このmigrationだけは `feeds` と `articles` のforeign-table readをarchitecture allowlistで許可する。runtime readではない。version 33 upgrade baseline退役時にmigrationとallowlist entryを削除する。

## Invariants

- 番組には1つ以上のPodcast sourceが必要である。
- 番組が参照するPodcast sourceは保存時点で存在しなければならない。
- source URLはPodcast Contextのdurable stateとして保持する。
- 同一番組では一度予約したstable entry identityを新規episodeへ再利用しない。
- episodeへ予約したfeed bodyは生成時点でsnapshotし、後続のfeed rotationに依存しない。
- linked page本文や一般知識をPodcast inputへ自動追加しない。
- generation providerの自動fallbackを行わない。
- Podcast runtimeはreaderの購読・既読stateへ依存しない。

## Related decisions

- `docs/adr/0249-news-podcast-context.md`
- `docs/adr/0250-podcast-owned-feed-sources.md`
