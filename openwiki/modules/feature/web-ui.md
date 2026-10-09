---
type: module
title: LAN Web UI：起動確認とアクセスURL表示
description: サーバーの起動・待機・停止状態、開始確認とHTTP公開の説明を扱うstateless dialogを説明する。
tags:
  - web
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-d3cbca75a5805c2823b92d9e
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt
  - id: openwiki-source-fd08d3f87684cbe34f43d294
    resource: repo://feature/web/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/web/WebServerDialog.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# LAN Web UI：起動確認とアクセスURL表示

`:feature:web:ui` は`WebServerDialog`を提供するComposeモジュールで、Web Domainの状態を表示へ変換する。サーバーや権限を自分で所有せず、stateとonStart/onStop/onDismissを受け取る。Serviceの寿命は画面の寿命から独立しており、dialogを閉じる操作と停止操作は別callbackである。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `WebServerDialog`（関数）、`webServerStatusText`（関数） | state に基づく公開説明と開始・停止・閉じる callback を提供する。 | [WebServerDialog.kt](../../../feature/web/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/web/WebServerDialog.kt) |


## 関数と操作の接続

公開 `WebServerDialog(state, onStart, onStop, onDismiss)` は現在状態を描画し、`webServerStatusText(state: LanWebServerState)` は表示ラベルを作る internal 関数です。開始ボタン → onStart、停止ボタン → onStop、dialog 閉鎖 → onDismiss の順路を呼び出し側へ戻します。

具体的な接続先は [LanWebServerDialogHost](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt) です。host が `collectAsState()` で controller state を購読し、通知権限とSDKに応じたローカル network 権限を検査して controller callback を接続します。開始に失敗した場合、この module は `state.error` の表示を担い、Service の再試行や network の変更は所有しません。

## 状態に応じた説明

停止中は未読・ブックマーク・あとで読む・feedをLANへ公開する用途と、平文HTTPで個人データを公開することを説明し、「理解して開始」からonStartを呼ぶ。起動中は状態ラベルを変え、access URLがなければ同じnetworkへの接続待ち、あればブラウザから開く選択可能なURLを示す。初回URLは一度だけ認証に使われ、共有しないよう案内する。

起動中にはHTTPが暗号化されないことと、アプリ画面を閉じても動作することを表示し、停止ボタンからonStopを呼ぶ。state.errorは別途error色で表示する。アクセスURLがまだないことをサーバー停止と同一視しないのが利用上の境界である。

## 呼び出し側と確認先

Android 17のローカルnetwork権限要求やcontroller stateの購読は呼び出し側の責務として確認する。`WebServerDialogTest`が直接確認するのはrunningによるstatus labelの切替であり、権限や実際のserver停止を保証するtestではない。文言や操作条件を変更する場合は、callbackの接続とDataが発行するwaiting/error状態を照合する。

## 調査・変更の入口

[WebServerDialog](../../../feature/web/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/web/WebServerDialog.kt)、[WebServerDialogTest](../../../feature/web/ui/src/test/kotlin/dev/terashima/yomitorirss/ui/WebServerDialogTest.kt) を起点に責務と呼び出し側を確認する。

関連: [web domain](web-domain.md) / [web data](web-data.md) / [全体構成](../../architecture/system.md)。
