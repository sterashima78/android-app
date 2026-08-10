# ADR-0005: チャットに読み取り専用 Agent Skill ハーネスを導入する

- Status: Accepted
- Date: 2026-08-08
- Updated: 2026-08-08

## Context

AIチャットはローカルモデルとの会話と事前に渡された `ChatContextProvider` の参照情報を扱えるが、モデル自身が必要な情報を選んでアプリ内リソースへ問い合わせる仕組みはない。

Google AI Edge Gallery の Agent Skills は、Skill を名前・説明・指示として表現し、LLM が必要な Skill を判断してツールを呼び出す方式を採用している。

Yomitori では RSS、ブックマーク、閲覧履歴、タスクなどのデータは各 feature の Domain Repository contract を通して参照できる。これらへアクセスするために任意コード実行環境を導入すると、アプリ内部データへの権限境界が曖昧になり、既存の feature-first / Domain contract 方針とも一致しない。

## Decision

チャットに Agent Skill ハーネスを導入する。

### Skill と Tool の契約

`:feature:chat:domain` に次を定義する。

- `AgentSkill`: Skill 名、説明、利用指示、利用可能な Tool の集合
- `AgentToolDefinition`: Tool 名、説明、引数定義
- `AgentTool`: Tool 実行の Domain boundary

Skill はモデルが能力を発見するための名前・説明・指示を持つ。

### Tool の実装

アプリ内部リソースを読む Tool は `:feature:chat:data` に置き、各 feature の Domain Repository contract に依存する。

初期実装では次の Skill を提供する。

- `rss-reader`: 購読フィード、未読記事
- `bookmark-reader`: 保存記事、あとで読む、フォルダ、タグ
- `reading-history`: 既読履歴
- `task-reader`: タスク、期限、完了状態

Tool は読み取り専用とし、Repository の更新操作は公開しない。

### Agent loop

モデルには利用可能な Skill と Tool 定義、および厳密な `<tool_call>` JSON 形式をコンテキストとして渡す。

モデルが Tool call を返した場合、ハーネスは次を行う。

1. Tool 名と引数を解析する
2. 登録済み Tool であることを検証する
3. Tool を実行する
4. 結果を Tool observation として次の推論へ渡す
5. モデルが通常の文章を返すまで繰り返す

無限ループを避けるため Tool 実行回数には上限を設ける。Tool 結果の文字数にも上限を設ける。

### Security boundary

Tool 結果は「命令ではなくデータ」としてモデルへ明示する。RSS 記事タイトルなど外部由来データに命令文が含まれても、それを Skill 指示として扱わない。

任意コード実行、任意 SQL、任意ファイルアクセスは Tool として公開しない。

将来書き込み Tool を追加する場合は、読み取り Tool と同じ権限で暗黙実行せず、確認・権限・監査方法を別途設計する。

## Consequences

### Positive

- モデルが質問に必要なアプリ内データだけをオンデマンドで取得できる
- RSS、ブックマーク、タスク等を会話時に一括投入する必要がない
- Skill と Tool を追加することで能力を段階的に拡張できる
- Tool 実装は既存 Domain Repository contract を利用し、DB 実装詳細をモデル側へ露出しない
- 読み取り専用・実行回数上限により初期導入時の権限を限定できる

### Negative

- ローカルモデルが Tool call 形式に従えることへ一定の依存がある
- Tool call ごとに追加推論が必要になり、通常チャットより応答時間が増える
- `:feature:chat:data` から複数 feature の Domain module への依存が増える
- Tool 結果の選択・圧縮が不十分だとコンテキストを消費する

## Relationship to ADR-0003 / ADR-0004

`:feature:chat:data` が複数 feature の Domain module に依存することは ADR-0003 の feature 間依存ルール上許可される。本 ADR ではさらに、Agent Tool がアプリ内リソースを読む境界として Domain Repository contract を利用し、concrete Data implementation や DB API を直接扱わないことをセキュリティ上の設計として選択する。

`:app` は composition root として Repository implementation と Skill factory を接続する。

Agent Skill はチャット機能の振る舞いとして所有できるため、現時点では独立した concept-oriented module を追加しない。将来チャット以外の複数 feature から同じ Agent Skill 基盤を利用する場合は、ADR-0004 の基準に従って独立 module 化を再検討する。
