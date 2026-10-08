---
type: workflow
title: AI とバックグラウンド処理
description: 技術 inference と feature policy の分離、application graph、Worker lifecycle、Knowledge の生成を説明する。
tags: [ai, background, knowledge]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T11:54:35.312Z
sources:
  - id: openwiki-source-b880a68aa13f1bcb9a781305
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt
  - id: openwiki-source-bd1d3e5466a81c988b4181b1
    resource: repo://core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt
  - id: openwiki-source-0f5c54634ad8069b69a5984b
    resource: repo://core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt
  - id: openwiki-source-c489159db325fe8b3a535420
    resource: repo://docs/architecture/ai-runtime.md
  - id: openwiki-source-872141f77f71851168245852
    resource: repo://docs/architecture/system-overview.md
  - id: openwiki-source-315fd2b1fd6c687db540a5fb
    resource: repo://feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeApplicationServiceArchitectureTest.kt
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# AI とバックグラウンド処理

AI の技術 runtime と、何を送信しどう生成するかという feature policy は別の責務である。[AI Runtime](../../docs/architecture/ai-runtime.md) と [Background Refresh](../../docs/architecture/background-refresh.md) を正本として、provider・prompt・task state・実行 lifetime を分けて調査する。

## provider-neutral capability

core の inference contract を Local / Cloud adapter が実装し、feature はその capability と自身の routing policy を使う。cloud egress を暗黙 fallback で追加しない。Chat の対話・streaming・tool execution と、非対話型 background inference の契約を混ぜない。

[ProcessIsolatedLocalAiTextInference](../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt) は background の生成 engine を短寿命 subprocess へ隔離する。model metadata と token count は main 側に置き、generation が Binder 境界を越える。application-scope adapter の共有と、重い engine の main process 常駐は同義ではない。

接続・response 待機には runtime 側の watchdog があり、timeout cleanup と呼び出し元 coroutine の cancellation を区別する。failure の詳細や現在の条件は実装と [ProcessIsolatedLocalAiTextInferenceTest](../../core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt) を確認する。

## UI から Worker へ

長時間処理は画面の Composable lifetime に閉じず、owner の scheduler / task controller と WorkManager / queue を使う。[AppWorkerFactory](../../app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt) は feature WorkerFactory を既存 AppContainer graph に接続する。Worker 内で並行する DB / Repository graph を新しく組み立てない。

UI は task の依頼・停止・再開・状態表示を扱い、非対話型 background inference capability を直接保持しない。task の durable state、retry、pause の意味は各 feature が所有する。DB に存在する処理 state がすべて backup 対象の user data になるわけではない。[永続化](../architecture/persistence.md) の local / durable boundary も確認する。

## アプリ内 Knowledge の生成

Knowledge の Repository は persistence を担い、cross-context source 収集と AI 生成を共同所有しない。generation service は SQL を直接扱わない。親 Worker が rebuild を計画し、topic Worker へ分けて生成する。[KnowledgeApplicationServiceArchitectureTest](../../feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeApplicationServiceArchitectureTest.kt) がこの分離を固定する。

実装入口は [KnowledgeBuildBackground](../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildBackground.kt)、正本は [Knowledge Architecture](../../docs/architecture/knowledge.md)。停止・再開・page AI task の state は [形式モデル一覧](../../spec-models/README.md) から対応する spec と model に進む。

ここでいう Knowledge はアプリのユーザー資料から Wiki を生成する機能。このリポジトリのソースから開発資料を作る OpenWiki は [開発・Wiki 運用](../operations/development.md) にある。

## cloud failure の責務

ChatGPT provider の network / HTTP / OAuth failure は core adapter で安定した taxonomy に正規化する。typed failure に raw response、prompt、token、account ID、対象 URL を保持しない。WorkManager retry や queue state への写像は feature policy で決める。Podcast の chapter retry は [媒体連携](../integrations/media.md) と Podcast Architecture を参照する。
