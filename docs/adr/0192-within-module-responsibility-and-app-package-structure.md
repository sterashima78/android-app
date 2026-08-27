# ADR-0192: module 内の責務肥大化を file / package 分割で抑制する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0101](0101-feature-route-and-background-runtime-ownership.md), [ADR-0122](0122-current-architecture-documentation.md), [ADR-0166](0166-lan-web-and-route-composition-responsibility-split.md)

## Context

Gradle module の責務境界を適切に保っていても、module 内の単一 file / class が複数の独立した変更理由を持つと、変更影響の把握とレビューが難しくなる。

`:app` の `MainActivity` は Android lifecycle と app-shell 接続に加えて、次の app-only concern を直接実装していた。

- 生体認証 lock と `FLAG_SECURE`
- share / widget の incoming Intent 解釈と非同期処理
- startup crash diagnostics の表示と clipboard 操作
- LAN Web Server の notification permission と dialog presentation
- Custom Tab 起動に伴う app lock transition

これらは ADR 群が `:app` ownership として認めている application/platform concern であり、feature module へ移す根拠はない。一方、同じ module に属することは同じ file / root package に集約する理由にもならない。

また、責務を分けるためだけに Gradle module を増やすと dependency graph と build configuration の複雑さが増える。ADR-0003 の multi-module 方針と、current architecture principles の「小さな責務は package / `internal` を優先する」という規則を維持しつつ、module 内の実装肥大化も明示的に抑制する必要がある。

## Decision

### 1. module boundary と file boundary を別の判断として扱う

Gradle module は ownership、依存方向、build boundary を表す。

file / class / package は同一 module 内の変更理由と可読性を局所化するために利用する。

単一 file / class が複数の独立した変更理由を持つ場合、別 module の新設を検討する前に、同一 module 内で次を行う。

- cohesive な class / function を別 file に分ける
- app-only / feature-only helper は `internal` を基本とする
- 同じ変更理由を持つ実装を意味のある package にまとめる
- 単なる file size の数値だけではなく、変更理由、依存、lifecycle、テスト境界を分割判断に使う

行数の固定上限は設けない。短い file の量産も避け、独立して名前を付けられる責務があるかを基準とする。

### 2. `:app` root package は top-level entry point と composition facade を中心にする

`dev.terashima.yomitorirss` root package は、Application / Activity entry point、application-scope composition facade 等の top-level object を中心にする。

app ownership のまま独立した実装責務を持つ code は、次のような subpackage に配置する。

- `entry`: share / widget 等の external Intent routing
- `security`: app lock、認証 session、secure-window transition
- `diagnostics`: startup crash、memory diagnostics、shareable diagnostic UI
- `platform`: Custom Tab、OS permission、platform dialog host
- `ui`: app-shell navigation / presentation

この一覧は closed set ではない。責務の名前を表す package を優先し、`common` / `util` のような汎用置き場は作らない。

### 3. `MainActivity` は delegation する

`MainActivity` は次の責務だけを直接扱う。

- Android Activity lifecycle の受け口
- app-shell `AppViewModel` と `YomitoriApp` の接続
- app lock / crash diagnostics による top-level content selection
- external Intent handler への delegation
- article open / LAN Web Server host への platform callback 接続

具体的な lock state machine、Intent の種類別処理、diagnostic UI、permission launcher は専用 file / class / composable に分ける。

### 4. behavior / ownership は変更しない

今回の分割は module ownership を変更しない。

- app lock は引き続き `:app`
- startup / memory diagnostics は ADR-0149 / ADR-0161 に従い `:app`
- external Intent と OS permission は `:app`
- LAN Web Server mutable runtime は引き続き `:feature:web:data`
- feature business logic は `MainActivityDependencies` 等の narrow contract 経由で利用する

## Consequences

### Positive

- `MainActivity` の変更理由が Activity lifecycle と app-shell wiring に限定される。
- app-only implementation が root package に散在せず、配置理由を source tree から読み取れる。
- module を増やさずに code review と test ownership を局所化できる。
- 今後 feature / core module 内で実装が肥大化した場合にも、同じ file / package 分割原則を適用できる。

### Negative

- 同一 Gradle module 内の package 数と import は増える。
- Android component 自体は top-level entry point として root package に残せるため、helper の整理だけで component identity を変更しない選択ができる。
- file 分割自体は architecture boundary を強制しないため、責務の命名と cohesive 性は引き続きレビュー対象になる。

## Verification

- app unit test
- `verifyArchitecture`
- public repository verifier
- `MainActivity` / `LibraryShareActivity` の Android component identity を変更していないこと
- app lock preference / external transition regression test
- shared bookmark / task widget routing regression test
- crash sanitizer / process-exit diagnostics regression test
- Android 17 memory diagnostics regression test

## Documentation

本 ADR を、同一 module 内の実装肥大化を扱う current decision とする。Gradle module を追加・分割する判断は従来どおり module ownership / dependency boundary の ADR に従う。

## Public repository review

本変更は class / package の再配置と presentation / routing 分割のみであり、credential、account identifier、private endpoint、実ユーザーの URL / title / crash report を repository に追加しない。test data は従来どおり synthetic value を使用する。
