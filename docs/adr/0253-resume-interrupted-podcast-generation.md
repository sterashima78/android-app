# ADR-0253: 中断されたニュースポッドキャスト生成を起動時に再開する

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0249](0249-news-podcast-context.md)

## Context

ニュースポッドキャストは、生成開始前にepisodeと記事snapshotをdurable stateへ予約し、生成中はepisodeを `GENERATING` として保持する。

通常の生成失敗は `FAILED` へ遷移するが、process終了やcoroutine cancellationでは結果を確定できないため `GENERATING` を保持する。ADR-0249では次回の番組生成時にこのsnapshotを再利用する設計とした。

しかし、手動生成中にprocessが終了した場合は次回の定刻実行や明示的な生成操作まで再開契機がなく、画面上は実際の処理が存在しないまま「原稿を生成中」と表示され続ける。

## Decision

application background runtimeの起動時にPodcast Contextのprogramを走査し、`GENERATING` episodeを持つprogramだけを既存の `GeneratePodcastEpisodeUseCase` で再開する。

再開では既存episode IDと保存済みarticle snapshotを利用し、新しいfeed候補を予約しない。episodeが既に別の実行で処理中なら既存のprogram単位in-memory guardに従い、重複推論を開始しない。

起動時再開は新しいscheduler、queue、durable stateを追加しない。番組の定刻schedule reconciliationとは独立したapplication-scope coroutineで実行し、再開処理が長時間かかってもschedule復元をブロックしない。

## Consequences

- process終了後に残った `GENERATING` episodeが、次回の明示操作を待たずに同じsnapshotから再開される。
- 新しい記事を誤って予約せず、中断前と同じepisodeを完了または失敗状態へ収束できる。
- Podcastのdurable ownership、Audioへの再生委譲、AI provider選択、schedule ownershipは変更しない。
- 起動時に中断episodeが存在する場合、その生成処理が自動的に再開する。

## Verification

- `GENERATING` episodeだけを起動時再開対象として検出することをunit testする。
- `QUEUED` / `READY` / `FAILED` だけのprogramでは起動時再開を開始しないことをunit testする。
- 再開時に同じepisode IDとarticle snapshotを利用する既存testを維持する。
