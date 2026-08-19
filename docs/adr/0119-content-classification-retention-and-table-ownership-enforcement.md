# ADR-0119: Content Classification・Retention・table ownership を明示的な境界へ移す

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0078, ADR-0106, ADR-0117

## Context

ADR-0106 は Gradle module と Domain Context / Aggregate を区別し、同一 SQLite database 内でも durable table の ownership を Context ごとに保つ方針を定めた。ADR-0117 の第一段階では Bookmark、Widget、LAN Web、Summary の主要な foreign access を公開契約へ寄せたが、Article data には次の横断依存が残っていた。

- Article の実効 `ContentType` 解決で `feeds` / `feed_folders` を直接 JOIN する。
- Article cleanup が `article_summaries` / `summary_tasks` を直接参照して削除可否を判断する。

また、これらと同種の foreign table access が再び追加されても、Gradle module の依存方向検査だけでは検出できなかった。

## Decision

### 1. Content Classification は Content domain の Domain Service とする

`ContentClassificationService` は次の優先順位だけを所有する。

1. Content 自身の override
2. Source の override
3. Source container の override
4. `ContentType.ARTICLE`

RSS の Feed / FeedFolder 永続化構造は RSS data が所有する。Content 側は `ContentClassificationSourceQuery` port から `SourceContentTypeOverrides` だけを受け取り、Feed table や Folder table を知らない。

現在の RSS adapter は `RssContentClassificationSourceQuery` とする。これにより ADR-0078 の「読み取り時に実効値を解決する」意味は維持しつつ、Article data 自身が RSS table を JOIN する実装は廃止する。

### 2. Content retention は Content domain の policy と外部保護 query に分離する

`ContentRetentionPolicy` は、既読かつ未保存の Content を30日後に削除候補とする既存ルールと、外部 Context に保護された Content を削除しない規則を表す。

Summary が Content の削除を保護する条件は Summary context が所有するため、Content は `ContentRetentionProtectionQuery` port だけを利用する。Summary data の `SummaryContentRetentionProtectionQuery` は次を保護対象として返す。

- 保存済み Summary が存在する Content
- queued / running の Summary task が存在する Content

Article data は Summary の table 名・state schema を参照しない。一方で従来の cleanup が持っていた原子的な削除判定を失わないよう、削除候補取得・保護 query・Article 削除は同一 SQLite transaction 内で実行する。保護 query は ID を chunk 化し、SQLite の bind variable 上限を超えないようにする。

### 3. durable table ownership を manifest と CI で検査する

`config/architecture/table-ownership.tsv` に今回の Context 境界で扱う durable table と owner data module を記録する。

production の `:feature:*:data` Kotlin source に対し、SQL および SQLite API から既知 table への参照を静的に抽出し、owner 以外からの参照を失敗させる。

既存の移行負債だけは `config/architecture/foreign-table-access-allowlist.tsv` に次を明示して許可する。

- repository path
- table
- ADR に基づく理由

allowlist の file/table が消えた場合は stale entry として検査を失敗させる。これにより allowlist を恒久的な例外リストにしない。

Gradle 9 では任意 build file を指定する `-b/--build-file` が利用できないため、ownership 検査は repository 内の Gradle init script として実装し、CI の `verifyArchitecture` 実行時に `-I` で適用する。PR と main build の双方で同じ検査を行う。

### 4. 今回は物理 Curation schema migration を行わない

`articles.saved_at` の Bookmark-owned table への移行は ADR-0117 の follow-up として残す。Bookmark read model、Summary の Read Later priority、RSS ingestion などに残る既知 foreign access は allowlist に固定し、別のリファクタリング単位で削除する。

## Consequences

### Positive

- Content Classification のルールと RSS の永続化責務が分離される。
- Article cleanup が Summary の table layout / task state schema に依存しなくなる。
- cleanup の候補判定と削除は従来どおり単一 transaction の整合性を保つ。
- Context boundary を破る新しい SQL / SQLite API access を CI で検知できる。
- 既存負債が path・table・理由の単位で可視化される。
- Article data の境界テストは RSS/Summary schema を持たない最小DBで成立する。

### Negative

- Article 一覧取得では Content 行取得後に Source override query が追加で発生する。
- Content と Source / Summary の間に小さな query port と adapter が増える。
- 静的検査は SQL parser ではなく既知パターンの抽出であり、動的に構築された table 名までは保証しない。
- CI 以外で同じ ownership 検査を行う場合は `-I gradle/table-ownership.gradle.kts` を付ける必要がある。

## Test strategy

- `ContentClassificationServiceTest`: override precedence を純粋な domain test で検証する。
- `ContentRetentionPolicyTest`: 30日 cutoff と protected Content 除外を検証する。
- `ArticleRepositoryBoundaryTest`: RSS/Summary の実 table schema を持たないDBで ArticleRepository が query port 経由で動作することを検証する。
- `SummaryContentRetentionProtectionQueryTest`: Summary owner adapter が summary / queued / running のみを保護し、大量 candidate を chunk 処理できることを検証する。
- table ownership init script 自身に、foreign access を検出する fixture と owner access を許可する fixture を持たせる。
- CI で ownership verification、既存 `verifyArchitecture`、全 unit test、lint を実行する。

## Public repository safety

manifest、allowlist、ADR、テストには token、credential、実ユーザーの購読 URL、作品名、個人データを含めない。テスト URL は `example.com` の固定値だけを使用する。

## Remaining follow-up

- `articles.saved_at` を Bookmark-owned persistence へ移行する。
- Bookmark read model から `feeds` / `feed_folders` JOIN を除去し、Content の公開 read model / classification contract を利用する。
- Summary の Read Later priority を Curation の named query へ移す。
- RSS ingestion の Content write を Content command/application boundary へ移す。
- allowlist entry を各 follow-up 完了時に削除する。
