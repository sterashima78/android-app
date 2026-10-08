---
type: module
title: "Asset UI：概要、収集、カテゴリ設定"
description: "資産画面の再読込、document import と Secure Web Collector、履歴グラフ。"
tags: [asset, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-4301550c5269cbc315378da8
    resource: repo://feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/AssetManagementDialog.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Asset UI：概要、収集、カテゴリ設定

`:feature:asset:ui` はAssetRoute と資産画面の概要・import・カテゴリ設定を所有する。`AssetViewModel` は注入された Domain Repository を使い、loading、overview、message を公開する。カテゴリ構成と積み上げ履歴の描画はこの層の表示責務であり、保存時の非負ルールは Data にある。ViewModel や画面から database を直接読む構成にはしない。

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
