# ADR-0064: YouTubeとRedditのブックマーク追加では自動AI処理を行わない

- Status: Accepted
- Date: 2026-08-15
- Supersedes: ADR-0030 の Decision 1 における YouTube/Reddit の扱い

## Context

ADR-0030では、通常操作で新しくブックマークされた記事を入口に依存せず自動的に要約・タグ生成へ投入する方針を採用した。その結果、Redditの記事を保存した場合と、YouTube機能から動画をブックマークへ保存した場合にもSummary WorkManagerキューが生成される。

YouTubeとRedditは、それぞれ専用機能で未読処理や保存を行う情報源であり、ブックマーク保存時に汎用記事と同じ自動要約タスクを発生させる必要はない。一方、ユーザーが明示的に要約を要求する経路や、その他のRSS記事・共有ブックマークに対する自動AI処理は維持する。

Bookmark feature が Reddit、YouTube、Summary の各featureへ直接依存するとfeature境界が崩れるため、除外判定は引き続き `:app` のcomposition rootで行う必要がある。また `:app` がBookmarkのDBスキーマへ直接SQLアクセスすることも、feature-owned database schemaの方針に反する。

## Decision

### 1. YouTubeとRedditをブックマーク自動AI処理の対象外にする

新規ブックマーク成立後の自動AI処理について、次を対象外とする。

- YouTube動画URLのブックマーク
- Redditフィード由来の記事
- RedditスレッドURLとして識別できる共有ブックマーク

これらでは `SummaryRepository.requestBookmarkEnrichment` を呼ばず、要約タスクおよびその後続のAIタグ生成タスクを自動作成しない。

通常のRSS記事やその他の共有ブックマークは、ADR-0030の方針どおり自動AI処理の対象とする。

### 2. 明示的な要約操作は変更しない

本Decisionは「ブックマーク追加を起点とする自動処理」のみを制御する。ユーザーが画面から明示的に要約を要求する既存経路は変更せず、YouTube/Redditであっても別途対応している明示的な要約経路がある場合はその契約を維持する。

### 3. 情報源判定はcomposition rootで行う

`DefaultBookmarkRepository` の `onBookmarkAdded(articleId)` callback契約は変更しない。

Bookmark dataに、ブックマーク済み記事から `url` と `sourceFeedUrl` の汎用メタデータだけを取得するreaderを置く。このreaderはReddit、YouTube、Summaryの型や判定ロジックを知らない。

`:app` の `AppContainer` はcallback受信時にそのメタデータを取得し、Reddit domainとYouTube domainのURL判定を組み合わせて自動AI処理の対象可否を決定する。対象の場合だけSummary featureへ要求する。

これにより、Bookmark featureへの横断feature依存と、`:app` からBookmark所有テーブルへの直接SQLアクセスの双方を避ける。

### 4. 判定は純粋関数としてテストする

自動AI処理の対象判定をcomposition root付近の純粋関数へ分離し、少なくとも次をテストする。

- 通常RSS記事は対象
- Redditフィード由来の記事は対象外
- RedditスレッドURLの共有ブックマークは対象外
- YouTubeのwatch、短縮URL、Shorts URLは対象外

## Consequences

### Positive

- YouTube/Redditを保存しても不要な要約WorkManagerタスクが増えない。
- その他のブックマークに対する自動要約・タグ生成は維持される。
- Bookmark featureは情報源別featureやAI featureに依存しない。
- BookmarkのDB読み取り責務はBookmark data内に残る。
- 自動処理の対象判定を純粋関数として回帰テストできる。

### Negative

- YouTube/Redditのブックマークには自動要約・自動タグが付与されなくなる。
- 情報源判定はURL/sourceFeedUrlに依存するため、新しいYouTube URL形式やReddit URL形式を追加した場合はdomain側のURL判定を更新する必要がある。
- ADR-0030の「入口に依存せず全ブックマークを自動AI処理する」というDecision 1は、YouTube/Redditについては本ADRにより例外化される。

## Relationship to existing ADRs

- ADR-0030のブックマークAI enrichment設計を維持しつつ、Decision 1の対象範囲だけを本ADRで変更する。
- ADR-0003のcomposition rootとfeature境界の方針を維持する。
- ADR-0047のfeature-owned database schema方針に従い、`:app` へBookmarkテーブルのSQLを持ち込まない。
- ADR-0010で分離したYouTube featureの所有権を維持し、Bookmark側へYouTube固有ロジックを追加しない。
