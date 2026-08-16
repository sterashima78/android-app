# ADR-0027: 長文記事は階層的に分割要約する

- Status: Accepted
- Date: 2026-08-13
- Amended: 2026-08-16

## Context

記事要約では、`ArticleContentClient` が本文を最大40,000文字まで取得する一方、ローカルAIへ渡す直前にモデルごとの `maxInputChars` へ縮小していた。上限を超えた場合は本文の先頭75%と末尾25%だけを残していたため、中盤にある固有名詞、数値、主張、結論などが要約対象から完全に外れる可能性があった。

2026-08-13に階層要約を導入し、本文を破棄せず2,500文字単位で分割する方針へ変更した。しかし2,500文字はコンテキスト長そのものではなく安全側に固定した文字数上限であり、2,500文字をわずかに超えただけでも「チャンク要約2回 + 最終要約1回」へ増える。長文ではチャンクごとにConversationと生成処理が必要になり、推論時間、電力消費、発熱の増加が大きい。

現在のGemma 4 E2B / E4B向けLiteRTモデルはモデルカード上32kコンテキストまで対応する。一方、Android向けモデルカードの主要ベンチマークは2,048トークンで測定されており、端末上でモデルの最大コンテキストをそのまま使用する根拠は十分ではない。

利用中のLiteRT-LM 0.14.0では `EngineConfig.maxNumTokens` によって入力と出力で共有するKV cacheの最大トークン数を明示できる。ただし同versionのKotlin APIには生成ごとの `maxOutputToken` とfeatureから利用できるtokenizer APIがない。このため厳密なtoken数で分割するのではなく、コンテキスト長から生成・runtime余白を引いたうえで安全側の文字数推定を行う必要がある。

## Decision

記事本文の分割判定を固定 `maxInputChars` ではなく、選択モデルの実行コンテキストと要約プロンプトから計算した入力予算で行う。

### Runtime capability

`core:ai-runtime` はモデルごとの `contextTokens` を技術能力として公開する。Gemma 4 E2B / E4Bのアプリ内実行コンテキストは8,192トークンとし、同じ値をLiteRT-LMの `EngineConfig.maxNumTokens` に明示する。

8,192はモデルが対応する最大32kを利用する設定ではなく、モバイル端末でのメモリ増加を抑えつつ過剰分割を減らすためのアプリ側上限である。将来、実端末計測またはLiteRT-LMの安定API改善に基づいて再調整できるものとする。

`maxInputChars` はChatなど既存featureの文字数ポリシーとして維持し、`promptBudgetChars` も既存利用箇所では4,096文字のままとする。コンテキスト拡張によって他featureの入力サイズを暗黙に増やさない。

### Summary input budget

`feature:summary:data` は各推論について次の予算を適用する。

- context: 8,192 tokens
- output reserve: 768 tokens
- runtime/template reserve: 256 tokens
- 残りを入力promptに利用する
- 安定版LiteRT-LMからtokenizerを利用できない間は、UTF-16文字数を `1文字 = 1.2 tokens` と見積もる

本文へ使える文字数は、上記入力予算から実際の要約prompt固定部分を差し引いて計算する。したがってユーザーが長い要約promptを設定した場合は本文予算が自動的に小さくなる。

既定要約promptでは1回の推論へ渡せる本文は約5,800文字となり、従来の2,500文字より大きくする。一方、8,192トークン全体を本文へ使わず、生成結果とtokenization誤差のための余白を残す。

### Hierarchical summary

1. 本文を正規化する。
2. ユーザーの最終要約promptを含めても入力予算へ収まる場合は、1回の推論で直接要約する。
3. 収まらない場合は、中間要約prompt自身の固定部分を考慮してチャンク上限を計算し、文末記号や空白を優先して先頭から分割する。
4. 各チャンクから、固有名詞、数値、日時、主張、結論、因果関係を優先した中間要約を作る。8,192トークン時の中間要約目標は最大600文字とする。
5. 全中間要約と最終promptが入力予算へ収まらない場合は、統合promptの予算に合わせてグループ化し、順序を維持したまま再圧縮する。
6. 最終的に予算へ収まった全中間要約を1つの文脈として、ユーザー設定の要約promptで最終要約を生成する。

中間要約は最終回答として保存せず、一時的な推論結果としてのみ扱う。最終要約だけを既存の要約テーブルへ保存する。

従来 `summarizeText` が入力超過時に先頭75%と末尾25%へ無言で切り詰めていたフォールバックは廃止する。階層要約の計算ミスや別用途からの過大入力が発生した場合は、情報を欠落させた成功結果を返さず明示的に失敗させる。

分割処理、中間要約のグループ化、token数の安全側推定、要約用prompt、進捗段階はSummary機能の意味を持つため `feature:summary:data` が所有する。`core:ai-runtime` は階層要約を知らず、実行コンテキスト等の技術能力と汎用 `generate` APIだけを提供する。これはADR-0056の責務境界を維持する。

## Output token limit

LiteRT-LMの開発中APIには生成出力上限を指定する機能が存在するが、採用中の安定版0.14.0には含まれない。そのため今回の実装では中間要約promptの「最大600文字程度」という指示と768トークンの予約領域で制御する。

安定版LiteRT-LMで生成ごとのoutput token上限が利用可能になった時点で、中間要約と最終要約へhard limitを設定することを再検討する。未リリースAPIへ依存するためだけのruntime更新は行わない。

## Cache compatibility

要約アルゴリズムと入力対象範囲が変わるため、要約cache世代を `hierarchical-v2-context-budget` へ更新する。`hierarchical-v1` の結果は同じモデル・同じユーザーpromptであっても自動再利用しない。

要約prompt本体とそのhash規則は `feature:summary:domain`、階層要約世代は `feature:summary:data` が所有する。

## Cancellation and failure

チャンク間および統合処理間でCoroutineのキャンセルを確認する。バックグラウンド要約タスクがキャンセルされた場合は次の推論へ進まない。

中間要約が想定どおり圧縮されず、一定回数の統合を行っても最終入力予算へ収まらない場合は要約処理を失敗させる。入力予算を超えた場合も本文を無言で切り捨てない。

## Consequences

既定promptでは約5,800文字まで直接要約できるため、2,500文字直後に発生していた急激な推論回数増加を緩和できる。40,000文字の記事も、中間要約が目標長へ収まる一般的なケースではおおむね7〜8チャンクと最終要約で処理でき、従来の約16チャンクより推論回数を減らせる。

LiteRT-LMのEngineを再利用するため、チャンクごとにモデルを再ロードすることはない。ただし各チャンクでConversationと生成処理は必要であり、長文ほど電力消費と発熱が増える性質自体は残る。

8,192トークンへKV cacheを拡張することで、従来の4,096設定より推論時メモリは増える。この設定はモデルの32k能力より十分低く抑えるが、実端末でOOM、速度低下、発熱が確認された場合はcontext上限を端末能力別にすることを検討する。

文字数からtoken数への1.2倍換算はtokenizerを利用できないことに対する暫定的な安全策であり、厳密なtoken計測ではない。安定版LiteRT-LMからtokenizerを利用できるようになった場合は、推定を実token budgetへ置き換える。

階層要約をfeature側に維持することで、要約アルゴリズムの変更や予算調整がChat、Knowledge、Library等へ暗黙に波及しない。

## References

- ADR-0020: ローカルAIをGemma 4 / LiteRT-LMへ統一する
- ADR-0056: ローカルAIの機能固有ポリシーをfeatureへ分離する
- LiteRT-LM 0.14.0 `EngineConfig.maxNumTokens`
- Gemma 4 E2B LiteRT model card: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Gemma 4 E4B LiteRT model card: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- `core/ai-runtime/.../LocalModelManager.kt`
- `feature/summary/data/.../HierarchicalSummary.kt`
- `feature/article/data/.../ArticleContentClient.kt`
