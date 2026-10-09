---
type: module
title: 動画 Domain：保存・購読・再生先
description: 動画モデルと保存・再生の契約、SMBブラウザ位置の導出を説明する。
tags:
  - video
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:30:12.060Z
sources:
  - id: openwiki-source-be38d43c71fe38f0fecd7268
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/video/AppVideoRuntimeDependencies.kt
  - id: openwiki-source-0230ab4ae8cfd15b5c0434fd
    resource: repo://feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt
  - id: openwiki-source-558d5e0997fba687e68b50ce
    resource: repo://feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt
generated: { by: "codex", at: "2026-10-09T04:30:12.060Z" }
---

# 動画 Domain：保存・購読・再生先

`:feature:video:domain` は動画catalog・保存・再生位置・購読providerと再生先解決のJVM公開契約です。UIとDataがモデルを受け渡し、SMBのcredential所有をLibraryへ保ったままoffset readの能力を公開します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `VideoSource`（enum class）、`VideoItem`（data class） | 取得元種別と一覧項目。isSavedはSMB/WEBかsavedStateの有無で導出する。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoSmbSource`（data class）、`VideoPlaybackState`（data class）、`VideoSavedState`（data class）、`VideoFolder`（data class） | 同期場所・視聴履歴・保存状態・保存folderを独立したモデルで渡す。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `WebVideoExtractorRule`（data class）、`WebVideoExtractionResult`（data class） | 抽出設定と一時結果。metadata/stream/referrer/cookie供給を受け渡す。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoPlaybackCookieProvider`（fun interface） | cookieHeaderFor(url)が各再生要求先の一時Cookieを返す。値を永続化/logへ流さない契約。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `WebVideoSecFetchSite`（enum class）、`WebVideoPlaybackReferrerSource`（enum class）、`WebVideoPlaybackDiagnostics`（data class） | 通信要求の非機密特徴とReferer選択元を区別する診断値。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoPlaybackTarget`（sealed interface） | Stream/Smb/WebPageの再生先をUIへ返すsealed interface。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoRepository`（interface） | catalog/SMB同期/保存folder/再生履歴/抽出ruleの操作契約。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoPlaybackResolver`（interface）、`VideoThumbnailResolver`（interface） | resolve(item)で再生targetまたはthumbnail URIを返す。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoByteSource`（interface）、`VideoByteSourceFactory`（interface） | open(sourceId)からlength/read/close可能なrange sourceを得る。 | [VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt) |
| `VideoProviderType`（enum class）、`VideoProvider`（data class）、`VideoSubscription`（data class） | YOUTUBE/CUSTOM、provider設定、入力source購読を表す。 | [VideoProviderModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoProviderModels.kt) |
| `VideoProviderVideo`（data class）、`VideoProviderRefreshResult`（data class） | 受信動画の未読/あとで見る投影とrefresh件数。 | [VideoProviderModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoProviderModels.kt) |
| `VideoProviderRepository`（interface） | provider/購読管理、refresh、未読/履歴/あとで見るの公開API。 | [VideoProviderModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoProviderModels.kt) |
| `VideoSmbFileIdentity`（data class）、`VideoSmbBrowserPath`（data class） | 永続source identityと一時的なroot-relative browser位置を分ける。 | [VideoSmbBrowser.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt) |
| `parseVideoSmbSourceId`（関数）、`isVideoSmbPathWithinRoot`（関数）、`VideoItem.smbBrowserPath`（関数） | source ID解析・segment包含・最深root導出の公開純粋処理。 | [VideoSmbBrowser.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt) |


## 主要 API・実装と状態の受け渡し

`VideoItem` の source/sourceId が取得元identity、`VideoSmbSource` が設定済み接続のshare/root、`VideoFolder` / `VideoSavedState` が保存先、`VideoPlaybackState` が履歴です。`VideoSource`、`VideoProviderType` は取得元と購読方式を別に分類します。`VideoProvider` / `VideoSubscription` / `VideoProviderVideo` / `VideoProviderRefreshResult` は設定・購読・受信投影・更新集計です。

`VideoRepository` は `items/addWeb/remove/refreshSmb` のcatalog操作、`smbSources/saveSmbSource/deleteSmbSource`、`folders/saveFolder/deleteFolder`、`saveVideo/removeSavedVideo`、`updatePlayback/setCompleted`、`extractorRules/saveExtractorRule/deleteExtractorRule` を公開します。`VideoProviderRepository` は provider CRUD、subscriptions/subscribe/title更新/unsubscribe、refresh、unread/watchLater/history、markRead/markUnread/setWatchLater/markAllReadを公開します。これらはcatalogの保存と購読の未読を独立して扱います。

`VideoPlaybackResolver.resolve(item)` は `VideoPlaybackTarget.Stream/Smb/WebPage`、`VideoThumbnailResolver.resolve(item)` は任意の画像URIを返します。`VideoByteSourceFactory.open(sourceId)` がread/close可能な `VideoByteSource` を返し、読み出し側はresourceを解放します。`WebVideoExtractorRule` は抽出コードと共有設定、`WebVideoExtractionResult` は一時的な抽出結果、`VideoPlaybackCookieProvider` は要求先ごとのCookie供給、`WebVideoPlaybackDiagnostics` と関連enumは非機密の通信特徴を表します。Cookie値の永続化/log出力を認めるモデルではありません。

具象は [Video Data](video-data.md) の各 Default 実装で、[AppVideoRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/video/AppVideoRuntimeDependencies.kt) が Repository と共有 extractor を resolver に注入し、同じresolverを byte source factory とthumbnail生成へ渡します。

## 代表的な再生要求

UIがVideoItemをresolverへ渡すと、SMBはSmb targetへ変換し、保存済みsizeがなければbyte sourceを開いて長さを取得します。設定済みroot内のidentity確認はbyte sourceのopenで行うため、sizeが保存済みの場合にはtarget解決の後、実際の読取時に確認します。Webはrule抽出からStreamまたはWebPageへ、SERVICEはpageUrlからWebPageへ変換されます。UIはStream/Smbを内蔵playerへ、WebPageを外部表示callbackへ戻します。公開 `parseVideoSmbSourceId` は不正identityをnull、`isVideoSmbPathWithinRoot` はsegmentで包含判定、`VideoItem.smbBrowserPath` は最も深いrootの下のディレクトリ投影を返します。

## 責務と再生境界

SMB、Web、service由来の動画をVideoItemで表し、動画カタログ、保存フォルダ、再生位置、購読providerの公開契約を持ちます。再生先は直接stream、SMBバイトsource、Webページに分かれ、UIが取得元の内部実装へ依存せず適切な再生表示を選べます。SMBの接続credentialは動画Domainで新たに管理せず、設定済み接続とshare/rootを参照するモデルを受け渡します。

再生状態と保存先状態は別モデルです。視聴位置・長さ・完了状態と、保存フォルダ・保存時刻を分けることで、保存操作を再生状態の初期化として扱いません。Webのcookie供給やrequest診断は再生先に付随する契約として渡し、実際のHTTPやWebViewはData/UI側へ置きます。

## SMB位置と変更の確認

SMBブラウザ位置は動画のsource IDと現在の同期rootから導出する一時的なprojectionです。serverが一致し、shareを含むsource IDではshareも一致するrootを候補にし、ファイルパスを含むrootのうち最も深いものを選び、rootより下のディレクトリを返します。パスを文字列prefixだけで比較せずsegmentで比較し、上位へ戻る`..`を拒否するため、似た名前の別rootへ誤って所属させません。ブラウザ位置を永続状態として追加する必要はありません。変更時はVideoSmbBrowserTestでブラウザ位置と重複rootを確認します。旧source IDと特殊文字の往復はDataの[DefaultVideoPlaybackResolverTest](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolverTest.kt)が確認先です。不正パスの拒否は現コードに根拠があり、このページで挙げたテストが直接検証するとは限りません。

## 調査と変更の入口

[VideoModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoModels.kt)、[VideoProviderModels.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoProviderModels.kt)、[VideoSmbBrowser.kt](../../../feature/video/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowser.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [VideoSmbBrowserTest.kt](../../../feature/video/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/video/VideoSmbBrowserTest.kt) です。

[video data](video-data.md)、[video ui](video-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。
