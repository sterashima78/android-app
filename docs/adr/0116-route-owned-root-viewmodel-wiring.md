# ADR-0116: Root feature ViewModel の wiring を Route composition が所有する

- Status: Accepted
- Date: 2026-08-19
- Amends: ADR-0001, ADR-0003, ADR-0103
- Amended by: ADR-0139
- Refined by: [ADR-0146](0146-active-tab-viewmodel-activation.md)

## Context

ADR-0001 は ViewModel を screen / navigation destination の state holder として扱い、ADR-0003 は `:app` を navigation・composition・dependency wiring に限定する方針を定めている。

一方、root UI では `MainActivity` が RSS、Reddit、Feed、Bookmark、Mail、Summary、Backup、AI Settings、Chat の ViewModel を `by viewModels` で生成していた。その結果、Android lifecycle entry point が feature 固有 factory、Repository 構成、source 判定まで知り、`YomitoriApp` に多数の ViewModel を引き渡す構造になっていた。

Library、Knowledge、Asset、Task、Workout、YouTube は既に `AppRouteDependencies` から factory を受け取り、Route composition が ViewModel を取得する方式へ移行しているため、root feature だけ異なる wiring 規則が残っていた。

また Gmail 認証は `MainActivity` が `MailViewModel` を直接操作しており、Activity Result と feature state の ownership が混在していた。

## Decision

### 1. MainActivity は feature ViewModel を所有しない

`MainActivity` が直接所有してよい ViewModel は app navigation state を表す `AppViewModel` とする。

RSS、Reddit、Feed、Bookmark、Mail、Summary、Backup、AI Settings、Chat など feature 固有 ViewModel は Activity property として生成しない。

`MainActivity` の責務は次に限定する。

- Android lifecycle entry point
- external Intent の受理
- app-level navigation の起動
- OS permission / service start など Activity または Context が必要な app integration
- crash diagnostics

### 2. ViewModel factory の組み立てを AppRouteDependencies に集約する

feature ViewModel の factory は `AppRouteDependencies` が `AppContainer` から組み立てる。

これにより Route / UI composition は concrete Repository を知らず、factory だけを受け取る。

source 分離など composition root 固有の policy も factory wiring と同じ場所に置く。

### 3. Route composition が `viewModel(factory = ...)` で ViewModel を取得する

`AppFeatureContent`、top bar route host、overlay host など feature presentation を構成する Composable が必要な ViewModel を `AppRouteDependencies` の factory から取得する。

`YomitoriApp` は feature ViewModel を引数として受け取らず、app navigation state、Route dependencies、app-level callback のみを扱う。

同じ `ViewModelStoreOwner` の下で同一 ViewModel class を取得する host は同じ instance を共有するため、Integrated view、top bar、feature route、overlay の既存の shared state は維持する。

### 4. 現時点では Activity ViewModelStore を保持する

本変更では Navigation Compose の back stack を新規導入しないため、`viewModel()` の owner は従来どおり Activity の `ViewModelStoreOwner` となる。

したがって lifecycle scope 自体を destination 単位へ変更するものではない。

目的は次の2点である。

- Activity が feature ViewModel wiring を所有しないこと
- ViewModel を利用する presentation composition が取得責務を持つこと

将来 NavHost / destination-scoped ViewModelStore を導入する場合は、Route 側の `viewModel(factory = ...)` をその destination owner に載せ替える。Activity property へ戻さない。

### 5. Activity Result は利用 feature の route host へ置く

Gmail account authorization のように feature UI 操作から開始される Activity Result は、その feature の route host が launcher と ViewModel 更新を所有する。

`MainActivity` は Mail の認証 outcome や `MailViewModel` を直接扱わない。

### 6. cross-feature overlay は composition host として扱う

Summary overlay、Bookmark edit overlay、global message effect は複数 route から利用されるため app composition に残してよい。

ただし feature ViewModel instance を `YomitoriApp` の引数に昇格させず、それぞれの host が factory から取得する。

## Consequences

### Positive

- `MainActivity` が feature 固有 Repository / factory / policy を知らなくなる。
- root feature と Library / Knowledge / Asset 等で ViewModel wiring 規則が揃う。
- `YomitoriApp` の引数が app shell に必要な情報へ縮小する。
- Gmail 認証の UI lifecycle と Mail feature state の ownership が近づく。
- 将来 destination-scoped ViewModelStore へ移行する際の変更点が Route 側に限定される。

### Negative

- 現時点では `viewModel()` の owner が Activity なので、ViewModel の実際の lifetime は従来と同じである。
- cross-feature host が同じ ViewModel class を取得することを前提にしているため、将来 owner を destination 単位に変更する際は shared overlay / Integrated view の state sharing を明示的に再設計する必要がある。
- factory が `AppRouteDependencies` に増えるため、composition root の wiring 定義は大きくなる。

## Follow-up

- Navigation destination ごとの lifecycle が必要になった時点で NavHost / destination owner の導入を別 ADR で検討する。
- `MainActivityArchitectureTest` で feature ViewModel import の再導入を検知する。将来 `verifyArchitecture` へ統合するかを検討する。
- cross-feature overlay が増える場合は ViewModel 共有ではなく domain event / explicit state contract が適切か再評価する。

## Public repository safety

本 ADR と実装には token、credential、OAuth secret、個人メールアドレス、内部 endpoint を含めない。
