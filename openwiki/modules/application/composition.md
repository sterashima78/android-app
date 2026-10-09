---
type: module
title: Application scope の依存合成
description: feature Data、共有基盤、route factory、WorkerFactory と DB schema を
  application lifetime へ束ねる。
tags:
  - composition
  - dependencies
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:31:39.659Z
sources:
  - id: openwiki-source-d62ac7d985204cb3acec963b
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt
  - id: openwiki-source-b880a68aa13f1bcb9a781305
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt
  - id: openwiki-source-09be1dfd6a431715c79bfd5d
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/IntegratedRefreshWorker.kt
generated: { by: "codex", at: "2026-10-09T04:31:39.659Z" }
---

# Application scope の依存合成

## 具体実装を束ねる場所

`:app:composition` は feature Domain / Data / UI と core infrastructure を接続する Android library で、画面から concrete Data を直接見せないための合成境界である。入口の [AppContainer](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) は application-scope facade として、機能別 runtime dependency group を同期 lazy で作る。HTTP はバージョン付き User-Agent、SQLite helper / DatabaseConnection と change notifier は共有する。UI が repository graph を再構築したり process ごとに別 runtime を作る経路を増やさない。

## 主要な構成要素と公開 API

| 宣言・種類 | 責務・主要 API と接続 | 根拠 |
| --- | --- | --- |
| `AppContainer` class | 共有 database と各 feature repository/capability、AI manager、audio/controller、shared entry/LAN gateway を公開する application facade。`startBackgroundRuntime()` が背景予約と podcast 復旧を開始する。 | [container](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt)。 |
| `AppRouteDependencies` class | content/supporting/video の route graph を包み、feature ViewModel factory、library/video/health/workout bundle、audio と設定読み書きを公開する。 | [route facade](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt)。 |
| `appDatabaseSchema` トップレベル val | feature Data の contribution を共有 schema に合成する。 | [schema 合成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt)。 |
| `createAppWorkerFactory` 関数 | container を受け `DelegatingWorkerFactory` を返す。統合 refresh、RSS recommendation、podcast、workout AI、backup、knowledge、mail、library、widget、summary の factory を登録する。 | [Worker 合成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt)。 |
| `SharedContentEntryCapability` class / `SharedBookmarkSaveOutcome` enum / `AddedSharedWebBook` data class | `saveBookmark` / `addWebBook` / `addWebVideo` は feature 用入力を渡し、exe に必要な追加済み/既存判定や title だけを返す。 | [share 境界](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/entry/SharedContentEntryCapability.kt)。 |
| `IntegratedRefreshNotificationContract` object | 統合通知の launch action を exe の Intent routing と共有する。 | [通知契約](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/IntegratedRefreshNotificationContract.kt)。 |
| `LibraryRouteDependencies` / `BookReaderRouteDependencies` data class | library/organization factory、SMB 契約、Web book CRUD/metadata extractor/test callback、reader の page source/position store を束ねる。 | [content route](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)。 |
| `HealthRouteDependencies` / `WorkoutRouteDependencies` data class | health factory/read permission、workout/history/AI factory/write permission を束ねる。 | [supporting route](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。 |
| `VideoRouteDependencies` data class | video repository/provider、factory、playback resolver、byte source factory。 | [video route](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppVideoRouteDependencies.kt)。 |
| `MailAuthorizationDependencies` / `LibraryAuthorizationDependencies` class | requestAccount と resultFromIntent callback。`MailAuthorizedAccount` / `LibraryAuthorizedAccount` class、`MailAuthorizationOutcome` / `LibraryAuthorizationOutcome` sealed interface が認可済み結果または resolution PendingIntent を表す。 | [authorization bridge](../../../app/composition/src/main/java/dev/terashima/yomitorirss/platform/authorization/AuthorizationDependencies.kt)。 |

`AppRouteDependencies.backgroundFetchWifiOnly` / `setBackgroundFetchWifiOnly` と `integratedRefreshIntervalMinutes` / `setIntegratedRefreshIntervalMinutes` は supporting route に委譲する。Wi-Fi 方針変更は mail 同期方針を更新して統合周期を再予約し、間隔変更も周期を再予約する。repository factory の getter は graph の公開であり、UI state や feature の業務判断を実行しない。

## runtime group の関係

internal group は責務別に次の実装を接続する。

| group | 合成内容と依存関係 |
| --- | --- |
| `AppAiCoreRuntimeDependencies` | shared LocalModelManager と process adapter、ChatGPT client/model preferences、settings adapter、summary repository。HTTP と database を受ける。 |
| `AppAudioRuntimeDependencies` | summary の読取/要求契約から audio playback controller を作る。 |
| `AppContentRuntimeDependencies` | article/bookmark/feed/Reddit と import/enrichment、RSS recommendation。共有 DB/通知/HTTP と summary/structured AI を受ける。 |
| `AppPodcastRuntimeDependencies` | local/cloud の生成、structured AI、audio playback を podcast repository/task/factory に接続し schedule を復旧する。 |
| `AppSupportingRuntimeDependencies` | asset/chat/task/workout/calendar/mail/backup/widget/X/LAN の具体 adapter。calendar は task/workout reader、workout AI は共通生成契約を使う。 |
| `AppHealthRuntimeDependencies` | Health Connect repository の構築。 |
| `AppKnowledgeTaskRuntimeDependencies` / `AppKnowledgeRuntimeDependencies` | 前者が task controller/scheduler/settings、後者が保存/生成/build/page runner。bookmark/summary reader と local/cloud を渡す。 |
| `AppLibraryRuntimeDependencies` / `LibraryRuntimeDependencies` | library catalog、SMB、authorization、metadata/organization AI、book reader と Worker capability をまとめる。 |
| `AppVideoRuntimeDependencies` / `VideoRuntimeDependencies` | library の SMB access/profile と Activity provider を使い video repository/provider/resolver/thumbnail をまとめる。 |
| `AppCrossFeatureRuntimeDependencies` | 上記 repository を受け chat tool、bookmark enrichment、task queue など横断 capability を作る。 |
| `AppBackgroundRuntime` | backup/backfill/integrated の予約と表示通知 observer。業務 task state を持たない。 |

各 group のソースは [Content の runtime group](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)で確認できる。具体型が Domain 契約を実装する箇所は group 内の constructor、presentation への接続は `AppContentRouteDependencies` / `AppSupportingRouteDependencies` / `AppVideoRouteDependencies` 内の ViewModel.Factory constructor を追う。authorization は Data manager の結果を上表の bridge に変換し、Activity Result launcher の所有を presentation に残す。

## 代表的な graph・background の処理フロー

1. exe Application が `AppContainer` を一度構築し、共有 database/notifier/HTTP と必要 group を lazy に初期化する。
2. route facade が factory と狭い bundle を返し、presentation が back-stack owner で ViewModel を生成する。WorkerFactory は同じ container の provider lambda から Worker を生成する。
3. startup は `AppBackgroundRuntime.start` と podcast schedule reconciliation を呼ぶ。予約失敗の一部は `runCatching` で分離し起動全体を止めない。
4. `IntegratedRefreshWorker.doWork` は未読キーを取得 → RSS/Reddit/video provider/mail を順に refresh → 未読差分を計算 → 通知権限がある場合に新着通知、の順で進む。source 別の通常失敗は次 source を妨げず、CancellationException は伝播する。前後の観測に失敗した場合は新着差分を空とする。

internal `IntegratedRefreshWorkerFactory` は自分の workerClassName のみ生成し、`IntegratedRefreshScheduler` は取得ポリシーを付けた unique periodic work を更新する。private `IntegratedRefreshNotifier` は通知と上記 launch contract を結ぶ。[統合実装](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/IntegratedRefreshWorker.kt)、[Worker test](../../../app/composition/src/test/kotlin/dev/terashima/yomitorirss/composition/background/IntegratedRefreshWorkerTest.kt)を確認する。

internal `AppLanWebContentGateway` は `LanWebContentGateway` を実装し、unread/saved/read-later/feed を feature repository から読み LAN 表示モデルへ変換する。[gateway](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/web/AppLanWebContentGateway.kt)は table を直接操作しない。
## route、background、schema への流れ

content、AI core、library、video、supporting などの group は必要な capability を受け取る。knowledge には bookmark / summary reader、cross-feature runtime には chat tool が必要とする各 Domain 契約を渡す。AppRouteDependencies は route ごとの ViewModel factory を組み立て、[presentation](presentation.md) へ渡す。

[createAppWorkerFactory](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt) は DelegatingWorkerFactory に機能別 factory を登録する。provider lambda を介して同じ container の repository / runtime を Worker に注入するため、WorkManager が作る background entrypoint でも Application lookup や第二の repository graph を不要にする。統合 refresh は複数 source の調整として composition 配下にあり、個別 task state は feature が持つ。

[AppDatabaseSchema](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) は feature の schema contribution をまとめて共有 [database](../core/database.md) に渡す。schema の所有そのものは feature Data にあり、app は合成順と version の統合を担う。

## 変更時の確認

[AppCompositionSourceArchitectureTest](../../../app/src/test/java/dev/terashima/yomitorirss/AppCompositionSourceArchitectureTest.kt)、AppCompositionPackageArchitectureTest、MainActivityDependenciesSourceArchitectureTest が依存境界を確認する入口になる。runtime 追加では既存 group を再利用できるか、application lifetime が維持されるか、Worker と UI が同じ capability を使うかを確認する。業務ロジックを container getter へ入れず、owner feature の実装と合成を分ける。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
