---
type: module
title: ChatGPT / Codex 認証とクラウド推論 adapter
description: device login、暗号化 credential、モデル選択、自由文・構造化生成と provider failure の正規化を扱う。
tags: [ai, cloud, authentication, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-27c6f4d7d5db5241ceb83796
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/AndroidChatGptCredentialStore.kt
  - id: openwiki-source-f93df2bf9bfd77631ef3e189
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptInferenceClient.kt
  - id: openwiki-source-56c05ddf24deabe06f6a226b
    resource: repo://core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptOpenAiClient.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# ChatGPT / Codex 認証とクラウド推論 adapter

## 認証から推論まで

`:core:ai-cloud-openai` は [network](network.md) の HttpClient と [ai-inference](ai-inference.md) の共通契約を使うクラウド adapter である。[ChatGptOpenAiClient](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptOpenAiClient.kt) が device login の開始・poll、code exchange、logout、model 一覧、生成 request を担当する。credential refresh は mutex 内で直列化し、生成で 401 を受けると強制 refresh 後に再送する。自由文、構造化 tool call、対象 URL を伴う Web search は別の入口である。

[AndroidChatGptCredentialStore](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/AndroidChatGptCredentialStore.kt) は Android Keystore の AES key で credential JSON を AES/GCM 暗号化し、IV と ciphertext を private preferences に保存する。読み取りは復号・parse が失敗すると未接続相当の null を返す。logout は preferences を clear する。接続情報表示は account suffix などに限定される。

## feature へ返す失敗

[ChatGptInferenceClient](../../../core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptInferenceClient.kt) は生の transport / provider 例外を feature に漏らさず、認証、未接続、rate limit、一時障害、拒否、Web target 未閲覧へ分類する。IOException は retryable な一時障害、429 や 408 / 5xx も再試行可能、401 / 403 は認証失敗である。CancellationException はそのまま伝播する。単発 text / structured adapter は worker 実行境界を継承し、未選択モデルや provider failure を安全な利用者向け説明へ変換する。

## 変更時の確認

[ChatGptProviderFailureTest](../../../core/ai-cloud-openai/src/test/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptProviderFailureTest.kt) が分類と機密を含まないエラーの入口になる。request、response、認証再送の変更は ChatGptOpenAiClientTest と ChatGptOpenAiRetryTest を合わせて確認する。新しい送信対象や Web search を追加するときは provider adapter と caller のデータ送信範囲を追い、credential を UI / domain に渡さない。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/15-privacy-security.md](../../../docs/spec/15-privacy-security.md)。
