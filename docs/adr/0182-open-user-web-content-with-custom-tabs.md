# ADR-0182: ユーザー閲覧用 Web content を Custom Tabs で開く

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0103](0103-app-route-composition-and-navigation-spec.md)
- Relates: [ADR-0173](0173-web-library-custom-metadata-extractors.md)
- Relates: [ADR-0180](0180-rss-custom-web-scraping-rules.md)

## Context

RSS 記事、ブックマーク、Web 由来の蔵書は、ユーザーが外部 Web ページを閲覧する入口を持つ。従来は `ACTION_VIEW` を直接起動していたため、通常のブラウザ画面へ遷移し、閲覧後にアプリへ戻る操作が Custom Tabs より分断されやすかった。

一方、Web 由来の蔵書には広告や tracker の多いページもあり、アプリ独自 WebView で閲覧するより、ユーザーが既定にしているブラウザの cookie、site setting、content blocking 等を利用できることが望ましい。

Library と RSS には metadata 取得、Web scraping rule 実行、実行テスト等で WebView を利用する処理もある。これらは DOM / JavaScript 実行をアプリ側が制御するための内部処理であり、ユーザー閲覧用ブラウザとは責務が異なる。

## Decision

### ユーザーが開く HTTP / HTTPS content は AndroidX Custom Tabs を利用する

RSS / Bookmark の Article 起動経路と Web 由来の蔵書の閲覧 URL は `androidx.browser` の `CustomTabsIntent` で開く。

Custom Tabs launcher は `:app` に置き、feature module は platform 実装を直接所有しない。Library feature には Web URL を開く callback を app route から渡す。これは ADR-0103 の app module を navigation / composition / platform wiring の境界とする方針に従う。

### ブラウザ package は固定しない

アプリ独自のブラウザ選択設定は追加せず、`CustomTabsIntent` に package を設定しない。Android の既定ブラウザ選択を尊重し、Custom Tabs を提供する既定ブラウザで開く。

launcher は HTTP / HTTPS のみを受け付ける。`kindle:` やアプリ固有 URI 等の custom scheme は Custom Tabs に渡さない。

### Library は Web source の閲覧だけを変更する

Library では `LibrarySource.WEB` の書籍から得られる閲覧 URL だけを Custom Tabs callback へ渡す。

次の既存起動方式は変更しない。

- SMB: アプリ内 book reader
- Kindle personal document / Kindle app: 既存 app link / package 起動
- Google Play Books: 既存 reader / package 起動
- Audible 等の非 Web source: 既存 `ACTION_VIEW` 起動

### データ取得用 WebView は変更しない

以下は Custom Tabs に移行せず、既存の専用 WebView 実装を維持する。

- Web 蔵書 metadata 取得
- Web 蔵書 custom extractor
- RSS Web scraping rule
- 各 rule の実行テスト

Custom Tabs はユーザー閲覧専用であり、アプリ側から DOM を操作する capability として利用しない。

## Consequences

### Positive

- Web 閲覧後に Custom Tab を閉じる、または Back で元のアプリへ戻りやすくなる。
- 既定ブラウザの cookie、site setting、広告・tracker 対策等をユーザー閲覧に利用できる。
- Brave 等の特定ブラウザをアプリ側へ hard-code せず、Android の既定ブラウザ設定をそのまま尊重できる。
- metadata / scraping 用 WebView とユーザー閲覧ブラウザの責務が明確になる。

### Negative

- Custom Tabs の提供機能や表示は既定ブラウザの実装に依存する。
- アプリは Custom Tab 内の DOM やページ UI を直接制御できない。
- `androidx.browser:browser` dependency が `:app` に追加される。

## Verification

- Custom Tab request が HTTP / HTTPS を受け付け、他 scheme を拒否することを unit test する。
- Custom Tab intent に browser package を設定しないことを unit test する。
- RSS / Bookmark の共通 Article 起動 callback が Custom Tabs launcher を使用することを source review する。
- Library が `LibrarySource.WEB` の URL だけを Custom Tabs callback の対象とし、既存の Kindle / Google Play Books / SMB 等の分岐を維持していることを review する。
- App / Library unit test、architecture verification、public repository verification、lint、assemble を CI で確認する。

## Public repository review

変更対象は platform launcher、route wiring、dependency、unit test、ADR のみとする。test URL は `example.com` の架空値だけを使い、credential、token、private endpoint、ユーザー固有 URL、cookie、閲覧履歴等を repository へ追加しない。
