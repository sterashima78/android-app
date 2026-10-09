---
type: module
title: Podcast UI：番組管理と生成・章再生
description: 番組/source管理、生成受付と永続状態の観測、共有Audio再生を説明する。
tags:
  - podcast
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-3440a22e1164279b40d695c4
    resource: repo://feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt
  - id: openwiki-source-14bfa64f0b1520e3d26e05fc
    resource: repo://feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Podcast UI：番組管理と生成・章再生

`:feature:podcast:ui` は番組/source/episode管理と章再生のCompose画面です。ViewModelが選択番組・表示条件・操作busyとmessageを所有し、durableモデルはRepository、生成寿命はcontroller、共有音声状態はAudio controllerへ委譲します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `podcastDisplayTitle`（関数）、`podcastClusteringSummary`（関数）、`PodcastPlaybackDialog`（関数） | 章・記事の表示と共通音声操作を提供する再生 UI。章移動計算は内部補助。 | [PodcastPlaybackDialog.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastPlaybackDialog.kt) |
| `PODCAST_ROUTE`（定数）、`PODCAST_TITLE`（定数）、`PodcastRoute`（関数） | 番組/source/episode の管理画面と dialog を callback へ接続する Compose 入口。 | [PodcastRoute.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRoute.kt) |
| `PodcastRouteWithPlayback`（関数）、`podcastChapterJumpDistance`（関数） | 画面状態と Audio state を lifecycle 付きで購読し、管理画面と再生 dialog を構成する。 | [PodcastRouteWithPlayback.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt) |
| `PodcastUiState`（data class）、`PodcastViewModel`（class）、`podcastEpisodeContentId`（関数）、`podcastChapterContentId`（関数） | Repository の変化と選択番組を UI state へ投影し、管理操作・生成受付・Audio 章キュー操作を行う。 主なメソッド: `reload`、`selectProgram`、`setShowArchivedEpisodes`、`saveSource`、`deleteSource`、`saveProgram`、`deleteProgram`、`generate`、`retry`、`archiveEpisode`、`restoreEpisode`、`deleteEpisode`、`play`、`dismissPlayback`、`togglePlayPause`、`skipPrevious`、`skipNext`、`seekBack`、`seekForward`、`setPlaybackSpeed`、`stopPlayback`、`clearMessage`。 | [PodcastViewModel.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastViewModel.kt) |


## 公開 API と操作経路

`PodcastRouteWithPlayback` が ViewModel を取得して両 state を購読し、`PodcastRoute` が管理画面、`PodcastPlaybackDialog` が再生画面を提供します。`PodcastUiState` は表示用状態で durable 原稿の正本ではありません。`PodcastViewModel.Factory` は [AppPodcastRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/podcast/AppPodcastRuntimeDependencies.kt) が Repository/controllers を注入して構成します。

ViewModel の `reload/selectProgram/setShowArchivedEpisodes` は表示投影、`saveSource/deleteSource/saveProgram/deleteProgram` は設定管理、`archiveEpisode/restoreEpisode/deleteEpisode` は episode 管理です。各非同期処理の成功は再読込とメッセージ、失敗は busy の解除とエラー表示へ戻ります。`generate` は手動 controller へ、UI の `retry` は `generationController.regenerate` へ送る「作り直し」です。Domain の checkpoint を再利用する `retry` と同じ API ではありません。

`play` は章が記事と対応していれば `podcastChapterContentId` で章キューを作り、対応しなければ `podcastEpisodeContentId` で全体一項目を渡します。両者は公開 ID 生成関数です。`togglePlayPause/skipPrevious/skipNext/seekBack/seekForward/setPlaybackSpeed` は共有 controller へ委譲します。`dismissPlayback` は dialog 表示だけを閉じ、`stopPlayback` は controller を停止して閉じます。`clearMessage` は UI のメッセージを消します。

したがって通常の流れは画面 callback → ViewModel → Repository/controller → 更新イベントまたは Audio StateFlow → route 再描画です。原稿生成の実行 lifetime と dialog の表示 lifetime を混同しないことが変更の境界です。

## 画面の入口と生成受付

PodcastRouteWithPlaybackはlifecycleに合わせてPodcastの画面状態とAudio再生状態を購読し、通常画面と再生dialogを構成します。PodcastViewModelはsourceと番組、選択中番組、episode一覧、アーカイブ表示、操作中のepisodeを持ちます。公開Repositoryとgeneration/schedule controller、共有Audio controllerに依存し、SQLiteやWorker具象には依存しません。

手動生成はgenerationControllerへ依頼して受付メッセージを表示します。長時間のAI生成をViewModel内で実行せず、バックグラウンドが保存した状態を再読込して進捗を示します。番組削除ではscheduleを停止してからRepositoryを更新します。source管理とRSS readerの購読管理は別経路なので、番組sourceの編集でreader側の購読を変える前提を置かないでください。

## 章再生と失敗の境界

生成済み原稿はplaybackChaptersで章へ分け、AudioQueueItemの読み上げ本文として共通Audio controllerへ渡します。章選択は現在キュー内の章IDを探し、そのindex差分だけ前後移動します。対象が現在キューにない場合や現在位置が不正なら移動しません。episodeの削除時には該当音声の再生停止も確認します。章選択のunit testは距離計算を検証しますが、TTSの生成や通知再生までを証明するものではありません。変更時は番組切替、生成失敗、dialogを閉じた後の再生とepisode削除を併せて確認します。

## 調査と変更の入口

[PodcastViewModel.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastViewModel.kt)、[PodcastRouteWithPlayback.kt](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastChapterJumpTest.kt](../../../feature/podcast/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastChapterJumpTest.kt)、[PodcastDisplayTitleTest.kt](../../../feature/podcast/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastDisplayTitleTest.kt) です。

[podcast domain](podcast-domain.md)、[podcast data](podcast-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
