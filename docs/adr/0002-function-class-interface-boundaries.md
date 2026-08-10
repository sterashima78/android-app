# ADR-0002: 関数・クラス・インターフェースの利用と責任境界を定義する

- Status: Accepted
- Date: 2026-08-07

## Context

ADR-0001 では UI・Domain・Data のレイヤ境界と依存方向を定義した。

一方、各レイヤの内部で処理を関数、クラス、インターフェースのどれとして表現するかについては判断基準が明文化されていない。

Kotlin / Android では、単純な変換処理まで stateless class として表現したり、テスタビリティを理由にすべての class に interface を用意したりすると、実装量と indirection が増える。一方で、外部 I/O や状態を持つ依存を concrete class のまま上位レイヤへ露出すると、アーキテクチャ境界が崩れ、テスト時の差し替えも難しくなる。

そのため、次の観点を分けて判断する必要がある。

- 純粋な計算か、副作用を持つ処理か
- 状態、ライフサイクル、リソースを所有するか
- アーキテクチャ境界をまたぐ依存か
- 複数実装やテスト用実装への差し替えが必要か
- その abstraction 自体がドメイン上の契約として意味を持つか

## Decision

関数、クラス、インターフェースは次の基準で使い分ける。

### 1. 純粋な計算は関数を第一候補とする

次の条件を満たす処理は、原則として class を作らず関数として表現する。

- 結果が引数だけで決まる
- 外部状態を読み書きしない
- 内部に保持すべき状態を持たない
- ライフサイクルやリソースを所有しない
- 呼び出しごとの独立した計算として意味が通る

例:

```kotlin
fun formatArticleTitle(article: Article): String = ...

fun calculateUnreadCount(articles: List<Article>): Int = ...
```

次のような、単一の純粋処理だけを包む stateless class は原則として作らない。

```kotlin
class ArticleFormatter {
    fun format(article: Article): String = ...
}
```

名前空間のためだけに class を導入しない。可視性や配置は package、file、`private` / `internal` などで制御する。

複数の純粋関数が同じ概念に属していても、共有状態やライフサイクルを必要としない限り、まず関数の集合として表現する。

### 2. 状態・依存・ライフサイクルを所有する処理はクラスとする

次のいずれかに該当する場合は class を使う。

- 状態を保持する
- 複数回の呼び出しにまたがる振る舞いを持つ
- Repository や platform API などの依存を保持する
- coroutine scope、接続、キャッシュ、モデル、ファイル等のライフサイクルを管理する
- 複数の操作をひとまとまりの責任として提供する
- オブジェクトとして identity を持つことに意味がある

例:

```kotlin
class RssViewModel(
    private val articleRepository: ArticleRepository,
    private val refreshFeeds: RefreshFeedsUseCase,
) : ViewModel() {
    ...
}
```

```kotlin
class DefaultArticleRepository(
    private val database: AppDatabase,
) : ArticleRepository {
    ...
}
```

class は「関連する関数を置く箱」ではなく、状態・依存・ライフサイクル・責任の所有者として導入する。

### 3. interface はアーキテクチャ境界または置換可能性が必要な箇所に導入する

すべての class に対応する interface を作ることはしない。

interface は、主に次のいずれかに該当する場合に導入する。

- 上位レイヤと下位レイヤのアーキテクチャ境界を表す
- DB、HTTP、ファイル、Android framework、AI model 等の副作用を上位レイヤから隠蔽する
- 本番実装と fake / test implementation を差し替える必要がある
- 複数実装が現実に存在する、またはその可能性が設計上重要である
- 呼び出し側が依存すべき capability / contract として独立した意味を持つ

ADR-0001 で定義した Repository は、この基準に該当するため interface を基本とする。

```kotlin
interface ArticleRepository {
    suspend fun markRead(articleId: Long)
}

class SqliteArticleRepository(...) : ArticleRepository {
    ...
}
```

ViewModel や UseCase が外部副作用を持つ collaborator に依存する場合、原則としてその collaborator の abstraction に依存する。

一方、同一レイヤ内部の単純な実装詳細であり、差し替えも契約の独立性も不要な class は concrete class のままでよい。

### 4. テスタビリティだけを理由に不要な interface を増やさない

テスタビリティは重要だが、「テストで mock したい可能性がある」ことだけを理由に全 class を interface 化しない。

純粋な計算はそのまま関数としてテストする。

```kotlin
@Test
fun unreadCountIsCalculated() {
    val result = calculateUnreadCount(articles)
    ...
}
```

副作用を持つ依存やアーキテクチャ境界は abstraction を介して差し替える。

```kotlin
class FakeArticleRepository : ArticleRepository {
    ...
}
```

つまり、テスタビリティのための abstraction は「副作用または境界を隔離する」ために使い、純粋処理まで interface 経由にはしない。

### 5. 依存性逆転はアーキテクチャ境界で適用する

上位レイヤは、下位レイヤの concrete implementation ではなく、自身が必要とする契約に依存する。

```text
ViewModel / UseCase
        ↓
Repository interface
        ↑
Repository implementation
        ↓
DB / HTTP / Platform
```

interface は原則として、利用側から見た契約を表現する場所に置く。実装クラスの API をそのまま複製しただけの interface を後付けしない。

同一レイヤ内部の純粋な計算や局所的な helper まで依存性逆転の対象にはしない。

### 6. 責任境界は「何を所有するか」で判断する

関数、クラス、interface の責任は次のように整理する。

| 構造 | 主な責任 |
| --- | --- |
| 関数 | 1つの計算・変換・判定を表現する |
| class | 状態、依存、ライフサイクル、関連する振る舞いを所有する |
| interface | レイヤ境界または置換可能な capability / contract を定義する |

class の責任が大きくなり、変更理由が複数に分かれる場合は class を分割する。

interface の責任が大きくなり、利用側ごとに不要なメソッドへ依存する場合は capability ごとに分割する。

関数が複数の外部依存を直接操作し始めた場合は、単なる関数のまま拡張せず、責任を持つ class / UseCase / Repository への移行を検討する。

## Decision guide

実装時は次の順で判断する。

```text
処理は引数だけで決まり、副作用も保持状態もないか？
  ├─ Yes -> 関数
  └─ No
       ↓
状態・依存・ライフサイクル・リソースを所有するか？
  ├─ Yes -> class
  └─ No -> まず関数として表現できないか再検討

その依存はアーキテクチャ境界をまたぐか、置換可能性が必要か？
  ├─ Yes -> interface / abstraction を導入
  └─ No -> concrete class のままでよい
```

「class だから interface が必要」「再利用するから class が必要」とは判断しない。

## Examples

### 純粋な表示用変換

```kotlin
fun formatPublishedAt(publishedAt: Instant, zoneId: ZoneId): String = ...
```

状態を持たないため class / interface は不要とする。

### ViewModel からのデータ更新

```kotlin
class RssViewModel(
    private val articleRepository: ArticleRepository,
) : ViewModel() {
    fun markRead(articleId: Long) {
        ...
    }
}
```

ViewModel は状態を持つため class とし、外部副作用を伴う Repository には abstraction 経由で依存する。

### 同一レイヤ内部の deterministic helper

```kotlin
class FeedRefreshPlanner {
    fun plan(feeds: List<Feed>): RefreshPlan = ...
}
```

この処理が純粋で状態を持たないなら class にせず、次のような関数を優先する。

```kotlin
fun planFeedRefresh(feeds: List<Feed>): RefreshPlan = ...
```

一方、前回実行時刻、rate limit、取得中状態等を保持する責任を持つようになった場合は class とする。

## Consequences

### Positive

- 単純なロジックに不要な class / interface が増えることを防げる
- 純粋関数と副作用の境界が明確になり、テストが単純になる
- Repository 等の重要なアーキテクチャ境界では依存性逆転を維持できる
- concrete class を許容する範囲が明確になり、過剰な abstraction を避けられる
- class の導入理由を状態・依存・ライフサイクル・責任として説明できる

### Negative

- 関数から class へ、または concrete class から interface へ後から変更する場合がある
- 「将来必要になるかもしれない」abstraction を先回りして作らないため、将来の変更時にリファクタリングが発生する

これらは、利用されない abstraction を先に増やし続けるコストより小さいと判断する。

## Relationship to ADR-0001

ADR-0001 のレイヤ境界に関する判断を優先する。

特に Repository、外部サービス、platform dependency など、レイヤ境界をまたぐ副作用のある依存は本 ADR の「concrete class を許容する」規則よりも、ADR-0001 の依存方向と abstraction の方針を優先する。

本 ADR は主として、各レイヤ内で関数・class・interface のどれを選択するか、および abstraction をどこまで導入するかを補足するものである。
