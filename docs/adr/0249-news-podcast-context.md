# ADR-0249: ニュースポッドキャストを独立Contextとして所有する

- Status: Accepted
- Date: 2026-09-09
- Refines: [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0235](0235-summary-audio-playback.md)

## Context

複数のRSS / Atomフィードから未読記事を集め、AIで日本語の音声用原稿を生成し、既存の音声再生機構で聞けるニュースポッドキャスト機能を追加する。

この機能には、単なるpresentationを越えて次のdurable stateとlifecycleが必要になる。

- 番組定義と対象feed群
- 生成providerと定刻実行設定
- 生成単位のepisode
- AI入力として確保した記事snapshot
- 生成済みscriptと失敗状態
- 番組単位で再利用を防ぐconsumed article state
- background schedule reconciliation

既存のRSS、Content、Summary、Audioへこれらを分散させると、記事取得、reading state、要約、音声再生のownershipが混ざる。逆にPodcast側がRSS / Content tableを直接読むとcross-context persistence ruleを破る。

また、AI生成失敗後に番組IDだけで再実行すると、既にconsumedとして予約した記事とは別の候補を取り込み、失敗したepisodeの再生成という意味を失う。フィードは更新によって古いentryを落とすため、1回の最大記事数を超えた未読候補をライブfeedだけに残すと、後続episode生成前に本文を失う可能性もある。

## Decision

### Podcastを独立Contextとする

`:feature:podcast:{domain,data,ui}` を追加し、Podcast Contextが次を所有する。

- program
- episode
- episode article snapshot
- generated script / generation status
- per-program consumed article state
- schedule configurationとschedule adapter

RSS / Content / Audio / AI runtimeは既存ownerを維持し、Podcastの共同ownerにしない。

### 入力は予約時点の未読記事かつfeed-carried contentに限定する

PodcastはContent-owned `ArticleRepository` から対象feedに属する未読記事のidentityとmetadataを取得し、RSS-owned `RssFeedContentReader` からRSS / Atomフィードに含まれるtitle / bodyを取得する。

両者をfeed IDとsource identityで照合し、予約時点で未読である記事だけを候補にする。Content側のfeed指定未読queryは全体表示用の件数上限を経由しない。

`RssFeedContentReader` はリンク先ページを取得しない。Podcast生成のAI入力にもリンク先ページ、一般知識、別の外部取得結果を追加しない。

Podcast DataからContent / RSS tableを直接readしない。フィード再取得にはapplication compositionが所有するprocess-wide `HttpClient`を注入し、Podcast用に別のHTTP transport / connection poolを構築しない。

### AI推論前にepisode snapshotをatomicに予約する

生成開始時に、番組内で未消費の候補を最大記事数ごとのchunkへ分け、現在取得できた候補を単一transactionで次へ保存する。

- episode row
- episode article snapshot
- consumed article row

最初のchunkは `GENERATING`、後続chunkは `QUEUED` とする。これにより1回の最大記事数を超えた候補も、その時点のfeed本文をPodcast-owned snapshotとして確保でき、次回生成前にfeedからentryが消えても失わない。

次回生成では新しいfeed候補を取得する前に、既存の中断済み `GENERATING` episodeを優先し、その次に最古の `QUEUED` episodeを `GENERATING` へpromoteする。新しい候補を予約するのは既存pending episodeがない場合だけとする。

AI推論はtransaction完了後に行う。これにより生成中にRSS内容やreading stateが変わっても、episodeの入力を固定できる。

### 失敗は同じsnapshotで明示的に再生成し、中断は次回実行で再開する

AI推論が通常のエラーで失敗した場合はepisodeを `FAILED` として保存し、article snapshotとconsumed stateを保持する。

再生成はepisode IDを指定して既存snapshotを利用する。定刻実行は生成失敗そのものをschedulerの恒久失敗にはせず、FAILED episodeを保存した上で翌日の定刻実行を維持する。FAILED episodeの再生成は明示操作で行い、新しい記事へ置き換えない。

一方、Worker停止やcoroutine cancellation、プロセス終了など、生成処理が完了結果を確定できない中断ではepisodeをFAILEDへ変更せずGENERATINGのまま保持する。次回の番組生成はその保存済みarticle snapshotから生成を再開する。

同一process内では番組ID単位で生成UseCaseを直列化し、手動生成と定刻生成が同じepisodeを同時に処理しない。process終了時にはこのin-memory guardは失われるが、durableなGENERATING / QUEUED状態から次回実行を復元する。

### 番組編集ではepisode historyを保持する

`podcast_programs` の設定編集はupdate-in-placeとする。foreign key cascadeを伴うreplace保存は使わない。

番組自体の削除時だけ、その番組が所有するepisode、article snapshot、consumed stateをcascade削除する。

### local / cloud generationは既存AI capabilityとbackground policyを利用する

Podcastは番組設定に従い既存のtext inference capabilityを選択する。provider-specificな第二のAI runtimeを追加せず、自動fallbackもしない。

Local生成は既存の `LocalAiBackgroundTaskGate` をNORMAL priorityで取得してから推論し、他featureの重い端末内AI処理と同時実行しない。定刻WorkerはLocal / Cloudそれぞれの共有background pause設定を確認し、対象providerが全体停止中なら記事snapshotを予約せずその定刻回を終了する。手動生成は明示的なユーザー操作なのでこのbackground pauseによって禁止しない。

生成promptは入力記事だけを根拠とし、日本語の音声合成向け連続文章を要求する。推論前に選択modelの `promptBudgetChars` と `maxInputChars` の小さい方へ入力を制限し、巨大なfeed本文によってmodel contextを恒久的に超過しないようにする。

### 音声再生はAudio Contextへ委譲する

Podcastは生成済みscriptを既存 `AudioPlaybackController` に渡す。

TTS、media session、playback queue、生成音声cacheはAudio ownershipを維持する。Podcastは音声ファイルや再生位置をdurable stateとして保存しない。

Podcastの生成・再生によってContentのread / unread stateを変更しない。

### database versionを33へ進める

version 32 -> 33で次のPodcast-owned tableを追加する。

- `podcast_programs`
- `podcast_episodes`
- `podcast_episode_articles`
- `podcast_consumed_articles`

既存Contextからのdata migrationは不要であり、Podcast stateは空から開始する。foreign-table migration exceptionも追加しない。

Podcast-owned durable stateは通常のdatabase snapshot backupに含める。Audioの再生成可能なTTS cacheはbackup対象外のままとする。

### scheduleはPodcastが所有し、application compositionはwiringとreconciliationだけを行う

Podcast Dataのscheduler adapterが番組ごとの1日1回の定刻実行を登録・解除する。

固定24時間のperiodic intervalは使わず、1回実行のworkが終了するたびに次のローカル日時を再計算して後続workを登録する。これによりtimezone / daylight-savingの時計変更後も、設定されたローカル時刻を次回目標として維持する。

番組作成・編集時は既存scheduleを置き換えて新しい目標時刻から再計算する。application起動時は保存済みprogramのworkを `KEEP` でensureし、Worker自身をstartup reconciliationでcancelしない。

Database snapshotの復元を含むdurable persistence変更後は、application compositionが前回確認したprogram集合と現在のprogram集合を比較し、追加programをensure、設定変更をsync、削除programをcancelする。Podcastの新しいdurable stateやbackup専用scheduler APIは追加しない。

Worker生成はPodcast-owned `WorkerFactory` をapplication-level WorkerFactory compositionへ登録し、Workerからservice locatorを利用しない。

## Consequences

- Podcastのdurable lifecycleをRSS、Content、Audioから分離できる。
- 生成episodeがどの記事内容を根拠にしたかを固定でき、通常失敗後の明示再生成と中断後の自動再開のどちらでも同じ入力を維持できる。
- 最大記事数を超えた候補も予約時点でsnapshotされるため、feed rotationで後続episodeの本文を失わない。
- 同じ番組では一度予約した記事を重複利用しない。
- RSS記事を既読にしても予約済み・過去episodeや生成済みscriptは保持される。
- background AIが一時停止中の定刻回ではepisode snapshotやconsumed stateを作らないため、対象記事は未消費のまま後続の生成候補に残る。
- feed本文に十分な情報がない記事は生成材料として弱くなるが、リンク先取得を追加しないためdata egressと取得境界は明確になる。
- program / episode / consumed stateが新しいdurable source of truthとなるためdatabase versionとbackup baselineが33へ進む。
- scheduleはOSのbackground scheduler特性に従うため、指定時刻は厳密なalarmではなく日次実行の目標時刻として扱う。

## Verification

- 同じ番組でconsumed済み記事が次回episodeへ入らないことをunit testする。
- 最大記事数を超えた候補がfeedから消えてもQUEUED snapshotから後続episodeを生成できることをunit testする。
- 生成失敗後のretryが同じarticle snapshotを使うことをunit testする。
- 生成中断後の次回実行がGENERATING episodeの同じarticle snapshotを再開することをunit testする。
- 同一番組の重複生成を同一process内で開始しないことをunit testする。
- 対象feedの未読queryが全体500件上限に影響されないことをrepository testする。
- Local / Cloudのbackground pauseが対象providerの定刻生成だけを抑止することをunit testする。
- promptがfeed title / bodyだけを根拠にする制約とmodel入力上限を守ることをunit testする。
- scheduleの次回ローカル時刻delay計算をunit testする。
- Podcast table ownershipと新しいdependencyをarchitecture verificationで検証する。
- version 32 -> 33 database migration、unit test、lint、R8、public repository verificationを最終CIで確認する。
