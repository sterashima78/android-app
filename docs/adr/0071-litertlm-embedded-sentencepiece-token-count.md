# ADR-0071: LiteRT-LMモデル内蔵SentencePieceで実トークン数を計測する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0027 では長文記事の階層要約をモデルのコンテキスト予算に合わせるため、採用中の LiteRT-LM Kotlin API から任意文字列をtokenizeできない間の暫定策として、UTF-16文字数を `1文字 = 1.2 tokens` と見積もっていた。

この推定は入力超過を避けるためには有効だが、日本語、英語、URL、コードなどで文字数とトークン数の比率が変わるため、実際にはまだ余裕がある入力を分割することがある。階層要約では分割数がそのままローカルLLMの推論回数、電池消費、発熱、処理時間へ影響するため、選択中モデルと同じtokenizerで入力予算を判断したい。

採用中の `com.google.ai.edge.litertlm:litertlm-android:0.14.0` の公開 Kotlin API には、分割前の任意文字列をtokenizeするAPIがない。`Conversation.getTokenCount()` はConversationのKV cache使用量を返すAPIであり、事前の入力分割には適さない。一方、LiteRT-LMの `.litertlm` ファイル形式はsection metadataを持ち、`SP_Tokenizer` をモデルパッケージへ格納できる。Gemma 4向けLiteRT-LMパッケージもSentencePiece tokenizerをモデルと同梱する。

別途 `tokenizer.json` やSentencePiece modelをアプリへ固定同梱すると、APKサイズが増えるだけでなく、ダウンロードした `.litertlm` とtokenizerのversionがずれた際に誤った入力予算を計算する危険がある。

ADR-0056では、`core:ai-runtime` はtokenizerや推論runtimeのような技術能力を提供し、要約の分割方法や予約トークン数などのfeature固有ポリシーは各featureが所有すると定めている。

## Decision

### 1. 選択中モデルと同梱されたSentencePieceを唯一のtokenizer sourceとする

`core:ai-runtime` はダウンロード済み `.litertlm` のheaderとFlatBuffers section metadataを読み、`SP_Tokenizer` sectionを特定する。

SentencePiece modelは別途ネットワークから取得せず、リポジトリやAPKにも固定資材として追加しない。必要時に `.litertlm` 内のsectionだけをアプリのcache directoryへ抽出する。

抽出cacheはモデルファイルのsizeとlast-modifiedを識別子へ含める。モデルが差し替わった場合は古いtokenizer cacheを再利用しない。モデル削除時には対応するcacheも削除する。

`.litertlm` のmajor format versionは現在のversion 1だけを受け入れる。magic number、header範囲、section範囲、tokenizer sizeを検証し、不正または未対応の形式は推測して読み進めず失敗させる。

### 2. SentencePieceのAndroid実行にはJavaCPP presetを利用する

`core:ai-runtime` は `org.bytedeco:sentencepiece:0.2.1-1.5.13` とAndroid arm64 native runtimeを利用し、抽出したSentencePiece modelを `SentencePieceProcessor` へ読み込む。

本アプリは既に `arm64-v8a` のみを配布対象としているため、Android arm64 classifierだけをruntime dependencyとする。JavaCPP PresetsはApache-2.0またはGPL+Classpath Exceptionのデュアルライセンスで提供されているため、本アプリではApache-2.0の条件で利用する。SentencePiece本体もApache-2.0である。

### 3. `core:ai-runtime` は汎用token count capabilityだけを公開する

`LocalModelManager` に `countTokens(text: String): Int` を追加する。

このAPIは選択中モデルの埋め込みSentencePieceで、与えられた文字列そのもののtoken数を返す。要約、チャット、ナレッジなどの業務概念や入力予算ポリシーは持たない。

SentencePiece processorはモデル単位でcacheし、同じモデルに対する繰り返し計測で再ロードしない。processorへのアクセスはlockで直列化し、モデル削除・manager終了時にcloseする。

### 4. Featureは実トークン数に独自の予約領域を加えて予算を決める

`feature:summary:data` はADR-0027の次の予約領域を維持する。

- output reserve: 768 tokens
- runtime/template reserve: 256 tokens

`countTokens()` はrender済みpromptのSentencePiece token数を正確に数えるが、LiteRT-LM内部で追加される制御tokenや将来のprompt template差分、生成出力までは含まない。そのため「実トークン数を使う」ことは予約領域をゼロにすることを意味しない。

要約featureは直接要約判定、チャンク分割、reduction時のgrouping、最終入力検証のすべてを `countTokens()` と同じ予約ルールで行う。文字数からの `1.2倍` 推定は廃止する。

### 5. LiteRT-LMが安定したKotlin tokenization APIを公開したら移行を再検討する

独自の `.litertlm` section parserとJavaCPP dependencyは、現行Kotlin APIの不足を補うadapterである。将来、採用可能な安定版LiteRT-LMから任意文字列のtoken IDsまたはtoken countを取得でき、選択モデルとの一致もruntime側で保証されるようになった場合は、そのAPIへ移行し、このparserとSentencePiece dependencyを削除することを優先する。

## Consequences

### Positive

- 日本語、英語、URL、コードなど入力内容による文字/token比の差を実際のGemma 4 tokenizerで扱える。
- 直接要約できる入力を文字数推定だけで過剰分割するケースを減らせる。
- E2B/E4Bを切り替えても、選択中 `.litertlm` とtokenizerが常に同じ配布物に由来する。
- 32MB級のtokenizer JSONなどをAPKへ重複同梱する必要がない。
- token count capabilityをcoreに置き、要約の分割ポリシーをfeatureに残すためADR-0056の責務境界を維持できる。

### Negative

- SentencePiece/JavaCPPのAndroid native dependencyがAPKへ追加される。
- `.litertlm` version 1のheader/section metadataを読む小さなadapterをアプリ側で保守する必要がある。
- 初回token count時に埋め込みSentencePieceをcacheへ抽出・ロードするI/Oが発生する。
- SentencePiece token数だけではLiteRT-LM内部の制御tokenまで完全には表現しないため、runtime reserveは引き続き必要である。

## Security and public repository considerations

- tokenizerやモデル本体をGitリポジトリへ追加しない。
- 新しい認証情報、API key、ユーザー識別子、ユーザーコンテンツをソースへ保存しない。
- 新しい外部通信先は追加しない。tokenizerは既にユーザーがダウンロードした `.litertlm` から端末内で抽出する。
- parserはファイル内offsetとsizeを検証し、想定外の巨大sectionやファイル範囲外参照を拒否する。

## Relationship to existing ADRs

- ADR-0020: Gemma 4 / LiteRT-LMをローカルAI runtimeとして利用する判断を維持し、そのモデルパッケージからtokenizer capabilityを取り出す。
- ADR-0027: 文字数推定による暫定token budgetを、本ADRの実SentencePiece token countで置き換える。
- ADR-0056: `core:ai-runtime` は汎用token count capabilityだけを持ち、予約領域・分割・階層要約は `feature:summary` が所有する。
- ADR-0069: application scopeで共有する選択中AIモデルを利用し、featureごとに別tokenizerを選択しない。

## References

- LiteRT-LM `.litertlm` header / section schema
- LiteRT-LM builder `add_sentencepiece_tokenizer`
- LiteRT-LM C API `litert_lm_engine_tokenize`
- JavaCPP Presets SentencePiece 0.2.1-1.5.13
- `core/ai-runtime/.../LiteRtLmTokenizer.kt`
- `core/ai-runtime/.../LocalModelManager.kt`
- `feature/summary/data/.../HierarchicalSummary.kt`
