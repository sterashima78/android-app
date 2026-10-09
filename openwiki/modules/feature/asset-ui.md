---
type: module
title: Asset UI：概要、収集、カテゴリ設定
description: 資産画面の再読込、document import と Secure Web Collector、履歴グラフ。
tags:
  - asset
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-4301550c5269cbc315378da8
    resource: repo://feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt
  - id: openwiki-source-e6c6f0ee05dd9d543c8fd3f5
    resource: repo://feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChart.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Asset UI：概要、収集、カテゴリ設定

`:feature:asset:ui` はAssetRoute と資産画面の概要・import・カテゴリ設定を所有する。`AssetViewModel` は注入された Domain Repository を使い、loading、overview、message を公開する。カテゴリ構成と積み上げ履歴の描画はこの層の表示責務であり、保存時の非負ルールは Data にある。ViewModel や画面から database を直接読む構成にはしない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| AssetRoute（Composable） | 注入された Factory から ViewModel を取得し AssetScreen を呼ぶ。 | [AssetRoute.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetRoute.kt) |
| AssetUiState / AssetViewModel / Factory（data class・class） | loading、overview、message を保持する。importTsv / importMoneyForward / addCategory / setCategory が runOperation を使い、dismissMessage が通知を閉じる。 | [AssetManagementDialog.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt) |
| AssetScreen（Composable）、AssetTab（private enum） | 概要・インポート・設定、document launcher と分類 dialog を組み立てる。 | [AssetManagementDialog.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt) |
| AssetOverviewTab / AssetImportTab / AssetSettingsTab（private Composable） | 総額と chart、入力の選択、カテゴリ別編集を担当する。 | [AssetManagementDialog.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt) |
| AssetCategoryGroup / groupAssetCategorySettings（internal 型・関数）、moneyForwardCollectorConfig（private 関数） | 空カテゴリを保持した grouping と origin / navigation 制限を持つ collector 設定。 | [AssetManagementDialog.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt) |
| AssetAreaBand / AssetAreaChartData / AssetYAxis / AssetXAxisTick / AssetPieSlice（internal data class） | 積上げ帯、縦軸・日付軸、円の描画用 read model。 | [AssetStackedHistoryChart.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChart.kt) |
| buildAssetCategoryColorMap / buildAssetPieSlices / buildAssetAreaChartData / buildAssetYAxis / buildAssetXAxisTicks（internal 関数） | カテゴリ色、正額のみの円、金額または比率の帯、見やすい軸と日付目盛を計算する。 | [AssetStackedHistoryChart.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChart.kt) |
| assetDateFraction / formatAssetYAxisValue / formatAssetXAxisValue / AssetStackedHistoryChart（internal 関数） | 暦日の間隔を座標へ変え、金額・比率ラベルと Canvas を描く。 | [AssetStackedHistoryChart.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChart.kt) |
| ASSET_ROUTE / ASSET_TITLE（定数） | navigation metadata。 | [NavigationDestination.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/NavigationDestination.kt) |

## 主要 API と接続

`AssetViewModel.Factory` は `AppSupportingRouteDependencies` で `container.assetRepository` に接続される。`onChanged` は注入可能な callback だが、現行接続では既定の空 callback。`AssetScreen` の OpenDocument が URI を文字列として渡し、`SecureWebCollectorDialog`（core:web-collector）が許可 origin のページから返した JSON を `importMoneyForward` に渡す。

## 代表的な処理フロー

文書選択 → `AssetViewModel.importTsv` → private `runOperation` → Repository import → onChanged → `loadOverview` → state → `AssetOverviewTab` の chart。operation 失敗は同じ onFailure で表示するが、成功 callback 内の onChanged / overview 再取得例外はその runCatching に捕捉されない。分類 grouping は項目ゼロの登録カテゴリを省略しない。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## 操作と再読込

初期化で overview を IO coroutine により取得する。TSV import、MoneyForward JSON import、カテゴリ追加、資産名分類は共通の操作 wrapper で実行する。operation が成功すると変更 callback を呼び overview を再取得し、その再取得も成功した場合に成功メッセージを表示する。operation 自体の失敗は loading を解除して例外メッセージを表示する。一方、成功 callback 内の onChanged と overview 再取得は runCatching の外であり、その例外は同じ onFailure で処理されない。import の完了だけを見てグラフ更新や loading 解除まで成功したと判断しない。

TSV は Compose の OpenDocument launcher が返す URI を Repository に渡す。MoneyForward は `SecureWebCollectorDialog` に feature 固有 config を渡し、収集可能なページと script を制限して得た JSON を同じ import command へ流す。認証ページの WebView と snapshot 保存を一体の汎用 storage にせず、収集 boundary と Domain の取り込みを分ける。

カテゴリ設定は登録カテゴリを基準に grouping し、項目のない登録カテゴリも空の group として残す。履歴 chart はカテゴリ色と時系列の read model を受け取る。`AssetCategoryGroupingTest` が空カテゴリと並び、`AssetStackedHistoryChartTest` がグラフ用の値を検証する。収集先や script を変える際は core web collector の許可境界も確認する。
## 変更時の調査先

- [AssetRoute.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetRoute.kt)
- [AssetManagementDialog.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt)
- [AssetStackedHistoryChart.kt](../../../feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChart.kt)
- [AssetCategoryGroupingTest.kt](../../../feature/asset/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/asset/AssetCategoryGroupingTest.kt)
- [AssetStackedHistoryChartTest.kt](../../../feature/asset/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/asset/AssetStackedHistoryChartTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/09-assets.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](asset-domain.md)、[data 層](asset-data.md)、[システム全体](../../architecture/system.md)。
