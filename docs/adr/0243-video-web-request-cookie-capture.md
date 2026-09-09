# ADR-0243: Web動画再生のCookie共有で実request Cookieを優先する

- Status: Accepted
- Date: 2026-09-08
- Amends: [ADR-0241](0241-video-web-stream-cookie-opt-in.md)
- Followed by: [ADR-0245](0245-video-playback-explicit-referrer-origin.md)

## Context

ADR-0241では、Web抽出ルールで明示的にCookie共有をONにした場合だけ、専用WebView profileのCookieをMedia3 foreground playbackへ一時的に共有することを決めた。

初期実装は専用profileの `CookieManager.getCookie(requestUrl)` をMedia3 requestごとに呼び出す方式とした。この方式は通常Cookieをhost / path / Secure等に従って選択できる一方、partitioned Cookieなど、WebViewの実request contextに依存するCookieを正確に再現できない場合がある。実機では `Referer` / `Origin` / `User-Agent` と通常Cookieを共有してもHTTP 403になるケースが残った。

AndroidX WebKit 1.15以降は、`COOKIE_INTERCEPT` capabilityが利用可能な場合に `WebSettingsCompat.setCookiesIncludedInShouldInterceptRequest` を有効化することで、`shouldInterceptRequest` の `WebResourceRequest.requestHeaders` に、そのrequestへ実際に適用される `Cookie` headerを含められる。この値はpartitioned CookieもWebViewのpartition contextに従って解決される。

## Decision

### Cookie共有ONの場合だけ実request Cookieを観測する

Web抽出ルールの `shareCookiesForPlayback` がONで、かつ `WebViewFeature.COOKIE_INTERCEPT` が利用可能な場合だけ、dedicated extractor WebViewのsettingsでrequest interceptionへのCookie付与を有効化する。

- Cookie共有OFFでは有効化しない。
- default WebView profileは利用しない。
- `Authorization` や他のcredential headerのcaptureへ一般化しない。
- third-party Cookie acceptance設定は従来どおり変更しない。

### playback extractorが返したstream URLと同一requestのCookieだけを優先する

`shouldInterceptRequest` で観測したHTTP(S) requestをbounded transient stateへ保持する。playback extractorが返したstream URLとscheme / host / effective port / path / queryが一致するrequestが存在する場合だけ、そのrequestの `Cookie` headerをMedia3の初回request用Cookieとして優先する。

別URLのCookieを推測で流用しない。fragmentはHTTP request identityに含めない。

### それ以外のrequestは既存providerへfallbackする

Media3がmanifestから派生したsegmentやredirect先など、WebViewで同一request Cookieを観測していないURLへrequestする場合は、ADR-0241の既存 `CookieManager.getCookie(requestUrl)` providerへfallbackする。

これにより、観測できた初回stream requestではpartitioned Cookieを含むWebView実requestのCookieを利用できる一方、未観測URLへ同じCookie文字列を無条件に転送しない。

### 非対応WebViewでは従来方式を維持する

`WebViewFeature.COOKIE_INTERCEPT` が利用できない場合はrequest Cookie captureを有効化せず、ADR-0241の `CookieManager.getCookie` providerだけを利用する。Cookie共有ON自体を失敗扱いにはしない。

## Consequences

- 明示opt-in済みのWeb streamについて、partitioned Cookie等が初回stream requestに必要なケースの互換性が改善する。
- credential boundaryの種類とON/OFF境界はADR-0241から増えないが、同じCookie共有capabilityがWebViewの実request Cookieを利用できるようになる。
- Cookie値の新しいdurable source of truthは追加しない。
- Cookie文字列はbounded in-memory captureとplayback provider内だけで扱い、database、SharedPreferences、backup、export、log、error UIへ保存・表示しない。
- WebViewで観測していないsegment URLへpartitioned Cookieを推測転送しないため、segment側でもpartitioned Cookieが必須なstreamは引き続き再生できない可能性がある。その場合は別設計判断とする。

## Security invariants

- 実request Cookie captureは `shareCookiesForPlayback = true` のruleだけで有効にする。
- Cookie共有OFFのruleで `COOKIE_INTERCEPT` を有効化しない。
- playback extractorのstream URLと一致するrequestのCookieだけを優先する。
- 別requestのCookieをhostやdirectoryだけを根拠に流用しない。
- Cookie値をdurable state / log / error UIへ残さない。
- default WebView profileのCookieを利用しない。
- `Authorization` や任意header共有へ一般化しない。
- user-authored extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Cookie共有OFFではrequest Cookie captureを利用しないことをunit testする。
- exact stream URLに観測Cookieがある場合、それを既存CookieManager lookupより優先することをunit testする。
- 別URLの観測Cookieは流用しないことをunit testする。
- 観測Cookieがない場合は既存CookieManager lookupへfallbackすることをunit testする。
- `COOKIE_INTERCEPT` 非対応時は従来providerだけで動作する構成を維持する。
- Public repository / Architecture / Unit Test / Lint / R8を通す。
- Android実機でCookie共有ONのWeb streamを再確認する。
