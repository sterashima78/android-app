---
type: module
title: Chat Domain — 会話と読み取りツールの契約
description: チャット履歴、会話生成、進捗とアプリ内情報参照の公開契約を説明する。
tags: [chat, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-297afba3076a18ad2268c756
    resource: repo://feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatGenerator.kt
  - id: openwiki-source-7ea745fba16ee2f4ebe39b08
    resource: repo://feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatModels.kt
  - id: openwiki-source-f266a37094a2e19181c423cd
    resource: repo://feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Chat Domain — 会話と読み取りツールの契約

`:feature:chat:domain`

## 責務と境界

ChatのDomainは、保存済み会話と推論へ渡す会話を区別し、UIとDataが交換するモデルを所有する。`StoredChatMessage`はセッションID、保存時刻、メッセージIDを持つ履歴であり、`ChatTurn`は生成へ渡すroleと本文だけを持つ。履歴保存は`ChatRepository`、生成は`ChatGenerator`の別契約なので、DB操作を生成実装へ混ぜずに差し替えられる。GradleはJVMモジュールとして構成され、Android画面やSQLiteの型をこの境界へ出さない。

## 会話の入出力

呼び出し側はセッションを作成しUSERメッセージを保存してから、履歴をturn列へ変換して`reply`を呼び、確定した文字列をASSISTANTとして保存する。契約の`selectedModel`、`progress`、`streamingReply`はFlowであり、完了値とは別にモデルの選択・生成段階・途中本文を観測できる。選択なしはnullableなモデル状態で表す。

`ChatContextProvider.contextFor`は検索語に対応するラベル付き資料を返す。`AgentSkill`は定義済みtoolの入口で、実際のowner API呼び出しや検索方針はData側のadapterへ渡る。Domain自身は永続テーブルも推論engineも保持しない。新しい会話情報はまずモデルと契約へ追加し、Dataの変換とUI表示を合わせて確認する。`ChatModelsTest`は進捗のモデル名と時間見積りを省略できることを検証する。

## 調査・変更の入口

- [ChatRepository.kt](../../../feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatRepository.kt)
- [ChatGenerator.kt](../../../feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatGenerator.kt)
- [ChatModels.kt](../../../feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/ChatModels.kt)
- [AgentSkill.kt](../../../feature/chat/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/AgentSkill.kt)
- [ChatModelsTest.kt](../../../feature/chat/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/chat/ChatModelsTest.kt)

関連モジュール: [chat-data](chat-data.md)、[chat-ui](chat-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
