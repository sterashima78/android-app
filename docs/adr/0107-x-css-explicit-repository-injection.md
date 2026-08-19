# ADR-0107: X CSS repository を route から明示注入する

- Status: Accepted
- Date: 2026-08-19
- Amends: ADR-0102

## Context

ADR-0102 では X feature を UI / Domain / Data に分離した際、段階的移行として `YomitoriApplication` が `XViewerCssRepositoryProvider` を実装し、X UI が `Context.applicationContext` から repository を取得する方式を許容した。

この方式は永続化実装を UI から分離できる一方、画面が宣言されていない application-level dependency を持つ service locator になっていた。また `XViewerCssPreferences` と `readDefaultXViewerCss()` の互換 adapter が残り、X UI の実際の依存関係を呼び出し側から把握しにくかった。

アプリの route dependency wiring が `AppRouteDependencies` に集約されたため、暫定 provider を維持する必要がなくなった。

## Decision

`XViewerCssRepositoryProvider` と UI 側の provider lookup / compatibility adapter を廃止する。

X CSS repository は application composition root で `SharedPreferencesXViewerCssRepository` として構築し、`AppRouteDependencies.xViewerCssRepository` から以下へ明示的に渡す。

- `XViewerScreen(repository = ...)`
- app-level `SettingsRoute`
- `XViewerCssSettingsSheet(repository = ...)`

feature settings の `SettingsScreen` は X CSS repository を直接知らず、`onOpenXCss` event のみを app-level route へ通知する。X feature の UI は `Context` から repository を解決せず、引数として受け取った `XViewerCssRepository` のみを利用する。

`XViewerCssRepository` contract と `SharedPreferencesXViewerCssRepository` の責務、および既存の保存 key / asset 形式は ADR-0102 のまま変更しない。

## Consequences

### Positive

- X UI の依存関係が Composable の引数に現れる
- `YomitoriApplication` が feature-specific provider interface を実装する必要がなくなる
- `Context.applicationContext` cast による service locator を除去できる
- `XViewerCssPreferences` 互換 adapter を削除できる
- X screen と CSS settings sheet に fake repository を直接渡せるため、UI テストの差し替えが容易になる

### Negative

- app-level route が X repository を wiring する責務を持つ
- `SettingsScreen` に X CSS dialog を開く callback が追加される

## Superseded part of ADR-0102

ADR-0102 のうち、`XViewerCssRepositoryProvider` を Domain が所有し、`YomitoriApplication` が実装して UI が provider lookup するという決定、および provider lookup を将来廃止するという Follow-up は本 ADR で置き換える。
