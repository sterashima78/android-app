# ADR-0164: 単発テキスト推論を provider 非依存 capability として分離する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0079](0079-process-wide-local-ai-inference-sessions.md), [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md)

## Context

現在の AI 利用は LiteRT-LM の `LocalModelManager` を application scope で共有し、Summary、Knowledge、Library、Chat など複数 feature から利用している。ADR-0056 により prompt、階層要約、Library 整理、Chat/Agent などの feature 固有ポリシーは owning feature が保持し、`core:ai-runtime` はローカル推論の技術 capability に限定している。

一方、単発テキスト生成を行う feature も concrete な `LocalModelManager` を直接受け取るため、feature の生成ポリシーと LiteRT-LM runtime の選択が型として結合している。将来ローカル以外の推論実装を追加する場合、この結合を残したまま provider 選択を feature ごとに増やすと、認証・transport・モデル能力・キャッシュ識別・進捗の扱いが各 feature へ漏れる。

ただし、将来の provider を先回りして cloud routing、認証、privacy policy、task type を core に持ち込むべきではない。また Chat は Conversation / Tool Calling / streaming recovery を利用し、SMB 書誌正規化の Vision 推論は ADR-0159 により専用 process とメモリ境界を持つため、単純なテキスト生成と同じ抽象へ統合すると既存の責務境界を弱める。

## Decision

### 1. `:core:ai-inference` を provider 非依存の技術 capability として追加する

新しい JVM module `:core:ai-inference` は、単発テキスト推論を利用する側が必要とする最小 contract を所有する。

- `AiTextInference`
  - 選択中モデルの取得
  - token count
  - 単発テキスト生成
  - 推論進捗の観測
- `AiTextInferenceModel`
  - model id / name
  - context token 数
  - feature が入力を組み立てるための文字数 budget
  - キャッシュ同一性を表す `cacheIdentity`
- `AiTextInferenceProgress`
  - model preparation
  - response generation

この module は Android、LiteRT-LM、記事、要約、蔵書、Knowledge、Chat、provider 名、認証、network transport を知らない。

### 2. `:core:ai-runtime` はローカル runtime のまま維持する

`LocalModelManager` は引き続き次を所有する。

- Gemma / LiteRT-LM model catalog と model file
- CPU / GPU backend と context 設定
- tokenizer
- LiteRT-LM Engine cache / session / idle release
- local model benchmark
- Vision / Conversation を含むローカル固有 capability

`LocalAiTextInference` を `:core:ai-runtime` に追加し、`LocalModelManager` の単発テキスト capability を `AiTextInference` へ投影する。

`cacheIdentity` は local model id と runtime cache variant を含め、backend/context 等の生成条件が変わった場合に feature cache が同一生成条件と誤認しないようにする。

### 3. feature 固有生成ロジックは移動しない

次の責務は引き続き owning feature に残す。

- Summary prompt、階層要約、chunk/reduction/final progress
- Bookmark metadata prompt と validation
- Knowledge source 選定、prompt、生成文書の parse
- Library organization prompt、JSON schema validation、repair

`:core:ai-inference` はこれらの task semantics を持たない。

### 4. Chat と Vision は今回の contract 対象外とする

Chat は構造化 Conversation、Tool Calling、streaming response、Gemma tool-call recovery を持つため、`AiTextInference` へ縮退させない。

SMB metadata normalization の Vision 推論は画像入力、Tool Call、専用 process、Engine memory lifetime を持つため、ADR-0145 / ADR-0159 の境界を維持する。

将来これらに複数 provider が必要になった場合は、それぞれの要求に合う capability を別途設計する。

### 5. 導入を挙動変更と feature migration に分離する

最初の変更では `:core:ai-inference` と `LocalAiTextInference` を追加するだけとし、既存 feature の実行先、queue、優先度、Local AI gate、model selection は変更しない。

後続変更で Summary、Knowledge、Library organization の順に concrete `LocalModelManager` 依存を `AiTextInference` へ置き換える。移行完了時も実装は `LocalAiTextInference` のみを composition し、生成結果・実行順・ローカル排他制御を維持する。

cloud provider、provider routing、OAuth、外部送信可否はさらに後続の独立した設計判断とする。

## Consequences

### Positive

- feature 固有生成ポリシーを維持したまま、単発テキスト生成と LiteRT-LM concrete runtime を分離できる。
- 後続 provider を追加しても Summary / Knowledge / Library が transport や認証を直接知る必要がない。
- local runtime の Engine lifecycle、benchmark、Vision、Chat capability を無理に共通化しない。
- feature migration と cloud 実行追加を別 PR として検証できる。

### Negative

- 移行期間中は `AiTextInference` と `LocalModelManager` の直接利用が併存する。
- token count を共通 contract に含めるため、将来 provider は同等の計数または安全側の実装を提供する必要がある。
- Chat / Vision にはこの抽象を再利用できない。

## Verification

- `:core:ai-inference` の model invariant unit test
- `LocalModelStatus` / `LocalInferenceProgress` から provider-neutral model / progress への mapping test
- `verifyArchitecture`
- module map verification
- ADR integrity verification
- public repository verification

## Public repository review

本変更は型、adapter、テスト、Gradle module、architecture documentation のみを追加する。credential、token、OAuth client secret、外部 account id、実ユーザー prompt、実記事、実蔵書、SMB path、診断 artifact を repository に追加しない。

## References

- [ADR-0003](0003-multi-module-architecture.md)
- [ADR-0056](0056-feature-owned-local-ai-policies.md)
- [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0079](0079-process-wide-local-ai-inference-sessions.md)
- [ADR-0145](0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0159](0159-isolate-smb-vision-inference-process.md)
- [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md)
