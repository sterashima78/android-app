# ADR-0254: ニュースポッドキャストの再生を記事チャプターへ対応付ける

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0249](0249-news-podcast-context.md), [ADR-0250](0250-podcast-owned-feed-sources.md)

## Context

ニュースポッドキャストの生成済みエピソードは、これまでエピソード全体の原稿を1つの音声再生itemとしてAudio Contextへ渡していた。そのため、再生中にどの記事を扱っているかを確認したり、対応するfeed記事を開いたり、記事単位で前後へ移動したりできない。

一方、Podcastは生成時点の記事snapshotを既に所有している。再生時にlive feedを再取得して記事URLや対応関係を解決すると、feed rotationによって過去episodeのentryが失われた場合に再現できず、生成時点のsnapshotをsource of truthとする既存設計とも整合しない。

Audio Contextは呼び出し元が渡した読み上げ原稿をqueue itemとして再生でき、再生速度、seek、前後item移動、MediaSessionを所有している。Podcast専用の第二のplayerを追加する必要はない。

## Decision

### feedに含まれる記事URLをepisode snapshotへ保存する

RSS / Atomのfeed parserが既に取得しているentry URLを `RssFeedContentEntry` のmetadataとして公開し、Podcastは生成予約時に `podcast_episode_articles.article_url` へ保存する。

このURLはfeed documentに含まれるmetadataであり、Podcast生成のAI入力には含めない。リンク先ページ本文をHTTP取得する既存禁止事項も変更しない。

既存episodeにはURLが保存されていないため `article_url` はnullableとする。database versionを34から35へ進め、既存行を保持したままnullable columnを追加する。

### 生成原稿に記事境界markerを要求する

Podcast生成promptは、入力記事1件につき1チャプターを出力し、各チャプター先頭へ元記事番号を持つ `[[CHAPTER:n]]` markerを出力する契約とする。従来どおり重要度の高い話題から並べてよく、記事番号は生成順ではなく元記事との対応キーとして扱う。

markerは読み上げ対象ではなくPodcastが再生queueへ投影するための構造metadataとして扱う。Podcastは生成済みscriptをmarkerで分割し、markerの記事番号から対応する記事snapshotを解決する。

marker数、記事番号の重複・欠落、記事数が一致しない原稿は誤った記事対応を作らず、エピソード全体を1つの読み上げitemとして扱う。既存episodeもこのfallbackによって従来どおり再生できる。

### Podcastは1チャプターを1 Audio queue itemとして投影する

構造化できたepisodeでは、Podcast UIが各チャプターを `AudioQueueItem` へ変換して既存 `AudioPlaybackController` に渡す。各itemは記事title、source、チャプター本文を持つ。

これによりAudioの既存「前 / 次」はチャプター移動として機能し、15秒戻し、30秒送り、再生速度変更、停止、background playback、MediaSessionはAudio Contextの既存実装を再利用する。

Podcast UIは `:feature:audio:ui` の共通再生controlsを利用し、Podcast固有の再生詳細画面では生成されたチャプター順の記事一覧、現在チャプター、feed記事を開く操作を表示する。記事URLを持たない既存snapshotでは記事リンク操作を表示しない。

再生詳細画面を閉じる操作は再生停止とは分離し、background playbackを維持する。明示的な「終了」はAudio playbackを停止する。

## Consequences

- 生成時点の記事URLと記事snapshotを保持でき、live feedの更新に依存せず記事リンクを表示できる。
- 原稿の重要度順構成を維持したまま、各チャプターを正しい元記事へ対応付けられる。
- 新しく生成された構造化episodeは記事単位で前後移動でき、RSS読み上げと同じAudio controlsと速度変更を利用できる。
- Podcast固有のplayer runtimeやMediaSessionを追加せず、Audio ownershipを維持できる。
- AIがmarker contractを満たさない場合や既存episodeでは、誤った対応を表示せず従来の全文再生へfallbackする。
- article URLはAI推論材料にはならず、リンク先本文を取得しないdata boundaryを維持する。
- `podcast_episode_articles` にnullable metadata columnが増えるためdatabase versionは35となる。

## Verification

- feed readerがfeed entryのURLをPodcast adapterへ渡すことをtestする。
- Podcast生成promptへ記事URLが含まれないことをtestする。
- 重要度順に並び替えられたchapter marker列を元記事番号から正しいsnapshotへ対応付けることをunit testする。
- marker重複・欠落またはmarkerなしのscriptが全文再生へfallbackすることをunit testする。
- version 34から35へのmigrationで既存episode article rowを保持し、`article_url` がnullableで追加されることをtestする。
- Podcast UIが共通Audio controlsを利用し、チャプターごとの記事リンクを表示できることをbuild / reviewで確認する。
