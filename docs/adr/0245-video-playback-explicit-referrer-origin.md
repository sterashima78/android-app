# ADR-0245: playback extractorから再生参照元URLを明示できるようにする

- Status: Accepted
- Date: 2026-09-09
- Refines: [ADR-0237](0237-video-library-and-web-extraction.md)
- Follows: [ADR-0243](0243-video-web-request-cookie-capture.md)
- Amended by: [ADR-0247](0247-video-playback-referrer-path-opt-in.md)

## Context

Video ContextのWeb動画は、user-defined playback extractorが一時的なstream URLを返し、Media3でforeground再生する。

現在はdedicated WebViewの`shouldInterceptRequest`でplayback extractorが返したstream URLと同一requestを観測できた場合、その実requestの`Referer`をoriginへ縮約してMedia3へ利用する。同一requestを観測できない場合は元page URLのoriginへfallbackする。

実機診断では、HTTP 403になるstreamについて`COOKIE_INTERCEPT`自体は利用可能でprofile Cookieも存在する一方、playback extractorが返したstream URLと同一のWebView requestを観測できないケースが確認された。この場合、partitioned Cookieや実requestのReferer/Originをexact stream requestから復元できない。

一方、埋め込みplayer等では元pageと実際のplayer originが異なる場合がある。exact stream requestを観測できないことだけを根拠に、別requestのstream URLや任意のRefererを推測して流用することは避ける必要がある。

## Decision

### playback extractorの返却値へ任意の`referrerUrl`を追加する

playback extractorは従来の`streamUrl` / `mimeType`に加え、任意の`referrerUrl`を返せるようにする。

```text
{ streamUrl, mimeType?, referrerUrl? }
```

`referrerUrl`はuser-defined extractorが現在のpage DOMやplayer設定等から明示的に選ぶ再生元URLであり、stream URLそのものとは別の値とする。

### 参照元の優先順位を固定する

Media3へ渡す参照元は次の優先順位で決定する。

1. WebViewでexact stream requestを観測できた場合、その実requestのReferer origin
2. playback extractorが`referrerUrl`を返した場合、そのURLのorigin
3. 上記がない場合、元page URLのorigin

実requestを観測できた場合は、それをuser-defined hintより優先する。実ブラウザrequestの事実を推測値より優先するためである。

### Media3へは原則としてoriginだけを渡す

`referrerUrl`はHTTP(S) URLとして検証する。相対URLは抽出が実行された最終page URLを基準に解決できる。

既定ではMedia3へ渡す前に既存の`webVideoReferrerOrigin`規則でorigin rootへ縮約する。

- `Referer`: scheme / host / optional port + `/`
- `Origin`: scheme / host / optional port
- path / query / fragmentは送らない
- userinfoを送らない

ADR-0247で、ruleが明示opt-inし、かつplayback extractorのexplicit `referrerUrl`が採用された場合だけ、`Referer`へpathを残せるように改訂する。query / fragment / userinfoと`Origin`の境界は変更しない。

### Cookie共有境界は変更しない

ADR-0241 / ADR-0243のCookie共有opt-inとrequest Cookie capture規則は変更しない。

- Cookie共有OFFではCookie providerを作らない。
- Cookie共有ONでもCookie値をdurable state / log / error UIへ残さない。
- `Authorization`等へ一般化しない。
- exact stream requestを観測できない場合、partitioned Cookieを別requestから推測転送しない。

## Consequences

- exact stream requestをWebViewで観測できなくても、user-defined ruleが埋め込みplayer等の再生元URLを把握できる場合、そのoriginをnative playbackへ明示できる。
- 個別site固有のURLやplayer判定をproduction codeへ追加せず、既存のuser-defined extractor capabilityを拡張できる。
- 既存ruleは`referrerUrl`を返さなければ従来どおり動作するためdurable migrationは不要である。
- 既定ではfull Refererが必要なstreamは対応外である。path共有の限定的opt-inはADR-0247で定義し、query共有が必要な場合はさらに別判断する。

## Security / privacy invariants

- `referrerUrl`はHTTP(S)以外を利用しない。
- 既定ではMedia3へ送る前にoriginへ縮約する。
- ADR-0247のopt-inでもquery / fragment / userinfoを送らない。
- 実URLをrepository fixture / documentへ保存しない。
- exact stream requestの実Refererが得られた場合は、それを優先する。
- Cookie / Authorization / arbitrary header共有の範囲を拡張しない。

## Verification

- playback extractor resultの`referrerUrl`をHTTP(S) URLとして解決できることをtestする。
- exact request Refererがexplicit `referrerUrl`より優先されることをtestする。
- exact requestがない場合にexplicit `referrerUrl`へfallbackすることをtestする。
- 既定ではresolverでexplicit URLもoriginへ縮約され、path/query/fragmentがMedia3へ渡らないことをtestする。
- ADR-0247のopt-inではexplicit URLのpathだけを保持できることをtestする。
- `referrerUrl`未指定時の既存page-origin fallbackを維持することをtestする。
- Public repository / Architecture / Unit Test / Lint / R8を通す。
