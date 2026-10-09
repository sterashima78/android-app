---
type: module
title: 共有 Compose 表現とジェスチャー
description: 一覧 swipe、pull-to-refresh、Markdown と chat 表現を業務状態から分離して共有する。
tags:
  - compose
  - designsystem
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-7e10af990204960994629f63
    resource: repo://core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/ChatMessageBubble.kt
  - id: openwiki-source-3b83cac3d851642b2037c88d
    resource: repo://core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/PullToRefreshContainer.kt
  - id: openwiki-source-456f56f4ed96057a441af57c
    resource: repo://core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/SwipeActionListItem.kt
  - id: openwiki-source-357aa32392b58c6ff015f5b6
    resource: repo://core/designsystem/src/test/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownTextTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 共有 Compose 表現とジェスチャー

## UI 部品の責務

`:core:designsystem` は各 feature UI が再利用する Compose 部品を所有し、repository や背景 queue を構築しない。[SwipeActionListItem](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/SwipeActionListItem.kt) は itemKey ごとの drag / commit 状態を保持し、呼び出し側が渡す SwipeAction の label、色、dismiss 動作、onCommit を使う。左右の通常・深い swipe と閾値を分け、DeliberateFarAction は幅に応じた閾値、抵抗、haptic で深い操作を意図的に選べるようにする。業務上の削除・保存は callback 側で実行する。

## 主要な構成要素・公開 API

| 宣言・種類 | 入力・動作・関係 | 根拠 |
| --- | --- | --- |
| `SwipeAction` data class / `SwipeBehavior` class | action の label、色、dismiss 方針、callback と、Default / DeliberateFarAction の閾値・抵抗・haptic 方針。 | [swipe 実装](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/SwipeActionListItem.kt)。 |
| `LazyItemScope.SwipeActionListItem` 拡張 Composable | itemKey、左右/深い左右 action、behavior、content を受け、drag と commit を所有する。 | 同上。 |
| `PullToRefreshContainer` Composable | caller の更新状態・enabled と onRefresh、BoxScope content を受ける。 | [refresh 部品](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/PullToRefreshContainer.kt)。 |
| `MarkdownText` Composable | Markdown 文字列を block と inline annotation へ変換し表示する。 | [Markdown 実装](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownText.kt)。 |
| `ChatMessageBubble` Composable | user/assistant、本文、表示 label を受ける。user は Text、assistant は MarkdownText で表示する。 | [bubble](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/ChatMessageBubble.kt)。 |

## 内部構成と処理フロー

swipe は internal `SwipeCommit` enum と `resolveSwipeCommit` で深い操作を通常操作より先に選び、`resolveFarThreshold` が幅比例の閾値、`applyFarTransitionResistance` が境界付近の抵抗を決める。drag 終了後に退出 animation、待機、`SwipeAction.onCommit` の順で進み、dismiss しない action は位置を戻す。callback の例外を業務用エラーに変換する機構はなく、caller が操作結果を扱う。

Markdown は internal `MarkdownBlock` sealed interface と `MarkdownListItem` を `parseMarkdownBlocks` が生成し、private の list/code/table Composable が描画する。`markdownInlineText` は装飾とリンクを AnnotatedString にし、`isSafeMarkdownLink` を通らない URL はクリック可能にしない。HTML の実行や WebView の生成は行わない。

[IntegratedScreen](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) は refresh/swipe の callback を feature の状態操作へ接続し、[AiChatScreen](../../../feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/AiChatScreen.kt) は保存済み/streaming 本文を bubble に渡す。部品が保持する状態はジェスチャーや描画のみである。
## 表現と状態の受け渡し

[PullToRefreshContainer](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/PullToRefreshContainer.kt) は enabled かつ更新中でない場合だけ onRefresh を呼ぶ。画面読み上げ用の「更新」custom accessibility action も同じ条件を使うため、ジェスチャーと accessibility で二重 refresh の抑制を揃える。isRefreshing は caller の状態で、部品側が取得処理を保持しない。

[MarkdownText](../../../core/designsystem/src/main/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownText.kt) は見出し、段落、リスト、code fence、table を block に分解して Compose 表示する。streaming 途中の未閉鎖 fence も code として保持し、安全なリンク scheme を判定して click を許す。ChatMessageBubble はこの表現をチャットで再利用する入口となる。永続 message、送信、生成 progress の所有は feature 側である。

## 部品変更の確認

[MarkdownTextTest](../../../core/designsystem/src/test/kotlin/dev/terashima/yomitorirss/core/designsystem/MarkdownTextTest.kt) は block 分解、table、未閉鎖 fence、http / https / mailto と危険 scheme の拒否を検証する。swipe の境界は SwipeDecisionTest と FarLeftSwipeDecisionTest を読む。部品を変える場合は feature の callback 契約と itemKey による状態 reset を保ち、画面ごとの業務判断を共通 UI に移さない。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)。
