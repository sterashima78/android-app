# ADR-0211: executable app の feature Domain fan-in を framework contract まで縮小する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0166](0166-lan-web-and-route-composition-responsibility-split.md), [ADR-0200](0200-app-composition-module-boundary.md), [ADR-0205](0205-app-presentation-module-boundary.md)

## Context

ADR-0200 / ADR-0205 により executable `:app` から feature Data / UI の direct dependency は除去された。一方 `app/build.gradle.kts` には 23 個の `:feature:*:domain` dependency が残っていた。

production source を再監査すると、直接 feature contract を利用していた箇所は主に次に限定されていた。

- `MainActivity`: `Article` を受け取り URL を Custom Tab へ渡す
- shared/external Intent handling: Bookmark save result と Library book result
- `YomitoriApplication`: widget / task framework provider と LAN Web framework provider
- LAN Web provider: Article / Bookmark / RSS Repository をそのまま Service へ公開

その他の多数の Domain dependency は executable shell の責務ではなく、application composition / presentation で必要な dependency が過去の app module に残存したものだった。

## Decision

### 1. executable callback は feature entity ではなく必要最小の値を受け取る

`MainActivity` は `Article` を受け取らず、presentation boundary から URL (`String`) のみを受け取る。

feature UI / app presentation 内部では従来どおり `Article` を利用できる。`YomitoriApp` が Article から URL へ変換して executable callback を呼び出す。

Custom Tab、app-lock external transition 等の executable platform ownership は ADR-0205 のまま変更しない。

### 2. shared/external Intent mutation は feature-neutral composition capability を利用する

`:app:composition` に `SharedContentEntryCapability` を置く。

この facade は Bookmark / Library の具体的な Domain result を executable `:app` へ公開せず、次の app-entry semantics に変換する。

- Bookmark: `SharedBookmarkSaveOutcome.ADDED` / `ALREADY_BOOKMARKED`
- Library: `AddedSharedWebBook(title)`

`IncomingIntentDependencies` はこの capability だけを利用し、Bookmark / Library Domain type を import しない。

### 3. LAN Web framework provider は Web-owned gateway のみ公開する

旧 `LanWebRepositoryProvider` は廃止する。

Android が直接生成する `LanWebServerService` は `LanWebContentGatewayProvider` から `LanWebContentGateway` を取得する。gateway contract と read DTO は `:feature:web:domain` が所有する。

cross-feature Repository composition と model conversion は `:app:composition` の `AppLanWebContentGateway` が担当する。

```text
YomitoriApplication
  -> LanWebContentGatewayProvider
       -> LanWebContentGateway (feature:web:domain)
            ^
            |
       AppLanWebContentGateway (:app:composition)
            -> ArticleRepository
            -> BookmarkRepository
            -> FeedRepository
```

これにより `:feature:web:domain` / `:feature:web:data` は Article / Bookmark / RSS Repository type を公開 API / compile dependency として持たない。

LAN Web の view selection、RSS/Reddit filtering、HTML rendering は Web feature 側に維持する。composition gateway は owner Domain model を Web-owned DTO へ投影し、source kind を付与するだけとする。

### 4. executable `:app` の direct feature Domain dependency を実利用 contract に限定する

本変更後 `:app` の direct `:feature:*:domain` dependency は次の3つに縮小する。

- `:feature:task:domain`: Android framework widget entry point へ `TaskRepositoryProvider` を提供するため
- `:feature:web:domain`: `LanWebServerController` と `LanWebContentGatewayProvider` の framework/app-shell contract
- `:feature:widget:domain`: widget provider / launch contract / refresh scheduler contract

Article / Bookmark / Library / RSS を含むその他の feature Domain dependency は `:app` から削除する。application-scope concrete graph は `:app:composition`、feature UI composition は `:app:presentation` が継続して所有する。

### 5. runtime lifetime と external behavior は変更しない

- `AppContainer` の application-scope lifetime を維持する
- `MainActivityDependenciesProvider` の framework lookup を増やさない
- LAN Web Service の Android component identity / port / authentication / rendering behavior を変更しない
- Bookmark / Library share Intent action、navigation target、user-facing result semantics を変更しない
- Widget framework provider contract の runtime lifetime を変更しない

## Consequences

### Positive

- executable `:app` の direct feature Domain dependency が 23 から 3 へ減る。
- `MainActivity` / external Intent handler が feature entity/result type を知る必要がなくなる。
- LAN Web Service が cross-feature Repository を Application から直接取得しなくなる。
- `:feature:web:domain` / `:feature:web:data` の cross-feature dependency を削除できる。
- app-only Android lifecycle / security / diagnostics の compile boundary が feature model の変更から影響を受けにくくなる。

### Negative

- composition boundary に小さな DTO conversion / gateway adapter が増える。
- Bookmark / Library の result semantics を capability DTO へ明示的に変換する必要がある。
- LAN Web 用に owner model から Web-owned read DTO への mapping が1段増える。

## Verification

- `MainActivity` が `feature.article.Article` を import せず URL callback を利用すること
- `IncomingIntentDependencies` が Bookmark / Library Domain type を import しないこと
- `SharedContentEntryCapability` が Bookmark / Library result を feature-neutral result へ変換すること
- `LanWebRepositoryProvider` が存在せず、Service が `LanWebContentGatewayProvider` を利用すること
- `feature:web:domain` / `feature:web:data` が Article / Bookmark / RSS Domain module に依存しないこと
- LAN Web read model test が RSS / Reddit / Feed filtering を維持すること
- `app/build.gradle.kts` の direct feature Domain dependency が Task / Web / Widget の3 moduleだけであること
- `verifyArchitecture`、unit tests、lint、public repository verifier を通すこと

## Documentation

current architecture docs の framework-provider 説明は `LanWebContentGatewayProvider` を正本とする。旧 ADR に残る `LanWebRepositoryProvider` は当時の履歴として保持し、本 ADR が current provider boundary を refine する。

## Public repository review

本変更は dependency graph、application composition facade、synthetic DTO、framework provider contract、source/unit test、architecture documentation のみを変更する。credential、OAuth token、account identifier、private endpoint、実ユーザー URL / title / mail / health data、diagnostic artifact、backup/database artifact を追加しない。
