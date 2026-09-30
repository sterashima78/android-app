# Mosaic 現行仕様

- 更新日: 2026-09-11
- 対象: 現在の `main` 系列

このファイルは現行ユーザー仕様の目次である。仕様本文は `docs/spec/` 配下のセクション別ファイルを正本とする。

一部のstate / relation invariantは、自然言語仕様を補完する機械検査可能なprojectionとして `spec-models/quint/` と `spec-models/alloy/` に記述する。形式化された仕様節から対応modelへ直接linkし、modelのscopeと検査対象が分かる状態を維持する。運用ルールは [`formal-modeling.md`](formal-modeling.md) を参照する。

機能変更を行う場合は、変更内容に対応するセクションだけを確認・更新する。仕様書の構成や目次そのものを変更する場合は、このファイルも同じ変更で更新する。対象セクションから形式modelが参照されている場合は、同じ変更でmodelも確認・更新する。

## 目次

1. [目的](spec/01-purpose.md)
2. [この仕様書の責務](spec/02-document-scope.md)
3. [対象環境](spec/03-platform.md)
4. [コンテンツ閲覧と整理](spec/04-content.md)
5. [端末内AI](spec/05-on-device-ai.md)
6. [メール](spec/06-mail.md)
7. [蔵書とBook Reader](spec/07-library-reader.md)
8. [タスク、カレンダー、ワークアウト、ヘルス](spec/08-task-calendar-workout-health.md)
9. [資産](spec/09-assets.md)
10. [Web、X、Widget、補助機能](spec/10-web-x-widget-games.md)
11. [永続化](spec/11-persistence.md)
12. [バックアップと復元](spec/12-backup-restore.md)
13. [Background execution](spec/13-background-execution.md)
14. [更新互換性](spec/14-update-compatibility.md)
15. [Privacy / security](spec/15-privacy-security.md)
16. [現在の非目標](spec/16-non-goals.md)
17. [関連文書](spec/17-related-documents.md)

## 参照方針

- ユーザーから見た現行機能と互換性に影響する振る舞いは、上記のセクション別仕様を参照する。
- 仕様節から参照される Quint / Alloy model は、その節から選択した状態遷移・構造制約の機械検査可能なprojectionとする。自然言語仕様を置き換えず、矛盾した場合は仕様不整合として同じ変更内で解消する。
- アーキテクチャ上の依存方向、module ownership、table ownership、テスト戦略、Android platform 基準は `docs/architecture/` を正本とする。
- 設計判断の理由と変更履歴は `docs/adr/` を正本とする。
- code から一意に決まる値は仕様書へ複製せず、production code や machine-readable manifest を参照する。
