# ADR-0247: 動画を統合ビューの対象外とする

- Status: Accepted
- Date: 2026-09-09
- Amends: [ADR-0025](0025-integrated-triage-home.md), [ADR-0118](0118-integrated-history-tab.md)
- Refines: [ADR-0242](0242-video-subscription-providers.md)

## Context

統合ビューは複数 source の未読、あとで読む、履歴を横断表示する presentation として始まり、購読型動画も対象に含めていた。

その後、購読型動画は ADR-0242 により Video Context の provider lifecycle へ統合され、購読管理、未読、あとで見る、保存、再生、履歴をトップレベルの動画機能で一貫して扱えるようになった。

この状態で統合ビューにも購読型動画を投影すると、同じ動画 lifecycle に対して統合ビュー専用の state projection、操作 dispatch、refresh wiring を維持する必要があり、動画機能だけで完結する利用モデルと重複する。

## Decision

### 1. 統合ビューから購読型動画を除外する

統合ビューの「未読 / あとで読む / 履歴」には購読型動画を表示しない。

source filter も RSS、Reddit、メールだけを対象とし、動画用 filter は提供しない。

### 2. 動画の状態変更は Video UI で完結させる

統合ビューは Video Domain の未読、あとで見る、履歴を読み取らず、動画の既読、保存、あとで見る等の command も発行しない。

`feature:integrated:ui` から Video Domain への依存を削除し、動画用の統合 ViewModel、projection target、dispatcher action を持たない。

### 3. 統合ビューの手動更新から動画更新を外す

統合ビューの pull-to-refresh は、統合ビューに表示する RSS、Reddit、メールだけを更新する。

動画画面の手動更新は Video UI が所有する既存 provider refresh を利用する。

### 4. application-scope の動画定期更新は維持する

application-scope の周期更新では、動画 provider の background refresh 自体は維持する。これは統合ビュー表示ではなく Video Context の新着状態を更新するためである。

ただし統合ビューへ遷移する新着通知の件数には動画を含めない。通知件数と遷移先で実際に確認できる項目を一致させる。

## Consequences

### Positive

- 購読型動画の確認・整理・再生が動画機能内で完結する。
- `feature:integrated:ui` から Video Domain への依存を削除できる。
- 動画操作の重複した UI contract と dispatch code を削除できる。
- 統合ビューの通知件数と表示対象が一致する。

### Negative

- 統合ビューだけでは全 source の未読を横断確認できなくなる。
- 購読型動画の新着確認は動画画面へ移動して行う必要がある。

## Compatibility

- durable state、database schema、backup format は変更しない。
- Video provider の subscription、unread、watch-later、saved、playback state は変更しない。
- 既存の周期更新設定は維持する。

## Verification

- Integrated projection test で RSS、Reddit、メールだけが統合されることを確認する。
- Integrated swipe / dispatcher test から動画固有 contract を除去する。
- architecture verification で `feature:integrated:ui` から Video Domain への依存が残っていないことを確認する。
- background refresh の新着判定が統合ビューに表示する source だけを通知対象にすることを確認する。
