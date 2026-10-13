# ADR-0285: 現行配布版の互換baselineへ収束し、所有責務を分割する

## Status

Accepted

## Context

現行配布版が使用するapplication database versionは41であり、Podcastの38→41 migration、旧再生成のRUNNING状態、operation未指定の旧generation workは現行実装では新規生成されない。一度限りの互換処理を保持し続けると、runtimeの状態分岐と検証範囲が増え続ける。

同時に、Podcast Domain、Podcast UI、Podcast Data、ローカル推論、およびSMB書誌正規化の一部ファイルが複数の責務を持っている。module所有境界を変更せずに責務を分離する。

## Decision

- 最新配布版のapplication database version 41を次版への更新互換性baselineとし、Podcastのtarget 39、40、41 migrationを退役する。次版はversion 42とし、41→42 migrationで使われなくなった再生成状態columnを削除する。fresh schemaはversion 42を直接作成する。version 40以下からの直接更新はサポートしない。
- 過去方式が作成した `regeneration_status=RUNNING` とoperation未指定WorkManager入力の互換分岐を廃止する。現行生成経路では `GENERATING` episodeと必須operation入力を使う。現行の失敗状態やcheckpoint復旧は維持する。
- Domainの生成オーケストレーションと原稿整形、UIの番組編集、Dataのrow mapping / query helper、AI推論エンジン、書誌正規化のschemaと支援関数をそれぞれ同一moduleの別ファイルへ移す。新しい依存・module・永続状態は追加しない。
- 履歴的な設計判断は旧ADRへ残し、現行の互換契約だけをarchitecture文書とテストで更新する。

## Compatibility / rollback

現在のversion 41のデータベースは41→42 migrationによってepisodeの再生成状態columnを除去し、その他の状態と記事checkpointを保持して読み込む。既存のversion 38〜40のデータベース、旧RUNNING再生成、operation未指定の保留workを直接持ち込む場合は互換対象外となる。これらの状態が残る端末はversion 41の配布版で更新・処理を完了してから次版へ更新する。

## Verification

- fresh schemaはversion 42を直接作成し、41→42 migrationで既存episodeのstatus・scriptを維持しつつ再生成状態columnを削除できる。
- 旧migration／worker入力／再生成状態へのsource referencesと専用fixtureを削除する。
- 分割前後で公開API、モデル選択、原稿生成、Podcast永続状態、書誌正規化の振る舞いを変えない。
- 対象unit / repository / architecture tests、lint、R8を実行する。

## Relations

- Refines: [ADR-0264](0264-database-v38-compatibility-baseline.md), [ADR-0268](0268-podcast-rebuild-regeneration.md), [ADR-0269](0269-podcast-durable-foreground-generation.md)
