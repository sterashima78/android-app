# Agent Entry Point

このファイルは、このリポジトリで作業する AI agent の共通入口である。

作業を始めるときは、個別の依頼内容だけで判断せず、次の順序で必要な正本を確認する。

1. `docs/development-workflow.md` — 調査、設計判断、実装、検証、独立レビュー、PR、マージ、成果物共有までの標準フロー
2. `docs/spec.md` — 現行ユーザー仕様の目次。依頼に関係する `docs/spec/*.md` を読む
3. `docs/formal-modeling.md` — Quint / Alloy を使う仕様の役割分担、traceability、変更・検査ルール
4. 仕様節から参照される `spec-models/quint/*.qnt` / `spec-models/alloy/*.als` — 対応する状態遷移・構造 invariant
5. `docs/architecture/system-overview.md` — system 全体像、capability、主要 data flow
6. `docs/architecture/principles.md` — invariant、dependency、ownership rule
7. `docs/architecture/context-map.md` — Domain Context と関係
8. 対象領域の `docs/architecture/*.md` と関連 ADR
9. production code、tests、machine-readable manifests — 文書と現在実装の照合

## 作業ルール

- `docs/development-workflow.md` の手順を必ず適用する。
- user-visible behavior を変更する場合は、対応する `docs/spec/*.md` を同じ変更で更新する。仕様書の構成や目次が変わる場合は `docs/spec.md` も更新する。
- 対象仕様から Quint / Alloy model が参照されている場合は同じ変更でmodelも確認し、意味が変わるなら更新する。新しいstateful / relational ruleの形式化判断とtraceabilityは `docs/formal-modeling.md` に従う。
- current architecture が変わる場合は `docs/architecture/` を更新し、設計判断が必要な場合は関連 ADR を確認して必要な記録を行う。
- code から一意に決まる値や current architecture の詳細を仕様書へ重複して持たせない。
- 既存 capability で要求を満たせる場合は、並行する第二実装や不要な abstraction を増やさない。
- 公開リポジトリへ credential、token、実ユーザーデータ、private endpoint、共有すべきでない artifact を追加しない。
- 実装後は変更範囲に応じた test / architecture verification と独立レビューを行う。
- 独立レビューを含むレビューはすべてローカルの作業環境（agent の作業環境を含む）で完結させる。GitHub PR 上でレビューを実施・投稿しない。結果は必要に応じて PR 本文の `Independent review result` に要約する。
- GitHub PR に対し、Codex、Copilot などの AI agent へレビューを依頼・起動しない。レビュアーの指定、PR コメントやメンションによる依頼、レビュー用 API / CLI / 自動レビュー機能の呼び出しを行わない。CI による検証は従来どおり実施する。

詳細は `docs/development-workflow.md` を正本とし、このファイルへ手順全文を複製しない。

## 開発用 Wiki の位置づけ

`openwiki/` は現在のソースとテストへの案内として使い、上記の仕様・設計・開発手順の正本を置き換えない。運用は `docs/openwiki.md`、生成方針は `openwiki/INSTRUCTIONS.md` を参照する。更新は Codex 連携から行い、OpenWiki 用の GitHub Actions workflow は追加しない。生成ツールが workflow や定期更新を前提とする案内を再作成した場合は、生成完了後に取り除く。

<!-- OPENWIKI:START -->

## OpenWiki

This repository has a generated `openwiki/` evidence index. It is optional just-in-time context, not required startup reading.

- Do not enumerate, preload, or search wikis at task start. Use retrieval when the user asks for it, when unfamiliar architecture or dependency behavior materially affects the task, or when source inspection leaves an important uncertainty. Stop once the question is grounded.
- When those conditions apply and OpenWiki retrieval tools are available, use `openwiki_search` for just-in-time context and `openwiki_read` for the relevant complete sections. If search returns `workspace_required`, ask which listed workspace to use and retry with its ID.
- Use `openwiki_list_workspaces` or `openwiki_list_wikis` when workspace membership itself needs to be discovered.
- If the retrieval tools are unavailable, read `openwiki/quickstart.md` and follow its links to the relevant pages.
- Treat source code and tests as authoritative. A brief's unknowns and review items are verification gaps, not automatic requirements.
- Prefer the narrowest quiet validation that proves the changed behavior. Preserve complete failure output.

Update the repository wiki through the Codex OpenWiki integration. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
