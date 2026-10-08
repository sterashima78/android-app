---
type: module
title: 動画 UI：一覧・再生session・寿命管理
description: 動画画面の状態、非同期thumbnail、Media3再生と位置保存を説明する。
tags: [video, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-b5132decffd65255e8196573
    resource: repo://feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerLifecycleEffects.kt
  - id: openwiki-source-37c4ce2564d4eea3c503e6b5
    resource: repo://feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 動画 UI：一覧・再生session・寿命管理

`:feature:video:ui` は次の責務を持ちます。

## 画面状態と操作の入口

VideoViewModelは動画一覧、保存フォルダ、SMB接続と同期場所、providerと購読、未視聴・あとで見るのprojectionを公開します。一覧の操作中状態とメッセージに加え、現在のVideoPlaybackSessionを別StateFlowとして保持するため、再生表示を変えても一覧の所有状態を作り直しません。公開Video/Library Domainとdesignsystemへ依存し、Data具象には依存しません。

Web追加や同期はRepository操作を実行してsnapshotを更新します。SMB thumbnailの取得は項目単位で重複要求を抑え、失敗しても一覧全体をエラーへ変えません。生成中に対象動画のversionが変わった場合は最新項目のthumbnailを再解決する経路があるため、古い取得結果を無条件で画面へ適用しない点を確認します。

## Playerの寿命と保存

Media3 playerは画面のlifecycle effectでlistenerを付け、再生終了、定期更新、画面破棄に合わせて位置を保存します。破棄時には位置保存後にlistenerを外しplayerとSMB data-source factoryを解放します。buffering中の経過時間や再生エラーは表示用の観測状態です。ViewModel内sessionとdurable再生位置は寿命が違うため、fullscreen切替を履歴保存の代替として扱わないでください。unit testはbusy解除、thumbnail失敗、session保持を確認します。player解放や実HTTP/SMB再生の変更では実機の停止・再開も確認してください。

## 調査と変更の入口

[VideoViewModel.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModel.kt)、[VideoPlayerLifecycleEffects.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerLifecycleEffects.kt)、[VideoFeatureRoute.kt](../../../feature/video/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoFeatureRoute.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [VideoViewModelTest.kt](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoViewModelTest.kt)、[VideoPlayerRequestPropertiesTest.kt](../../../feature/video/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/video/ui/VideoPlayerRequestPropertiesTest.kt) です。

[video domain](video-domain.md)、[video data](video-data.md)、[媒体連携](../../integrations/media.md)を参照してください。
