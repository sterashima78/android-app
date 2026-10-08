---
type: module
title: "Article Domain：コンテンツ分類と保持の契約"
description: "RSS に限らない Content の閲覧状態、分類継承、保持期間と source command port を定義する。"
tags: [article, domain, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-f9cb58af605eb50d6c5c61f2
    resource: repo://feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ContentClassificationService.kt
  - id: openwiki-source-c1294c11614f33435f4ade09
    resource: repo://feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ContentRetentionPolicy.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と所有権

`:feature:article:domain` は Content Context のモデルと公開契約を持つ JVM module である。実装名は Article だが、共有URLやインポート済みコンテンツも扱う。記事のidentity、取得元情報、既読状態とコンテンツ種別を表現し、Bookmarkの保存日時やタグ・フォルダを自身の状態にはしない。画面やSQLite実装に依存せず、UIとDataが同じ意味の契約を使う入口となる。

## 入口と処理フロー

ArticleRepository は未読・履歴の取得、記事検索、既読化・未読化と種別変更を提供する。RSS等のsourceはContentSourceGatewayを通して取り込み・名称変更・切り離しを要求する。分類は記事自身のoverride、sourceのoverride、source所属containerのoverrideの順に解決し、何も指定されていなければ記事として扱う。

## 状態と境界

分類と保持は永続状態を持たないDomain serviceである。分類に必要なsource設定はContentClassificationSourceQuery、削除保護はContentRetentionProtectionQueryから受け取る。保持方針は既読から30日を既定期限とし、期限超過候補から他Contextに保護されたidentityを除外する。実際にどの行を候補にするか、SQLや変更通知をどう発行するかはDataが担う。

## 変更時の確認

コンテンツ種別を追加するときは継承順位と自動処理対象可否の双方を確認する。保持条件を変えるときはCurationやSummaryの保護queryを迂回しない。ContentClassificationServiceTestは記事優先・source優先・既定値を、ContentRetentionPolicyTestは保持期限と保護対象の除外を検証する。

## ソースと検証の入口

- [ContentClassificationService](../../../feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ContentClassificationService.kt)
- [ContentRetentionPolicy](../../../feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ContentRetentionPolicy.kt)
- [ContentSourceGateway](../../../feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ContentSourceGateway.kt)
- [ArticleRepository](../../../feature/article/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleRepository.kt)
- [ContentClassificationServiceTest](../../../feature/article/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/article/ContentClassificationServiceTest.kt)
- [ContentRetentionPolicyTest](../../../feature/article/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/article/ContentRetentionPolicyTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [article-data module](article-data.md)
- [article-ui module](article-ui.md)
- [rss-domain module](rss-domain.md)
- [bookmark-domain module](bookmark-domain.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
