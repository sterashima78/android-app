---
type: module
title: Android executable と platform 入口
description: Application、Activity、Intent、ロックと platform action を app composition / presentation へ接続する。
tags: [android, application, entrypoint, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-e0fbba98157491aebe744ff5
    resource: repo://app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Android executable と platform 入口

## 起動と platform ownership

`:app` は APK を作る Android application module で、Android framework が生成する Application / MainActivity と executable 固有の platform、security、entry 処理を所有する。[Manifest](../../../app/src/main/AndroidManifest.xml) が component と permission を定義し、[YomitoriApplication](../../../app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt) が [composition](composition.md) の AppContainer、route dependencies、WorkerFactory を提供する。画面内容と navigation は [presentation](presentation.md) が担い、MainActivity は注入した狭い契約で接続する。

## process と lifetime

Application.onCreate は process 名が package 名と一致する main process だけで runtime を開始する。AI subprocess や game process に背景 runtime、診断 hook、Activity tracker を重複して起動しない。main では crash / memory 診断を導入して container.startBackgroundRuntime を呼ぶ。container と route dependencies は同期 lazy で application lifetime に一つ作る。WorkManager configuration もこの container から作った WorkerFactory を使う。

現在 Activity は weak reference で追跡し、pause / stop / destroy 時に同じ Activity の参照を消す。MainActivity の外部 Intent、アプリロック、生体認証、Web URL の起動など executable の判断は presentation の platform callback へ渡す。feature ViewModel や concrete Data の直接 import を避ける境界は [lint-rules](../tooling/lint-rules.md) と source architecture tests で検査される。

## 変更時の確認

[ApplicationProcessPolicyTest](../../../app/src/test/java/dev/terashima/yomitorirss/ApplicationProcessPolicyTest.kt) は main、local AI text / vision、game の初期化条件を検証する。Intent 変更は SharedBookmarkTest、NotificationLaunchRoutingTest、TaskWidgetLaunchRoutingTest、ロックは AppLockSessionViewModelTest を確認する。component / permission は AppManifestOwnershipArchitectureTest と platform 仕様を読み、Android 側の lifetime と framework からの再生成を確認する。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
