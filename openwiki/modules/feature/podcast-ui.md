---
type: module
title: Podcast UI：番組管理と生成・章再生
description: 番組/source管理、生成受付と永続状態の観測、共有Audio再生を説明する。
tags: [podcast, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3440a22e1164279b40d695c4
    resource: repo://feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt
  - id: openwiki-source-14bfa64f0b1520e3d26e05fc
    resource: repo://feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Podcast UI：番組管理と生成・章再生

`:feature:podcast:ui` は次の責務を持ちます。

## 画面の入口と生成受付

PodcastRouteWithPlaybackはlifecycleに合わせてPodcastの画面状態とAudio再生状態を購読し、通常画面と再生dialogを構成します。PodcastViewModelはsourceと番組、選択中番組、episode一覧、アーカイブ表示、操作中のepisodeを持ちます。公開Repositoryとgeneration/schedule controller、共有Audio controllerに依存し、SQLiteやWorker具象には依存しません。

手動生成はgenerationControllerへ依頼して受付メッセージを表示します。長時間のAI生成をViewModel内で実行せず、バックグラウンドが保存した状態を再読込して進捗を示します。番組削除ではscheduleを停止してからRepositoryを更新します。source管理とRSS readerの購読管理は別経路なので、番組sourceの編集でreader側の購読を変える前提を置かないでください。

## 章再生と失敗の境界

生成済み原稿はplaybackChaptersで章へ分け、AudioQueueItemの読み上げ本文として共通Audio controllerへ渡します。章選択は現在キュー内の章IDを探し、そのindex差分だけ前後移動します。対象が現在キューにない場合や現在位置が不正なら移動しません。episodeの削除時には該当音声の再生停止も確認します。章選択のunit testは距離計算を検証しますが、TTSの生成や通知再生までを証明するものではありません。変更時は番組切替、生成失敗、dialogを閉じた後の再生とepisode削除を併せて確認します。

## 調査と変更の入口

[PodcastViewModel.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastViewModel.kt)、[PodcastRouteWithPlayback.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastChapterJumpTest.kt](../../../feature/podcast/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastChapterJumpTest.kt)、[PodcastDisplayTitleTest.kt](../../../feature/podcast/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastDisplayTitleTest.kt) です。

[podcast domain](podcast-domain.md)、[podcast data](podcast-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
