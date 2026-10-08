---
type: module
title: "LAN Web Domain：読み取りgatewayと起動状態"
description: "LAN公開のread-onlyデータ契約、起動状態と通知からの起動アクションを説明する。"
tags: [web, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-161b6055430a81b6259b39a4
    resource: repo://feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt
  - id: openwiki-source-be46cce610102bcf7a77ddff
    resource: repo://feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# LAN Web Domain：読み取りgatewayと起動状態

`:feature:web:domain` はLAN Web機能が必要とする読み取り面と、サーバーを開始・停止する契約を定義する。JVMモジュールでcoroutinesを利用し、HTTP transportやAndroid Service、Contentの具体的Repositoryには依存しない。

## read-only公開面

`LanWebContentGateway`は未読・保存済み・あとで読むの記事一覧とfeed一覧だけを提供する。Web側の表示用モデルにはタイトル、URL、source、日付、RSS/Redditの種別、tagを含む。記事状態を変更するAPIは持たず、read modelとrendererがこの投影を利用する。`LanWebContentGatewayProvider`はAndroid-created serviceがApplicationからこの狭い能力を取得する境界である。

## 起動状態と利用側

`LanWebServerController`はStateFlowの`LanWebServerState`とstart/stopを提供する。状態はrunning、address、port、access URL、errorを分け、起動中でもaddressやURLがない待機状態を表現できる。通知からアプリのサーバー導線へ戻るactionは`LanWebServerLaunchContract`が共有する。tokenの生成・寿命やServiceの再起動はこの状態モデルだけで保証せず、Dataの認証・Serviceが実装する。

このDomain配下には専用testがなく、Web Dataのgateway依存境界test、state store test、UIのstatus表示testが利用契約の確認先となる。公開内容を増やす場合はgatewayの投影を拡張し、既存ownerを迂回するdatabase accessや更新APIがWebへ流れ込まないかを確認する。

## 調査・変更の入口

[LanWebContentGateway](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebContentGateway.kt)、[LanWebServerController](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerController.kt)、[LanWebServerLaunchContract](../../../feature/web/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/web/LanWebServerLaunchContract.kt) を起点に責務と呼び出し側を確認する。

関連: [web data](web-data.md) / [web ui](web-ui.md) / [全体構成](../../architecture/system.md)。
