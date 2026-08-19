# ADR-0011: HTML メールを専用の制限付き WebView で表示する

- Status: Accepted
- Date: 2026-08-09

## Context

Gmail API の `format=full` では、メール本文を MIME part として取得できる。HTML メールでは `text/plain` と `text/html` の両方を持つことがあり、従来の mail data 実装は `text/plain` を優先し、HTML しかない場合も `Html.fromHtml()` でプレーンテキストへ変換していた。

このため、表、装飾、レイアウト、HTML 内のリンクなど、送信者が意図した表示情報は同期時点で失われていた。また、プレーンテキスト本文に含まれる URL は通常の Compose `Text` として表示しており、タップ可能ではなかった。

一方、メール本文は第三者から届く信頼できない HTML である。X viewer のように外部 Web アプリを実行する WebView と同じ設定を共有すると、JavaScript、ローカルリソースアクセス、ネットワークリソース読み込みなど、メール表示には不要な権限を与えることになる。

## Decision

### 1. プレーンテキストと HTML を別々に保持する

`MailMessage.body` は従来どおりプレーンテキスト表示用の本文として維持し、nullable な `MailMessage.htmlBody` を追加する。

Gmail MIME parser は `text/plain` と `text/html` を独立して取得する。`text/plain` が存在しない場合だけ、HTML をプレーンテキスト化した値を `body` のフォールバックとして保存する。

これにより UI は HTML 表示の可否とは独立して常にプレーンテキストのフォールバックを持てる。

### 2. HTML 本文をローカル DB に保存する

`mail_messages` に nullable な `html_body` を追加し、DB version を 9 とする。

version 8 以前からの migration では既存メッセージの `html_body` を `NULL` のまま保持する。既存キャッシュを破棄したり全件再同期したりはしない。以後 Gmail API から再取得したメッセージについて HTML 本文が保存される。

### 3. HTML メールは mail feature 専用 WebView で表示する

HTML 本文は Compose の `AndroidView` で包んだ専用 WebView に `loadDataWithBaseURL()` で読み込む。

この WebView は X viewer の WebView と共有しない。X viewer は外部 Web アプリを動作させるため JavaScript や DOM storage を必要とするが、メール本文では実行可能コンテンツを必要としないため、信頼境界が異なる。

メール表示用 WebView では次を固定する。

- JavaScript を無効化する
- DOM storage を無効化する
- file access を無効化する
- content access を無効化する
- HTTP/HTTPS を含む network subresource load を遮断する
- mixed content を許可しない
- WebView Safe Browsing を有効化する
- JavaScript bridge を作成しない
- synthetic HTTPS origin を base URL として HTML を読み込む

初期実装では外部画像、外部 CSS、tracking pixel なども network load として遮断する。data URI 等、HTML 自体に埋め込まれている内容は WebView 内で表示できる。

### 4. メール本文から WebView 内遷移は行わない

メール HTML のリンクをタップしてもメール WebView 自身は遷移しない。

`WebViewClient.shouldOverrideUrlLoading()` で main-frame かつユーザー操作による navigation のみを捕捉し、次の scheme だけ Android の外部 URL handler へ渡す。

- `http`
- `https`
- `mailto`
- `tel`

その他の scheme、自動 navigation、subframe navigation は破棄する。

### 5. プレーンテキストの URL を自動リンク化する

HTML 本文がない場合は Android `TextView` と `Linkify` を使い、Web URL とメールアドレスをタップ可能にする。

HTML 表示のためだけにプレーンテキストを WebView へ渡さない。単純な本文は通常の Android text rendering とする。

## Consequences

### Positive

- HTML メールの装飾、表、リンク、インライン画像等を維持できる
- プレーンテキストメールの URL も直接開ける
- メール本文から任意 JavaScript を実行しない
- メール本文からアプリの file/content provider へアクセスさせない
- 外部画像や tracking pixel を自動取得しない
- X viewer とメール renderer の権限・信頼境界が分離される
- 既存メールキャッシュを破棄せず DB migration できる

### Negative

- 外部画像や外部 CSS に依存する HTML メールは完全な見た目を再現しない
- HTML メール 1 件ごとに WebView を使用するため、プレーンテキスト表示よりリソース消費が増える
- 既存キャッシュには HTML 本文が存在しないため、そのメッセージが Gmail から再取得されるまでは従来のプレーンテキスト表示になる
- HTML 本文の保存分だけローカル DB 使用量が増える

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data の責務分離を維持する。MIME 解析と保存は data、表示と navigation は UI が担当する
- ADR-0003 / ADR-0004 の feature 分離に従い、mail renderer は mail feature 内に閉じる
- ADR-0008 mail triage workflow の一覧・状態遷移には変更を加えない
- ADR-0115 x-webview-css-customization の X WebView とは設定を共有しない。両者は WebView を使うが、信頼モデルと必要権限が異なる

## References

- https://developer.android.com/reference/android/webkit/WebSettings
- https://developer.android.com/reference/android/webkit/WebViewClient
- https://developer.android.com/privacy-and-security/risks/cross-app-scripting
