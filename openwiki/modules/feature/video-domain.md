---
type: module
title: 動画 Domain：保存・購読・再生先
description: 動画モデルと保存・再生の契約、SMBブラウザ位置の導出を説明する。
tags: [video, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-0230ab4ae8cfd15b5c0434fd
    resource: repo://feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt
  - id: openwiki-source-558d5e0997fba687e68b50ce
    resource: repo://feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 動画 Domain：保存・購読・再生先

`:feature:video:domain` は次の責務を持ちます。

## 責務と再生境界

SMB、Web、service由来の動画をVideoItemで表し、動画カタログ、保存フォルダ、再生位置、購読providerの公開契約を持ちます。再生先は直接stream、SMBバイトsource、Webページに分かれ、UIが取得元の内部実装へ依存せず適切な再生表示を選べます。SMBの接続credentialは動画Domainで新たに管理せず、設定済み接続とshare/rootを参照するモデルを受け渡します。

再生状態と保存先状態は別モデルです。視聴位置・長さ・完了状態と、保存フォルダ・保存時刻を分けることで、保存操作を再生状態の初期化として扱いません。Webのcookie供給やrequest診断は再生先に付随する契約として渡し、実際のHTTPやWebViewはData/UI側へ置きます。

## SMB位置と変更の確認

SMBブラウザ位置は動画のsource IDと現在の同期rootから導出する一時的なprojectionです。serverが一致し、shareを含むsource IDではshareも一致するrootを候補にし、ファイルパスを含むrootのうち最も深いものを選び、rootより下のディレクトリを返します。パスを文字列prefixだけで比較せずsegmentで比較し、上位へ戻る`..`を拒否するため、似た名前の別rootへ誤って所属させません。ブラウザ位置を永続状態として追加する必要はありません。変更時はVideoSmbBrowserTestでブラウザ位置と重複rootを確認します。旧source IDと特殊文字の往復はDataの[DefaultVideoPlaybackResolverTest](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolverTest.kt)が確認先です。不正パスの拒否は現コードに根拠があり、このページで挙げたテストが直接検証するとは限りません。

## 調査と変更の入口

[VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt)、[VideoProviderModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoProviderModels.kt)、[VideoSmbBrowser.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [VideoSmbBrowserTest.kt](../../../feature/video/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowserTest.kt) です。

[video data](video-data.md)、[video ui](video-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。
