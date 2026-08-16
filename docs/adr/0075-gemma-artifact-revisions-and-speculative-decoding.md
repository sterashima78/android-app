# ADR-0075: Gemma 4 artifact revisionとSpeculative Decodingを管理する

- Status: Accepted
- Date: 2026-08-16

## Context

ローカルAIはADR-0020によりGemma 4 / LiteRT-LMへ統一している。

Gemma 4 E2B / E4Bのモデルcatalogは、ファイルサイズについては2026-05-04に更新されたartifactの値を保持していた一方、ダウンロードURLは2026-04-01の古いrevisionへ固定されていた。また、ダウンロード済み判定が想定サイズの±2%だけだったため、artifactのrevisionを区別できなかった。

LiteRT-LMのGemma 4向けモデルは2026-05-05時点でSpeculative Decoding対応が案内されている。案内では、それ以前に取得したモデルはSpeculative Decodingを利用するため再ダウンロードが必要とされている。

LiteRT-LM 0.14.0では `ExperimentalFlags.enableSpeculativeDecoding` によりEngine生成時にSpeculative Decodingを指定できる。この値は新しいEngineを生成するときだけ読み込まれるグローバルなexperimental flagである。

## Decision

### モデルartifactをrevision固定する

Gemma 4の汎用LiteRT-LM artifactを次のrevisionへ更新する。

- Gemma 4 E2B: `6e5c4f1e395deb959c494953478fa5cec4b8008f`
- Gemma 4 E4B: `28299f30ee4d43294517a4ac93abd6163412f07f`

モデル定義はダウンロードURLだけでなく `artifactRevision` を保持する。

ダウンロード完了時に、取得したartifactのrevisionをローカル設定へ保存する。利用可能判定はファイルサイズとrevisionの両方が現在のモデル定義と一致することを条件とする。

既存インストールについては、現在revisionと完全に同じファイルサイズを持つartifactのみrevision markerを補完する。古いartifactは起動時にモデルファイルとruntime cacheを削除し、再ダウンロードが必要な状態へ戻す。

この仕組みにより、今後同じモデルIDのartifactを更新するときもrevision変更によって旧artifactを無効化できる。

### Speculative Decodingをユーザー設定として公開する

Speculative Decodingは既定で無効とする。

モデル管理画面では、選択中モデルがSpeculative Decoding対応の場合だけ切り替えを表示する。設定値はローカルAIの推論設定として永続化する。

推論開始時は、選択モデルが対応している場合に限って設定値を有効化する。未対応モデルへ誤って有効化しない。

### Engine lifecycleへ設定値を含める

`ExperimentalFlags.enableSpeculativeDecoding` はEngine生成時だけ評価されるため、次を行う。

- Engine cache keyへSpeculative Decodingの有効/無効を含める
- backend cache directoryをstandard/speculativeで分離する
- 設定変更後の次回推論では別条件としてEngineを再生成する
- Engine生成中だけ `ExperimentalFlags.enableSpeculativeDecoding` を設定し、生成後は元の値へ戻す
- グローバルflagの競合を避けるためEngine初期化部分をプロセス内lockで直列化する

### 要約cacheを分離する

Speculative Decodingの有効/無効を `LocalModelManager.inferenceCacheVariant` に反映する。

Speculative Decodingは通常、生成結果の意味を変える設定ではないが、推論経路が変わるため、既存の要約cacheとの混在を避けて検証可能性を保つ。

## Consequences

アップデート時、2026-04-01版のGemma 4を保存している端末ではモデルが削除され、利用者による再ダウンロードが必要になる。数GBのモデルをアプリ更新だけで自動取得しない。

Speculative Decodingによる速度向上は端末、backend、入力、生成長によって異なるため既定OFFとする。利用者はモデル管理画面から明示的に有効化できる。

LiteRT-LMのAPIはexperimentalであるため、将来のruntime更新時にはAPI互換性を確認する必要がある。

## References

- ADR-0020: ローカルAIをGemma 4 / LiteRT-LMへ統一する
- LiteRT-LM 0.14.0 `ExperimentalFlags.enableSpeculativeDecoding`
- `litert-community/gemma-4-E2B-it-litert-lm`
- `litert-community/gemma-4-E4B-it-litert-lm`
