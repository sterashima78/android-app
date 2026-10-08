---
type: module
title: Application scope の依存合成
description: feature Data、共有基盤、route factory、WorkerFactory と DB schema を application lifetime へ束ねる。
tags: [composition, dependencies, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-d62ac7d985204cb3acec963b
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt
  - id: openwiki-source-b880a68aa13f1bcb9a781305
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Application scope の依存合成

## 具体実装を束ねる場所

`:app:composition` は feature Domain / Data / UI と core infrastructure を接続する Android library で、画面から concrete Data を直接見せないための合成境界である。入口の [AppContainer](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) は application-scope facade として、機能別 runtime dependency group を同期 lazy で作る。HTTP はバージョン付き User-Agent、SQLite helper / DatabaseConnection と change notifier は共有する。UI が repository graph を再構築したり process ごとに別 runtime を作る経路を増やさない。

## route、background、schema への流れ

content、AI core、library、video、supporting などの group は必要な capability を受け取る。knowledge には bookmark / summary reader、cross-feature runtime には chat tool が必要とする各 Domain 契約を渡す。AppRouteDependencies は route ごとの ViewModel factory を組み立て、[presentation](presentation.md) へ渡す。

[createAppWorkerFactory](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt) は DelegatingWorkerFactory に機能別 factory を登録する。provider lambda を介して同じ container の repository / runtime を Worker に注入するため、WorkManager が作る background entrypoint でも Application lookup や第二の repository graph を不要にする。統合 refresh は複数 source の調整として composition 配下にあり、個別 task state は feature が持つ。

[AppDatabaseSchema](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) は feature の schema contribution をまとめて共有 [database](../core/database.md) に渡す。schema の所有そのものは feature Data にあり、app は合成順と version の統合を担う。

## 変更時の確認

[AppCompositionSourceArchitectureTest](../../../app/src/test/java/dev/terashima/yomitorirss/AppCompositionSourceArchitectureTest.kt)、AppCompositionPackageArchitectureTest、MainActivityDependenciesSourceArchitectureTest が依存境界を確認する入口になる。runtime 追加では既存 group を再利用できるか、application lifetime が維持されるか、Worker と UI が同じ capability を使うかを確認する。業務ロジックを container getter へ入れず、owner feature の実装と合成を分ける。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
