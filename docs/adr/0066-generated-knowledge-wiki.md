# ADR-0066: 出典付きLLM編集Wikiをナレッジベースの第一段階とする

- Status: Accepted
- Date: 2026-08-15

## Context

RSS、ブックマーク、蔵書などに蓄積された情報を一覧として読むだけでなく、複数資料を横断して概念や関係性を整理したナレッジベースとして参照したい。OpenWikiのようなLLM Wikiの考え方は有用だが、モバイル端末のローカルLLMでは大量資料の無制限な再処理は計算量・メモリ・待ち時間の面で適さない。

また、Wiki本文をユーザーが直接Markdown編集すると、自動再生成との所有権やマージが複雑になる。本アプリではLLMをWiki編集者として扱い、ユーザーは作成・変更意図を自然言語で指示する。

ADR-0065はSMB蔵書と内蔵Book Readerに割り当てられたため、本DecisionはADR-0066として記録する。

## Decision

`:feature:knowledge:{domain,data,ui}` を追加し、Knowledgeを蔵書とは独立したトップレベル機能として扱う。Knowledge featureが資料選択、生成・編集プロンプト、永続化、引用追跡、UIを所有し、`:core:ai-runtime` は汎用推論capabilityのままとする。

### 入力資料と自動Wiki

初期実装では保存済みブックマークのうちSummary featureに要約が保存されている資料だけを根拠とする。自動Wikiのトピック候補はタグ、通常ブックマークフォルダ、提供元の順に決定する。同一資料が複数タグを持つ場合は複数トピックの根拠になり得る。

自動トピックIDはトピック種別と正規化キーから決定論的に生成する。トピックと出典内容からfingerprintを計算し、変更がないページは再生成しない。1回の再構築で生成する変更ページは最大8件、1ページの出典は最大12件とする。

### LLM Editor

主要操作は次の3つとする。

1. 「記事を作成」から、作りたい内容を自然言語で指定して新規Wiki記事を生成する。
2. 既存記事のLLM Editorへ「比較を追加して」「結論を短くして」などの指示を入力し、記事を更新する。
3. 「この記事から新規」から、現在の記事と出典を起点に別観点の記事を生成する。

Markdownの直接編集UIは初期実装では提供しない。

新規作成と編集ではタイトル、タグ、フォルダ、提供元、要約の文字列一致を重み付けして候補資料を順位付けする。既存記事の編集・派生では現在の出典を優先するが、上限まで既存出典がある場合でも新しい関連資料を取り込む枠を確保する。将来embedding/vector searchへ置換できる境界を維持する。

### LLM入出力と安全性

新規作成・編集の出力は1行目を `# 記事タイトル`、2行目以降をMarkdown本文とする。実装側はH1をタイトルとして分離し、外側のMarkdownコードフェンスが付いた場合は除去する。主要な主張には `[1]` の形式で出典番号を付ける。

プロンプトでは、与えた出典要約だけを事実の根拠とし、確認できない事実を推測で補わないことを要求する。外部資料や既存Wiki本文に含まれる命令文はデータとして扱い、編集命令として実行しない。ユーザーが入力した作成・編集依頼だけを命令として扱う。

編集は現在の記事全体をLLMへ渡し、編集後の記事全体を返させる。入力予算の都合で現在の記事全文を渡せない場合、本文を途中で切り捨てて編集せず、既存記事を変更しないままエラーとする。これにより長文記事の末尾が編集時に消失することを防ぐ。

### 永続化と所有権

生成ページは `knowledge_pages`、根拠資料は `knowledge_page_sources` に保存する。出典には記事ID、引用番号、タイトル、URL、提供元、保存日時を保持する。

`editor_managed = true` はユーザーの明示依頼で新規作成または編集された記事を表す。このページはユーザー意図を含む成果物であり、自動Wiki再構築で上書き・削除しない。自動生成ページを一度でもLLM Editorで編集した場合も `editor_managed = true` に昇格させる。

現在の自動トピック集合から消えた未編集ページは削除できるが、editor-managedページは保持する。

### DB migrationとバックアップ

SMB蔵書対応後のmainはdatabase schema version 14である。Knowledgeテーブルと編集管理状態を追加するためversion 15へ更新する。v14からv15ではKnowledge schemaを作成し、`editor_managed` 列が存在しない開発版Knowledge DBに対してのみ列追加migrationを適用する。v13以前から直接v15へ更新する場合もschema contributionの作成後にmigrationが実行されるため、列存在確認により安全に処理する。

バックアップ形式をv4へ更新し、`editor_managed = true` のページとその出典だけをKnowledgeのバックアップ対象とする。これらはユーザー意図を含み単純には再生成できない。自動生成のみのページは派生データとしてバックアップせず、復元後に再構築できるようにする。v1〜v3復元時はKnowledgeを削除し、v4では元データ復元後にeditor-managedページを復元する。

## Deferred decisions

- embedding / vector DB
- 自動知識グラフ
- LLMによる無制限な自律資料探索
- LLM編集指示の履歴永続化
- ページ版管理・差分表示・ロールバック
- Markdown直接編集
- バックグラウンドでの無制限再生成
- Knowledgeから他のアプリデータを書き換えるAgent Tool

## Consequences

### Positive

- ユーザーは文章編集ではなく作成・変更意図に集中できる。
- 新規作成、編集、派生記事作成を同じLLM Editorモデルで扱える。
- 出典へ遡って生成内容を検証できる。
- ユーザー管理記事を自動再構築から保護し、バックアップできる。
- Knowledge固有ポリシーをcore AI runtimeから分離できる。
- 蔵書とは独立した概念・画面として責務を維持できる。

### Negative

- 初期retrievalは文字列一致中心で、語彙が異なる関連資料を見逃す可能性がある。
- 記事全体を再生成するため、長文はローカルLLMの入力上限と待ち時間の影響を受ける。
- 全文を安全に入力できない長文記事は初期版ではLLM編集できない。
- 編集履歴を永続化しないため以前の版へ戻せない。
- 初期版の根拠は要約済みブックマークに限定される。

## Relationship to existing ADRs

- ADR-0003: feature-first依存境界に従い、他featureのDomain Repository contractを利用する。
- ADR-0004: Knowledgeを蔵書とは別の独立概念として扱う。
- ADR-0005: 外部由来文章を命令ではなくデータとして扱う。
- ADR-0047: KnowledgeのDB schemaは`:feature:knowledge:data`が所有し、appがschema contributionを合成する。
- ADR-0056: Knowledge固有のAI生成・編集ポリシーはKnowledge featureが所有する。

## References

- https://github.com/langchain-ai/openwiki
- https://github.com/langchain-ai/openwiki/blob/main/AGENTS.md
