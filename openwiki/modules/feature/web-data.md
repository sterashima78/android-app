---
type: module
title: LAN Web Data：Service・HTTP・一度限りの認証
description: LAN Serviceの寿命、gateway/read model/renderer分離とbootstrap/session認証を説明する。
tags:
  - web
  - data
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-39da0322c330b7a0df6949f1
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt
  - id: openwiki-source-be570059f454254aa4a39fe6
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt
  - id: openwiki-source-13b22f69a10b16c7253a6bb5
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# LAN Web Data：Service・HTTP・一度限りの認証

`:feature:web:data` はAndroid controllerとforeground Service、socket HTTP server、read model、HTML rendererを所有する。依存するfeatureはWeb Domainだけで、他featureのRepositoryやdatabaseを直接受け取らず、Application提供のWeb gatewayを使う。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `AndroidLanWebServerController`（class）、`LanWebServerStateStore`（object） | Service 起動/停止 adapter と内部 StateFlow store。Service が状態を更新し UI が購読する。 主なメソッド: `start`、`stop`、`starting`、`running`、`stopped`。 | [AndroidLanWebServerController.kt](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/AndroidLanWebServerController.kt) |
| `LanWebReadModel`（class）、`LanWebHomePage`（data class）、`LanWebContent`（sealed interface）、`LanWebViews`（object） | gateway の一覧を view ごとの Articles/Feeds 投影へ変換する。未知 view は UNREAD。 主なメソッド: `loadHome`。 | [LanWebReadModel.kt](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt) |
| `LanWebRenderer`（object）、`escapeHtml`（関数） | 投影を HTML に変換し、表示文字列を escapeHtml でエスケープする。 主なメソッド: `renderHome`、`renderError`。 | [LanWebRenderer.kt](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebRenderer.kt) |
| `LanWebServer`（class）、`AuthenticationResult`（sealed interface）、`LanWebAuthentication`（class）、`URI.withoutBootstrapToken`（関数） | GET socket transport、認証、redirect、read model と renderer の接続。認証結果型は bootstrap/session の分岐を表す。 主なメソッド: `start`、`replaceBootstrapToken`、`close`、`authenticate`、`invalidate`。 | [LanWebServer.kt](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt) |
| `LanWebServerService`（class） | foreground 通知、LAN address 観測、token rotation、server の生成と破棄を所有する。 主なメソッド: `onCreate`、`onStartCommand`、`onBind`、`onDestroy`。 | [LanWebServerService.kt](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt) |


## 主要 API と transport の経路

`AndroidLanWebServerController.start/stop` は Service の companion API へ委譲します。`LanWebServer.start` は accept/request executor を使う socket server を開始し、`replaceBootstrapToken` は旧 session を失効、`close` は認証を失効させ socket と executor を閉じます。内部 `LanWebServerStateStore.starting/running/stopped` が UI へ渡す StateFlow を更新します。

request は `handle` で client address の許可判定、GET 判定、URI/query/Cookie の解析、`LanWebAuthentication.authenticate` の順に進みます。bootstrap 成功は 303 と Cookie、不一致は 403。認証済み home のみ `LanWebReadModel.loadHome` → `LanWebRenderer.renderHome` に進みます。`robots.txt` は巡回拒否、未知 path は 404、GET 以外は 405 です。`AuthenticationResult` の Bootstrapped/Authenticated/Rejected が分岐を型で区別します。`URI.withoutBootstrapToken` は redirect URL から token query だけを取り除きます。

renderer の `renderHome` / `renderError` は HTML 文字列を返し、`escapeHtml` が外部由来文字列を escape します。HTTP response には no-store、no-referrer、CSP が付きます。client address 判定は loopback/site-local/link-local の分類であり、ネットワークの同一性を細かく認証する仕組みではありません。gateway 読込の例外を HTTP 500 ページに変換する分岐は `handle` にありません。

## 起動から公開へ

controllerからServiceを開始し、foreground通知とnetwork callbackを登録してserverを起動する。read modelは未知viewを未読へ戻し、RSS/Redditの未読とRSS feedを種別で分ける。transportは認証後にread modelを読み、rendererへ渡す。起動失敗はstateのerrorへ反映してServiceを停止する。破棄時にはcallback登録解除、server close、executor停止、状態resetを行う。

## 認証の寿命

bootstrap tokenとsession tokenはメモリ内に保持する。最初の一致query tokenだけが成功し、同じ同期処理でbootstrapを消費してsessionを生成する。HTTP応答はHttpOnly・SameSite=Strict Cookieと、tokenを除いたURLへのredirectを返す。以後はquery token付きrequestを拒否しsession Cookieを照合する。LAN address変更で新bootstrapへ差し替えるとsessionを失効し、closeでも両tokenを失効する。平文HTTPである点は認証があっても変わらない。

`LanWebServerTest`はone-shot認証、停止競合、address rotation、再起動とredirectを、read model・boundary・state store testは責務分離を確認する。変更ではtoken生成と状態URL更新の順序を合わせて確認する。

## 調査・変更の入口

[LanWebServer](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt)、[LanWebServerService](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt)、[LanWebReadModel](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt)、[LanWebServerTest](../../../feature/web/data/src/test/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerTest.kt)、[LanWebReadModelTest](../../../feature/web/data/src/test/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModelTest.kt) を起点に責務と呼び出し側を確認する。

関連: [web domain](web-domain.md) / [web ui](web-ui.md) / [全体構成](../../architecture/system.md)。
