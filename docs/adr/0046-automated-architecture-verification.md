# ADR-0046: アーキテクチャ制約を CI で自動検証する

- Status: Accepted
- Date: 2026-08-14
- Updated: 2026-08-21

## Context

ADR-0003 では feature-first のマルチモジュール構成と layer 間の依存方向を定義している。

レビューだけでは、モジュール数と feature 間依存が増えるにつれて逆依存、循環依存、過去の構成に由来する source layout のずれを見落としやすくなる。

また ADR-0101 で、実際に再発した次の ownership drift を修正した。

- `YomitoriApp` が複数 feature の UI state と dialog を再び所有する
- `Screen` が concrete Repository、DB connection、WorkManager dependency を直接生成する
- feature 固有の Worker runtime が `:app` に置かれ、feature data が app implementation に依存する

その後、`FeatureUiAdapters.kt` が `YomitoriApplication.container` から Settings の Repository を service locate し、モデルベンチマークの state と orchestration を所有する drift も確認された。Adapter という名前の presentation bridge であっても、feature 固有 state や dependency lookup を app 側へ戻してはならない。

これらには完全な意味解析を行わなくても、再発時に現れやすい構造的なパターンがあるため CI で検出できる。

## Decision

ルート Gradle project の `verifyArchitecture` task で、Gradle project dependency、production source layout / import、および ownership の構造的な再発パターンを検証する。

### Gradle dependency rules

次を禁止する。

- `core` -> `feature`
- `domain` -> `ui` / `data`
- `ui` -> feature の concrete `data` implementation
- Gradle project dependency の循環

project dependency の対象 configuration は次とする。

- `api`
- `implementation`
- `compileOnly`
- `compileOnlyApi`
- `runtimeOnly`
- build type / product flavor による上記 configuration の派生形（例: `debugImplementation`）

名前に `test` を含む configuration は production architecture の依存方向とは別の責務を持つため対象外とする。

### Production source rules

各 subproject の `src/main/java` と `src/main/kotlin` にある Kotlin file を対象に、次を検証する。

- package declaration と物理 path を一致させる
- domain module から `android.*` を直接 import しない
- `YomitoriApp` は `appViewModel.state` 以外の feature ViewModel state を `collectAsState` / `collectAsStateWithLifecycle` しない
- `YomitoriApp` から feature-owned `*UiState` / `*Screen` / `*Dialog` を直接 import しない
- `YomitoriApp` に feature 用 Activity Result launcher を置かない
- `:app` の `ui` package にある `*Adapter.kt` / `*Adapters.kt` から `YomitoriApplication`、`AppContainer`、`.container` を参照して feature dependency を service locate しない
- `*Screen.kt` から feature data implementation、DB connection、WorkManager API を直接 import しない
- `*Screen.kt` で `Default*Repository`、`DatabaseConnection`、`YomitoriDatabase`、`WorkManager*Scheduler` / `WorkManager*Controller` を直接生成しない
- `:app` の `feature` package に feature 固有 Worker runtime を置かない
- `:feature:*:data` から `YomitoriApplication`、`AppContainer`、`MainActivity` を参照しない

`Route` は `:app` が composition root として dependency wiring を行う場所になり得るため、concrete dependency 生成の禁止対象は `Screen` に限定する。Route 内の wiring が business logic へ肥大化していないかは別途レビューする。

`Adapter` / `Adapters` は feature-owned presentation API を app shell へ接続する薄い bridge に限定する。app implementation の container へ到達して Repository を取得したり、feature 固有の非一時的 state / orchestration を所有したりする用途には使わない。feature state は owning feature の ViewModel 等へ置き、Route はその state と callback を presentation API へ接続する。

WorkManager が永続化した旧 Worker FQCN を維持する compatibility shim のように、構造上どうしても例外が必要な場合は、検査コード内の明示的な exception map に repository path と ADR に基づく理由を登録する。暗黙の除外や file-name pattern による広い除外は行わない。

### Regression fixtures

source ownership rule 自体の退行を防ぐため、`verifyArchitectureRuleTests` task に最小 source fixture を置く。

fixture では少なくとも次を固定する。

- feature state を collect する `YomitoriApp` は失敗する
- feature Screen/Dialog import を持つ `YomitoriApp` は失敗する
- Activity Result launcher を持つ `YomitoriApp` は失敗する
- `appViewModel.state` の collect は許可される
- app UI adapter から `YomitoriApplication.container` を service locate すると失敗する
- app UI adapter が feature presentation API を props/callback だけで bridge することは許可される
- concrete data import / construction を持つ `Screen` は失敗する
- composition adapter である `Route` の wiring はこの rule では許可される
- app feature package の Worker は失敗する
- ADR-0101 の Knowledge Worker compatibility shim は許可される
- feature data から app implementation を参照すると失敗する

`verifyArchitecture` は `verifyArchitectureRuleTests` に依存させ、CI で source rule とその fixture を常に同時に実行する。

### CI

GitHub Actions では次のタイミングで `./gradlew verifyArchitecture` を実行する。

- `main` 向け pull request の quality checks。architecture / test / lint は独立 runner で並列実行し、完了後に `quality` job で結果を集約する
- `main` push 時の release build job

これにより pull request だけでなく main への直接 push に対しても同じ制約を適用する。pull request の各検証は相互依存しないため並列化し、CI のリードタイムを最長の検証時間に近づける。

## Scope

これらは Kotlin source の構造的パターンを検出する guardrail であり、完全な意味解析ではない。

特に次は引き続きレビュー対象とする。

- feature 固有コードが generic `core` module に置かれていないこと
- DB / HTTP 型を Domain API に間接的に露出していないこと
- module の公開 API が必要以上に広くないこと
- package 名そのものが機能の ownership と意味的に一致していること
- Route / composition adapter に business orchestration が蓄積していないこと
- naming や wrapper を使って禁止 implementation の直接生成を隠していないこと

機械判定可能な再発パターンが新たに見つかった場合は、レビュー指摘だけで終わらせず fixture と `verifyArchitecture` rule を追加する。

## Consequences

### Positive

- ADR-0003 の主要な依存方向を pull request ごとに自動検証できる
- ADR-0101 で修正した3種類の ownership drift を再発時に早期検出できる
- app UI adapter の名前で service locator と feature state ownership を隠す再発を検出できる
- module 数が増えても禁止依存のレビュー漏れを防げる
- 過去の directory 構成に source file だけが残る drift を検出できる
- Domain への Android framework 依存を追加時点で検出できる
- architecture checker 自体の rule regression を fixture で検出できる
- 例外が一か所に明示され、理由を ADR と関連付けられる
- main への直接 push でも architecture drift を検出できる
- pull request の architecture / test / lint を並列実行し、独立した検証の待ち時間を直列加算しない

### Negative

- Gradle configuration 後の verification で production Kotlin source を走査する処理が増える
- regex / source text ベースのため意味上同等な全パターンを捕捉できるわけではない
- 正当な新しい composition pattern を導入する場合は rule または ADR の更新が必要になる
- compatibility exception は不要になった時点で明示的に削除する必要がある
- pull request では複数 runner が同時に Gradle / Android SDK setup を行うため、runner 使用量は増える

## Relationship to ADR-0003 / ADR-0101

ADR-0003 と ADR-0101 の設計判断は変更しない。本 ADR は、そのうち機械的に判定できる dependency direction、source layout、UI ownership、concrete dependency construction、background runtime ownership の強制方法を定める。
