---
type: module
title: 動画 UI：一覧・再生session・寿命管理
description: 動画画面の状態、非同期thumbnail、Media3再生と位置保存を説明する。
tags:
  - video
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5ed4be062d7ddb4906635aa4
    resource: repo://feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoFeatureRoute.kt
  - id: openwiki-source-b5132decffd65255e8196573
    resource: repo://feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerLifecycleEffects.kt
  - id: openwiki-source-37c4ce2564d4eea3c503e6b5
    resource: repo://feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 動画 UI：一覧・再生session・寿命管理

`:feature:video:ui` はcatalog/購読/保存browserのCompose画面とMedia3再生を提供します。ViewModelが表示stateと再生session、player側がstream/SMBのdata sourceと表示寿命を所有し、動画とLibraryの公開Domain能力を利用します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `VIDEO_ROUTE`（定数）、`VIDEO_TITLE`（定数） | appのnavigationに渡すroute/title定数。 | [NavigationDestination.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/NavigationDestination.kt) |
| `SmbVideoDataSource`（class） | Domainのbyte sourceをMedia3のopen/read/closeへ接続する。 主なメソッド: `open`、`read`、`getUri`、`close`。 | [SmbVideoDataSource.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/SmbVideoDataSource.kt) |
| `SMB_VIDEO_READ_AHEAD_BYTES`（定数）、`SmbVideoSourcePool`（class） | source ID別接続をleaseと参照数で共有し、source寿命と複数range readを管理する。 主なメソッド: `acquire`、`close`、`read`。 | [SmbVideoSourcePool.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/SmbVideoSourcePool.kt) |
| `VideoFeatureRoute`（関数） | ViewModel状態を画面へ渡し、PlaybackResolver結果からWebページ/内蔵playerを選ぶ公開Compose入口。 主なメソッド: `play`。 | [VideoFeatureRoute.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoFeatureRoute.kt) |
| `VideoPlayerDialog`（関数） | 再生targetからplayer mediaを生成し、status/view/effectとfullscreen操作を構成する公開dialog。 主なメソッド: `retryPlayback`。 | [VideoPlayerDialog.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerDialog.kt) |
| `VideoPlayerLifecycleEffects`（関数） | player listener/定期位置保存/終了・破棄時保存とplayer/source解放を実行する。 主なメソッド: `savePosition`。 | [VideoPlayerLifecycleEffects.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerLifecycleEffects.kt) |
| `VideoPlayerMedia`（data class）、`createVideoPlayerMedia`（関数） | Stream/SMBに応じたMedia3 playerとdata source factoryを作る内部構成。 | [VideoPlayerMedia.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerMedia.kt) |
| `VIDEO_PLAYER_DOUBLE_TAP_SEEK_MS`（定数）、`videoPlayerDoubleTapSeekPositionMs`（関数）、`FullscreenSystemBarsEffect`（関数） | double tapのseek位置計算とfullscreen system bars effect。 | [VideoPlayerPresentation.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerPresentation.kt) |
| `webStreamRequestProperties`（関数）、`webStreamCookieRequestProperties`（関数）、`webStreamOriginHeaderValue`（関数） | Referer/Origin headerと要求URL別Cookie headerを作る補助。 | [VideoPlayerRequestProperties.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerRequestProperties.kt) |
| `VIDEO_PLAYER_SLOW_LOADING_MS`（定数）、`VIDEO_PLAYER_STALLED_LOADING_MS`（定数）、`VideoPlayerStatusUi`（data class）、`videoPlayerStatusUi`（関数）、`webVideoPlaybackDiagnosticLines`（関数）、`findHttpStatusCode`（関数） | buffering/error/HTTP statusと安全なWeb診断を表示モデルと文字列へ変換する。 | [VideoPlayerStatus.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerStatus.kt) |
| `VideoPlayerView`（関数） | PlayerViewのAndroidView埋め込みとpointer/コントロールの表示接続。 | [VideoPlayerView.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerView.kt) |
| `VideoProviderInboxDialog`（関数） | provider未読動画の再生・既読・あとで見るcallbackを描画する。 | [VideoProviderInboxDialog.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoProviderInboxDialog.kt) |
| `VideoProviderSettingsDialog`（関数） | providerの有効化/script/購読URL・名前の管理をViewModel callbackへ渡す。 | [VideoProviderSettingsDialog.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoProviderSettingsDialog.kt) |
| `VideoSavedBrowserLocation`（sealed interface）、`VideoSavedDirectoryEntry`（data class）、`VideoSavedBreadcrumb`（data class）、`VideoSavedBrowserContent`（data class）、`buildVideoSavedBrowserContent`（関数） | 保存動画とfolder・SMB pathからroot/directory/breadcrumb投影を作る。 | [VideoSavedBrowserProjection.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoSavedBrowserProjection.kt) |
| `VideoScreen`（関数）、`VideoItem.isUnwatched`（関数） | 一覧・保存folder・SMB階層browser・操作dialogを構成する公開Compose画面。isUnwatchedは視聴判定補助。 | [VideoScreen.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoScreen.kt) |
| `VideoSmbSettingsDialog`（関数） | 公開Library接続profileを選び動画同期場所を管理する。 | [VideoSmbSettingsDialog.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoSmbSettingsDialog.kt) |
| `VideoUnwatchedLayout`（enum class）、`VideoUnwatchedLayoutSelector`（関数）、`VideoUnwatchedList`（関数） | 未視聴一覧のlist/grid/masonry表示とlayout selectorを提供する。 | [VideoUnwatchedList.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoUnwatchedList.kt) |
| `VideoUiState`（data class）、`VideoPlaybackSession`（data class）、`VideoViewModel`（class） | catalogとprovider投影、操作busy/message、thumbnail取得、独立PlaybackSessionをStateFlowへ出す。Factoryが公開Domain能力を注入する。 主なメソッド: `resolve`、`reload`、`addWeb`、`refreshSmb`、`ensureThumbnail`、`saveSmbSource`、`deleteSmbSource`、`saveProvider`、`deleteProvider`、`subscribe`、`updateSubscriptionTitle`、`unsubscribe`、`refreshProviders`、`markProviderRead`、`markProviderUnread`、`setProviderWatchLater`、`markAllProviderRead`、`saveVideo`、`removeSavedVideo`、`saveFolder`、`deleteFolder`、`remove`、`setCompleted`、`openPlayback`、`setPlaybackFullscreen`、`closePlayback`、`resumePositionMs`、`savePlayback`、`saveExtractorRule`、`deleteExtractorRule`、`dismissMessage`。 | [VideoViewModel.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModel.kt) |
| `WebVideoHttpDataSource`（class）、`webVideoRequestProperties`（関数） | stream HTTP/range read、redirectと各要求先のCookie取得をMedia3へ接続する。 主なメソッド: `open`、`read`、`getUri`、`close`。 | [WebVideoHttpDataSource.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/WebVideoHttpDataSource.kt) |


## 公開入口と主要 API

`VIDEO_ROUTE/VIDEO_TITLE` はnavigation定数、`VideoFeatureRoute` はfactory・resolver・byteSourceFactory・外部Web/fullscreen callbackを受ける公開入口、`VideoScreen` はstateとcatalog操作callback、`VideoPlayerDialog` はitem/target/再開位置/fullscreenと保存callbackを受けます。`VideoViewModel.Factory` は公開Domain能力をUIへ注入する境界です。[AppSupportingRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt) がruntimeのrepository/resolverを画面依存へまとめます。

ViewModelは `reload/addWeb/refreshSmb/ensureThumbnail`、SMB/provider/購読/folder/rule管理、保存/削除/完了状態操作、providerの既読/未読/あとで見る操作をRepositoryへ送ります。`openPlayback/setPlaybackFullscreen/closePlayback` が `VideoPlaybackSession` を更新し、`resumePositionMs/savePlayback` がsessionとdurable位置を接続、`dismissMessage` は表示messageを消します。設定dialog群はそれぞれSMB/provider/未読のcallbackを渡し、`buildVideoSavedBrowserContent` は保存folderとSMB root/階層/breadcrumbを投影します。

## 一覧からplayer・resource解放へ

1. `VideoFeatureRoute` のplayが重複resolveを抑えて resolverへVideoItemを渡します。WebPageなら外部callback、Stream/Smbなら `openPlayback`。resolve例外時もpageUrlがあれば外部callbackへ戻ります。
2. `VideoPlayerDialog` が `createVideoPlayerMedia` からplayerを作り、StreamにはHTTP data source、Smbには `SmbVideoDataSource` と共有leaseの `SmbVideoSourcePool` を接続します。
3. `WebVideoHttpDataSource` は各request URLごとにCookieを解決し、`webStreamRequestProperties` がReferer/Originを構成します。診断は `videoPlayerStatusUi` / `webVideoPlaybackDiagnosticLines` が安全な表示へ変換します。
4. `VideoPlayerView` がplayer表示を埋め込み、double tapは `videoPlayerDoubleTapSeekPositionMs`、fullscreenは `FullscreenSystemBarsEffect` へ渡します。`VideoPlayerLifecycleEffects` がlistener、終了/定期/dispose時の位置保存と解放を行います。

SMB poolのlease/read寿命はplayerのrange要求と合わせ、HTTP redirect時のCookie要求先は [request test](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerRequestPropertiesTest.kt)、保存階層は [projection test](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoSavedBrowserProjectionTest.kt) が確認先です。

## 画面状態と操作の入口

VideoViewModelは動画一覧、保存フォルダ、SMB接続と同期場所、providerと購読、未視聴・あとで見るのprojectionを公開します。一覧の操作中状態とメッセージに加え、現在のVideoPlaybackSessionを別StateFlowとして保持するため、再生表示を変えても一覧の所有状態を作り直しません。公開Video/Library Domainとdesignsystemへ依存し、Data具象には依存しません。

Web追加や同期はRepository操作を実行してsnapshotを更新します。SMB thumbnailの取得は項目単位で重複要求を抑え、失敗しても一覧全体をエラーへ変えません。生成中に対象動画のversionが変わった場合は最新項目のthumbnailを再解決する経路があるため、古い取得結果を無条件で画面へ適用しない点を確認します。

## Playerの寿命と保存

Media3 playerは画面のlifecycle effectでlistenerを付け、再生終了、定期更新、画面破棄に合わせて位置を保存します。破棄時には位置保存後にlistenerを外しplayerとSMB data-source factoryを解放します。buffering中の経過時間や再生エラーは表示用の観測状態です。ViewModel内sessionとdurable再生位置は寿命が違うため、fullscreen切替を履歴保存の代替として扱わないでください。unit testはbusy解除、thumbnail失敗、session保持を確認します。player解放や実HTTP/SMB再生の変更では実機の停止・再開も確認してください。

## 調査と変更の入口

[VideoViewModel.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModel.kt)、[VideoPlayerLifecycleEffects.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerLifecycleEffects.kt)、[VideoFeatureRoute.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoFeatureRoute.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [VideoViewModelTest.kt](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModelTest.kt)、[VideoPlayerRequestPropertiesTest.kt](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerRequestPropertiesTest.kt) です。

[video domain](video-domain.md)、[video data](video-data.md)、[媒体連携](../../integrations/media.md)を参照してください。
