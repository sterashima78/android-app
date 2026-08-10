# ADR-0003: Feature-first のマルチモジュール構成を採用する

- Status: Accepted
- Date: 2026-08-07
- Updated: 2026-08-08

## Context

ADR-0001 では UI・Domain・Data のレイヤ境界と依存方向を定義した。その後、RSS、Bookmark、Summary、Backup などで Repository / ViewModel の分離が進み、機能ごとの責務境界が明確になった。

一般的な `:core:data`、`:core:domain`、`:core:model` といった layer-first の構成をそのまま採用すると、Data / Domain 側で RSS、Bookmark、Summary、Chat、Task など異なる変更理由を持つコードが再び同じ巨大なモジュールへ集約されやすい。

このアプリでは変更の多くが RSS、Bookmark、Summary、Chat、Settings、Task などの機能・ドメイン単位で発生する。また、Article のように複数の画面機能から利用されるが、独立した所有権を持つアプリ固有の概念も存在する。

一方で、database、network、AI runtime のように複数領域から共有される横断的な技術 capability も存在する。これらとアプリケーション固有の領域をリポジトリルートで同列に並べ続けると、モジュール数が増えるほどトップレベルの構造を把握しにくくなる。

## Decision

マルチモジュール化では feature-first を基本とし、アプリケーション固有の機能・ドメイン概念を `:feature` namespace の配下に配置する。

基本となる Gradle project path は次の形式とする。

```text
:feature:<feature-name>:<layer>
```

リポジトリ上の物理配置もこれに対応させる。

```text
feature/
└── <feature-name>/
    ├── domain/
    ├── data/
    └── ui/
```

すべての feature が必ず3モジュールを必要とするという意味ではない。責務・依存・ビルド境界として分離する価値がある場合にのみ `domain` / `data` / `ui` を作る。

### 1. `feature` はアプリケーション固有の ownership namespace とする

ここでいう `feature` は「1画面の機能」だけを意味しない。

RSS、Bookmark、Settings のようなユーザー向け機能に加え、Article のような複数機能から共有されるアプリ固有の安定したドメイン概念も `:feature` 配下に置ける。

`feature` の目的は、アプリケーション固有の ownership を `app` や横断 capability の `core` から区別することである。

### 2. 第一の分割軸は変更理由と ownership とする

RSS の Data 実装は `:core:data` ではなく `:feature:rss:data` に置く。

Bookmark の Data 実装は `:feature:bookmark:data`、Summary の Data 実装は `:feature:summary:data` のように、特定の領域のために存在するコードはその領域が所有する。

### 3. Domain は各 feature が所有する

特定 feature の仕様、状態表現、操作、Repository contract、UseCase は原則としてその feature の `domain` に置く。

Domain module は単なる data class 置き場ではなく、その feature の意味・制約・操作を表す。

ADR-0002 の方針に従い、純粋な計算は関数、状態・依存・ライフサイクルを所有する処理は class、アーキテクチャ境界は interface として表現する。

### 4. `core` は共有 capability として作る

`core` 配下に巨大な `data`、`util`、`common` モジュールを作らない。

横断的に共有する必要がある技術機能は、責務が分かる名前の module にする。

```text
:core:database
:core:network
:core:designsystem
:core:ai-runtime
```

これらは feature 固有のユースケースや Repository を所有しない。

例えば `:core:database` は接続、transaction、汎用的な schema migration mechanism のような database capability を提供できるが、`ArticleRepository` や `BookmarkRepository`、Bookmark 固有 migration の意味まで所有しない。それらは利用する feature の `data` が所有する。

### 5. Data module は Domain contract を実装する

同一 feature 内では次を基本とする。

```text
:feature:rss:ui
       ↓
:feature:rss:domain
       ↑
:feature:rss:data
       ↓
:core:database / :core:network
```

`domain` が Repository interface を定義し、`data` がその実装を提供する。`:app` は composition root として必要な implementation を組み立てる。

### 6. feature 間依存を許容し、layer 境界で制約する

feature 間依存そのものは禁止しない。ある feature が別 feature の概念・UI・Data capability を利用することが責務として自然であれば、Gradle dependency として明示してよい。

依存可否は feature 境界ではなく layer の責務を優先して判断する。

```text
Domain -> 他 feature の Domain                  許可
Data   -> 他 feature の Domain / Data / core    許可
UI     -> 他 feature の Domain / UI             許可
app    -> 各 feature の Domain / Data / UI      許可
```

一方、次は feature 間であっても避ける。

- Domain -> UI / Data
- UI -> concrete Data implementation
- core -> feature
- Android / DB / HTTP の実装型を Domain API に露出すること

feature 間依存が増えた場合は、依存そのものを排除するのではなく、循環依存、責務の逆転、所有権の曖昧化が起きていないかを確認する。

### 7. module は package の代替として細分化しすぎない

以下の場合は別 Gradle module を作らず、同じ module 内の package / `internal` visibility で分離する。

- 数個の純粋関数しかない
- 独立した依存関係を持たない
- ビルド境界として分ける利点がない
- 他 module から利用されない
- 分割すると公開 API が不自然に増える

モジュール数を増やすこと自体は目的としない。

### 8. module の公開 API を小さく保つ

モジュール外から利用する必要のない型・関数は `internal` を基本とする。

特に Data source、mapper、DB entity、HTTP DTO は必要がない限り公開しない。feature 間で Data implementation を再利用する場合も、公開範囲は明示的に選ぶ。

### 9. `app` は composition root と navigation を担当する

`:app` に業務ロジックや Repository 実装を置かない。

主な責務は次とする。

- Application / Activity entry point
- navigation graph
- feature の組み立て
- Repository / UseCase implementation の dependency wiring
- Android manifest 等の application-level configuration

app に置く route / adapter は dependency wiring と navigation callback の接続に限定し、feature 固有 UI state は feature 側で所有する。

### 10. Gradle project path と Kotlin package の命名を対応させる

アプリケーション固有コードの Kotlin package は `feature` ownership を明示する。

基本 package は次とする。

```text
Gradle:  :feature:article:domain
Path:    feature/article/domain
Package: dev.terashima.yomitorirss.feature.article

Gradle:  :feature:article:data
Package: dev.terashima.yomitorirss.feature.article.data

Gradle:  :feature:article:ui
Package: dev.terashima.yomitorirss.feature.article
```

Domain と UI の公開 API は `dev.terashima.yomitorirss.feature.<feature-name>` を基本とし、実装上必要なら下位 package を追加する。Data implementation は `.data` を基本とする。

Android library の namespace は module ごとに一意にするため、次を基本とする。

```text
dev.terashima.yomitorirss.feature.<feature-name>.<layer>
```

`core` は従来どおり `dev.terashima.yomitorirss.core.<capability>` とする。

## Dependency rules

禁止または原則回避する依存は次とする。

- `core` -> `feature`
- `domain` -> `ui` / `data`
- `ui` -> concrete `data` implementation
- feature 固有コードを generic な `core` module へ置くこと
- Android / DB / HTTP 型を Domain API に露出すること
- Gradle module の循環依存

feature 間依存は上記に反しない限り許容する。

## Repository layout

リポジトリルートは主要な分類だけを見せる。

```text
/
├── app/
├── feature/
│   ├── article/
│   ├── rss/
│   ├── bookmark/
│   ├── summary/
│   ├── chat/
│   ├── task/
│   ├── settings/
│   ├── backup/
│   ├── web/
│   └── widget/
├── core/
├── docs/
└── gradle/
```

## Migration

- Gradle project path は `:feature:<feature-name>:<layer>` に統一する
- 物理ディレクトリは `feature/<feature-name>/<layer>` に統一する
- Kotlin package は `dev.terashima.yomitorirss.feature.<feature-name>` を基準に統一する
- Data implementation は `.data`、Android namespace は `.<layer>` を付けて module ごとに一意にする
- 古い `dev.terashima.yomitorirss.data`、`dev.terashima.yomitorirss.domain`、`dev.terashima.yomitorirss.<feature>` package は残さない
- 既存の feature 間依存は構造変更だけを理由に解消しない

## Consequences

### Positive

- リポジトリルートが `app` / `feature` / `core` という少数の主要分類になる
- Gradle project と Kotlin package の ownership を対応させやすい
- feature の UI / Domain / Data を同じ ownership として追跡できる
- `core:data` のような巨大な横断モジュールへの集約を避けられる
- feature 間の再利用を不自然な composition wrapper へ押し込めずに表現できる
- layer の依存違反を Gradle レベルで検出しやすい

### Negative

- Gradle project path と Kotlin package がやや深くなる
- 同じ feature 内でも UI / Domain / Data 間の API 設計が必要になる
- feature 間依存が増える場合は循環依存を監視する必要がある
- 小さすぎる module を作ると Gradle 設定と dependency wiring の管理コストが増える

## Relationship to ADR-0001, ADR-0002 and ADR-0004

ADR-0001 の UI・Domain・Data の責務と依存方向を Gradle module の境界として具体化し、ADR-0001 の single-module 方針を上書きする。

ADR-0002 の関数・class・interface の判断は各 module 内でも引き続き適用する。

ADR-0004 の concept-oriented ownership は維持する。Article のような共有概念も `feature/` 配下に置き、必要な feature から通常の feature 間依存として利用できる。
