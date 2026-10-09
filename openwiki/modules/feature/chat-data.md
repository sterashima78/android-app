---
type: module
title: Chat Data — 履歴保存とローカル会話生成
description: 履歴の保存順序と保持上限、ローカルモデルへのprompt・tool接続、生成失敗を説明する。
tags:
  - chat
  - data
  - module
sources:
  - id: openwiki-source-89882cbab574eaa63d090479
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/crossfeature/AppCrossFeatureRuntimeDependencies.kt
  - id: openwiki-source-149a7ba97d0249a71ab81ba5
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/AppResourceSkills.kt
  - id: openwiki-source-8982e7d0f2c8f1230f35235e
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatResponseStream.kt
  - id: openwiki-source-0a8a55dab7402c8d05fbc822
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatStore.kt
  - id: openwiki-source-1c7685ee176d615dd99bff0b
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/KnowledgeLibraryResourceSkills.kt
  - id: openwiki-source-d5024ffe929f1d8ed162b404
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LexicalRetrieval.kt
  - id: openwiki-source-e0dc1953ec24a90da1e8d1da
    resource: repo://feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LocalChatGenerator.kt
generated: { by: "codex", at: "2026-10-09T08:22:11.354Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T08:22:11.354Z
---

# Chat Data — 履歴保存とローカル会話生成

`:feature:chat:data`

## 責務と入力経路

`DefaultChatRepository`はChatStoreへ履歴操作を委譲し、`LocalChatGenerator`はcore runtimeの`LocalConversationInference`をChat契約へ適合する。UIが履歴と生成を組み合わせるため、このモジュールは保存操作と生成操作を別々に提供する。generatorは選択モデルを確認し、注入されたcontext providerがあれば最後のUSER本文に応じた参照blockを取得して、モデルの入力予算に従ってChatPromptを組み立てる。現行のapp compositionはcontext providerを注入せず、アプリ内データ参照は登録済みskill/tool経由で行う。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [DefaultChatRepository](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/DefaultChatRepository.kt) / [ChatStore](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatStore.kt) | 公開Repositoryとinternal store。session作成・列挙、message追加・取得をDBへ委譲し、storeがタイトル正規化・古いsession整理とtransactionを所有する。 |
| [chatDatabaseSchema](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatDatabaseSchema.kt) | 公開val。DatabaseSchemaContributionとしてchat_sessions/chat_messagesと索引・cascade関係をowner chatから登録する。 |
| [LocalChatGenerator](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LocalChatGenerator.kt) | 公開class。ChatGenerator実装。選択model、context、skillをconversation requestへ変換し、streamとtool形式回復を担う。 |
| [createAppResourceSkills](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/AppResourceSkills.kt) / [LambdaAgentTool](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/AppResourceSkills.kt) | 公開factoryとprivate adapter。RSS、Reddit、保存記事、履歴、taskのowner契約をtoolとして公開する。 |
| [createKnowledgeLibraryResourceSkills](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/KnowledgeLibraryResourceSkills.kt) / [ReadOnlyAgentTool](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/KnowledgeLibraryResourceSkills.kt) | 公開factoryとprivate adapter。KnowledgeとLibraryの候補検索・必要な一件の詳細取得を読み取りtoolとして登録する。 |
| [RenderedChatPrompt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatPrompt.kt) / [ChatPrompt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatPrompt.kt) | internalモデル・object。renderでsystem/参照/履歴/最後のuserを分け、trimTurnsで直近履歴を予算内へ制限する。 |
| [ChatResponseStream](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/ChatResponseStream.kt) | internal object。partial/completeがthink block、制御marker、assistant接頭辞を除き、確定空応答を拒否する。 |
| [RetrievalField](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LexicalRetrieval.kt) / [rankByQuery](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LexicalRetrieval.kt) / [compactExcerpt](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LexicalRetrieval.kt) | internal型・関数。field重み、全検索語一致、phrase加点、同点の元順保持と抜粋を担う。 |
| [commonSearchArguments](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/AppResourceSkills.kt) / [recentBookmarks](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/AppResourceSkills.kt) / [isGemmaToolCallParseFailure](../../../feature/chat/data/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/data/LocalChatGenerator.kt) | internal補助関数群。省略可能query、保存時刻順の既定候補、cause chain内のtool解析失敗判定を担う。最後の関数はLocalChatGenerator.ktにある。 |

## 主要なAPI・構成要素の接続

`createAppResourceSkills`はownerのRepositoryを受け、候補検索と保存済み記事の詳細、最近取得、task状態照会をtoolへ結び付ける。`createKnowledgeLibraryResourceSkills`はKnowledgeReaderとLibraryReaderだけを受ける。アプリのKnowledgeは蓄積知識であり、このリポジトリのOpenWikiとは別の情報である。

`LocalChatGenerator.reply`は選択modelと最後のUSERを検査し、注入されたcontext providerがあれば順に呼んで`ChatPrompt.render`へ渡す。skill toolは名前一意を検査してruntime toolへ変換し、結果文字数を制限する。`generateWithToolCallRecovery`はtool call解析失敗だけ追加指示付きで一度再試行し、cancelは再throwする。stream chunkは累積全文と差分の両方を扱い、UIには`ChatResponseStream.partial`、永続保存には`complete`の結果を返す。

[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)がDB接続をRepositoryへ、[AppCrossFeatureRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/crossfeature/AppCrossFeatureRuntimeDependencies.kt)がLocalConversationInferenceと両skill factoryをgeneratorへ注入する。`ChatStore.appendMessage`はmessage挿入とsession更新を同じtransactionにし、sessionが存在しない場合は保存を失敗させる。

`LocalChatGenerator`の`contextProviders`を省略すると空の既定値が使われる。現行構成ではapp data skillがinference toolとして生成要求へ渡り、モデルがtoolを呼び出した時に各owner APIを実行する。


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
