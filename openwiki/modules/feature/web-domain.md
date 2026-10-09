---
type: module
title: LAN Web Domain：読み取りgatewayと起動状態
description: LAN公開のread-onlyデータ契約、起動状態と通知からの起動アクションを説明する。
tags:
  - web
  - domain
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-d62ac7d985204cb3acec963b
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt
  - id: openwiki-source-13b22f69a10b16c7253a6bb5
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt
  - id: openwiki-source-161b6055430a81b6259b39a4
    resource: repo://feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt
  - id: openwiki-source-be46cce610102bcf7a77ddff
    resource: repo://feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# LAN Web Domain：読み取りgatewayと起動状態

`:feature:web:domain` はLAN Web機能が必要とする読み取り面と、サーバーを開始・停止する契約を定義する。JVMモジュールでcoroutinesを利用し、HTTP transportやAndroid Service、Contentの具体的Repositoryには依存しない。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `LanWebContentGateway`（interface） | suspend listUnreadArticles/listSavedArticles/listReadLaterArticles/listFeedsを返す読み取り契約。 | [LanWebContentGateway.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt) |
| `LanWebContentGatewayProvider`（interface） | Applicationからgatewayを取得するAndroid-created Service向け境界。 | [LanWebContentGateway.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt) |
| `LanWebArticleItem`（data class）、`LanWebFeedItem`（data class）、`LanWebSourceKind`（enum class） | 記事とfeedの表示用モデル、RSS/REDDITの種別。可変entityを公開しない。 | [LanWebContentGateway.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt) |
| `LanWebServerState`（data class） | 起動・address・port・URL・errorの値。runningとURLの有無は独立する。 | [LanWebServerController.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt) |
| `LanWebServerController`（interface） | stateとstart/stopを公開しAndroid Service実装へ委譲する。 | [LanWebServerController.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt) |
| `LanWebServerLaunchContract`（object） | 通知のACTION_OPEN_SERVERをexecutable appへ共有する。 | [LanWebServerLaunchContract.kt](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerLaunchContract.kt) |


## 公開 API と composition

`listUnreadArticles` / `listSavedArticles` / `listReadLaterArticles` は `List<LanWebArticleItem>`、`listFeeds` は `List<LanWebFeedItem>` を返す suspend API です。`LanWebSourceKind` は RSS/REDDIT の投影種別で、更新可能な記事 entity の代替ではありません。`LanWebServerController.start/stop` は返却結果を持たず、状態変化は `state` で観測します。

gateway の実装は [AppLanWebContentGateway](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/web/AppLanWebContentGateway.kt) で、Content の公開 Repository を組み合わせます。[AppContainer](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) が gateway を構成し、Application の `LanWebContentGatewayProvider` 経由で Service へ渡します。controller の実装は [AndroidLanWebServerController](web-data.md) で、[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt) が生成します。

## read-only公開面

`LanWebContentGateway`は未読・保存済み・あとで読むの記事一覧とfeed一覧だけを提供する。Web側の表示用モデルにはタイトル、URL、source、日付、RSS/Redditの種別、tagを含む。記事状態を変更するAPIは持たず、read modelとrendererがこの投影を利用する。`LanWebContentGatewayProvider`はAndroid-created serviceがApplicationからこの狭い能力を取得する境界である。

## 起動状態と利用側

`LanWebServerController`はStateFlowの`LanWebServerState`とstart/stopを提供する。状態はrunning、address、port、access URL、errorを分け、起動中でもaddressやURLがない待機状態を表現できる。通知からアプリのサーバー導線へ戻るactionは`LanWebServerLaunchContract`が共有する。tokenの生成・寿命やServiceの再起動はこの状態モデルだけで保証せず、Dataの認証・Serviceが実装する。

このDomain配下には専用testがなく、Web Dataのgateway依存境界test、state store test、UIのstatus表示testが利用契約の確認先となる。公開内容を増やす場合はgatewayの投影を拡張し、既存ownerを迂回するdatabase accessや更新APIがWebへ流れ込まないかを確認する。

## 調査・変更の入口

[LanWebContentGateway](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt)、[LanWebServerController](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt)、[LanWebServerLaunchContract](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerLaunchContract.kt) を起点に責務と呼び出し側を確認する。

関連: [web data](web-data.md) / [web ui](web-ui.md) / [全体構成](../../architecture/system.md)。
