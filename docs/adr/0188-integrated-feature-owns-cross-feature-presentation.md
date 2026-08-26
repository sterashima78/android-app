# ADR-0188: Integrated feature が横断 presentation の ownership を持つ

- Status: Accepted
- Date: 2026-08-27
- Amends: [ADR-0062](0062-extract-integrated-ui-from-app.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md)
- Refines: [ADR-0003](0003-multi-module-architecture.md)

## Context

ADR-0062 では `:feature:integrated:ui` に再利用可能な表示だけを置き、RSS、Reddit、YouTube、Mail の state/action mapping と `IntegratedRoute` は `:app` の composition adapter が所有すると決めた。ADR-0142 も Integrated Route を app composition adapter の一つとして整理した。

その後の architecture cleanup で projection、target dispatch、item action が独立した型へ分離され、Integrated の変更理由がより明確になった。現在 app に残る Integrated 実装は、単なる dependency wiring ではなく、複数 source feature の状態を一つの画面へ射影し、Integrated tab の意味に応じて操作を各 source feature へ dispatch する presentation policy を所有している。

ADR-0003 は feature 間依存自体を禁止しておらず、UI が責務上必要な別 feature の Domain / UI に依存することを許可している。複数 feature を利用することだけを理由に、その feature 固有の変更理由を `:app` へ移す必要はない。

## Decision

Integrated を「複数 source feature を一つの triage surface として統合する」独立した feature responsibility として扱う。

`:feature:integrated:ui` は次を所有する。

- `IntegratedRoute`
- RSS / Reddit / YouTube / Mail の UI state から `IntegratedItem` への projection
- source item と Integrated item の対応付け
- Integrated item action の source feature への dispatch
- Integrated tab と Mail mailbox の対応
- Integrated 固有の loading / refreshing / snackbar presentation
- Integrated 固有の補助 action 定義

この責務を実装するため、`:feature:integrated:ui` から必要な sibling feature の Domain / UI module への依存を明示する。feature 間依存は ADR-0003 の layer rule に従い、UI から concrete Data implementation へは依存しない。

`:app` は Integrated の feature semantics を持たず、次の application-level 接続だけを担当する。

- source feature ViewModel / Factory の dependency wiring
- Article 画面や Mail tab など app-shell navigation callback
- Android `Intent` を使う外部 URL 起動 callback
- Summary feature など別 feature の capability callback の接続

Mail item を開く際の `mailViewModel.openThread` は Integrated feature の item action として実行し、`:app` には Mail tab へ遷移する callback だけを渡す。

`IntegratedProjection` は引き続き Compose / Android framework に依存しない純粋な mapper とし、feature module 内の単体テストで projection semantics を固定する。

## Consequences

### Positive

- Integrated の変更理由が `:feature:integrated:ui` に集約される。
- `:app` の「composition root」という責務が dependency wiring / navigation / platform integration に限定される。
- cross-feature であることと app ownership であることを同一視しなくなる。
- projection / dispatch / item action のテストが owning module と同じ場所に置かれる。
- source feature の追加や Integrated action の変更時に、Integrated feature の変更として追跡できる。

### Negative

- `:feature:integrated:ui` から sibling feature UI / Domain への明示的な Gradle dependency が増える。
- source feature の public UI state / ViewModel contract 変更に Integrated feature が追随する必要がある。
- Integrated が source feature の内部実装まで知り始めると依存が肥大化するため、Data implementation 依存や feature 間循環依存を architecture verification で防ぐ必要がある。

## Verification

- `IntegratedRouteAdapterTest` と `IntegratedTargetDispatcherTest` を `:feature:integrated:ui` の test source へ移し、既存 semantics を維持する。
- `AppCompositionSourceArchitectureTest` で Integrated Route / projection / dispatcher / item actions が `:app` に戻らないことを検証する。
- 同 test で `IntegratedProjection` が Compose / Android framework に依存しないことを引き続き検証する。
- `verifyArchitecture` と全 unit tests、release lint を実行する。

## Documentation

- `docs/architecture/module-map.md` の app composition 説明を、named feature responsibility と application-only composition の区別に合わせて更新する。
- ADR-0062 / ADR-0142 の Integrated app adapter 判断は本 ADR により amend される。

## Public repository review

本変更は production source、架空データのみを使う unit test、architecture document を変更する。credential、token、OAuth secret、実ユーザーの URL・メールアドレス・健康情報、database / backup / private artifact を追加しない。
