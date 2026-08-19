# ADR-0106: Domain context・Aggregate・永続化 ownership を DDD として定義する

- Status: Accepted
- Date: 2026-08-19
- Amends: ADR-0003, ADR-0004, ADR-0047

## Context

ADR-0003 は feature-first の module 構成、ADR-0004 は Article のような共有概念を concept-oriented module として所有する方針、ADR-0047 は database schema と migration を各 feature の data module が所有する方針を定めている。

一方、実装が拡大するにつれて、Gradle 上の feature 境界と Domain-Driven Design 上の Bounded Context / Aggregate 境界が同一であるかのように扱われる箇所が生じた。

特に次の状態がある。

- `articles` は `:feature:article:data` が schema ownership を持つが、Bookmark が `saved_at` を直接更新し、共有 URL の保存時には `articles` 自体を作成する。
- Web / Widget が Article、RSS、Bookmark の table を直接読み取る。
- Summary が Bookmark 所有の tag table を直接読み書きする。
- Article の effective content type は RSS の Feed / FeedFolder の設定を参照して決定される。
- Article の cleanup policy は Summary の永続状態を参照する。

これは単に SQL の配置を移動すれば解決する問題ではない。複数の概念を扱う処理について、Aggregate、Domain Service、Application Service、Read Model / Projection のどれで表現するかを区別する必要がある。

## Decision

### 1. Gradle feature と Bounded Context を同一視しない

`feature/<name>` は ownership とビルド境界を表す。DDD の Bounded Context や Aggregate と 1 対 1 で対応する必要はない。

同じ Bounded Context に複数 feature module が存在してもよく、1つの feature module が移行途中で複数の概念を含むことも許容する。

module の再編成は Domain model が安定した後に行い、先に名前だけを合わせるための大規模 rename は行わない。

### 2. 現在の Content 周辺 Context Map を次のように捉える

```text
Source contexts
  RSS / Reddit / YouTube
          |
          | content identity / metadata
          v
Content context
  ContentItem (現在の Article が移行上の実装)
  - identity
  - url / title / source metadata
  - published / fetched state
  - reading state
  - content classification override
          |
          | ContentItemId
          +----------------------+----------------------+
          v                      v                      v
Curation context            Summary context       Knowledge context
  Bookmark                  generated summary     generated/edited wiki
  Tag / Folder
  Read Later

Presentation / delivery adapters
  Web / Widget / Integrated UI
  -> Domain API / named Query API の利用者であり durable Domain table を所有しない
```

この Context Map は module 再編成を直ちに要求するものではない。現在の `article` module は Content context の移行上の実装として扱う。

### 3. ContentItem と Bookmark を別 Aggregate として扱う

現時点では Article/ContentItem と Bookmark を同一 Aggregate には統合しない。

ContentItem はコンテンツそのものと閲覧状態を所有する。

Bookmark は ContentItemId を参照し、保存・整理に関する状態を所有する。

```text
ContentItem Aggregate
  id
  identity
  url / title / source metadata
  publishedAt / fetchedAt
  readAt
  contentTypeOverride

Bookmark Aggregate
  contentItemId
  savedAt
  folder membership
  tag membership
  read-later membership
```

現在 `articles.saved_at` に格納されている `savedAt` はこのモデルと一致しないため、将来 migration で Bookmark ownership の table へ移す。

Tag / Folder の aggregate 粒度は現行の不変条件をさらに確認した上で決める。少なくとも Article/ContentItem が Tag / Folder 自体を所有するものとはしない。

### 4. Source は ContentItem の一部ではなく上流 Context とする

RSS Feed / FeedFolder、Reddit、YouTube は ContentItem の生成・識別に必要な情報を供給するが、現時点では ContentItem Aggregate 内へ統合しない。

Source 固有の購読、同期、取得状態、認証等はそれぞれの Context が所有する。

RSS / Reddit / YouTube が十分に同じ ubiquitous language と lifecycle を持つことが確認できるまでは、抽象的な `source` module へまとめない。

### 5. feature 横断の処理を用途に応じて分類する

複数 Context / Aggregate を扱うという理由だけで新しい Aggregate を作らない。

#### Domain Service

複数 Aggregate の情報から Domain rule を決定するが、自身の永続状態を必要としない場合に使う。

例:

- ContentItem override、Feed override、FeedFolder override から effective content type を解決する Content Classification

#### Application Service

複数 Aggregate の command を1つのユーザー操作として orchestration する場合に使う。

例:

- 「あとで読む」: ContentItem を既読にし、Bookmark を保存し、Read Later に所属させる
- 共有 URL の保存: ContentItem を取得または作成し、Bookmark を作成する

Application Service は各 Context の公開 Domain API を利用する。別 Context の table を直接操作しない。

複数 Aggregate を単一 transaction で常に更新しなければ不変条件を維持できない場合は、直接 SQL の例外を追加するのではなく Aggregate 境界を再検討する。

#### Read Model / Projection

複数 Context の情報を大量に読み取り、公開 API の合成では明確な性能問題がある場合だけ利用する。

Projection は次を満たす。

- read-only
- 目的を表す具体的な名前を持つ
- 参照する Context / table を明示する
- generic な `cross-feature` / `shared` module に置かない
- Domain command を提供しない

Web や Widget の存在だけを理由に Projection を作らない。既存 Repository API で十分ならそれを利用する。

### 6. table access ownership を schema ownership と一致させる

ADR-0047 の table ownership は schema/migration だけでなく、通常の永続化 access ownership も表すものとする。

原則:

```text
owned table への直接 SELECT/INSERT/UPDATE/DELETE
  -> owner data module のみ

他 Context からの利用
  -> owner が公開する Domain API / named Query API

複数 Context の最適化 read
  -> 明示された read-only Projection のみ

他 Context 所有 table への直接 write
  -> 禁止
```

同一 SQLite database を共有していることは共同 ownership の根拠にはしない。

Foreign key の存在も別 Context の table を自由に読み書きできる理由にはしない。

### 7. cross-context API はユースケース単位で公開する

他 Context の都合だけで低レベル CRUD を Domain API に追加しない。

例えば Summary が自動生成 tag を保存したい場合、Summary が `tags` / `article_tags` を直接更新するのではなく、Curation context が `addGeneratedTags(contentItemId, names)` のように意味を持つ command を公開する。

同様に Web / Widget は table projection を自作せず、Content / Curation / Source が公開する query を利用する。

### 8. 現在の Article 名は移行上維持する

現在の `Article` は RSS 記事だけでなく共有 URL や他 source の content を表しており、Domain 上は `ContentItem` に近い。

ただし本 ADR では `Article` -> `ContentItem` の rename や module 移動を行わない。

まず persistence ownership と API 境界を整え、実際の ubiquitous language が安定した後に別 ADR で rename / module restructuring を判断する。

## Migration plan

### Phase 1: 不要な foreign table access を除去する

- Widget の `articles` 直接 read/write を ArticleRepository 経由へ変更する。
- Web の Article / RSS / Bookmark table 直接 read を各 Repository API 経由へ変更する。
- architecture verification で foreign table access を検出するための table ownership manifest / allowlist を導入する。

### Phase 2: Curation ownership を物理 schema に反映する

- `bookmarks(content_item_id, saved_at, ...)` に相当する Bookmark-owned table を導入する。
- `articles.saved_at` を Bookmark-owned table へ migration する。
- Bookmark の command が `articles` を直接 write しないようにする。
- Summary の tag write を Bookmark/Curation の公開 command へ移す。

### Phase 3: named Domain Service / Projection を導入する

- effective content type の解決を Content Classification の Domain Service として明示する。
- Article cleanup と Summary retention の横断 rule を明示的な retention policy / query contract にする。
- 性能測定で必要と確認できた read path だけ named Projection とする。

### Phase 4: ubiquitous language と module 名を再評価する

- `Article` が継続して ContentItem 相当の意味を持つなら rename を検討する。
- RSS / Reddit / YouTube の Source Context 統合は、モデルと lifecycle が実際に収束した場合だけ行う。

## Testing and architecture verification

- owner Repository の contract test で table access と Domain semantics を検証する。
- cross-context Application Service は fake Repository を用いた Domain/Application test を持つ。
- Projection は owner table の schema change を検出できる integration test を持つ。
- `verifyArchitecture` は production source における foreign table access を原則拒否し、明示された Projection だけを allowlist とする。
- public repository に追加する ADR / architecture manifest には token、credential、個人データ、内部 endpoint を含めない。

## Consequences

### Positive

- Gradle feature 境界と DDD 境界の混同を避けられる。
- table の schema ownership と access ownership が一致する。
- feature 横断処理を Aggregate、Domain Service、Application Service、Projection に分類できる。
- 性能最適化を理由に Domain ownership が崩れることを防げる。
- `Article` の実際の意味を Content context として段階的に見直せる。

### Negative

- 既存の単一 SQL より Repository 合成が増える箇所がある。
- Bookmark の schema migration と command API 再設計が必要になる。
- cross-context transaction が必要な操作では Aggregate 境界を再検討するコストが生じる。
- module 名と Domain 名が移行期間中は完全には一致しない。

## Relationship to previous ADRs

ADR-0003 の feature-first module 構成は維持するが、feature module が Bounded Context / Aggregate と同義であるとは扱わない。

ADR-0004 の concept-oriented ownership は維持する。Article は共有概念として独立 ownership を持つが、その概念の ubiquitous language と Aggregate 境界を本 ADR により再評価する。

ADR-0047 の schema ownership を persistence access ownership まで拡張する。単一 SQLite database を維持する判断は変更しない。
