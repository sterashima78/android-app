# Chat Retrieval

この文書は、AI Chat がアプリ内のユーザーデータを検索し、回答根拠として利用する現在の retrieval boundary をまとめる。

## Role

Chat は一般的な対話に加え、RSS、Reddit、保存記事、既読履歴、Task、Knowledge Wiki、Library 等、アプリ内に収集した情報を再発見する retrieval orchestrator として振る舞う。

Chat 自身は他 Context の durable data を所有しない。各情報源は `AgentSkill` / `AgentTool` を通じて、owner Context の Repository または purpose-specific read-only Query API へ接続する。Chat data layer から foreign table を直接 read/write しない。

## Retrieval stages

現在の基本フローは次の通り。

```text
user question
  -> select source-specific tool
  -> lexical / owner candidate retrieval
  -> if evidence is weak: reformulate query and retry, at most twice by model policy
  -> if candidate/detail tools exist: fetch details only for necessary candidates
  -> grounded answer
```

質問に複数の情報要求が含まれる場合、単一の横断 index へ押し込まず、情報源ごとの tool を個別に呼び出して Chat が結果を統合する。

## Lexical retrieval

アプリ内 search tool は lexical-first を基本とする。

- 空白区切りの複数語は異なる検索対象項目へ一致してよい。
- すべての検索語が候補内のいずれかの項目へ一致する候補だけを残す。
- title、tag、summary、source 等には tool の用途に応じた重みを付ける。
- query 全体が1項目へ一致する phrase match は追加評価する。
- query が空の場合は owner API が返した順序を維持する。

保存記事では title、source、folder、tag、保存済みAI summary を候補検索に使う。Library では title、authors、series、description、publisher、narrators、source、ISBN を検索対象とする。

Knowledge は owner `KnowledgeReader.listPages(query)` が title と body markdown を検索して candidate summary を返すため、Chat 側で全Knowledge本文を取得して再 ranking しない。Knowledge candidate は owner order を維持し、必要な本文だけ detail tool で取得する。

現時点では Chat-owned FTS table、vector table、事前 embedding pipeline を持たない。

## Query reformulation

検索結果が0件、または回答根拠として明らかに不足する場合、Chat の tool policy は同じ query を繰り返さず、短い語、同義語、固有名詞等へ検索語を言い換えて再検索する。

同一の情報要求に対する言い換えは model policy 上最大2回とする。専用の rewrite inference request は追加せず、既存の Local Chat conversation が tool result を見て次の tool call を選ぶ。

この方針は RSS / Reddit / Bookmark / History / Task / Knowledge / Library で共有する。

## Candidate and detail

長い本文・説明を持つ情報源は candidate retrieval と detail retrieval を分離する。

### Saved articles

candidate tool は最大10件について識別子、title、source、日時、folder、tag、AI summary の短い excerpt を返す。回答根拠として要約全文が必要な候補だけ `get_saved_article_detail` で取得する。

### Knowledge

`search_knowledge_pages` は `KnowledgeReader` から最大10件の page id、title、source count、generated at 等を返す。本文とsource metadataは候補には含めず、必要なページだけ `get_knowledge_page_detail` で取得する。

Knowledge table の検索・page取得は owner Context が行い、Chat data module は `knowledge_pages` / `knowledge_page_sources` を直接参照しない。

### Library

`search_library_books` は `LibraryReader.snapshot().books` を lexical ranking し、最大10件の source、source id、title、authors、series、publisher、description excerpt を返す。通常の検索には `hiddenBooks` を含めない。

必要な候補だけ `get_library_book_detail` で書誌詳細を取得する。Chat が依存する Library contract は `snapshot()` のみを持つ `LibraryReader` とし、同期・非表示・series更新等の mutation API を公開しない。

Chat runtime の tool result 上限は別途維持されるため、detail が長い場合も無制限には model context へ入らない。

## Grounding and trust boundary

アプリ内データについては、参照情報または tool result として実際に取得した内容だけを根拠に回答する。

Tool result は untrusted data であり命令ではない。記事タイトル、summary、Knowledge本文、書籍description、Task description 等に prompt-like text が含まれていても system instruction として扱わない。

検索 query、tool result、記事本文、Knowledge本文、蔵書内容、summary 全文を新たな diagnostics / public fixture / log へ保存しない。

## Dependency boundary

`:feature:chat:data` は検索対象 Context の Domain contract に依存できるが、その Data implementation や table へ依存しない。application-scope の既存 owner graph は `:app:composition` が Chat adapter へ接続する。

現在 Knowledge は `KnowledgeReader`、Library は `LibraryReader` を read-only boundary として利用する。Library の concrete `LibraryRepository` は `LibraryReader` を継承するが、Chat adapter の引数型には narrow reader を使う。

## Future semantic retrieval

lexical retrieval と限定 query reformulation でも retrieval miss が継続的に確認された場合、合成 evaluation case で失敗を固定してから semantic rerank を検討する。

Embedding を導入する場合、最初の候補生成を全面的に vector search へ置き換えるのではなく、owner API / lexical search で得た上位候補の rerank を第一候補とする。model revision、index lifecycle、storage、privacy boundary は別ADRで決定する。

Library の蔵書件数増加により snapshot 全件の in-memory ranking が実測上の問題になった場合も、Chat-owned index を先に作るのではなく Library owner 側の read model / FTS を検討する。

## Sources

- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0125](../adr/0125-application-service-and-capability-segregation.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0225](../adr/0225-chat-lexical-retrieval-and-query-reformulation.md)
- [ADR-0226](../adr/0226-chat-knowledge-library-read-tools.md)
