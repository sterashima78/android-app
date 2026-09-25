# Podcast architecture

## Responsibility

Podcast Contextはニュースポッドキャストの番組、入力source、episode生成queue、生成時の記事・ニュースクラスタsnapshot、ニュース分類の診断状態、ニュースチャプター単位の生成checkpoint、番組単位のentry消費状態と除外済みentry identity、自然文の除外条件、生成原稿、定刻設定を所有する。

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

Podcast側では取得したentryを `sourceId:feedEntryIdentity` 形式のstable identityへ変換する。Unicode表記、空白、大文字小文字を正規化したtitleが一致するentryはRSS boundaryで確実な重複として1件へ縮約する。その後の同一ニュース判定はPodcast domainの `PodcastNewsClusterer` が担当する。

`PodcastNewsClusterer` は候補のtitle、source title、published timeだけから「同じ具体的な出来事」を表すentry index群と分類状態を返す。同じ主体を扱うだけの別イベントは統合せず、曖昧な場合は別clusterとする。production compositionでは番組で選択された生成providerと同じAI推論基盤を利用するが、原稿生成の `PodcastScriptGenerator` とはdomain capabilityを分離する。分類結果は通常テキストでは受け取らず、provider-neutral `AiStructuredTextInference` の `submit_podcast_news_clusters(group_ids)` tool callを利用する。group ID配列は候補記事と同じ順序・同じ要素数を要求し、同じIDを持つindexを同一clusterへまとめる。tool schemaに加えて全候補がちょうど1回含まれることをfeature側で検証し、不正時は1回だけ再生成する。分類推論または最終validationに失敗した場合は1記事1clusterへfallbackする。coroutine cancellationだけはfallbackせず伝播する。分類状態は正常終了、推論失敗fallback、分類出力不正fallback、分類不要を区別し、raw promptやraw response、raw tool argumentsは診断状態へ保持しない。

`PodcastNewsExcluder` は番組の除外条件が空でない場合だけ、候補のtitle、source title、feed bodyを使って各entryをinclude / excludeへ分類する。番組で選択された生成providerと同じprovider-neutral `AiStructuredTextInference` を利用し、`submit_podcast_news_exclusion(decisions)` のtool argumentsだけを結果として採用する。候補本文はuntrusted dataとして扱い、本文内の命令へ従わないことをsystem instructionで固定する。推論、tool call、decode、validationに失敗した場合は全候補をincludeへfallbackし、記事を欠落させない。除外されたentry identityは `podcast_excluded_articles` へ保存し、後続生成で再判定しない。

Podcast runtimeは候補選択のために `FeedRepository`、`ArticleRepository`、`feeds` table、`articles` tableを参照しない。

## Background execution boundary

Podcast画面からの新規生成と作り直しは `PodcastGenerationController` へ依頼し、UIの `viewModelScope` では長時間生成を実行しない。controllerはPodcast-owned WorkManagerへ即時workを登録し、定刻生成と同じ `PodcastGenerationWorker` / application-scope dependency graphを利用する。手動workと定刻workは番組ごとの別unique identityで重複登録を抑え、同時に実行可能になった場合は既存のprogram単位generation guardで直列化する。

`PodcastGenerationWorker` は生成処理開始時にforeground workへ昇格し、episodeが予約された後は `PodcastGenerationProgress` を使ってREADY checkpoint数 / 全chapter数をongoing notificationへ反映する。notificationとWorkManager progressは観測用projectionであり、新しいdurable source of truthではない。

旧versionが永続化したWorker inputとの互換のため、operation指定がない `PodcastGenerationWorker` は従来の定刻生成として解釈する。定刻生成だけがLocal / Cloudのbackground pause設定を尊重し、利用者が画面から明示的に開始した生成は従来の手動操作と同様に実行する。

Podcast UIはPodcast repositoryのpersistence change projectionを購読し、Workerが更新したepisode / chapter stateを再読込する。UIはWorker stateそのものをPodcastのbusiness stateとして保持しない。

## Generation lifecycle

1. `GeneratePodcastEpisodeUseCase` が番組を取得する。
2. 中断済みまたは予約済みepisodeがあれば、そのsnapshotとchapter checkpointを優先して再開する。この経路では再クラスタリングしない。
3. 番組の `sourceIds` に対応するPodcast-owned source定義を取得し、欠落があれば設定エラーとする。
4. source URLからRSS / Atom entryのtitle / body / published time / entry URL metadataを取得する。
5. program-scoped `podcast_consumed_articles` と `podcast_excluded_articles` のどちらにも存在しないentryだけを候補にする。
6. 番組の除外条件が空でなければ `PodcastNewsExcluder` で候補を判定する。除外判定に成功してexcludeになったentry identityは番組単位でdurable stateへ記録する。判定失敗時は全候補を残す。
7. 除外後の候補が0件なら新しいepisodeを作らず終了する。
8. 完全一致重複除外後の今回候補を `PodcastNewsClusterer` へ渡し、同一ニュースclusterと分類状態を確定する。過去episodeとの意味的重複判定は行わない。
9. 最大ニュース数単位でclusterをepisodeへ分割し、cluster内の全entry metadata、feed body、entry URL、`chapter_position` を `podcast_episode_articles` へ、分類状態を `podcast_episodes.clustering_status` へsnapshotする。同じclusterのrowsは同じ `chapter_position` を持ち、全entryを消費済みにする。
10. 最初のepisodeを生成し、残りはqueueへ保持する。
11. episode内のclusterを独立したchapter checkpointとして処理する。clusterの `READY` checkpointは再利用し、未完了clusterだけを1回のAI推論単位として扱う。クラウド生成では未完了chapterを小さい固定上限で有界並列実行し、retryableな一時失敗は同じchapter内で2秒・5秒・15秒を基準とするjitter付きbackoffにより最大3回再試行する。再試行を使い切るまではchapterを `FAILED` へ確定しない。ローカル生成では端末内推論runtimeの単一実行性を維持して順次処理する。
12. chapter生成promptではcluster内の全記事のtitle / feed bodyだけを根拠に、重複内容を繰り返さず、矛盾しない追加情報を統合した音声ニュース向けの短い日本語見出しと本文を生成する。entry URLはpromptへ含めない。見出しを `[[TITLE:...]]` markerとして `chapter_script` 内へ保持する。同じclusterの全rowへ同一のcheckpoint、script、errorを保存する。
13. 全cluster checkpointが `READY` になったら、Podcast Contextがchapter順に `chapter_script` を連結する。`[[CHAPTER:n]]` markerと番組の冒頭・締めはアプリ側で決定的に付与し、episode全体を対象とする追加AI推論は行わない。
14. 完成したepisode原稿をAudio Contextへ再生委譲する。

process終了やcoroutine cancellationによって初回生成の `GENERATING` または再生成の `regeneration_status=RUNNING` が残ったepisodeは、次のapplication background runtime起動時にも同じepisode ID、保存済み記事・cluster snapshot、checkpointから自動再開する。起動時再開は無関係な `QUEUED` をpromoteせず、新しいfeed候補も予約しない。定刻scheduleのreconciliationとは別のapplication-scope coroutineで実行し、新しいschedulerやdurable queueは追加しない。

クラウドの有界並列生成でも各chapterは生成成功直後に `READY` と原稿をdurable checkpointへ保存する。中断時点で `READY` のchapterは再開時に再推論せず、`PENDING` / `GENERATING` / `FAILED` の未完了chapterだけを続行する。並列完了順は最終原稿の順序へ影響せず、全checkpoint完成後に `chapter_position` 順で組み立てる。

クラウドchapterではprovider adapterが正規化した `retryable` failureだけをPodcast Dataのretry policyで再試行する。HTTP statusやprovider固有messageの解析はPodcastへ持ち込まない。retry exhaustionまたはnon-retryable errorになった時点で通常生成エラーとしてchapterを `FAILED` へ保存する。他の独立chapterはキャンセルせず、同じ並列batch内で成功可能なchapterを最後まで処理してcheckpointする。外部からのcoroutine cancellationだけはretryせず並列batch全体へ伝播させる。

`retry()` と中断再開の内部経路は同じ記事・cluster snapshotを使い、`READY` checkpointを再利用して失敗・未完了chapterだけを続行する。

利用者が `READY` または `FAILED` episodeを明示的に「現在の条件で作り直す」場合は別経路とする。保存済みarticle snapshotを `PodcastFeedEntry` へ戻し、現在の番組の除外条件を再適用した後、`PodcastNewsClusterer` でcluster境界を再計算する。全候補が除外された場合は既存episodeを変更せず終了する。候補が残る場合は同じepisode IDの `podcast_episode_articles` を新しいarticle / cluster snapshotへtransactionで置き換え、episode scriptを消去して `GENERATING` へ戻す。以降は通常生成と同じcheckpoint生成を行い、失敗時は `FAILED` とする。

旧versionで開始済みの `regeneration_status=RUNNING` episodeは互換のため従来どおり同じsnapshotから再開する。新しい明示的な作り直しでは `regeneration_status` を新規作成しない。`regeneration_status=RUNNING` のepisodeでは重複操作、archive、deleteを引き続き拒否する。

Podcast生成や再生はreader側の記事を既読化しない。

## AI task queue projection

Podcastのdurable generation stateはPodcast Contextに残し、`:feature:ai-task-queue` にはPodcast domainの `PodcastGenerationTaskReader` 経由でepisode単位の観測情報だけを投影する。AIタスクキュー側はPodcast-owned tableを直接参照しない。

- `QUEUED` episodeは待機中として表示する。
- 初回生成中または再生成中は実行中として表示する。
- 初回生成失敗または再生成失敗は失敗として表示する。
- 進捗はdistinct `chapter_position` の `READY` 数 / 全chapter数で表示する。
- 実行中は「チャプターを生成中」と表示する。
- providerはローカル / クラウドを表示する。
- 通常の `READY`、`ARCHIVED`、`DELETED` は表示しない。

この統合は観測専用とし、Podcast jobの停止、キャンセル、再開、再生成の操作意味論はPodcast Context側に残す。

## Episode organization lifecycle

`READY` episodeは、再生成中でなければ通常一覧から `ARCHIVED` へ移動でき、`ARCHIVED` episodeは `READY` へ復元できる。再生成失敗後にarchiveした場合は失敗したattempt状態を閉じ、既存の生成原稿と記事・cluster snapshotを保持する。generation queueは `QUEUED` / `GENERATING` と明示的な再生成状態だけを対象にするため、アーカイブ状態は生成予約や中断再開へ影響しない。

`READY` / `ARCHIVED` / `FAILED` episodeは、再生成中でなければ削除できる。削除はepisode rowの物理削除ではなく `DELETED` tombstoneへの不可逆な遷移とする。削除時にはtitle、script、error message、再生成状態、分類診断状態、`podcast_episode_articles` snapshotを消去するが、`podcast_consumed_articles` とその参照先として必要なepisode IDは保持する。これにより削除済みepisodeに由来するentryも未消費へ戻らず、新規episodeへ再利用されない。

通常一覧は `ARCHIVED` / `DELETED` を除外し、アーカイブ一覧は `ARCHIVED` だけを表示する。`DELETED` はどの一覧にも表示せず、再生、再生成、復元の対象にしない。

## Playback projection

新しく生成するepisodeでは1つのnews clusterを1つのchapter markerへ対応付け、1ニュース分の原稿segmentを1つの `AudioQueueItem` へ投影する。`[[TITLE:...]]` markerがあるsegmentではそのmarkerから日本語見出しを抽出し、marker自体を読み上げ本文から除外する。1件目は抽出した日本語見出しをqueue titleとして使い、2件目以降は短い音声キュー「続いて。」を先頭へ付けた日本語見出しをqueue titleとして渡す。

`PodcastPlaybackChapter` は互換用の代表articleに加えてcluster内の全articlesを保持する。再生詳細画面にはchapter順でnews clusterを表示し、複数記事を含むchapterでは関連記事件数と各source articleを表示する。保存済み `article_url` がある各記事には個別にリンクを開く操作を表示する。再生詳細画面では `clustering_status` と保存済みarticle / `chapter_position` から、分類成功・fallback理由・分類省略・旧episodeの記録なしと、記事数からニュース数への集約結果を表示する。再生詳細画面を閉じてもAudio playbackは停止しない。

`[[TITLE:...]]` markerがない旧形式chapterは保存済み記事titleをqueue titleとして使う。見出しmarkerの有無は新しいdurable columnを追加せず、既存 `chapter_script` / episode script内の生成形式で表現する。

Podcast UIは `:feature:audio:ui` の共通再生controlsを再利用する。このため再生 / 一時停止、前後チャプター移動、15秒戻し、30秒送り、再生速度変更、停止、background playback、通知・lock screen等のMediaSession操作はAudio Contextの既存挙動を利用する。

markerがない既存episode、marker数や番号が不正な既存原稿は誤った記事対応を作らず、episode原稿全体を1つのAudio queue itemとして従来どおり再生する。この場合、記事一覧は関連記事として表示し、再生位置との対応を主張しない。

## Durable state

Podcast-owned tablesは次のとおり。

- `podcast_sources`: Podcast専用RSS / Atom source catalog
- `podcast_programs`: 番組定義、source ID集合、生成provider、schedule、最大ニュース数（DB columnは互換上 `max_articles` を継続利用）
- `podcast_episodes`: episode generation / organization lifecycleと生成原稿。再生成中断状態、分類診断状態 `clustering_status`、`ARCHIVED` / `DELETED` を含む
- `podcast_episode_articles`: 生成に予約したentry metadata、feed body、entry URL、`chapter_position` snapshotとchapter checkpoint。同一 `chapter_position` のrowsが1ニュースを構成する。`DELETED` episodeでは消去する
- `podcast_consumed_articles`: 番組ごとの消費済みentry identity。cluster内の全entryを記録し、episode削除後も再利用防止のため保持する

これらは通常のdatabase snapshot backup対象である。Audioが生成する再生成可能な音声cacheは対象外とする。

## Compatibility baseline

application database versionは39で、更新互換性baselineはversion 38とする。fresh version 39 schemaはPodcast-owned source、番組の除外条件、除外済みentry identity、記事URL、chapter checkpoint、news cluster position、clustering diagnosticsを直接含む。

version 38 -> 39 migrationは `podcast_programs.exclusion_prompt` と `podcast_excluded_articles` を追加する。version 33〜37からversion 38へ到達する過去migrationはADR-0264で退役済みであり、current runtimeは旧RSS / Content tableやpre-38 Podcast columnをcompatibility inputとして参照しない。

## Invariants

- 番組には1つ以上のPodcast sourceが必要である。
- 番組が参照するPodcast sourceは保存時点で存在しなければならない。
- source URLはPodcast Contextのdurable stateとして保持する。
- 同一番組では一度予約または除外したstable entry identityを新規episodeへ再利用せず、除外条件変更時にも過去の除外済みentryを自動再評価しない。
- 除外判定が失敗した場合は候補を捨てず全件includeへfallbackする。
- episodeへ予約したfeed body、entry URL、cluster境界、分類診断状態は生成時点でsnapshotし、後続のfeed rotationや再生成時の再分類に依存しない。
- entry URLはAI生成promptへ含めず、linked page本文も取得しない。
- 意味的な同一ニュース判定は現在の未消費候補内だけで行い、過去episodeを意味比較して続報を抑止しない。
- clustererが正常な完全partitionを返せない場合は1記事1clusterへfallbackし、fallback原因の分類状態を保存する。
- 分類診断のためにraw prompt、raw AI response、例外本文を新たに永続化しない。
- 1回のchapter生成AI推論は1つのnews clusterだけを生成材料とし、そのcluster外の記事本文を混在させない。
- 同一clusterの全article rowは同じcheckpoint、chapter script、errorを共有する。
- checkpointが `READY` のchapterはretry / interrupted recoveryで再生成しない。
- retryableなクラウドchapter failureはbounded retryを使い切るまで `FAILED` checkpointへ確定せず、non-retryable failureとretry exhaustionだけを既存failure lifecycleへ渡す。
- foreground notificationとWorkManager progressはdurable generation stateのprojectionであり、Podcastのsource of truthにしない。
- UIから開始する生成と作り直しはPodcast-owned WorkManagerへ登録し、ViewModel coroutine lifetimeへ生成処理を結び付けない。
- クラウドchapter生成の並列化はepisode内の未完了checkpointに限定し、episode単位のbackground jobとprogram単位の重複実行guardを維持する。
- ローカルchapter生成は同一episode内で並列実行しない。
- episode scriptは全checkpoint完成後にchapter順で決定的に組み立てる。
- 日本語見出しはchapterごとのAI生成結果から再生時に抽出し、新しい独立したdurable source of truthを追加しない。
- 日本語見出しを抽出できない旧形式chapterは保存済み記事titleへfallbackする。
- 中断された生成は同じ記事・cluster snapshotとcheckpointを利用し、新しいfeed候補を予約しない。
- 明示的な作り直しはfeedを再取得せず保存済み記事へ現在の除外条件とクラスタリングを再適用し、開始後は旧scriptを保持しない。
- `regeneration_status=RUNNING` のepisodeへ重複再生成、archive、deleteを行わない。
- `ARCHIVED` episodeは再生可能な原稿とsnapshotを保持し、復元時は同じepisode IDで `READY` へ戻る。
- `DELETED` episodeは原稿と記事snapshotを保持しないが、clusterに含まれていた全entryの消費済みidentityは保持する。
- episode削除によって一度予約したentryを未消費へ戻さない。
- chapter markerとcluster snapshotの対応を検証できない既存原稿は全文再生へfallbackし、誤った記事対応を作らない。
- generation providerの自動fallbackを行わない。
- Podcast runtimeはreaderの購読・既読stateへ依存しない。

## Related decisions

- `docs/adr/0249-news-podcast-context.md`
- `docs/adr/0250-podcast-owned-feed-sources.md`
- `docs/adr/0253-resume-interrupted-podcast-generation.md`
- `docs/adr/0255-podcast-playback-chapters.md`
- `docs/adr/0256-podcast-episode-archive-delete.md`
- `docs/adr/0257-podcast-chapter-generation-jobs.md`
- `docs/adr/0259-podcast-news-clustering.md`
- `docs/adr/0264-database-v38-compatibility-baseline.md`
- `docs/adr/0265-podcast-cloud-chapter-parallelism.md`
- `docs/adr/0267-podcast-news-exclusion-filter.md`
- `docs/adr/0268-podcast-rebuild-regeneration.md`
- `docs/adr/0269-podcast-durable-foreground-generation.md`
