# ADR-0071: LiteRT-LMモデル内蔵SentencePieceで実トークン数を計測する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0027 では長文記事の階層要約をモデルのコンテキスト予算に合わせるため、採用中の LiteRT-LM Kotlin API から任意文字列をtokenizeできない間の暫定策として、UTF-16文字数を `1文字 = 1.2 tokens` と見積もっていた。

この推定は入力超過を避けるためには有効だが、日本語、英語、URL、コードなどで文字数とトークン数の比率が変わるため、実際にはまだ余裕がある入力を分割することがある。階層要約では分割数がそのままローカルLLMの推論回数、電池消費、発熱、処理時間へ影響するため、選択中モデルと同じtokenizerで入力予算を判断したい。

採用中の `com.google.ai.edge.litertlm:litertlm-android:0.14.0` の公開 Kotlin API には、分割前の任意文字列をtokenizeするAPIがない。`Conversation.getTokenCount()` はConversationのKV cache使用量を返すAPIであり、事前の入力分割には適さない。低レベルSessionのprefillをtoken count用途へ転用する案も、要約の分割探索では同じ文章を何度も測るため推論runtimeを起動するコストが大きすぎる。

一方、LiteRT-LMの `.litertlm` ファイル形式はsection metadataを持ち、`SP_Tokenizer` をモデルパッケージへ格納できる。現在固定しているGemma 4 E2BのLiteRT-LM配布物にも約4.7MBの `SP_Tokenizer` sectionが含まれる。

別途 `tokenizer.json` やSentencePiece modelをアプリへ固定同梱すると、APKサイズが増えるだけでなく、ダウンロードした `.litertlm` とtokenizerのversionがずれた際に誤った入力予算を計算する危険がある。

当初は抽出したSentencePieceをJavaCPP Presetsから実行する案を実装した。しかし `org.bytedeco:sentencepiece:0.2.1-1.5.13` はAndroid native classifierを公開しておらず、PR CIで `sentencepiece-0.2.1-1.5.13-android-arm64.jar` を解決できないことを確認した。JavaCPP本体がAndroidをサポートしていても、SentencePiece presetの配布物としてAndroid実行経路がないため、この案は採用しない。

ADR-0056では、`core:ai-runtime` はtokenizerや推論runtimeのような技術能力を提供し、要約の分割方法や予約トークン数などのfeature固有ポリシーは各featureが所有すると定めている。

## Decision

### 1. 選択中モデルと同梱されたSentencePieceを唯一のtokenizer sourceとする

`core:ai-runtime` はダウンロード済み `.litertlm` のheaderとFlatBuffers section metadataを読み、`SP_Tokenizer` sectionを特定する。

SentencePiece modelは別途ネットワークから取得せず、リポジトリやAPKにも固定資材として追加しない。必要時に `.litertlm` 内のsectionだけをアプリのcache directoryへ抽出する。

抽出cacheはモデルファイルのsizeとlast-modifiedを識別子へ含める。モデルが差し替わった場合は古いtokenizer cacheを再利用しない。モデル削除時には対応するcacheも削除する。

`.litertlm` のmajor format versionは現在のversion 1だけを受け入れる。magic number、header範囲、section範囲、tokenizer sizeを検証し、不正または未対応の形式は推測して読み進めず失敗させる。

### 2. SentencePiece ModelProtoは必要フィールドだけを読み、BPEを純粋Kotlinで実行する

Android native classifierを持たない外部SentencePiece wrapperには依存しない。`core:ai-runtime` に小さなprotobuf readerを置き、埋め込みSentencePiece `ModelProto` からtoken countに必要な次の情報だけを読む。

- `pieces`: piece文字列、score、type
- `TrainerSpec`: BPE model type、byte fallback、whitespace suffix設定
- `NormalizerSpec`: identity、dummy prefix、余分な空白除去、space escape

BPE mergeはSentencePiece本体の規則に合わせ、scoreが高い候補を優先し、同scoreでは左側の候補を優先する。`USER_DEFINED` は初期longest-prefix matchでfreezeし、`UNUSED` pieceはreverse mergeを保存して最終resegmentationを行う。未知pieceは `byte_fallback=true` の場合UTF-8 byte数へ展開する。

現在のGemma 4 tokenizerはidentity normalizerを使う。将来の選択モデルが `precompiled_charsmap` を含むnormalizerやidentity以外の正規化規則を要求する場合、JavaのNFKC等で近似せず明示的に失敗させる。モデルと異なるtoken数を「実トークン数」として扱うことを避けるためである。そのモデルを正式対応するときに正規化実装またはLiteRT-LM公開tokenization APIへの移行を行う。

protobuf parserはwire type、length、piece数、piece長を検証する。tokenizer本体や生成済みprotobufコード、外部tokenizer assetは追加しない。

### 3. `core:ai-runtime` は汎用token count capabilityだけを公開する

`LocalModelManager` に `countTokens(text: String): Int` を追加する。

このAPIは選択中モデルの埋め込みSentencePieceで、与えられた文字列そのもののtoken数を返す。要約、チャット、ナレッジなどの業務概念や入力予算ポリシーは持たない。

解析済みtokenizerはモデル単位でcacheし、同じモデルに対する繰り返し計測で再ロードしない。アクセスはlockで直列化し、モデル削除・manager終了時にcache参照を破棄する。

### 4. Featureは実トークン数に独自の予約領域を加えて予算を決める

`feature:summary:data` はADR-0027の次の予約領域を維持する。

- output reserve: 768 tokens
- runtime/template reserve: 256 tokens

`countTokens()` はrender済みpromptのSentencePiece token数を数えるが、LiteRT-LM内部で追加される制御tokenや将来のprompt template差分、生成出力までは含まない。そのため「実トークン数を使う」ことは予約領域をゼロにすることを意味しない。

要約featureは直接要約判定、チャンク分割、reduction時のgrouping、最終入力検証のすべてを `countTokens()` と同じ予約ルールで行う。文字数からの `1.2倍` 推定は廃止する。

### 5. LiteRT-LMが安定したKotlin tokenization APIを公開したら移行を再検討する

独自の `.litertlm` section parser、SentencePiece ModelProto reader、BPE counterは、現行安定版Kotlin APIの不足を補うadapterである。LiteRT-LMのC/Python APIにはengine tokenization capabilityがあるため、将来、採用可能な安定版Kotlin APIから任意文字列のtoken IDsまたはtoken countを取得でき、選択モデルとの一致もruntime側で保証されるようになった場合は、そのAPIへ移行し、アプリ側tokenizer実装を削除することを優先する。

## Consequences

### Positive

- 日本語、英語、URL、コードなど入力内容による文字/token比の差をGemma 4の実tokenizer定義で扱える。
- 直接要約できる入力を文字数推定だけで過剰分割するケースを減らせる。
- E2B/E4Bなどモデルを切り替えても、選択中 `.litertlm` とtokenizerが同じ配布物に由来する。
- 32MB級のtokenizer JSONや別のSentencePiece modelをAPKへ重複同梱する必要がない。
- SentencePiece/JavaCPPの追加native libraryをAPKへ入れずに済む。
- token count capabilityをcoreに置き、要約の分割ポリシーをfeatureに残すためADR-0056の責務境界を維持できる。

### Negative

- `.litertlm` version 1のheader/section metadataとSentencePiece ModelProtoの必要部分を読むadapterをアプリ側で保守する必要がある。
- SentencePiece BPE merge規則の小さな互換実装を保守する必要がある。
- identity以外のprecompiled normalizerを使うモデルは、対応を追加するまでtoken count capabilityを利用できない。
- 初回token count時に埋め込みSentencePieceをcacheへ抽出・解析するI/Oとメモリ使用が発生する。
- SentencePiece token数だけではLiteRT-LM内部の制御tokenまで完全には表現しないため、runtime reserveは引き続き必要である。

## Rejected alternatives

### JavaCPP Presets SentencePiece

SentencePiece presetのAndroid arm64 artifactがMaven Centralへ公開されておらず、PR CIで依存解決に失敗するため却下した。

### LiteRT-LM Conversation/Sessionをtoken counterとして使う

0.14.0のKotlin APIには任意文字列を直接tokenizeするAPIがない。Conversation KV cacheやprefillを使うとモデル処理を伴い、要約チャンク境界の多数回probeには高コストなため却下した。

### 外部tokenizer JSON/modelを別ダウンロードする

モデルとのrevision不一致、追加通信、重複ストレージが生じるため却下した。

### 一般的なUnicode NFKCでSentencePiece normalizerを近似する

SentencePieceのprecompiled normalizationと完全一致しない可能性があるため却下した。対応外normalizerはfail-fastする。

## Security and public repository considerations

- tokenizerやモデル本体をGitリポジトリへ追加しない。
- 新しい認証情報、API key、ユーザー識別子、ユーザーコンテンツをソースへ保存しない。
- 新しい外部通信先は追加しない。tokenizerは既にユーザーがダウンロードした `.litertlm` から端末内で抽出する。
- `.litertlm` parserはファイル内offsetとsizeを検証し、想定外の巨大sectionやファイル範囲外参照を拒否する。
- SentencePiece protobuf readerは不正length、未対応wire type、過大piece数/長を拒否する。

## Relationship to existing ADRs

- ADR-0020: Gemma 4 / LiteRT-LMをローカルAI runtimeとして利用する判断を維持し、そのモデルパッケージからtokenizer capabilityを取り出す。
- ADR-0027: 文字数推定による暫定token budgetを、本ADRの実SentencePiece token countで置き換える。
- ADR-0056: `core:ai-runtime` は汎用token count capabilityだけを持ち、予約領域・分割・階層要約は `feature:summary` が所有する。
- ADR-0069: application scopeで共有する選択中AIモデルを利用し、featureごとに別tokenizerを選択しない。

## References

- LiteRT-LM `.litertlm` header / section schema
- LiteRT-LM builder `add_sentencepiece_tokenizer`
- LiteRT-LM C API `litert_lm_engine_tokenize`
- SentencePiece `sentencepiece_model.proto`
- SentencePiece BPE `bpe_model.cc`
- `core/ai-runtime/.../LiteRtLmTokenizer.kt`
- `core/ai-runtime/.../SentencePieceBpeTokenCounter.kt`
- `core/ai-runtime/.../LocalModelManager.kt`
- `feature/summary/data/.../HierarchicalSummary.kt`
