---
type: module
title: Chat UI — 履歴選択とストリーミング会話
description: チャット画面の送信制御、履歴保存と生成の順序、失敗時の表示状態を説明する。
tags:
  - chat
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-53b36ffb0640bdad2b34450f
    resource: repo://feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatViewModel.kt
  - id: openwiki-source-251f046dca5bdc7b68a2e763
    resource: repo://feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/MarkdownMessage.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Chat UI — 履歴選択とストリーミング会話

`:feature:chat:ui`

## 画面の入口と所有状態

ChatRouteはChatViewModelのStateFlowを購読し、初期読込中はprogress indicator、完了後はAiChatScreenを表示する。画面のセッション選択・新規会話・送信操作はViewModelへ渡り、具体的なDBや推論engineには接続しない。ChatUiStateは履歴、選択モデル、進捗、途中応答、送信中フラグ、エラーを所有する一時的な表示状態で、durableな履歴の正本はRepository側にある。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [CHAT_ROUTE](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/NavigationDestination.kt) / [CHAT_TITLE](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/NavigationDestination.kt) | 公開const。app navigationが使用するrouteと表示名。 |
| [ChatRoute](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatRoute.kt) / [AiChatScreen](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/AiChatScreen.kt) | 公開Composable群。Routeはinitializedを確認してstateと操作callbackをScreenへ渡す。Screenはsession選択、新規会話、入力、途中応答・進捗・errorを表示する。 |
| [ChatUiState](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatViewModel.kt) / [ChatViewModel](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatViewModel.kt) / [Factory](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatViewModel.kt) | data classと公開class群。session/messagesとmodel/progress/streamを合わせ、selectSession/startNewSession/sendMessageを公開する。 |
| [MarkdownMessage](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/MarkdownMessage.kt) | internal Composable。会話本文をdesignsystemのMarkdownTextへ渡す表示adapter。 |

## 主要なAPI・構成要素の接続

`sendMessage(text)`は空入力、送信中、model未選択では開始しない。新規sessionが必要なら`createSession`、USER保存→`reloadSession`→`ChatGenerator.reply`→ASSISTANT保存→再読込の順に進む。例外時も既存sessionを再読込し、finallyでsendingと途中応答を解除する。USER保存成功後の推論失敗は入力を履歴に残す。

`selectSession`と`startNewSession`は送信中に操作しない。新規ボタンはUIを空にするだけで、session永続作成は最初の送信時に行う。generatorの三FlowはViewModel寿命でcollectし、streamはsending中だけstateに残す。`Factory`はDomain Repository/Generatorを受け、app compositionで作った[具体実装](chat-data.md)へ接続する。


## 送信フローと失敗

初期読込ではセッション一覧の先頭を選び、対応するメッセージを取得する。新規会話操作だけでは永続セッションを作らず、最初の有効な送信時に作成する。送信は入力をtrimし、空入力・送信中・モデル未選択なら開始しない。USERメッセージを保存して履歴を再取得し、generatorへturn列を渡した後、確定したASSISTANT応答を保存して再読込する。

送信中はセッション切替と新規会話を抑止する。途中応答のFlowは送信中だけ表示し、応答開始フラグを更新する。例外時はエラーを表示して履歴の再読込を試みるため、生成前に保存したUSER入力を確認できる。finallyで送信状態と途中文字列を解除する。生成は対話としてviewModelScopeに属し、KnowledgeやSummaryのdurable background taskとは実行単位が異なる。

表示変更はAiChatScreenやMarkdownMessage、操作順序の変更はChatViewModelから調べる。ChatUiStateTestは初期状態を検証するが、送信中の失敗や画面操作をすべて検証するテストではない。

## 調査・変更の入口

- [ChatRoute.kt](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatRoute.kt)
- [ChatViewModel.kt](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatViewModel.kt)
- [AiChatScreen.kt](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/AiChatScreen.kt)
- [ChatUiStateTest.kt](../../../feature/chat/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/chat/ChatUiStateTest.kt)

関連モジュール: [chat-domain](chat-domain.md)、[chat-data](chat-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
