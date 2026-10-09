---
type: module
title: Asset Data：資産 snapshot の置換と非負集計
description: TSV/JSON import、日付単位 transaction と既存負額を除く read model。
tags:
  - asset
  - data
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-99afe94b3795153de2b45323
    resource: repo://feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Asset Data：資産 snapshot の置換と非負集計

`:feature:asset:data` はAssetRepository を共有 database と document 読取に接続する。feature の schema contribution が明細、資産名別カテゴリ、登録カテゴリの保存を担当する。`DefaultAssetRepository` は TSV と MoneyForward JSON を共通の parsed row に正規化し、日付別 snapshot として保存する。資産の保存 ownership はこの feature にあり、UI のグラフを正本にしない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| DefaultAssetRepository（class） | AssetRepository を共有 database と ContentResolver に接続する。loadOverview が現在のカテゴリ設定で全履歴を再集計する。 | [DefaultAssetRepository.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt) |
| ParsedAssetRow（internal data class） | TSV と JSON を同じ date / name / amount / account へ正規化する中間行。 | [DefaultAssetRepository.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt) |
| parseTsv / parseAmount / parseDate / buildAssetRecordName（internal 関数） | BOM・見出しと日付書式、円記号等の金額を解析し、口座があれば資産名に連結する。 | [DefaultAssetRepository.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt) |
| buildAssetSnapshotReplacements（internal 関数）、replaceSnapshots（private メソッド） | 日付で grouping して負額除外後の置換集合を作り、transaction でその日の既存行を削除して再挿入する。 | [DefaultAssetRepository.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt) |
| assetDatabaseSchema（val）、createCategoryDefinitions（private 関数） | 明細・資産名分類・カテゴリ定義と索引を所有し、既定カテゴリと既存分類をカテゴリ定義に取り込む。 | [AssetDatabaseSchema.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/AssetDatabaseSchema.kt) |

## 主要 API と接続

`importTsv` は UTF-8 の URI 入力を閉じながら解析し、空結果や開けない入力を拒否する。`importMoneyForwardJson` は format / version / 日付・明細を検査する。`addCategory` は trim 後の空値・重複を拒否し、`setCategory` はカテゴリ定義の登録と資産名分類の upsert を同一 transaction にする。

## 代表的な処理フロー

`AssetViewModel.importTsv` → `DefaultAssetRepository.importTsv` → `parseTsv` → `ParsedAssetRow` → `buildAssetSnapshotReplacements` → `replaceSnapshots` → AssetImportResult。`loadOverview` は asset_entries と asset_categories を join し、日付順の履歴から最後を最新とする。カテゴリ変更は保存済み各日のカテゴリ列を書き換えるのではなく、その後の join 結果を変える。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## import の置換規則

保存は import に含まれる全対象日を一つの transaction で置換し、それぞれの日の既存明細を削除して入力明細を挿入する。対象日を入力全体から先に確定し、その後で金額が負の行を除く。そのためある日の入力が全て負でも、その日を置換対象から消さず、以前の正の snapshot を残さない。0 の明細は保存対象に残る。結果の行数と snapshot 数は実際の非負の保存候補から数える。

read model も SQL で `amount >= 0` を条件にし、既存 database に負額が残っていても総額・履歴・資産名分類に混ぜない。明細の資産名をカテゴリ設定へ結合し、設定のないものは既定カテゴリへまとめる。登録カテゴリは既存設定も加えて正規化し、項目を持たない分類を保持する。表示除外だけのために保存済み負額の物理削除を前提にしない。

document 読取、形式 parse、SQL の失敗は呼出し側へ伝わり、UI が操作の失敗を表示する。`AssetDelimitedParserTest` は金額・日付・区切り形式の解釈、`DefaultAssetRepositoryTest` は日付置換と負数境界、カテゴリの read model を確認する。保存形式を変える場合は backup と migration、負額ルールを変える場合は対応 Alloy model も調べる。
## 変更時の調査先

- [DefaultAssetRepository.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepository.kt)
- [AssetDatabaseSchema.kt](../../../feature/asset/data/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/data/AssetDatabaseSchema.kt)
- [AssetDelimitedParserTest.kt](../../../feature/asset/data/src/test/kotlin/dev/terashima/yomitorirss/feature/asset/data/AssetDelimitedParserTest.kt)
- [DefaultAssetRepositoryTest.kt](../../../feature/asset/data/src/test/kotlin/dev/terashima/yomitorirss/feature/asset/data/DefaultAssetRepositoryTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/09-assets.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](asset-domain.md)、[ui 層](asset-ui.md)、[システム全体](../../architecture/system.md)。
