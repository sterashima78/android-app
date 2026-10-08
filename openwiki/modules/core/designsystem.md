---
type: module
title: 共有 Compose 表現とジェスチャー
description: 一覧 swipe、pull-to-refresh、Markdown と chat 表現を業務状態から分離して共有する。
tags: [compose, designsystem, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3b83cac3d851642b2037c88d
    resource: repo://core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/PullToRefreshContainer.kt
  - id: openwiki-source-456f56f4ed96057a441af57c
    resource: repo://core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/SwipeActionListItem.kt
  - id: openwiki-source-357aa32392b58c6ff015f5b6
    resource: repo://core/designsystem/src/test/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownTextTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 共有 Compose 表現とジェスチャー

## UI 部品の責務

`:core:designsystem` は各 feature UI が再利用する Compose 部品を所有し、repository や背景 queue を構築しない。[SwipeActionListItem](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/SwipeActionListItem.kt) は itemKey ごとの drag / commit 状態を保持し、呼び出し側が渡す SwipeAction の label、色、dismiss 動作、onCommit を使う。左右の通常・深い swipe と閾値を分け、DeliberateFarAction は幅に応じた閾値、抵抗、haptic で深い操作を意図的に選べるようにする。業務上の削除・保存は callback 側で実行する。

## 表現と状態の受け渡し

[PullToRefreshContainer](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/PullToRefreshContainer.kt) は enabled かつ更新中でない場合だけ onRefresh を呼ぶ。画面読み上げ用の「更新」custom accessibility action も同じ条件を使うため、ジェスチャーと accessibility で二重 refresh の抑制を揃える。isRefreshing は caller の状態で、部品側が取得処理を保持しない。

[MarkdownText](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownText.kt) は見出し、段落、リスト、code fence、table を block に分解して Compose 表示する。streaming 途中の未閉鎖 fence も code として保持し、安全なリンク scheme を判定して click を許す。ChatMessageBubble はこの表現をチャットで再利用する入口となる。永続 message、送信、生成 progress の所有は feature 側である。

## 部品変更の確認

[MarkdownTextTest](../../../core/designsystem/src/test/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownTextTest.kt) は block 分解、table、未閉鎖 fence、http / https / mailto と危険 scheme の拒否を検証する。swipe の境界は SwipeDecisionTest と FarLeftSwipeDecisionTest を読む。部品を変える場合は feature の callback 契約と itemKey による状態 reset を保ち、画面ごとの業務判断を共通 UI に移さない。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)。
