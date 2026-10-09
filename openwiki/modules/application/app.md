---
type: module
title: Android executable と platform 入口
description: Application、Activity、Intent、ロックと platform action を app composition
  / presentation へ接続する。
tags:
  - android
  - application
  - entrypoint
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-c26a447e058312f2391d4734
    resource: repo://app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt
  - id: openwiki-source-e0fbba98157491aebe744ff5
    resource: repo://app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Android executable と platform 入口

## 起動と platform ownership

`:app` は APK を作る Android application module で、Android framework が生成する Application / MainActivity と executable 固有の platform、security、entry 処理を所有する。[Manifest](../../../app/src/main/AndroidManifest.xml) が component と permission を定義し、[YomitoriApplication](../../../app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt) が [composition](composition.md) の AppContainer、route dependencies、WorkerFactory を提供する。画面内容と navigation は [presentation](presentation.md) が担い、MainActivity は注入した狭い契約で接続する。

## 主要な構成要素と公開境界

| 宣言・種類 | 責務・主要 API | 根拠 |
| --- | --- | --- |
| `YomitoriApplication` class | `onCreate` で main process runtime を起動。container/routeDependencies、WorkManager configuration、database/schema、widget/task/LAN provider 契約を公開する。 | [Application](../../../app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt)。 |
| `MainActivity` class | `onCreate`、`onNewIntent` と start/resume/pause/stop を画面・Intent・ロックへ接続する。 | [Activity](../../../app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt)。 |
| `LibraryShareActivity` / `VideoShareActivity` class | 各共有入口の `onCreate` が payload を MainActivity 向け action へ転送して終了する。 | [Library](../../../app/src/main/java/dev/terashima/yomitorirss/LibraryShareActivity.kt)、[Video](../../../app/src/main/java/dev/terashima/yomitorirss/VideoShareActivity.kt)。 |
| `MainActivityDependenciesProvider` interface | presentation / LAN / incoming Intent の三つの狭い依存を供給する。 | [provider](../../../app/src/main/java/dev/terashima/yomitorirss/MainActivityDependenciesProvider.kt)。 |
| `MainActivityPresentationDependencies` / `MainActivityLanWebDependencies` class | routeDependencies と LAN controller をそれぞれ包み、Application だけが構築する。 | [画面依存](../../../app/src/main/java/dev/terashima/yomitorirss/MainActivityPresentationDependencies.kt)、[LAN依存](../../../app/src/main/java/dev/terashima/yomitorirss/MainActivityLanWebDependencies.kt)。 |
| `IncomingIntentDependencies` class | `saveSharedArticle(url,title,sourceTitle)` / `addSharedWebBook(url,title)` / `addSharedWebVideo(url)` を composition の capability に委譲する。 | [Intent依存](../../../app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentDependencies.kt)。 |

## 内部構成と関係

[IncomingIntentHandler](../../../app/src/main/java/dev/terashima/yomitorirss/entry/IncomingIntentHandler.kt) の `consume` は library/video/bookmark share、notification、task widget、article widget を順に調べる。internal `SharedBookmark` と `parseSharedBookmark` は最初の HTTP(S) URL を抽出し、contentTitle → subject → URL 以外の本文 → host の順で title を選ぶ。[共有 parser](../../../app/src/main/java/dev/terashima/yomitorirss/entry/SharedBookmark.kt)は保存 repository を作らない。`forwardLibraryShareIntent` / `forwardVideoShareIntent` は executable share Activity と MainActivity を接続する。notification/widget mapping は route 文字列ではなく presentation の `AppNavigationTarget` を返す。

[AppLockCoordinator](../../../app/src/main/java/dev/terashima/yomitorirss/security/AppLockCoordinator.kt) は `initialize` / lifecycle メソッド / `updateEnabled` / `requestUnlock` を持ち、`AppLockPreferences` の永続 enabled と `AppLockSessionViewModel` の `lock` / `unlock` を組み合わせる。ViewModel が再構成間の認証 session を保持し、coordinator が prompt と FLAG_SECURE を扱う。`AppLockContent` は解除操作だけを callback に渡す。`AppLockExternalTransitionTracker` は Custom Tab 起動直後の stop を一度だけ免除し、起動失敗で免除を消す。[WebContentLauncher](../../../app/src/main/java/dev/terashima/yomitorirss/platform/WebContentLauncher.kt) の `buildWebCustomTabRequest` と `openWebContentInCustomTab` が検査済み URL と tracker を接続する。

diagnostics は `StartupCrashStore` が uncaught crash と最近の process exit を記録・peek・clear し、`CrashDiagnosticsContent` と `copyCrashReport` が表示とコピーを行う。`CrashDiagnosticsSanitizer` / `sanitizeCrashDetails` は機密を抑制する。`NativeTombstoneSummary` / `NativeBacktraceFrame` と bounded parser は native tombstone の要約を作る。`Android17MemoryAnomalyProfiler`、`AppLocalAiMemoryMonitor`、sampling window は起動時 hook と local AI memory の採取を所有する。[診断実装](../../../app/src/main/java/dev/terashima/yomitorirss/diagnostics/StartupCrashStore.kt)から周辺の診断ファイルを追える。

## 代表的な起動・共有フローと失敗

1. framework が Application を生成し provider の lazy graph を準備する。main process だけで tracker と診断 hook、背景 observer を開始する。
2. MainActivity は注入 provider を取得して lock を初期化する。NavController を lock/crash/main の分岐より上で保持し、ロック表示中も back stack と destination ViewModel を破棄しない。
3. 未ロックかつ crash 表示なしなら Intent を consume する。share の action/extras を先に消して再処理を防ぎ、IO で capability を呼ぶ。
4. 成功なら semantic navigation target と Toast、失敗なら Toast を返す。ロック中の `onNewIntent` は保存だけ行い、解除 callback 後に処理する。crash 診断表示中は通常 Intent 処理を抑制する。

生体ロック有効化は強い生体認証が利用可能かを確認して認証成功後に設定する。pause/stop は再構成・認証 prompt・外部遷移の条件で保護/再ロックを判断する。これらを feature UI や composition に移す場合は executable lifecycle と認証 session の仕様を合わせて確認する。
## process と lifetime

Application.onCreate は process 名が package 名と一致する main process だけで runtime を開始する。AI subprocess や game process に背景 runtime、診断 hook、Activity tracker を重複して起動しない。main では crash / memory 診断を導入して container.startBackgroundRuntime を呼ぶ。container と route dependencies は同期 lazy で application lifetime に一つ作る。WorkManager configuration もこの container から作った WorkerFactory を使う。

現在 Activity は weak reference で追跡し、pause / stop / destroy 時に同じ Activity の参照を消す。MainActivity の外部 Intent、アプリロック、生体認証、Web URL の起動など executable の判断は presentation の platform callback へ渡す。feature ViewModel や concrete Data の直接 import を避ける境界は [lint-rules](../tooling/lint-rules.md) と source architecture tests で検査される。

## 変更時の確認

[ApplicationProcessPolicyTest](../../../app/src/test/java/dev/terashima/yomitorirss/ApplicationProcessPolicyTest.kt) は main、local AI text / vision、game の初期化条件を検証する。Intent 変更は SharedBookmarkTest、NotificationLaunchRoutingTest、TaskWidgetLaunchRoutingTest、ロックは AppLockSessionViewModelTest を確認する。component / permission は AppManifestOwnershipArchitectureTest と platform 仕様を読み、Android 側の lifetime と framework からの再生成を確認する。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
