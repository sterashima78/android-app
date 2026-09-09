# ADR-0250: ニュースポッドキャストのfeed sourceをPodcast Contextで所有する

- Status: Accepted
- Date: 2026-09-09
- Refines: [ADR-0249](0249-news-podcast-context.md)

## Context

ADR-0249ではPodcast番組が既存RSS購読のfeed IDを選び、Contentの未読状態とRSSのfeed本文を組み合わせてepisode候補を作る設計を採用した。

しかしニュースポッドキャストにとってRSS / Atomは「購読して読む対象」ではなく「番組生成の入力source」である。RSS reader側で購読を追加・削除したり既読状態を変更するライフサイクルと、Podcast番組で継続利用したいsourceのライフサイクルは一致しない場合がある。

既存RSS購読をPodcast設定のsource of truthにすると、RSS reader側の整理操作がPodcastの生成可否へ影響し、Podcast Contextが所有するとした番組lifecycleと矛盾する。

## Decision

### Podcast Contextがfeed sourceを所有する

Podcast Contextに `PodcastSource` を追加し、source ID、表示名、RSS / Atom feed URLをdurable stateとして保存する。

番組はRSS Contextのfeed IDではなくPodcast-owned source ID集合を参照する。sourceはPodcast画面から追加・編集・削除でき、RSS購読一覧には自動追加しない。RSS購読の追加・削除・既読化もPodcast sourceへ反映しない。

同じPodcast sourceは複数番組から再利用できる。

### feed取得・entry消費はPodcastのライフサイクルで完結する

Podcast生成は選択sourceのfeedを直接取得し、feedに含まれるtitle / bodyだけを候補とする。Contentのread / unread stateは候補選択に利用しない。

Podcastはsource IDとfeed entry identityから安定したarticle identityを作り、既存のprogram-scoped consumed stateで一度予約したentryを再利用しない。これによりRSS reader側の既読状態と独立して「Podcastとして未消費か」を判断する。

リンク先ページを取得しない制約、episode snapshot、queue / retry semantics、AI provider選択、Audio委譲はADR-0249を維持する。

### RSSのfeed形式処理は再利用するが、RSS購読stateには依存しない

RSS / AtomのHTTP取得・解析ロジックは既存capabilityを拡張して再利用する。PodcastからRSS-owned tableやContent tableをreadしない。

application compositionはprocess-wide HTTP transportを再利用し、Podcast専用のconnection poolを追加しない。

### version 33から34で既存Podcast設定を移行する

application database versionを34へ進め、Podcast-owned `podcast_sources` tableを追加し、`podcast_programs.feed_ids` を `source_ids` へrenameする。

version 33で保存済みのprogramは旧feed IDをそのままsource IDとして維持する。migration時に旧RSS `feeds` tableから対応するtitle / feed URLを一度だけコピーして `podcast_sources` を初期化する。

これは既存Podcast設定を失わずにownershipを移すためのone-time compatibility migrationであり、runtime codeはmigration後にRSS `feeds` tableを参照しない。このmigration pathだけをforeign-table allowlistへ明示し、version 33 upgrade baseline退役時に削除する。

旧RSS feedがmigration前に既に削除されておりURLを復元できない場合、そのsourceは自動復元できない。これは旧設計がPodcast側にURLを保持していなかったことによる制約であり、ユーザーはPodcast画面からsourceを再追加できる。

## Consequences

- RSS購読の管理とPodcast source管理を独立できる。
- RSS側でfeedを削除・既読化してもPodcastのsource定義とconsumed stateは変化しない。
- Podcast生成候補は「RSS readerで未読か」ではなく「このPodcast番組で未消費か」で決まる。
- Podcast Contextにsource定義というdurable stateが追加される。
- version 33 -> 34だけ、ownership移行のためRSS `feeds` tableを読むmigration exceptionが必要になる。
- RSS / Atom parserを複製せず、既存format capabilityを再利用できる。

## Verification

- Podcast sourceのcreate / edit / deleteと複数番組からの参照をrepository testする。
- source URLから取得したentryがContent read stateなしでepisode候補になることをtestする。
- 同じsource entryが同一番組でconsumed後に再生成されないことをtestする。
- RSS購読一覧が空でもPodcast-owned sourceから生成できることをtestする。
- version 33 -> 34 migrationで既存programのfeed IDがsource IDとして維持され、対応するfeed URLがPodcast-owned sourceへコピーされることをtestする。
- Podcast runtimeからArticleRepository / FeedRepository / RSS table direct read依存が消えることをarchitecture verificationする。
