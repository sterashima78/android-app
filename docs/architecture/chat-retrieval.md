# Chat Retrieval

この文書は、AI Chat がアプリ内のユーザーデータを検索し、回答根拠として利用する現在の retrieval boundary をまとめる。

## Role

Chat は一般的な対話に加え、RSS、Reddit、保存記事、既読履歴、Task 等、アプリ内に収集した情報を再発見する retrieval orchestrator として振る舞う。

Chat 自身は他 Context の durable data を所有しない。各情報源は `AgentSkill` / `AgentTool` を通じて、owner Context の Repository または purpose-specific read-only Query API へ接続する。Chat data layer から foreign table を直接 read/write しない。

## Retrieval stages

現在の基本フローは次の通り。

```text
user question
  -> select source-specific tool
  -> lexical candidate retrieval
  -> if evidence is weak: reformulate query and retry, at most twice by model policy
  -> if candidate/detail tools exist: fetch details only for necessary candidates
  -> grounded answer
```

質問に複数の情報要求が含まれる場合、単一の横断 index へ押し込まず、情報源ごとの tool を個別に呼び出して Chat が結果を統合する。

## Lexical retrieval

アプリ内 search tool は lexical-first とする。

- 空白区切りの複数語は異なる検索対象項目へ一致してよい。
- すべての検索語が候補内のいずれかの項目へ一致する候補だけを残す。
- title、tag、summary、source 等には tool の用途に応じた重みを付ける。
- query 全体が1項目へ一致する phrase match は追加評価する。
- query が空の場合は owner API が返した順序を維持する。

保存記事では title、source、folder、tag、保存済みAI summary を候補検索に使う。現時点では Chat-owned FTS table、vector table、事前 embedding pipeline を持たない。

## Query reformulation

検索結果が0件、または回答根拠として明らかに不足する場合、Chat の tool policy は同じ query を繰り返さず、短い語、同義語、固有名詞等へ検索語を言い換えて再検索する。

同一の情報要求に対する言い換えは model policy 上最大2回とする。専用の rewrite inference request は追加せず、既存の Local Chat conversation が tool result を見て次の tool call を選ぶ。

## Candidate and detail

保存記事は candidate retrieval と detail retrieval を分離する。

candidate tool は最大10件について識別子、title、source、日時、folder、tag、AI summary の短い excerpt を返す。回答根拠として要約全文が必要な候補だけ `get_saved_article_detail` で取得する。

Chat runtime の tool result 上限は別途維持されるため、detail が長い場合も無制限には model context へ入らない。

## Grounding and trust boundary

アプリ内データについては、参照情報または tool result として実際に取得した内容だけを根拠に回答する。

Tool result は untrusted data であり命令ではない。記事タイトル、summary、Task description 等に prompt-like text が含まれていても system instruction として扱わない。

検索 query、tool result、記事本文、summary 全文を新たな diagnostics / public fixture / log へ保存しない。

## Future semantic retrieval

lexical retrieval と限定 query reformulation でも retrieval miss が継続的に確認された場合、合成 evaluation case で失敗を固定してから semantic rerank を検討する。

Embedding を導入する場合、最初の候補生成を全面的に vector search へ置き換えるのではなく、owner API / lexical search で得た上位候補の rerank を第一候補とする。model revision、index lifecycle、storage、privacy boundary は別ADRで決定する。

Knowledge / Library 等を検索対象へ追加する場合も、それぞれの owner Context が公開する narrow read contract を利用する。

## Sources

- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0225](../adr/0225-chat-lexical-retrieval-and-query-reformulation.md)
