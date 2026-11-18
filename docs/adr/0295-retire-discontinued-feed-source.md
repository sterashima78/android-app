# ADR-0295: 提供終了した外部フィード連携の退役

- Status: Accepted
- Date: 2026-11-18
- Supersedes: ADR-0009

## Context

Reddit の公開RSS endpointが終了し、従来の専用購読・投稿更新追跡を継続できなくなった。外部の非公式APIやスクレイピングへの移行は継続性・利用条件・保守コストの不確実性を増やす。

既存の専用機能は一般RSS transportに依存し、元データの一部をContentとCurationの共通テーブルで保持する。削除時に既存保存記事を破棄してはならない。

## Decision

1. Reddit専用のdomain/data/ui、navigation、統合ビューへのprojection、AI skill、LAN Webの専用view、周期更新を撤去する。一般のRSS・Atom、ニュースポッドキャスト、ブックマークは維持する。
2. 旧フィード購読レコードは自動削除しない。RSS-owned legacy URL filterを使い、フィード一覧・未読一覧・自動更新・新規登録・OPML importの対象から除外する。refresh APIの直接呼び出しでも取得しない。
3. Content/Curationが所有する既存の記事・保存状態は変更せず、ブックマークや保存済み要約を引き続き利用できるようにする。既存の履歴データの即時物理削除は行わない。
4. 旧URL分類コードは失効済みデータへの互換性ガードのみとし、専用購読APIや汎用的な外部API抽象化を再導入しない。
5. 現行の仕様・設計と検証を更新する。生成されたWikiは手編集せず、既存生成フローに任せる。

## Consequences

- サポート終了した情報源へのネットワークアクセスと専用UI依存をなくせる。
- 既存購読行はバックアップ互換性のために残る。将来のデータ移行・整理は、保存済みデータの保全を検証してから独立した判断として実施する。
- ADR-0009のRSS transport再利用とReddit feature維持の決定はここで失効する。
