---
type: module
title: Podcast Data：保存・Worker・AI境界
description: Podcastの永続状態とWorker、RSS境界、AI生成実装を説明する。
tags: [podcast, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-7d324167f63ff25c4618b848
    resource: repo://feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt
  - id: openwiki-source-14f9fe925e8af6cc70cef342
    resource: repo://feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastRepositoryPersistenceTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Podcast Data：保存・Worker・AI境界

`:feature:podcast:data` は次の責務を持ちます。

## 保存と外部能力の接続

SqlitePodcastRepositoryがsource、番組、episode、記事snapshot、章checkpoint、消費・除外済みentryを保存します。RSS Domainのfeed-content readerを通して番組指定のsourceを読み、RSS readerの購読テーブルをPodcast生成状態として使いません。AI分類や原稿生成は既存のinference capabilityへ接続し、Podcast Domainに対して生成・分類契約を実装します。

章checkpointとepisodeの状態は永続化されるので、Workerの実行状態とは分けて調査します。episodeのアーカイブや削除で消費済みentryを再利用可能へ戻さないこと、存在しないsourceを参照する番組を保存しないことはRepositoryのpersistence testが確認します。状態変更はchanges projectionを通してUIの再読込へ伝わります。

## Workerと失敗

PodcastGenerationWorkerは手動・定刻・作り直しを判別し、同時生成ならretry、キャンセルなら伝播、通常の生成失敗ならworkを終了します。episode予約後の失敗は生成処理がepisodeや章のFAILED状態への保存を試みますが、保存自体が失敗する場合もあり、予約前のfeed取得などの失敗では新しいepisodeがない場合があります。定刻workは次回予定の登録も担います。現コードではforegroundへ昇格するのは手動生成と作り直しで、定刻生成は対象外です。[設計文書](../../../docs/architecture/podcast.md)の「処理開始時にforeground workへ昇格」という記述はこの区別を明記していないため、挙動変更時はコードと正本の両方を確認してください。operation未指定の旧workは定刻として解釈し、永続Worker inputとの互換を維持します。

## 調査と変更の入口

[SqlitePodcastRepository.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/SqlitePodcastRepository.kt)、[PodcastGenerationWorker.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt)、[RssPodcastFeedContentSource.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSource.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastRepositoryPersistenceTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastRepositoryPersistenceTest.kt)、[PodcastGenerationWorkerTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorkerTest.kt)、[RssPodcastFeedContentSourceTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSourceTest.kt) です。

[podcast domain](podcast-domain.md)、[podcast ui](podcast-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
