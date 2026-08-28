# ADR-0208: Compose Activity Result launcher の ownership を source guard で固定する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md), [ADR-0196](0196-app-boundary-ownership-cleanup.md), [ADR-0205](0205-app-presentation-module-boundary.md)

## Context

ADR-0205 は Activity Result の ownership を lifecycle の種類で分けた。

- Composable Route / Host と一体の launcher は `:app:presentation`
- Activity / component lifecycle や executable-only state と一体の integration は `:app`
- feature 固有 UI 自身で完結する launcher は owning `:feature:<name>:ui`

`MainActivityDependencies` の narrow facade 化、app presentation の責務分割、feature navigation metadata の ownership 整理まで完了したため、残っていた Activity Result の物理配置を再監査した。

監査時点で executable `:app` の production source に `rememberLauncherForActivityResult` は存在しない。app-shell coordination を必要とする Compose launcher は `:app:presentation` の次の狭い adapter に限定されている。

- `AppTopBarRoute.kt`: RSS OPML document picker
- `CalendarRoute.kt`: Calendar permission
- `LanWebServerDialogHost.kt`: notification permission
- `LibraryRoute.kt`: Google Books authorization resolution
- `MailRouteHost.kt`: Gmail authorization resolution
- `SettingsRoute.kt`: backup export/import/folder picker

Bookmark / Asset 等、feature UI 内だけで完結する Activity Result launcher は owning feature UI にあり、app presentation へ引き上げない。

## Decision

### 1. executable `:app` では Compose launcher を所有しない

`app/src/main/java` の production Kotlin source は `rememberLauncherForActivityResult` を使用しない。

この規則は Compose lifecycle の launcher だけを対象とする。将来、Activity / component lifecycle と一体の integration に `registerForActivityResult` が必要になった場合は ADR-0205 の executable ownership に従い `:app` で所有できる。

### 2. `:app:presentation` の launcher は監査済みの狭い adapter に限定する

`rememberLauncherForActivityResult` を app presentation の global root (`YomitoriApp`, `AppNavHost` 等) に追加せず、対象 capability と同じ lifecycle を持つ Route / Host / route-aware chrome adapter に置く。

現在の監査済み host inventory は Context に列挙した6ファイルとする。新しい app-owned Compose launcher を追加する場合は、その launcher が feature-owned UI ではなく app-shell coordination に属する理由をレビューし、source architecture guard の inventory を意図的に更新する。

### 3. feature-local launcher は owning feature UI に残す

feature 内だけで lifecycle と結果処理が閉じる document picker / permission / picker UI は owning `:feature:<name>:ui` が所有できる。app-wide callback、cross-feature coordination、executable platform policy が必要という理由だけでない限り `:app:presentation` へ移さない。

### 4. runtime behavior は変更しない

本変更は ownership audit と regression guard の追加だけであり、permission contract、document MIME type、authorization flow、route、ViewModel lifetime、NavController lifetime、dependency direction を変更しない。

## Consequences

### Positive

- ADR-0205 の lifecycle-based Activity Result ownership を自動検査できる。
- executable shell に Compose feature integration が戻る drift を検出できる。
- `YomitoriApp` のような global host に feature-specific launcher が集約されるのを防げる。
- 新しい launcher 追加時に app-shell coordination と feature-local ownership を明示的に判断できる。

### Negative

- app presentation に新しい正当な launcher host を追加する場合、architecture test の inventory 更新が必要になる。
- source marker ベースの guard なので、Activity Result API 全般の semantic analysis ではない。意図的に Compose `rememberLauncherForActivityResult` の ownership に限定する。

## Verification

`AppActivityResultOwnershipSourceArchitectureTest` で次を検証する。

- executable `:app` production source に `rememberLauncherForActivityResult` がないこと
- `:app:presentation` で launcher を持つファイル集合が監査済み inventory と一致すること
- 各監査済み host が `ActivityResultContracts` を明示的に使用すること

通常の `test`、`verifyArchitecture`、lint、public repository verifier も実行する。

## Documentation

ADR-0205 と `docs/architecture/principles.md` の lifecycle-based ownership は変更しない。本 ADR は残タスクとして実施した物理配置監査と regression guard を具体化する refinement である。

## Public repository review

本変更は synthetic source architecture test と architecture documentation のみを追加する。credential、OAuth token、account identifier、private endpoint、実ユーザー URL / title / mail / health data、diagnostic artifact、backup/database artifact を追加しない。
