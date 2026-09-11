# Agent Entry Point

このファイルは、このリポジトリで作業する AI agent の共通入口である。

作業を始めるときは、個別の依頼内容だけで判断せず、次の順序で必要な正本を確認する。

1. `docs/development-workflow.md` — 調査、設計判断、実装、検証、独立レビュー、PR、マージ、成果物共有までの標準フロー
2. `docs/spec.md` — 現行ユーザー仕様の目次。依頼に関係する `docs/spec/*.md` を読む
3. `docs/architecture/system-overview.md` — system 全体像、capability、主要 data flow
4. `docs/architecture/principles.md` — invariant、dependency、ownership rule
5. `docs/architecture/context-map.md` — Domain Context と関係
6. 対象領域の `docs/architecture/*.md` と関連 ADR
7. production code、tests、machine-readable manifests — 文書と現在実装の照合

## 作業ルール

- `docs/development-workflow.md` の手順を必ず適用する。
- user-visible behavior を変更する場合は、対応する `docs/spec/*.md` を同じ変更で更新する。仕様書の構成や目次が変わる場合は `docs/spec.md` も更新する。
- current architecture が変わる場合は `docs/architecture/` を更新し、設計判断が必要な場合は関連 ADR を確認して必要な記録を行う。
- code から一意に決まる値や current architecture の詳細を仕様書へ重複して持たせない。
- 既存 capability で要求を満たせる場合は、並行する第二実装や不要な abstraction を増やさない。
- 公開リポジトリへ credential、token、実ユーザーデータ、private endpoint、共有すべきでない artifact を追加しない。
- 実装後は変更範囲に応じた test / architecture verification と独立レビューを行う。

詳細は `docs/development-workflow.md` を正本とし、このファイルへ手順全文を複製しない。
