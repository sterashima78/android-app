# ADR-0172: Web Library に URL パターン別のカスタム metadata extractor を導入する

- Status: Accepted
- Date: 2026-08-25
- Amends: [ADR-0154](0154-web-library-rendered-metadata-fallback.md)
- Refines: [ADR-0136](0136-public-repository-content-verification.md), [ADR-0163](0163-webview-renderer-exit-recovery.md)

## Context

ADR-0154 では Web Library の metadata 取得について、静的 HTTP を primary path とし、title または thumbnail が不足するときに専用 WebView で固定 JavaScript を実行する fallback を採用した。固定 script は Open Graph / Twitter Card / document title / author / 先頭画像を読むため、多くのサイトでは十分である。

一方で、サイト固有の DOM 構造から title や表紙画像を取得したい場合がある。サイト側の markup 変更へアプリのリリースを伴わず追従するためには、特定 URL 群に対する抽出ロジックを端末上で変更できる必要がある。また、SPA の追加読み込みやページ origin への fetch など、抽出処理自体が非同期になるケースもある。

ただし、任意 JavaScript を実行する仕組みを native bridge や通常ブラウザへ広げると、既存の WebView security boundary を崩す。公開 repository に実サイト URL やユーザー固有 script をコミットする設計も避ける必要がある。

## Decision

### extractor rule は Library Context が所有する durable user data とする

Library Domain は `WebLibraryMetadataExtractor` と `WebLibraryMetadataExtractorRepository` capability を公開する。Library Data は `web_library_metadata_extractors` table を所有し、次を保存する。

- rule ID
- URL pattern
- function code
- updated timestamp

rule は application database backup の通常 snapshot に含まれる。新 table は Library の idempotent schema initializer から作成し、現行 database compatibility baseline を変更するためだけの database version bump は行わない。

実際の URL や function code はユーザー端末の DB にのみ保存し、repository の source/test/docs には含めない。

### URL pattern は HTTPS glob とする

URL pattern は `https://` から始まる文字列とし、次の wildcard だけを解釈する。

- `*`: 0 文字以上の任意文字列
- `?`: 任意の 1 文字

regex 自体を入力させず、pattern からアプリ側で安全な regex を生成する。複数 rule が一致した場合は wildcard 以外の文字数が多い、より具体的な pattern を優先し、同じ具体度では更新日時が新しい rule を優先する。

WebView 自体が HTTPS 標準 port の main-frame navigation に限定される既存制約は維持する。

### function contract は Promise を返す title / thumbnail extractor とする

登録する function code は WebView のページ context で実行される JavaScript function expression とする。呼び出し時に `{ url: location.href }` を渡し、戻り値は `Promise<{ title, thumbnailUrl }>` とする。

```javascript
async ({ url }) => ({
  title: document.querySelector("h1")?.textContent?.trim() ?? null,
  thumbnailUrl: document.querySelector("img.cover")?.currentSrc ?? null,
})
```

`async` / `await` や `fetch` など、ページ context で利用可能な非同期処理を function 内で実行できる。同期 object を直接返す function は contract 違反として custom result を採用せず、固定 metadata extraction へ fallback する。

Android `WebView.evaluateJavascript` の callback 自体に Promise 完了待ちを依存しない。custom function の Promise はページ JavaScript context 内で開始し、一時 state に resolve/reject 結果を書き込み、Android 側は native JavaScript bridge を追加せず `evaluateJavascript` でその state を短時間 polling する。Promise が resolve した時点で `title` / `thumbnailUrl` を回収する。

Promise は最大 10 秒待機する。reject、構文エラー、不正な戻り値、一時 state の異常、または 10 秒以内に完了しない場合は custom extraction を打ち切り、既存の固定 rendered metadata extraction へ fallback する。全体の WebView timeout と renderer exit handling は既存値を維持する。

保存対象として解釈する field は `title` と `thumbnailUrl` だけとする。`thumbnailUrl` は最終ページ URL に対して相対解決した後、既存の rendered metadata と同じく HTTPS 標準 port の URL だけを採用する。

### rule 一致時は WebView を明示的な extraction path とする

通常追加でも、requested URL に custom extractor rule が一致する場合は静的 metadata が揃っていても WebView extraction を実行する。

custom function が有効な `title` または `thumbnailUrl` を返した場合、その値を固定 rendered metadata の同 field に overlay する。その後、静的 metadata との merge では title / thumbnail に限って rendered 側を優先する。

`description` と `authors` は custom extractor の対象外であり、通常追加時は ADR-0154 の静的 metadata 優先を維持する。明示的な「再取得」では従来どおり rendered metadata 全体を優先できる。

custom function が syntax error、Promise reject、不正な戻り値、空結果、timeout となった場合は、その rule によって Web Library 追加・再取得全体を失敗させず、既存の固定 rendered metadata extraction へ fallback する。

### WebView の既存 security boundary を拡張しない

custom function は ADR-0154 の metadata 用専用 WebView profile 内だけで実行する。次の既存制約を変更しない。

- native JavaScript bridge を追加しない
- file/content access を無効化する
- mixed content を許可しない
- third-party Cookie を受け入れない
- geolocation / multiple windows を許可しない
- HTTPS 標準 port 以外への main-frame navigation を拒否する
- timeout、renderer exit handling、成功/失敗/cancel 時の WebView 破棄を維持する

function code はページと同じ JavaScript context で動くため、技術的には DOM 変更やページ origin への network request 等の副作用を起こし得る。これは端末ユーザー自身が登録する local customization として許容するが、UI では必要な非同期処理だけを行い、不要な DOM 変更等の副作用を避けることを推奨する。アプリはこの function に native capability を与えない。

## Consequences

- サイト固有 DOM から Web 蔵書の title / thumbnail を取得でき、サイト変更へアプリ release なしで追従できる。
- Promise contract により SPA 待機、ページ origin への fetch 等を extractor 内に記述できる。
- custom rule が存在する URL では静的 metadata が完全でも WebView を起動するため、そのサイトの追加・再取得コストは増える。
- function code の不具合、Promise reject、timeout は固定 extractor へ fallback するため、設定ミスで従来 metadata path が失われにくい。
- user-authored JavaScript を扱う surface は増えるが、native bridge や通常 WebView へ execution capability を広げない。
- URL pattern と code はバックアップされる user data になるため、公開 repository の fixture や ADR に実値を転記しない運用が必要になる。

## Verification

- glob pattern の `*` / `?` と HTTPS 制約を unit test する。
- 複数一致時の具体度優先を unit test する。
- Promise 開始 script が Promise/thenable を要求し、poll script が pending/complete state を扱うことを unit test する。
- Promise 完了 state から custom title / relative thumbnail を復元することを unit test する。
- HTTP thumbnail が保存されないことを unit test する。
- rule 一致時は静的 metadata が完全でも rendered path を実行し、title / thumbnail だけを優先する unit test を行う。
- custom field overlay が description / authors を変更しないことを unit test する。
- `web_library_metadata_extractors` を Library-owned table として architecture verification する。
- fresh database schema test に `web_library_metadata_extractors` を含める。
- public repository verification、Library data unit test、app unit test、lint、assemble を CI で実行する。
