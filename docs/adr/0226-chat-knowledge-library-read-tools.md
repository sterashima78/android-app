# ADR-0226: Knowledge と Library を owner read contract 経由で Chat retrieval へ参加させる

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0225](0225-chat-lexical-retrieval-and-query-reformulation.md)

## Context

ADR-0225 では Chat をアプリ内情報の retrieval orchestrator とし、新しい検索対象を追加するときも owner Context の Repository または purpose-specific read-only Query API を利用する方針を採用した。RSS、Reddit、保存記事、既読履歴、Task は既に Chat tool から参照できるが、Knowledge Wiki と Library は対象外だった。

Knowledge は既に `KnowledgeReader` を公開しており、`listPages(query)` と `findPage(id)` で読み取りを分離している。現在の owner 実装では `listPages(query)` が Knowledge table 内の title / body markdown を検索するため、Chat が Knowledge table を直接読む必要はない。

Library は `LibraryRepository` が `snapshot()` と同期・非表示・series更新等の mutation を同じ interface に持っている。Chat が必要なのは表示中蔵書の参照だけであり、Repository 全体へ依存させると read-only consumer に不要な command surface まで公開することになる。

また Knowledge 本文や書籍 description を候補一覧ですべて Local Chat context に入れると、ADR-0225 で避けたかった context budget の浪費が再発する。

## Decision

### 1. Knowledge は既存 KnowledgeReader を Chat tool へ適合する

Chat に `knowledge-reader` Skill を追加する。

- `search_knowledge_pages` は `KnowledgeReader.listPages(query)` を呼び、最大10件の page id、title、source count、生成日時等だけを candidate として返す。
- `get_knowledge_page_detail` は candidate の page id を指定して `KnowledgeReader.findPage(id)` を呼び、回答根拠として必要なページだけ body markdown と source metadata を返す。
- Chat data module から `knowledge_pages` / `knowledge_page_sources` を直接 SELECT しない。

Knowledge の candidate search は owner が title / body を検索した結果を利用し、Chat 側で本文を全件取得して再 ranking しない。本文を candidate scoring のためだけに model context へ展開しないことを優先し、owner が返す order を維持する。

### 2. LibraryRepository から LibraryReader を分離する

`feature:library:domain` に `LibraryReader` を追加し、`snapshot()` だけを公開する。既存 `LibraryRepository` は `LibraryReader` を継承し、同期・非表示・series更新等の command contract は従来通り `LibraryRepository` に残す。

Chat は `LibraryReader` のみに依存し、Library mutation API を参照しない。

### 3. Library は lexical candidate search と detail retrieval を分離する

Chat に `library-reader` Skill を追加する。

- `search_library_books` は `LibraryReader.snapshot().books` を対象に、title、authors、series、description、publisher、narrators、source、ISBN を ADR-0225 の lexical matcher で検索する。
- title、authors、series、description 等へ用途別 weight を付け、空白区切りの複数語は異なる書誌項目へ一致してよい。
- candidate は最大10件とし、description は短い excerpt に限定する。
- `get_library_book_detail` は candidate に含まれる `source` と `source_id` で1件を選び、必要な書誌詳細だけを返す。
- 通常の検索対象は `LibrarySnapshot.books` とし、`hiddenBooks` は含めない。

### 4. composition root で owner runtime を接続する

`:app:composition` の cross-feature runtime が既存 application-scope graph から `KnowledgeReader` と `LibraryReader` を Chat adapter へ渡す。

`:feature:chat:data` は `:feature:knowledge:domain` と `:feature:library:domain` にのみ新規依存し、Knowledge / Library data implementation、DatabaseConnection、SQLite table へ依存しない。

### 5. 既存の query reformulation と trust boundary を共有する

Knowledge / Library の検索結果が0件または根拠不足の場合も、ADR-0225 の最大2回の query reformulation を共有する。複数情報源を横断する質問は、それぞれの Skill を個別に呼び Chat が回答を統合する。

Knowledge本文、出典URL、蔵書情報、検索語、tool result を新しい diagnostics / public fixture / log へ保存しない。Tool result は引き続き untrusted data として扱う。

## Consequences

### Positive

- Chat から Knowledge Wiki と蔵書を再発見・比較できる。
- Knowledge / Library の durable ownership を変更せず、既存 owner read contract 経由で参照できる。
- Library consumer に mutation API を公開しない narrow interface ができ、他の read-only consumer にも再利用できる。
- candidate/detail 分離により、Knowledge本文や書籍descriptionでLocal model contextを早期に消費しにくい。
- 新しい FTS / vector table、embedding pipeline、cross-context projection を追加しない。

### Negative

- Knowledge candidate の順位は owner `listPages` の order を利用するため、Chat 側の weighted lexical ranking は適用しない。
- Library search は snapshot 全体をメモリ上で lexical ranking するため、蔵書件数が大幅に増えた場合は owner 側 read model / FTS の再検討が必要になる。
- detail tool は source / source_id の2引数を model が正しく引き継ぐ必要がある。

## Verification

- Knowledge candidate tool が検索queryを `KnowledgeReader` へ渡し、候補段階では本文を返さない unit test を追加する。
- Knowledge detail tool が選択ページの本文・source metadataを返す unit test を追加する。
- Library の複数検索語が異なる書誌項目へ一致できる unit test を追加する。
- Library detail tool が source / source_id で対象書籍を取得する unit test を追加する。
- `verifyArchitecture` で Chat data が owner Domain contract にのみ依存することを確認する。
- Public repository / Test / Lint を PR CI で確認する。

## Public repository review

実装・テスト・文書には実ユーザーの Knowledge 本文、蔵書内容、SMB path、購入履歴、アカウント情報、token、private URL を追加しない。テストデータは `example.com` と合成した書誌・本文だけを利用する。
