---
type: module
title: 動画 Data：カタログと再生先解決
description: Web/SMB動画保存、公開SMB能力の利用と再生request境界を説明する。
tags:
  - video
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5945a98a127e92c77061e074
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt
  - id: openwiki-source-db2f77ea69c72039f9d185a4
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolver.kt
  - id: openwiki-source-6181ac6ac64de08b1715a413
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoProviderRepository.kt
  - id: openwiki-source-5336c3b072d030176c88d507
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepository.kt
  - id: openwiki-source-fb615b608ebb048a406b6134
    resource: repo://feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/VideoDatabaseSchema.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 動画 Data：カタログと再生先解決

`:feature:video:data` は動画catalogとproviderのSQLite状態を所有し、LibraryのSMB能力、HTTP、隔離WebViewをDomainのrepository/resolverへ接続するAndroid実装です。compositionが同じresolverを再生先解決とbyte source factoryに配線します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `CustomVideoProviderRuntime`（interface）、`AndroidCustomVideoProviderRuntime`（class） | 隔離WebViewでcustom provider scriptを実行し、明示HTTP capabilityと結果検証を提供する。 主なメソッド: `subscribe`、`refresh`、`fail`。 | [AndroidCustomVideoProviderRuntime.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/AndroidCustomVideoProviderRuntime.kt) |
| `AndroidWebVideoExtractorClient`（class）、`shouldObserveWebVideoStreamRequestAfterExtraction`（関数）、`WebVideoRequestReferrerCapture`（class）、`WebVideoRequestCookieCapture`（class）、`createPlaybackCookieProvider`（関数）、`validPlaybackCookieUrl`（関数）、`validPlaybackRequestUrl`（関数）、`samePlaybackRequestUrl`（関数）、`webVideoReferrerOrigin`（関数）、`selectWebVideoPlaybackReferrerUrl`（関数）、`resolveWebVideoExtractorUrl`（関数）、`isSafeExtractorPageUrl`（関数） | 再開中ActivityのWebViewでmetadata/streamを抽出し、一時的なCookie/Referer供給と非機密診断を返す。 主なメソッド: `extract`、`extractForPlayback`、`record`、`referrerFor`、`cookieFor`、`retainOnly`、`clear`。 | [AndroidWebVideoExtractorClient.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/AndroidWebVideoExtractorClient.kt) |
| `DefaultVideoPlaybackResolver`（class）、`webVideoPlaybackReferrerSource`（関数）、`webStreamReferrerUrl`（関数）、`webVideoPlaybackReferrerUrl`（関数） | PlaybackResolverとByteSourceFactoryの両契約を実装。Web抽出と設定済みSMBのoffset readを接続する。 主なメソッド: `resolve`、`open`。 | [DefaultVideoPlaybackResolver.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolver.kt) |
| `DefaultVideoProviderRepository`（class）、`shouldRetryProviderRefresh`（関数） | provider種別からYouTube/clientまたはcustom runtimeを選び、購読とrefreshをDBへ保存する。 主なメソッド: `providers`、`saveProvider`、`deleteProvider`、`subscriptions`、`subscribe`、`updateSubscriptionTitle`、`unsubscribe`、`refreshProviders`、`unreadVideos`、`watchLaterVideos`、`historyVideos`、`markRead`、`markUnread`、`setWatchLater`、`markAllRead`。 | [DefaultVideoProviderRepository.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoProviderRepository.kt) |
| `DefaultVideoRepository`（class） | SQLiteの動画catalog/保存/再生位置/SMB場所/抽出ruleを管理し、公開Library能力で同期する。 主なメソッド: `items`、`addWeb`、`remove`、`refreshSmb`、`smbSources`、`saveSmbSource`、`deleteSmbSource`、`folders`、`saveFolder`、`deleteFolder`、`saveVideo`、`removeSavedVideo`、`updatePlayback`、`setCompleted`、`extractorRules`、`saveExtractorRule`、`deleteExtractorRule`。 | [DefaultVideoRepository.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepository.kt) |
| `DefaultVideoThumbnailResolver`（class）、`ThumbnailVideoDataSource`（class） | SMBのbyte sourceからMedia3でthumbnailを生成しcacheへ保存する。ThumbnailVideoDataSourceはdecoder向けadapter。 主なメソッド: `resolve`、`open`、`read`、`getUri`、`close`。 | [DefaultVideoThumbnailResolver.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoThumbnailResolver.kt) |
| `SmbVideoLocation`（typealias）、`smbVideoSourceId`（関数）、`legacySmbVideoSourceId`（関数）、`parseSmbVideoSourceId`（関数）、`isSmbVideoPathWithinRoot`（関数）、`stableVideoId`（関数）、`videoMimeType`（関数）、`SMB_VIDEO_EXTENSIONS`（値） | 新旧SMB source ID・安定video ID・MIMEを変換し、Domainのidentity/root判定を再利用する。 | [SmbVideoSource.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/SmbVideoSource.kt) |
| `videoDatabaseSchema`（値）、`ensureVideoSchema`（関数） | 動画所有table/indexをSQLiteへ用意するschema補助。 | [VideoDatabaseSchema.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/VideoDatabaseSchema.kt) |
| `VideoProviderDatabase`（class） | provider/購読/feed itemのtransactionと未読/あとで見る/履歴投影を所有する内部DBadapter。 主なメソッド: `providers`、`saveProvider`、`deleteProvider`、`subscriptions`、`requireProvider`、`updateCustomSubscriptionTitle`、`upsertProviderFeed`、`unsubscribe`、`unreadVideos`、`watchLaterVideos`、`historyVideos`、`markRead`、`markUnread`、`setWatchLater`、`markAllRead`。 | [VideoProviderDatabase.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/VideoProviderDatabase.kt) |
| `StaticWebVideoMetadata`（data class）、`WebVideoMetadataClient`（class）、`normalizeWebVideoUrl`（関数）、`resolveWebVideoUrl`（関数） | 静的HTML metadataとURL正規化から表示情報を取得する。 主なメソッド: `fetch`。 | [WebVideoMetadataClient.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/WebVideoMetadataClient.kt) |
| `ObservedWebVideoRequestDiagnostics`（data class）、`WebVideoRequestDiagnosticsCapture`（class）、`webVideoPlaybackDiagnostics`（関数） | WebView要求の特徴だけを記録して公開diagnosticsへ変換する。header値そのものを診断モデルに保存しない。 主なメソッド: `record`、`diagnosticsFor`、`clear`。 | [WebVideoRequestDiagnostics.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/WebVideoRequestDiagnostics.kt) |
| `validateWebVideoExtractorRule`（関数）、`findMatchingWebVideoExtractorRule`（関数） | rule入力を検証しHTTPS URLへglobを適用して具体性/更新時刻から優先ruleを選ぶ。 | [WebVideoRuleMatcher.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/WebVideoRuleMatcher.kt) |
| `VideoProviderFeed`（data class）、`VideoProviderFeedItem`（data class）、`VideoProviderHttpException`（class）、`YouTubeVideoProviderClient`（class） | YouTubeの購読URL/channel IDからfeedを取得・解析して内部feedモデルへ変換する。 主なメソッド: `subscribe`、`refresh`。 | [YouTubeVideoProviderClient.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/YouTubeVideoProviderClient.kt) |


## 構成接続と主なメソッド

`videoDatabaseSchema` は Core Database の `DatabaseSchemaContribution` 値です。[AppDatabaseSchema](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) が application の schema contribution 一覧へ登録し、feature が所有する表の生成を共通 database の初期化へ接続します。`ensureVideoSchema` は同じ所有表の生成をまとめる補助関数です。

[AppVideoRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/video/AppVideoRuntimeDependencies.kt) が Library 公開能力とHTTP/DB/再開中Activity providerを各具象に注入します。`DefaultVideoRepository` は Domain CRUD/API を実装し、`WebVideoMetadataClient` と `AndroidWebVideoExtractorClient.extract` をWeb追加へ、Library能力をSMB同期へ使います。`ensureVideoSchema` と内部 `VideoProviderDatabase` が保存構造を用意します。`smbVideoSourceId` / `legacySmbVideoSourceId` / `parseSmbVideoSourceId` / `stableVideoId` / `videoMimeType` は identity/MIME 変換です。

`DefaultVideoPlaybackResolver.resolve` はtarget選択、`open` は設定rootを確認してLibraryのmedia file handleへread/closeを委譲します。Webは `findMatchingWebVideoExtractorRule` → `extractForPlayback` → referrer正規化 → Stream、抽出できなければWebPageです。`DefaultVideoThumbnailResolver.resolve` はSMB byte sourceをMedia3の `ThumbnailVideoDataSource` へ渡して画像cacheを返します。

`AndroidWebVideoExtractorClient.extract/extractForPlayback` は画面WebViewを使う抽出境界です。内部request capture型が要求URL別の一時Cookie/Refererと通信特徴を保持し、`createPlaybackCookieProvider` と `webVideoPlaybackDiagnostics` が再生へ渡します。URL validation・referrer origin/path制限は補助関数へ分かれ、診断と機密値の保存を混同しません。

## 取り込みと保存

DefaultVideoRepositoryは動画カタログ、保存フォルダ、再生位置、SMB同期場所とWeb抽出規則をSQLiteへ保存します。Web追加はURLを正規化し、静的metadataと一致する抽出規則の結果からタイトルとthumbnailを決めます。抽出が失敗した場合は静的metadata等へ戻り、sourceとURLから安定IDを作って保存します。削除は動画に付随する保存状態と再生状態も同じtransactionで削除します。

SMB同期ではLibrary Domainの接続プロファイルとSmbMediaFileAccessを利用します。動画独自のshare/rootは保持しますが、Library Dataや接続credentialの具体実装へ直接依存しません。再生用のoffset readも公開SMB能力へ委譲し、ファイル全体を取得してからでなければ再生できない経路に固定しません。

## 再生先と通信情報

DefaultVideoPlaybackResolverはWeb抽出規則でstreamが得られた場合だけ直接再生先を返し、それ以外は元ページ表示へ戻ります。Refererは既定でorigin相当へ制限し、path共有を明示した場合でもuserinfo、query、fragmentを除きます。cookieは再生要求先に応じて供給する契約を通して扱います。変更時はURL validation、Referer設定、cookie転送、SMB設定root内のreadを関連テストで確認してください。metadata取得成功と再生成功は異なるため、両方の境界を分けて調査します。

## provider refreshと失敗経路

`DefaultVideoProviderRepository.subscribe` は有効providerを確認し、YOUTUBEを `YouTubeVideoProviderClient.subscribe`、CUSTOMを `CustomVideoProviderRuntime.subscribe` へ送り、`VideoProviderDatabase.upsertProviderFeed` で保存します。refreshは enabled providerの各購読を同様に更新します。YouTubeの所定HTTP失敗だけ遅延retryし、成功分は保存されたまま、最終失敗があれば成功/失敗件数を含む例外を返します。`VideoProviderRefreshResult.failedSubscriptions` に常に部分失敗を返す実装ではありません。

`AndroidCustomVideoProviderRuntime` は独立WebView profileにCookie禁止・直接network/file/contentアクセス禁止を設定し、scriptのrequestを既存HTTP capabilityで実行します。実行時間・request回数・出力を検証し、refreshでsourceIdが変われば拒否、finallyでWebViewを破棄します。provider scriptの境界変更は [CustomVideoProviderRuntimePolicyTest](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/CustomVideoProviderRuntimePolicyTest.kt) を確認します。

## 調査と変更の入口

[DefaultVideoRepository.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepository.kt)、[DefaultVideoPlaybackResolver.kt](../../../feature/video/data/src/main/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolver.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultVideoPlaybackResolverTest.kt](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoPlaybackResolverTest.kt)、[DefaultVideoRepositoryTest.kt](../../../feature/video/data/src/test/kotlin/dev/terashima/yomitorirss/feature/video/data/DefaultVideoRepositoryTest.kt) です。

[video domain](video-domain.md)、[video ui](video-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。
