---
type: module
title: Chat Data — 履歴保存とローカル会話生成
description: 履歴の保存順序と保持上限、ローカルモデルへのprompt・tool接続、生成失敗を説明する。
tags: [chat, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-0a8a55dab7402c8d05fbc822
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatStore.kt
  - id: openwiki-source-e0dc1953ec24a90da1e8d1da
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LocalChatGenerator.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Chat Data — 履歴保存とローカル会話生成

`:feature:chat:data`

## 責務と入力経路

`DefaultChatRepository`はChatStoreへ履歴操作を委譲し、`LocalChatGenerator`はcore runtimeの`LocalConversationInference`をChat契約へ適合する。UIが履歴と生成を組み合わせるため、このモジュールは保存操作と生成操作を別々に提供する。generatorは選択モデルを確認し、最後のUSER本文からcontext providerを呼び、モデルの入力予算に従ってChatPromptを組み立てる。

## 永続化と生成の状態

ChatStoreは`chat_sessions`と`chat_messages`を使う。セッションは更新日時の降順、メッセージはID昇順で読む。メッセージ追加とセッション更新を同一transactionに置き、空白だけの本文と存在しないセッションへの追加を拒否する。新規作成・追加時には最新5セッションを残すので、履歴の保持方針を変更する際はstoreとschemaの削除関係を確認する。

生成側は資料と会話をruntime requestへ変換し、事前登録済みskillをinference toolへ変換する。tool名の重複は初期化時に拒否する。AppResourceSkillsとKnowledgeLibraryResourceSkillsは各featureのDomain capabilityを使って読み取りを実行し、Chatが資料テーブルのownerになる構成にはしない。

streamの途中値はChatResponseStreamで表示用に整え、確定時に完成文字列を返す。CancellationExceptionは伝播させ、Gemmaのtool-call parse failureだけを有限回再試行し、その他の失敗は呼び出し側へ返す。変更箇所に応じてChatPromptTest、ChatResponseStreamTest、ToolCallRecoveryTest、LexicalRetrievalTestを参照する。RepositoryContractTestは型契約の検査であり、SQLite保存の統合検証とは区別する。

## 調査・変更の入口

- [ChatStore.kt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatStore.kt)
- [LocalChatGenerator.kt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LocalChatGenerator.kt)
- [ChatPrompt.kt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatPrompt.kt)
- [ToolCallRecoveryTest.kt](../../../feature/chat/data/src/test/kotlin/dev/terashima/yomitorirss/feature/chat/data/ToolCallRecoveryTest.kt)
- [DefaultChatRepositoryContractTest.kt](../../../feature/chat/data/src/test/kotlin/dev/terashima/yomitorirss/feature/chat/data/DefaultChatRepositoryContractTest.kt)

関連モジュール: [chat-domain](chat-domain.md)、[chat-ui](chat-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
