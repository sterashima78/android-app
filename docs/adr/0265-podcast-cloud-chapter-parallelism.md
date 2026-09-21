# ADR-0265: ニュースポッドキャストのクラウドチャプター生成を有界並列化する

- Status: Accepted
- Date: 2026-09-21
- Amends: [ADR-0257](0257-podcast-chapter-generation-jobs.md)
- Refines: [ADR-0253](0253-resume-interrupted-podcast-generation.md), [ADR-0259](0259-podcast-news-clustering.md)

## Context

ニュースポッドキャストは、ニュースクラスタリング後の各チャプターを独立した生成checkpointとして保存している。各チャプターは1つのnews clusterだけを入力とし、成功した原稿は `READY` としてdurable stateへ保存される。中断・失敗後は同じ記事・cluster snapshotを使い、`READY` checkpointを再生成せず未完了chapterだけを続行できる。

ADR-0257では、このcheckpointをepisode単位background jobの内部で順次処理することを決定した。しかしクラウド生成では各chapterの推論requestが相互に独立しており、順次処理するとchapter数に比例して待ち時間が増える。また、複数chapterのうち一部が完成してからprocessが中断した場合に、完成済みcheckpointが確実に再利用されることを実装と回帰testで明示する必要がある。

ローカル生成はapplication scopeの端末内推論runtimeを利用するため、同一episode内で複数推論を同時実行する前提を追加しない。

## Decision

### 1. episode単位background jobを維持する

Podcast生成worker、application-scope recovery、program単位の重複実行guardは維持する。chapterごとのWorker、scheduler、durable queueは追加しない。

### 2. クラウド生成だけ未完了chapterを有界並列実行する

クラスタリングとepisode予約が完了した後、`READY` ではないchapterを独立した生成単位として扱う。

クラウド生成では、未完了chapterを小さい固定上限で並列実行する。並列数は実装上のruntime policyとし、新しいユーザー設定やdurable stateにはしない。

ローカル生成は従来どおりchapterを順次処理する。

### 3. 各chapterを完了直後にcheckpointする

各chapterは生成開始前に `GENERATING`、生成成功直後に `READY` と原稿を保存する。episode全体の完了を待ってからまとめて保存しない。

中断・失敗・再実行時は、保存済み `READY` chapterを再推論せず、`PENDING` / `GENERATING` / `FAILED` のchapterだけを続行する。再開時にfeedを再取得せず、cluster境界も再計算しない。

### 4. episode scriptは全chapter完了後に決定的に組み立てる

並列実行によってchapterの完了順が変わっても、episode scriptは全checkpointが `READY` になった後に `chapter_position` 順で組み立てる。生成完了順を再生順へ反映しない。

### 5. 失敗範囲をchapter checkpointへ閉じ込める

通常の生成エラーが発生したchapterは `FAILED` とし、そのepisode attemptを失敗へ遷移させる。同時実行中だった他chapterは、すでに `READY` へ保存済みなら再利用し、未完了なら次回実行で再開する。

coroutine cancellationではepisodeを通常失敗へ確定せず、完了済みcheckpointだけを保持したまま既存の中断復旧経路へ委ねる。

## System delta

- Changed capability: Podcast chapter generation orchestration
- Changed Context / ownership: none
- Changed durable data / schema: none
- Changed background execution: episode内のクラウドchapterを順次実行から有界並列実行へ変更
- Changed external communication: 送信内容・送信先は変更せず、同一episode内で複数の独立requestを同時実行可能にする
- New durable state: none
- New dependency: 新規外部ライブラリは追加せず、既存の coroutine libraryをPodcast domainから直接利用する
- New scheduler / queue: none
- Local inference concurrency: unchanged

## Consequences

### Positive

- chapter数が多いクラウドepisodeの生成待ち時間を短縮できる。
- 中断前に完成したchapterは次回実行で再利用され、完成済み原稿を最初から作り直さない。
- 既存のepisode / chapter checkpoint ownershipを維持したまま並列化できる。
- 並列完了順に関係なく、再生順と最終原稿は決定的なまま維持できる。

### Negative

- 同一episodeから複数のクラウド推論requestが同時に発生するため、逐次実行より短時間のrequest集中が増える。
- 1つのchapterが失敗した時点で、同時実行中のchapterには `READY`、未完了、失敗が混在し得る。ただし既存checkpointから再開可能である。
- runtime policyとして並列数の上限管理が必要になる。

## Verification

- 中断前に `READY` になったchapterが再開時に再生成されないことをunit testする。
- クラウド生成で設定した上限までchapterが同時実行されることをunit testする。
- ローカル生成が従来どおり順次処理される既存testを維持する。
- 全chapter完了後のepisode scriptがchapter順に決定的に組み立てられる既存testを維持する。
- architecture verificationと公開リポジトリ検査を実行する。
