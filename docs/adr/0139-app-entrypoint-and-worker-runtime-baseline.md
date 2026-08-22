# ADR-0139: app entry point と Worker runtime の現行互換性基準を明確化する

- Status: Accepted
- Date: 2026-08-22
- Amends: ADR-0046, ADR-0101, ADR-0116
- Refines: ADR-0125

## Context

ADR-0101 では feature 固有の background runtime を owning feature の data module へ移し、WorkManager が永続化する旧 Worker FQCN のために `:app` へ compatibility shim を残した。ADR-0116 では `MainActivity` の責務を Android lifecycle、external Intent、platform integration 等へ限定し、feature ViewModel wiring を Route composition へ移した。

その後も次の移行負債が残っていた。

- `MainActivity` が LAN Web Server の concrete `LanWebServerService` と global mutable `LanServerStatus` を直接操作していた。
- `:app` に旧 Bookmark / Knowledge Worker FQCN を維持する compatibility shim が残り、architecture verification に個別例外が必要だった。
- `MainActivity` の feature ViewModel ownership は app unit test だけで検査され、root の `verifyArchitecture` と guardrail が分かれていた。
- Worker の source rule は `CoroutineWorker` 等を直接継承する記法だけを対象にしており、Kotlin import alias で回避できた。
- `MailRouteHost` のような app UI composition が concrete Mail data implementation を import し、Mail Worker が application scope の Repository graph と別に database / Repository を再構築できる経路が残っていた。

現在利用するアプリは最新バージョンであり、旧アプリから未実行の WorkManager request を直接引き継ぐことは互換性要件に含めない。DB / backup についても ADR-0138 で現行 schema を互換性基準としている。

## Decision

### 1. MainActivity は feature runtime を narrow contract 越しに操作する

`MainActivity` は notification permission request、dialog visibility、Android lifecycle といった Activity 固有の責務を引き続き所有する。

LAN Web Server の起動・停止・状態取得は `feature:web:domain` の `LanWebServerController` 契約だけを利用する。`MainActivity` は `feature:web:data` の Service や mutable runtime store を import しない。

`AppContainer` が `AndroidLanWebServerController` を application scope で構築し、`MainActivityDependencies` を通して Activity へ渡す。

### 2. LAN Web Server の mutable runtime state は feature data が所有する

server lifecycle に伴って変化する状態は `feature:web:data` の内部 state store が所有し、Android Service が更新する。domain module は immutable な `LanWebServerState` と controller contract のみを公開する。

`LanWebServerService` は Android が constructor を所有する framework entry point であるため、repository 取得には既存の明示的 `LanWebRepositoryProvider` contract を利用してよい。この provider lookup は `config/architecture/framework-provider-lookups.tsv` で監査を継続する。

### 3. 旧 app-package Worker FQCN compatibility shim を終了する

現在の最新版アプリを互換性基準とし、旧 app package に残していた次の WorkManager compatibility shim を削除する。

- Bookmark auto-enrichment backfill Worker の旧 FQCN
- Knowledge build Worker の旧 FQCN
- Knowledge charging-resume Worker の旧 FQCN
- 上記 Knowledge shim 専用 bridge

新規・現行の WorkManager request は owning feature module の Worker class を利用する。

旧アプリで enqueue 済みの request が残った状態から本版へ直接更新し、その request の worker class が旧 FQCN を参照しているケースはサポート対象外とする。これは現在利用中の最新版だけを更新基準とするという運用上の前提に基づく。

将来 Worker class を再移動する場合は、互換性期間と終了条件をその時点の ADR で明示する。期限のない compatibility shim を既定とはしない。

### 4. architecture verification に current rule を統合する

`verifyArchitecture` / `verifyArchitectureRuleTests` と Architecture job の init script で次を固定する。

- `MainActivity` は `AppViewModel` を除く feature ViewModel を import しない。
- `MainActivity` は concrete `feature.*.data.*` implementation を import しない。
- `:app` production source に feature 固有 Worker を置かない。旧 shim path に例外を設けない。
- `CoroutineWorker` / `Worker` / `ListenableWorker` の import alias を解決して同じ違反として検出する。
- `Screen` / `Route` だけでなく、`:app` の `ui` composition 配下にある `*Host.kt` 等も concrete feature data、database、WorkManager implementation を import / construct しない。

ファイル名だけを architecture boundary とみなさず、app UI composition という責務の場所に rule を適用する。

### 5. framework entry point は application scope の同一 dependency graph を再利用する

Android / WorkManager が constructor を所有する Worker / Service 等は、owner feature が公開する narrow Provider contract を `Application` から取得してよい。ただし Provider の目的は service locator による任意依存取得ではなく、framework entry point を既存の application scope dependency graph へ接続することに限定する。

Mail では `MailSyncWorker` が `MailRepositoryProvider` を通じて `AppContainer` の `MailRepository` を取得し、UI/Settings と同じ Repository graph を再利用する。Worker 内で `YomitoriDatabase` と `DefaultMailRepository` を独立再構築しない。

Provider lookup は `config/architecture/framework-provider-lookups.tsv` へ登録し、production lookup と manifest の集合を検査する。通常の Route / Screen / ViewModel は Provider lookup を利用せず、composition root から渡された contract を利用する。

## Consequences

### Positive

- Activity の platform integration と Web feature の runtime implementation が契約で分離される。
- LAN Web Server の mutable state ownership が domain から data/runtime へ移り、domain API は immutable contract に限定される。
- `:app` の feature Worker 例外がなくなり、background runtime ownership rule を一律に適用できる。
- MainActivity と Worker alias の drift を CI の同一 architecture verifier で検出できる。
- `Host.kt` 等へ concrete data wiring が移動して architecture rule を回避することを防げる。
- Worker と通常 runtime が同じ application scope Repository graph を利用し、database helper / scheduler / state の二重構築を減らせる。
- 旧 compatibility bridge / test exception を保守し続ける必要がなくなる。

### Negative

- 旧版アプリから直接更新し、旧 FQCN を参照する WorkManager request が端末に残っている場合、その request の実行互換性は保証しない。
- MainActivity や app UI composition の feature data 直接参照が必要な新しい platform integration を追加する場合は、先に narrow contract と composition wiring を用意する必要がある。
- framework entry point 用 Provider contract と lookup manifest を明示的に維持する必要がある。

## Verification

- `verifyArchitectureRuleTests` で MainActivity の feature ViewModel / concrete data import を違反として固定する。
- import alias を利用した app Worker fixture も違反として固定する。
- Architecture job の init script で `MailRouteHost.kt` 相当の app `ui` composition concrete data import fixture を固定する。
- framework Provider lookup は `config/architecture/framework-provider-lookups.tsv` と production source を照合する。
- LAN Web Server の state transition test は mutable state の owner である `feature:web:data` に置く。
- Web Server dialog の表示 state test は `feature:web:ui` に維持する。
- Mail repository behavior test と通常の `verifyArchitecture`、unit test、lint を CI で実行する。

## Public repository safety

本変更には credential、access token、OAuth secret、実ユーザー URL、メールアドレス、健康情報、実バックアップ等を追加しない。LAN state test のアドレスは private network 用の固定テスト値だけを利用する。

## References

- [ADR-0046](0046-automated-architecture-verification.md)
- [ADR-0101](0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0116](0116-route-owned-root-viewmodel-wiring.md)
- [ADR-0120](0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0125](0125-application-service-and-capability-segregation.md)
- [ADR-0138](0138-database-v27-compatibility-baseline.md)
