# Architecture Glossary

この文書は Domain とアーキテクチャ議論で同じ語を同じ意味で使うための用語集である。実装 rename の要求ではなく、現在の ubiquitous language と移行上の名称差を説明する。

## Architecture terms

### UI layer

Compose Screen、Route、ViewModel、UiState 等の presentation responsibility。ユーザー操作を受け、Domain/Application API を呼び、表示 state へ変換する。DB / HTTP / WorkManager の concrete implementation を直接操作しない。

### Domain layer

アプリ固有の意味、rule、state、Repository/port contract を表す layer。Android framework、SQLite、HTTP 等の実装詳細から独立する。

### Data layer

Domain contract の implementation と、local / remote / platform adapter を所有する layer。schema、SQL、HTTP DTO、parser、Android platform detail を上位へ漏らさない。

### Feature module

`:feature:<name>:<layer>` 形式の application-specific ownership / build boundary。画面機能だけでなく Article のような共有 concept を所有する場合もある。Bounded Context と同義ではない。

### Core capability

`:core:<capability>` 形式の cross-cutting technical capability。database、network、AI runtime、design system 等。feature-specific Domain concept / use case を所有しない。

### Bounded Context

特定の ubiquitous language と Domain model が一貫した意味を持つ境界。Gradle module と 1 対 1 である必要はない。

### Aggregate

transactional consistency と invariant を維持する単位。複数 Context を扱う処理があるだけでは新しい Aggregate を作らない。

### Composition root

concrete implementation を生成して contract へ配線する場所。このアプリでは主に `:app` / `AppContainer` 等が担当する。business rule の所有場所ではない。

### Application Service

複数 Aggregate / Context の command を1つの use case として orchestration する stateless な application-level service。各 owner の公開 contract を利用し、foreign table を直接操作しない。

### Domain Service

単一 Entity/Aggregate に自然に属さない Domain rule を表す service。自身の durable state を所有せず、Domain の意味を解決する。

### Query API / Query port

owner Context が他 Context 向けに公開する read contract。consumer に owner の table layout を露出せず、目的を表す値を返す。

### Read Model

表示・検索・処理のために複数の Domain data を読み取り向けに構成した model。ownership を移すものではない。

### Projection

複数 Context の大量 read で通常 API の合成に実測上の問題がある場合に導入する named read-only view/query implementation。参照 Context/table を明示し、command は提供しない。

### Persistence ownership

schema/migration と通常の直接 table access をどの Context / data module が所有するかという規則。同一 SQLite database は共同 ownership を意味しない。

### Transitional allowlist

既知の migration debt として一時的な architecture exception を path/table/reason 単位で列挙する manifest。恒久的 extension point ではなく、migration 完了時に削除する。

### Provider lookup

Android / WorkManager 等が constructor を所有する framework entry point から Application が公開する狭い dependency contract を取得する仕組み。通常の Screen、Route、ViewModel、Data object での service locator としては利用しない。

### ADR

Architecture Decision Record。特定時点で「なぜその設計判断をしたか」「何を選び、何を選ばなかったか」を履歴として残す文書。現在形の全体像は `docs/architecture/` が担当する。

## Domain terms around Content

### Content Context

RSS、Reddit、YouTube、shared URL 等の source から得られる「読める/参照できる content」そのものの identity、metadata、reading state 等を扱う Context。

### ContentItem

Content Context 上の中心概念。現在のコードでは主に `Article` という名称で実装されているが、RSS 記事だけに限定されない意味を持つため Domain 上では `ContentItem` に近い。

### Article

現在の implementation/module/model 名。歴史的には RSS article を中心に始まったが、現在はより広い Content を扱う。`ContentItem` への rename は persistence/API boundary と ubiquitous language が安定した後に判断する。

### Source Context

Content の上流で source-specific subscription、sync、authentication、external semantics を所有する Context。現在は RSS、Reddit、YouTube 等を別 Context として扱う。

### Curation Context

Content を保存・整理する意味を所有する Context。現在の主要 implementation は `:feature:bookmark`。

### Bookmark

ContentItem を保存した状態。Curation が所有し、ContentItemId を参照する。

### Tag

Curation が所有する分類概念。Content 自体の identity/metadata の一部ではない。

### Folder

Curation が所有する grouping concept。system folder を持つ場合、その invariant も Curation が所有する。

### Read Later

Curation の membership/state。Summary task priority 等が参照する場合も ownership は Curation に残り、consumer は named query を利用する方向へ移行する。

### Summary Context

Content を入力として generated summary と summary task lifecycle を所有する Context。

### Knowledge Context

Content/Curation を source として Knowledge page、source relationship、generated/edited state を所有する Context。

## Naming rule

用語が実装名と異なる場合は、文書内で「Domain term」と「current implementation name」を併記する。名前だけを合わせるための大規模 rename は行わず、意味・ownership・lifecycle が安定してから ADR で判断する。

## Sources

- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0004](../adr/0004-concept-oriented-modules.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
