# ADR-0184: 漫画サイト固有のRSS取得実装をカスタムWeb取得ルールへ移行する

- Status: Accepted
- Date: 2026-08-26
- Supersedes: [ADR-0090](0090-yanmaga-web-html-derived-rss.md), [ADR-0091](0091-mangaone-webview-derived-rss.md)
- Refines: [ADR-0180](0180-rss-custom-web-scraping-rules.md)

## Context

ADR-0090 と ADR-0091 では、RSS を公開していない漫画サイトを購読するため、RSS Data にサイト固有の synthetic feed client を組み込んだ。ADR-0180 では同じ用途を URL pattern と端末保存 JavaScript function で実現できる汎用 Web scraping rule を追加し、移行期間中だけ既存 site-specific client を fallback として残した。

端末上の custom rule で既存の対象サイトについて必要な synthetic feed を取得できることを確認できたため、アプリ本体にサイト名、URL 構造、DOM selector、WebView 操作を固定実装し続ける必要はなくなった。

custom rule の実 URL pattern と function code は ADR-0180 のとおり user data であり、公開 repository へ保存しない。

## Decision

### 1. RSS Data からサイト固有 client を削除する

`YanmagaFeedClient` と `MangaOneFeedClient`、およびそれら専用の unit test を削除する。

`DefaultFeedRepository` から対象サイトの host / path 判定、専用 client への routing、専用 WebView renderer、サイト固有エラーメッセージを削除する。

### 2. feed 取得経路を custom rule と標準 feed の2系統にする

`inspect`、`addFeed`、`refreshFeed` の取得方法は次の順序だけとする。

1. URL に一致する user-defined Web scraping rule
2. 通常の RSS / Atom discovery / fetch

custom rule が存在しない Web ページに対して、アプリ本体のサイト固有 synthetic feed へ fallback しない。

### 3. サイト固有のコンテンツ種別自動設定も削除する

サイト host を根拠に新規 feed を自動的に `ContentType.COMIC` とする処理は削除する。custom Web scraping rule 自体は synthetic feed の取得方法だけを定義し、コンテンツ分類を暗黙に持たせない。

既存 feed に保存済みの `content_type` は migration せず、そのまま維持する。必要な分類は既存の feed / folder のコンテンツ種別設定で明示する。

### 4. user rule を source code や test fixture に移植しない

今回の移行確認に使用した実 URL pattern、DOM selector、function code は repository に追加しない。汎用 rule mechanism の test は引き続き `example.com` 等の例示データで行う。

サイト固有 client の過去の設計判断は ADR-0090 / ADR-0091 に履歴として残すが、現行実装の判断としては本 ADR がそれらを supersede する。

## Consequences

- RSS Data から漫画サイト固有の取得コードと保守対象がなくなる。
- 対象サイトの DOM 変更にはアプリ更新ではなく端末上の custom rule 更新で追従できる。
- 新しい非RSSサイトも同じ拡張ポイントで扱えるため、site-specific client を追加する必要がなくなる。
- custom rule を削除した場合、以前の site-specific client へは戻らず、通常の RSS / Atom 取得へ進む。
- 新規 custom-rule feed はサイト名だけでは `COMIC` に自動分類されない。必要な場合は feed / folder のコンテンツ種別を明示する。
- 既存購読の feed ID、記事、既読状態、保存済みコンテンツ種別には変更を加えない。

## Verification

- `MangaOneFeedClient` / `YanmagaFeedClient` と専用 test が repository から削除されていることを確認する。
- `DefaultFeedRepository` に対象サイト名、host 判定、site-specific routing が残っていないことを確認する。
- ADR-0180 で追加した URL matching、rule validation、script execution contract、result parsing、WebView security、DB schema の汎用 test を継続して通す。
- RSS Data の unit test、lint、architecture verification を CI で実行する。
- public repository に user rule、実購読 URL、Cookie、認証情報などの公開すべきでない情報が追加されていないことを PR 前に独立レビューする。
