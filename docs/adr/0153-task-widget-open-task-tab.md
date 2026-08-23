# ADR-0153: タスクウィジェット起動時はタスクタブを開く

- Status: Accepted
- Date: 2026-08-23
- Amends: [ADR-0024](0024-task-home-screen-widget.md)
- Refines: [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md), [ADR-0150](0150-app-shell-navigation-ui-ownership.md)

## Context

ADR-0024 では、タスクウィジェットのヘッダーまたはタスク行を押した場合はアプリを起動するだけとし、タスク専用の遷移は当時のスコープ外としていた。

現在の app shell では `MainTab.TASKS` がタスク画面の選択状態を表し、`AppViewModel` が Activity-scoped な selected tab を所有している。ウィジェットからアプリを開いたにもかかわらず別タブが表示されると、ホーム画面からタスクを確認する操作として余分なナビゲーションが必要になる。

一方で `feature:widget:ui` から app の `MainActivity` 型を直接参照すると、feature UI から app entry point への逆向き依存を作る。ADR-0142 / ADR-0150 で整理した ownership を維持したまま、起動意図だけを app shell へ伝える必要がある。

## Decision

### 1. Task widget は package launch Intent に専用 action を付与する

`TaskWidgetProvider` は従来どおり `PackageManager.getLaunchIntentForPackage` でアプリの launch Intent を取得し、app の Activity 型へ直接依存しない。

取得した Intent には `TaskWidgetProvider.ACTION_OPEN_TASKS` を設定し、既存の `NEW_TASK` / `CLEAR_TOP` / `SINGLE_TOP` flags を維持する。

ヘッダーの `PendingIntent` と、タスク行を開く broadcast から再起動する経路の双方で同じ launch Intent を利用する。

### 2. MainActivity は widget launch action を app shell navigation に変換する

`MainActivity` は `onCreate` と `onNewIntent` の双方で task widget launch action を消費する。

専用 action の場合は `AppViewModel.selectTab(MainTab.TASKS)` を呼び、タスクタブを選択する。消費後は Intent の action を `null` に戻し、configuration change 等で同じ起動要求を再適用しない。

action から `MainTab` への対応は小さな pure function として切り出し、通常の JVM unit test で検証する。

### 3. navigation ownership は変更しない

Task feature 自身は app shell の `MainTab` を知らない。widget feature も app shell state を直接変更しない。

Android の起動 Intent は widget UI が生成し、その Intent を app shell navigation state に変換する責務は app entry point が持つ。これにより ADR-0150 の app shell ownership と整合させる。

## Consequences

### Positive

- ホーム画面のタスクウィジェットから開いた場合、追加操作なしでタスクタブが表示される。
- アプリが未起動の場合と既に foreground / task stack に存在する場合の双方で同じ意味になる。
- `feature:widget:ui` から `:app` の Activity implementation への依存を追加しない。
- Task feature の navigation 非依存性を維持できる。

### Negative

- app entry point が Task widget の platform action contract を1つ認識する。
- 将来 app shell navigation の表現が `MainTab` 以外へ変わる場合は action の解決側を追随させる必要がある。

## Verification

- `TaskWidgetProviderTest` で widget launch Intent に専用 action と既存の Activity flags が設定されることを確認する。
- `TaskWidgetLaunchRoutingTest` で task widget action が `MainTab.TASKS` に解決され、無関係な action は解決されないことを確認する。
- app unit tests で既存 navigation state の回帰がないことを確認する。
- `verifyArchitecture` で feature -> app entry point の逆向き依存が増えていないことを確認する。
- release lint と関連 module の compile / unit test を実行する。
- PR 差分で credential、token、OAuth secret、実ユーザー URL・メールアドレス・個人データ等が追加されていないことを確認する。

## Documentation

- ADR-0024 の「タスク専用遷移はスコープ外」という過去判断を本 ADR で明示的に amend する。
- `docs/adr/README.md` の Android runtime / navigation 関連索引へ本 ADR を追加する。

## Public repository review

本変更で公開するのは widget launch action 名、app shell の navigation wiring、架空の test action、設計文書のみである。credential、token、OAuth secret、実ユーザー URL・メールアドレス・個人データ、database / backup / private artifact は追加しない。
