# ADR-0270: RSS記事を除外条件から推薦スコアリングする

- Status: Accepted
- Date: 2026-09-25
- Refines: [ADR-0119](0119-content-classification-retention-and-table-ownership-enforcement.md), [ADR-0183](0183-rss-settings-tab.md)
- Related: [ADR-0263](0263-podcast-structured-clustering-output.md), [ADR-0267](0267-podcast-news-exclusion-filter.md)
- Amended by: [ADR-0271](0271-rss-recommendation-background-queue.md), [ADR-0272](0272-rss-recommendation-local-cloud-routing.md)

## Context

RSS readerでは購読sourceごとに内容が大きく異なるため、固定カテゴリによる非表示ではなく、利用者が指定した「読みたい必要性が低い記事」の条件をもとに記事ごとの推薦度を表したい。

最初の判定材料は記事タイトルだけとする。タイトルだけでは判断できない記事を無理に数値化すると、推薦スコアの意味と「情報不足」「推論失敗」が混在する。したがって、スコア可能な記事だけを1〜10で評価し、評価できない記事は理由付きの未評価状態とする。

利用者は未読一覧から記事を「除外参考」として明示できる。この操作は記事を既読にすると同時に、今後の除外条件改善に使うfeedbackとして扱う。連続操作を1件ずつAI推論へ送らず、短い遅延後にまとめて既存条件を改善する必要がある。

構造化結果は通常テキストからJSONを解析せず、既存のprovider-neutralなtool call境界を再利用する。

## Decision

### RSS Contextが推薦ポリシーと評価結果を所有する

RSS Contextは次のdurable stateを所有する。

- 利用者が手動入力する除外条件
- AIが除外参考から改善した学習条件
- 条件revision
- 記事ごとの推薦評価
- 未処理の除外参考

Content Contextは引き続きread / unread stateを所有する。「除外参考」操作ではRSS側へfeedbackを保存した後、公開されたContent capabilityで記事を既読化する。RSSがContent-owned tableを直接更新しない。

手動条件と学習条件は別に保持し、判定時だけ結合する。AIによる条件改善で利用者の明示入力を上書きしない。

### スコアと未評価理由を分離する

推薦評価は次のどちらかとする。

- scored: 1〜10。1は除外条件へ強く該当し、10は正常に評価した結果として除外する必要がない
- unscored: 数値を持たず、少なくとも「タイトルだけでは情報不足」「推論またはtool call検証失敗」を区別する

除外条件が存在せずスコアリング自体を開始していない記事は評価recordを作らない。

モデルが返せる未評価理由は情報不足だけとする。推論失敗、tool未呼び出し、argument decode失敗、候補数不一致、範囲外score等はfeature側validationから推論失敗として記録する。

### tool callだけを構造化出力として採用する

RSSスコアリングは `AiStructuredTextInference` を利用し、指定toolのargumentsだけを結果として採用する。通常テキストやMarkdown、自由形式JSONは結果として扱わない。

現行の共通tool schemaがobject arrayを持たないため、候補順に対応するstatus配列とscore配列を返し、feature側で次を検証する。

- 配列長が候補数と一致する
- statusが既知値だけである
- scoredのscoreが1〜10である
- unscoredにはscoreを要求しない
- すべての候補がちょうど1件の結果へ対応する

検証失敗時は1回だけrepair requestを行い、最終的に成立しなければbatch内の対象を推論失敗のunscoredとして保存する。coroutine cancellationは通常どおり伝播する。

判定入力にはタイトルだけを使う。source名、URL、feed本文、リンク先本文、保存済み要約は初期実装では入力しない。

### 除外参考は遅延して条件改善へ使う

未読記事に「除外参考」のswipe actionを追加する。確定すると、

1. RSS-owned pending feedbackへ記事ID、タイトル、現在の評価状態を保存する
2. Content capabilityで既読化する
3. 未読一覧から除く

pending feedbackが増えるたびに条件改善の実行を後ろへずらし、最後の追加から一定時間経過した後にまとめて1回のAI推論へ渡す。初期値は30秒とする。

改善入力には手動条件、現在の学習条件、pending feedbackのタイトルを渡す。AIは手動条件を書き換えず、タイトル固有語の単純列挙を避けて一般化可能な学習条件だけを返す。根拠が弱い場合は既存学習条件を維持できる。

条件改善の開始時にpending集合をsnapshotする。処理中に追加されたfeedbackは次回対象とする。改善成功時だけsnapshot対象を消費済みにし、条件が変わった場合だけrevisionを進める。失敗時はpending feedbackと既存条件を保持する。

### 条件revisionでstale評価を識別する

各評価は判定に使った条件revisionを保持する。現在revisionと一致しない評価はstaleとして扱い、未読記事を順次再評価する。staleな低スコアを現在条件の確定結果として扱わない。

過去の既読記事を全件再評価しない。

### UI

RSS設定タブで手動除外条件と学習条件を確認でき、手動条件を編集できる。学習条件は利用者が確認でき、リセットできる。

未読一覧にはscored結果だけ1〜10を表示する。unscoredは数値を表示せず、理由を参照できる表示にする。評価recordがない場合はスコア表示を行わない。

統合ビューに投影されるRSS未読記事も同じ評価表示と「除外参考」操作を利用する。統合ビューは推薦評価や学習状態を所有せず、RSS UI stateとRSS ViewModelの公開操作をpresentationとして投影する。

共通swipe componentには深い左swipeを追加し、RSS未読一覧では通常左を既読、深い左を除外参考、通常右をブックマーク、深い右をあとで読むとする。統合ビューのRSS未読も同じswipe意味を維持する。

## Consequences

- 推薦スコアの意味が数値として一意になり、情報不足や実行失敗を10へ押し込めない。
- 情報不足率を観測できるため、タイトルだけの判定から要約併用へ進む必要性を後から判断できる。
- false negativeと推論失敗を区別したまま除外feedbackを蓄積できる。
- 手動条件をAIが上書きしないため、利用者が指定した明示的な意図を保持できる。
- RSS-owned durable stateが増えるが、Content ownershipとAI runtime ownershipは変更しない。
- 共通swipe UIにfar-left gestureが加わるが、既存consumerは指定しない限り従来動作を維持する。

## Verification

- scoring parser / validationで正常score、情報不足、件数不一致、範囲外score、tool不一致をtestする。
- scoring処理でタイトル以外をAI入力へ含めないこと、最終失敗をunscoredとして保存することをtestする。
- repository testで設定、revision、評価、pending feedback、成功時のfeedback消費を確認する。
- swipe resolver testでfar-left thresholdと既存gestureの互換性を確認する。
- RSS ViewModel / UI testで除外参考が既読化とfeedback登録を行うこと、score / unscored表示を確認する。
- public repository verification、architecture verification、unit test、lintを実行する。
