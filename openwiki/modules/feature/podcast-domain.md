---
type: module
title: Podcast Domain：番組生成と章checkpoint
description: 番組独立source、ニュース生成、章checkpointと再開の契約を説明する。
tags: [podcast, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3391ea41cc8fdc84138466e7
    resource: repo://feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt
  - id: openwiki-source-cafc036dfaf531d238cf1a8d
    resource: repo://feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastInterruptedRecoveryTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Podcast Domain：番組生成と章checkpoint

`:feature:podcast:domain` は次の責務を持ちます。

## 責務と生成の流れ

番組、入力source、記事snapshot、ニュースcluster、episode、章checkpointと生成結果の契約を所有します。入力はPodcastFeedContentSource、候補消費判定はPodcastCandidateFilter、保存はPodcastRepositoryへ委譲し、AndroidのWorkerやSQLiteをDomainへ持ち込みません。Podcast sourceは番組が参照する独立したsourceで、reader画面の購読や既読をそのまま生成状態として使う設計ではありません。

GeneratePodcastEpisodeUseCaseは番組を取得し、予約済みepisodeがあれば先に処理します。新規生成ではsourceから候補を取得して利用可能なentryを絞り、除外条件とニュースclusterを適用した後でepisodeを予約します。候補が残らなければ新しいepisodeを作らず終了します。本文生成と分類の能力を分けているため、記事の採否や章境界と原稿生成の問題を別々に追えます。

## 再開と変更時の注意

中断episodeは保存済み記事snapshotと章checkpointを用いて再開します。新しいfeed取得を再開の前提にせず、作り直しでも保存済み記事を用います。処理は番組ごとのgeneration guardで同時実行を抑え、キャンセルは通常失敗へ変換しないよう扱います。章のモデル変更では記事と章の対応、既存原稿の保持、READY checkpointの再利用を確認してください。代表的な検証は中断復旧と再生成のDomain testであり、実際の予約・保存の原子性はData testも参照します。

## 調査と変更の入口

[Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt)、[PodcastGenerationController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastGenerationController.kt)、[PodcastScheduleController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastScheduleController.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastInterruptedRecoveryTest.kt](../../../feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastInterruptedRecoveryTest.kt)、[PodcastRegenerationTest.kt](../../../feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRegenerationTest.kt) です。

[podcast data](podcast-data.md)、[podcast ui](podcast-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
