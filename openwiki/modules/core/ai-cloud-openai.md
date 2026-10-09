---
type: module
title: ChatGPT / Codex 認証とクラウド推論 adapter
description: device login、暗号化 credential、モデル選択、自由文・構造化生成と provider failure の正規化を扱う。
tags:
  - ai
  - cloud
  - authentication
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-27c6f4d7d5db5241ceb83796
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/AndroidChatGptCredentialStore.kt
  - id: openwiki-source-f93df2bf9bfd77631ef3e189
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptInferenceClient.kt
  - id: openwiki-source-56c05ddf24deabe06f6a226b
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptOpenAiClient.kt
  - id: openwiki-source-3730592239c3b3c10d1e73e5
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptTextInference.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# ChatGPT / Codex 認証とクラウド推論 adapter

## 認証から推論まで

`:core:ai-cloud-openai` は [network](network.md) の HttpClient と [ai-inference](ai-inference.md) の共通契約を使うクラウド adapter である。[ChatGptOpenAiClient](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptOpenAiClient.kt) が device login の開始・poll、code exchange、logout、model 一覧、生成 request を担当する。credential refresh は mutex 内で直列化し、生成で 401 を受けると強制 refresh 後に再送する。自由文、構造化 tool call、対象 URL を伴う Web search は別の入口である。

[AndroidChatGptCredentialStore](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/AndroidChatGptCredentialStore.kt) は Android Keystore の AES key で credential JSON を AES/GCM 暗号化し、IV と ciphertext を private preferences に保存する。読み取りは復号・parse が失敗すると未接続相当の null を返す。logout は preferences を clear する。接続情報表示は account suffix などに限定される。

## 主要な構成要素と公開契約

| 宣言・種類 | 責務・主要メソッド | 根拠 |
| --- | --- | --- |
| `ChatGptOpenAiClient` class | `create(context, httpClient)` で credential store を接続。`connectionStatus`、`startDeviceLogin`、`pollDeviceLogin`、`logout`、`listModels`、`generate`、`generateToolCall`、`generateWithWebSearch` を公開。 | [protocol client](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptOpenAiClient.kt)。 |
| `ChatGptConnectionStatus` / `ChatGptDeviceLogin` data class / `ChatGptDeviceLoginPollResult` enum | 接続表示、device code/期限/poll 間隔、PENDING/SLOW_DOWN/AUTHORIZED を渡す。 | 同上。 |
| `ChatGptGenerationResult` / `ChatGptWebGenerationResult` / `ChatGptModelInfo` data class | text と model ID、Web の opened URL、catalog の能力/表示/priority を caller へ返す。 | 同上。 |
| `ChatGptInferenceClient` class / `ChatGptProviderException` class / `ChatGptProviderFailureKind` enum | 推論用 wrapper が同じ三種の生成 API と connectionStatus を公開し、安全な失敗分類・retryable/status を提供。 | [failure 境界](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptInferenceClient.kt)。 |
| `ChatGptModelPreferences` class | `selectedModelId` / `selectModel` / `clearSelection` で選択を保持。空 ID は拒否。 | [選択設定](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptModelPreferences.kt)。 |
| `ChatGptTextInference` / `ChatGptStructuredTextInference` class | 共通 background 契約の provider 実装。前者は model metadata、空の progress Flow と UTF-8 byte 数による countTokens を提供し、後者は tool call を返す。 | [自由文](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptTextInference.kt)、[構造化](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptStructuredTextInference.kt)。 |

internal `ChatGptCredentialStore` interface を `AndroidChatGptCredentialStore` が実装し、`ChatGptCredentials` と protocol config は core 内に留める。credential の文字列表現も redacted で、接続表示に token を渡さない。公開の `DEFAULT_CHATGPT_CODEX_MODEL_ID` は初期選択候補であり、実際の選択は preferences から読む。

## 注入・API の流れ

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt) が共通 HTTP から client、failure wrapper、model preferences、text/structured adapter を生成する。device login は開始結果を UI が提示し poll 結果に応じて待機間隔を扱う。AUTHORIZED 時に code exchange した credential を保存し、logout は store を消す。

生成は Worker の共通契約 → adapter → inference wrapper → protocol client → HttpClient の順に進む。client は credential を更新して request を送信し、401 なら一度 refresh して再送する。応答は SSE を集約し自由文か単一 tool call を返す。malformed SSE、複数 tool call、空 text は失敗する。Web search では対象 URL を検査し、応答の open_page 記録が対象ページと一致しなければ失敗する。

catalog は priority と表示名で並べる。自由文 adapter の countTokens は実 tokenizer の token 数ではないので、容量表示や入力予算を変更するときは利用側との契約を確認する。
## feature へ返す失敗

[ChatGptInferenceClient](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptInferenceClient.kt) は生の transport / provider 例外を feature に漏らさず、認証、未接続、rate limit、一時障害、拒否、Web target 未閲覧へ分類する。IOException は retryable な一時障害、429 や 408 / 5xx も再試行可能、401 / 403 は認証失敗である。CancellationException はそのまま伝播する。単発 text / structured adapter は worker 実行境界を継承し、未選択モデルや provider failure を安全な利用者向け説明へ変換する。

## 変更時の確認

[ChatGptProviderFailureTest](../../../core/ai-cloud-openai/src/test/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptProviderFailureTest.kt) が分類と機密を含まないエラーの入口になる。request、response、認証再送の変更は ChatGptOpenAiClientTest と ChatGptOpenAiRetryTest を合わせて確認する。新しい送信対象や Web search を追加するときは provider adapter と caller のデータ送信範囲を追い、credential を UI / domain に渡さない。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/15-privacy-security.md](../../../docs/spec/15-privacy-security.md)。
