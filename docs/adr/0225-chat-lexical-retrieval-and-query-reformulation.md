# ADR-0225: Chat は語彙検索を第一段にして検索語を限定再構成する

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0117](0117-cross-context-persistence-boundary-phase1.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md)

## Context

Yomitori の Chat は一般知識だけを回答する画面ではなく、RSS、Reddit、保存記事、既読履歴、Task など、アプリ内に集めたユーザー自身の情報を再発見して活用する入口でもある。既存実装は各 Context の Repository を `AgentSkill` / `AgentTool` へ適合し、Chat が必要な情報源を tool calling で選択する。

従来の検索 tool は、入力された query 全体がタイトル、配信元、タグ、フォルダ、AI要約等のどれか1項目に部分一致することを要求していた。このため「Android memory」のように複数概念が異なる項目へ分散している場合や、ユーザーの自然文と保存済み表現が異なる場合に候補を落としやすい。

一方で、全コンテンツを事前 embedding して vector index を新たな source of truth とすると、index 更新、model revision、chunk policy、cross-context ownership、端末ストレージと計算量を新たに管理する必要がある。現在の利用規模では、まず既存の owner API と保存済み要約を利用した軽量な検索を改善し、実測で不足が確認された段階で semantic rerank を検討する方が単純である。

また Local Chat の tool result は入力 budget を消費する。候補検索の時点で保存済みAI要約全文を複数件返すと、本当に回答に必要な証拠へ使える context が減る。

## Decision

### 1. Chat retrieval は lexical-first とする

Chat のアプリ内検索 tool は、既存 Context の公開 read API から取得した候補を語彙一致で絞り込み、関連度順に返す。

- 空白区切りの複数検索語は、同一項目ではなく異なる項目へ一致してよい。
- すべての検索語が候補内のいずれかの検索対象項目へ一致することを要求し、単純な OR 検索によるノイズ拡大は避ける。
- タイトル、タグ、要約、配信元等には用途に応じた重みを付ける。
- query 全体の phrase match には追加点を与える。
- query が空の場合は既存の domain order を維持する。保存記事の recent tool は引き続き保存日時順を正本とする。

現段階では DB FTS table や vector table を追加しない。検索量やデータ量から必要性が実測された場合、owner Context の read model として FTS を導入する余地は残す。

### 2. 検索結果が弱い場合だけ Chat agent が検索語を再構成する

検索結果が0件、または質問を回答する根拠として明らかに不足する場合、Chat agent は同じ query を繰り返さず、より短い語、同義語、固有名詞等へ検索語を言い換えて同じ目的の検索を再試行する。

同一の情報要求に対する言い換えは model policy 上最大2回とする。それでも根拠が得られない場合、見つからなかったことを回答し、無制限に tool call を繰り返さない。

この再構成のためだけに別の inference request は追加しない。既存の streaming / tool-capable conversation が tool result を見て次の tool call を選ぶ能力を利用する。

### 3. 複数 intent は情報源ごとに検索する

1つの質問に「保存記事と未読記事を比較する」等の複数 intent が含まれる場合、1つの曖昧な横断検索 index へまとめず、Chat が各 Context の適切な read tool を個別に呼び出して結果を統合する。

Chat は検索 orchestration を所有するが、Content、Curation、Knowledge、Library 等の durable table を直接 read/write しない。新しい検索対象を追加する場合も、各 owner が公開する Repository または purpose-specific read-only Query API を Chat adapter から利用する。

### 4. 保存記事は candidate と detail を分離する

保存記事検索、あとで読む検索、最近の保存記事一覧は candidate retrieval とし、最大10件についてタイトル、配信元、日時、folder、tag、AI要約の短い excerpt を返す。

回答根拠として要約全文が必要な候補だけ、article id を指定して `get_saved_article_detail` で詳細を取得する。candidate 一覧の全件に対して detail を取得しない。

Local Chat 側に既に存在する tool result 上限は維持し、詳細が長い場合も runtime の上限で切り詰める。

### 5. Embedding は measured fallback として rerank に限定して検討する

lexical retrieval と限定 query reformulation を導入した後も、表記が大きく異なる関連情報を継続的に取り逃すことが評価ケースで確認された場合だけ embedding を検討する。

導入する場合も、全件 vector search を最初の検索手段にはせず、owner API / lexical search で得た上位候補に対する rerank を第一候補とする。embedding model、revision、index lifecycle、privacy、storage cost はその時点で別ADRに記録する。

## Consequences

### Positive

- 既存の保存済み metadata / AI要約を検索資産として再利用できる。
- vector DB や embedding pipeline を追加せず、複数語検索の recall を改善できる。
- 検索語が保存済み表現とずれる場合だけ Local model の既存 tool loop で補正できる。
- candidate/detail 分離により、Local model の限られた context を候補一覧ではなく必要な証拠へ使いやすくなる。
- Chat が他 Context の table owner にならず、既存の Domain Context boundary を維持できる。

### Negative

- 日本語の空白なし自然文は lexical matcher だけでは1語として扱われるため、model の検索語再構成に依存する場合がある。
- query reformulation は model policy であり、検索品質は選択中モデルの tool selection 能力にも依存する。
- candidate 作成時に保存済み要約を検索対象へ含めるため、現在と同様に候補集合の要約 lookup が必要になる。
- semantic similarity が必須なケースは本変更だけでは解決しない。

## Verification

- 複数検索語が異なる項目へ一致できる unit test を追加する。
- 高い重みの項目一致が上位へ来る unit test を追加する。
- query が空の場合に既存順序を維持する unit test を追加する。
- candidate 用 summary excerpt が上限内に収まる unit test を追加する。
- Chat system instruction に限定 query reformulation、multi-intent 分割、candidate/detail 方針が含まれることを unit test で固定する。
- Architecture / Test / Lint / public repository verification を PR CI で確認する。

## Future extension

Knowledge / Library 等を Chat retrieval へ追加する場合は、Chat からそれぞれの durable table を直接読むのではなく、owner Context の narrow read contract を追加する。検索精度を評価する際は、実ユーザーデータを repository fixture や public log へ持ち込まず、合成 evaluation case を使う。

## Public repository review

実装、テスト、ADR に private URL、記事本文、メール本文、蔵書内容、アカウント情報、token、実ユーザー検索履歴を追加しない。ranking test は合成文字列のみを利用し、Chat の検索 query / tool result を新たに log または diagnostics へ保存しない。
