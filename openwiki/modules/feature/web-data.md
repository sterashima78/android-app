---
type: module
title: "LAN Web Data：Service・HTTP・一度限りの認証"
description: "LAN Serviceの寿命、gateway/read model/renderer分離とbootstrap/session認証を説明する。"
tags: [web, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-39da0322c330b7a0df6949f1
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt
  - id: openwiki-source-be570059f454254aa4a39fe6
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt
  - id: openwiki-source-13b22f69a10b16c7253a6bb5
    resource: repo://feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# LAN Web Data：Service・HTTP・一度限りの認証

`:feature:web:data` はAndroid controllerとforeground Service、socket HTTP server、read model、HTML rendererを所有する。依存するfeatureはWeb Domainだけで、他featureのRepositoryやdatabaseを直接受け取らず、Application提供のWeb gatewayを使う。

## 起動から公開へ

controllerからServiceを開始し、foreground通知とnetwork callbackを登録してserverを起動する。read modelは未知viewを未読へ戻し、RSS/Redditの未読とRSS feedを種別で分ける。transportは認証後にread modelを読み、rendererへ渡す。起動失敗はstateのerrorへ反映してServiceを停止する。破棄時にはcallback登録解除、server close、executor停止、状態resetを行う。

## 認証の寿命

bootstrap tokenとsession tokenはメモリ内に保持する。最初の一致query tokenだけが成功し、同じ同期処理でbootstrapを消費してsessionを生成する。HTTP応答はHttpOnly・SameSite=Strict Cookieと、tokenを除いたURLへのredirectを返す。以後はquery token付きrequestを拒否しsession Cookieを照合する。LAN address変更で新bootstrapへ差し替えるとsessionを失効し、closeでも両tokenを失効する。平文HTTPである点は認証があっても変わらない。

`LanWebServerTest`はone-shot認証、停止競合、address rotation、再起動とredirectを、read model・boundary・state store testは責務分離を確認する。変更ではtoken生成と状態URL更新の順序を合わせて確認する。

## 調査・変更の入口

[LanWebServer](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt)、[LanWebServerService](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerService.kt)、[LanWebReadModel](../../../feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt)、[LanWebServerTest](../../../feature/web/data/src/test/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServerTest.kt)、[LanWebReadModelTest](../../../feature/web/data/src/test/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModelTest.kt) を起点に責務と呼び出し側を確認する。

関連: [web domain](web-domain.md) / [web ui](web-ui.md) / [全体構成](../../architecture/system.md)。
