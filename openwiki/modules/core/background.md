---
type: module
title: 背景取得ポリシーとローカル AI の実行調停
description: 機能を横断する通信制約、AI 一時停止設定、優先度付き直列実行を管理する。
tags: [background, scheduling, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-b1c43ce83f8d4073f1e2815e
    resource: repo://core/background/src/main/kotlin/dev/terashima/yomitorirss/core/background/BackgroundDataFetchPolicy.kt
  - id: openwiki-source-748bfa73e0bdb101c81e9fee
    resource: repo://core/background/src/main/kotlin/dev/terashima/yomitorirss/core/background/CloudAiBackgroundExecutionPreferences.kt
  - id: openwiki-source-6961e379a2f04159d9de755d
    resource: repo://core/background/src/main/kotlin/dev/terashima/yomitorirss/core/background/LocalAiBackgroundTaskGate.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 背景取得ポリシーとローカル AI の実行調停

## 共有するものと feature の所有物

`:core:background` は feature queue の業務状態を保存する場所ではなく、横断的な実行条件を提供する。[BackgroundDataFetchPolicy](../../../core/background/src/main/kotlin/dev/terashima/yomitorirss/core/background/BackgroundDataFetchPolicy.kt) は Wi-Fi 限定と統合 refresh 間隔を SharedPreferences に保持し、WorkManager Constraints と実行時のネットワーク確認を作る。Wi-Fi 限定時は INTERNET capability と Wi-Fi transport を要求する。refresh 間隔は許可された候補のみ書け、保存済みの不正値は既定値へ戻す。

## AI の停止と順序

ローカル AI は paused と充電時再開の設定、クラウド AI は独立した paused 設定を持つ。クラウドの実行可否はローカルの充電ポリシーとは分離される。実際の task の保存、再予約、状態遷移は feature が担当する。

[LocalAiBackgroundTaskGate](../../../core/background/src/main/kotlin/dev/terashima/yomitorirss/core/background/LocalAiBackgroundTaskGate.kt) は重いローカル AI 処理を feature 間で直列化する process 内 singleton である。withPermit の block を実行し、終了・例外時に finally で解放する。実行中の処理は割り込まず、解放時に待機者の HIGH / NORMAL / LOW 優先度と同優先度 FIFO を使う。待機キャンセルは queue から除き、permit 付与直後のキャンセルも次へ解放する。診断 label は現在の実行者を示すだけで、業務状態には使わない。

## 変更時の確認

[LocalAiBackgroundTaskGateTest](../../../core/background/src/test/kotlin/dev/terashima/yomitorirss/core/background/LocalAiBackgroundTaskGateTest.kt) が優先度、待機、キャンセルの確認入口となる。設定を追加するときは LocalAiBackgroundExecutionPreferencesTest、CloudAiBackgroundExecutionPreferencesTest、BackgroundDataFetchPolicyTest を合わせて読む。process 再起動でこの gate の待機列は失われるため、durable な再開は [app composition](../application/composition.md) と各 Worker 側を追う。

仕様・設計の正本: [architecture/background-refresh.md](../../../docs/architecture/background-refresh.md)、[spec/13-background-execution.md](../../../docs/spec/13-background-execution.md)。
