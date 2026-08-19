# Architecture Decision Log

`docs/adr/` は、このアプリで行った設計判断の履歴を保存する。

現在のアーキテクチャを理解したい場合は、ADR を番号順にすべて読むのではなく、まず [`../architecture/README.md`](../architecture/README.md) から現在形を確認し、理由・代替案・移行経緯が必要な箇所だけ根拠 ADR へ遡る。

## ADR and current architecture docs

```text
architecture/*.md
  「現在どう設計するか」
       |
       | Sources
       v
adr/*.md
  「なぜその判断をしたか」
```

ADR を後から現在形へ書き換えることは避け、後続判断で変更する場合は新しい ADR の `Supersedes` / `Amends` / `Refines` 等で関係を示す。現在形の集約は `docs/architecture/` を更新する。

## Core architecture source set

### Layer / module / ownership

- [ADR-0001: UI・Domain・Data レイヤの責務を分離する](0001-layered-architecture.md)
- [ADR-0002: 関数・class・interface の境界](0002-function-class-interface-boundaries.md)
- [ADR-0003: Feature-first のマルチモジュール構成](0003-multi-module-architecture.md)
- [ADR-0004: 安定した共有概念の concept-oriented ownership](0004-concept-oriented-modules.md)
- [ADR-0046: アーキテクチャ制約を CI で自動検証する](0046-automated-architecture-verification.md)
- [ADR-0063: feature UI ownership cleanup](0063-feature-ui-ownership-cleanup.md)
- [ADR-0101: feature route と background runtime ownership](0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0116: route-owned root ViewModel wiring](0116-route-owned-root-viewmodel-wiring.md)
- [ADR-0120: Bookmark application service / framework provider boundary](0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0124: Application Service と capability interface を責務境界として使う](0124-application-service-and-capability-segregation.md)

### Domain / Context / persistence

- [ADR-0047: Feature-owned database schema contribution](0047-feature-owned-database-schema-contributions.md)
- [ADR-0098: durable user data を単一 DB へ統合する](0098-unified-user-database.md)
- [ADR-0106: Domain context・Aggregate・persistence ownership](0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117: cross-context persistence boundary phase 1](0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119: Content Classification・Retention・table ownership enforcement](0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0123: Content / Curation 永続化境界の第二段階を完了する](0123-content-curation-persistence-phase2.md)

### Documentation governance

- [ADR-0055: ADR 番号を一意な単調増加番号として管理する](0055-adr-numbering-policy.md)
- [ADR-0122: ADR を根拠とする current architecture documentation を維持する](0122-current-architecture-documentation.md)

## Supporting architecture areas

### Background / AI runtime

- [ADR-0006: durable background sync](0006-durable-background-sync.md)
- [ADR-0020: local AI runtime options](0020-local-ai-runtime-options.md)
- [ADR-0056: feature-owned local AI policies](0056-feature-owned-local-ai-policies.md)
- [ADR-0069: unified AI model settings and task queue](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0071: prioritized background AI task scheduling](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0079: process-wide local AI inference sessions](0079-process-wide-local-ai-inference-sessions.md)
- [ADR-0104: AI task queue feature ownership](0104-ai-task-queue-feature-ownership.md)

### Content / summary / knowledge

- [ADR-0078: content type inheritance](0078-content-type-inheritance-for-rss-articles.md)
- [ADR-0092: Summary と Bookmark metadata generation の分離](0092-separate-summary-and-bookmark-metadata-generation.md)
- [ADR-0105: summary content preparation pipeline](0105-summary-content-preparation-pipeline.md)
- [ADR-0109: generated Knowledge wiki](0109-generated-knowledge-wiki.md)
- [ADR-0113: Knowledge page lifecycle management](0113-knowledge-page-lifecycle-management.md)
- [ADR-0124: Application Service と capability interface を責務境界として使う](0124-application-service-and-capability-segregation.md)

この索引は「現在の architecture source set」を優先した案内であり、全 ADR の機能別目録ではない。特定 feature の設計履歴は `docs/adr/` の番号順ファイルまたは repository search から辿る。

## Numbering and integrity

ADR の番号は4桁の一意な単調増加番号とする。新しい ADR は現在存在する最大番号より大きい番号を使う。

ローカル検査:

```bash
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
```

検査は filename/header の番号一致、番号重複、存在しない ADR 参照、壊れた ADR link 等を検出する。意味的に正しい ADR を参照しているかはレビューで確認する。

## Public repository rule

ADR には設計判断に必要な情報だけを記録し、credential、token、OAuth secret、実ユーザー URL、メールアドレス、個人データ、公開を意図しない endpoint 等を含めない。

## Sources

- [ADR-0055](0055-adr-numbering-policy.md)
- [ADR-0122](0122-current-architecture-documentation.md)
