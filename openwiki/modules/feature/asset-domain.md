---
type: module
title: "Asset Domain：dated snapshot の公開契約"
description: "最新総額、カテゴリ履歴とインポート・カテゴリ操作の境界。"
tags: [asset, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-c482dd5141dd1d42cc6d17c0
    resource: repo://feature/asset/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetModels.kt
  - id: openwiki-source-721ab034d63a5242476d090b
    resource: repo://feature/asset/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Asset Domain：dated snapshot の公開契約

`:feature:asset:domain` は資産の dated snapshot を閲覧・分類・取り込むための Kotlin/JVM 契約を定義する。`AssetRepository` は overview の読取、document URI の TSV import、MoneyForward JSON import、カテゴリ追加と資産名への分類を公開する。SQLite、ContentResolver、WebView はこの契約に露出させず、それぞれ Data と UI adapter に分ける。

## snapshot と分類

`AssetOverview` は最新日付、最新総額、カテゴリ構成、日別履歴、資産名ごとのカテゴリ設定、登録カテゴリをまとめる。最新日付は nullable なので、未保存の状態を架空の日付や金額データとして扱わない。履歴点は日付・総額・カテゴリ別総額を保持し、画面はこの read model から構成と時系列のグラフを作る。

カテゴリは日々の明細から独立した資産名の設定である。新しい分類を追加する操作と、資産を分類に割り当てる操作を分けることで、まだ項目を持たない登録カテゴリも UI に表示できる。import の結果は行数と snapshot 数を返す。URI の読取権限や Web Collector の収集許可は Domain では解決しない。

金額の負数除外と日付単位置換の実施点は Data であり、Domain の DTO に全ての検査を置いていると推測しない。この Domain に専用 test source は現在なく、Repository の動作は Data の `DefaultAssetRepositoryTest`、表示の分類と履歴は UI の focused test で確認する。契約を広げるときは [資産仕様](../../../docs/spec/09-assets.md) と [非負 snapshot model](../../../spec-models/alloy/asset_nonnegative_snapshots.als) を読む。
## 変更時の調査先

- [AssetRepository.kt](../../../feature/asset/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetRepository.kt)
- [AssetModels.kt](../../../feature/asset/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetModels.kt)

仕様の正本は [機能仕様](../../../docs/spec/09-assets.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [data 層](asset-data.md)、[ui 層](asset-ui.md)、[システム全体](../../architecture/system.md)。
