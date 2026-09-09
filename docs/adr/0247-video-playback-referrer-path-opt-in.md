# ADR-0247: Web動画再生でReferer path共有をルール単位でopt-inする

- Status: Accepted
- Date: 2026-09-09
- Amends: [ADR-0245](0245-video-playback-explicit-referrer-origin.md)
- Coexists with: [ADR-0241](0241-video-web-stream-cookie-opt-in.md), [ADR-0243](0243-video-web-request-cookie-capture.md)

## Context

ADR-0245では、playback extractorが`referrerUrl`を返せるようにし、WebViewでexact stream requestを観測できない場合でも再生元originをMedia3へ渡せるようにした。ただしprivacy boundaryを広げないため、`Referer`は常にorigin rootへ縮約し、path / query / fragmentは送らない設計とした。

実機診断では、WebViewのexact stream requestは未観測、Cookie共有はprofile fallback、playback extractorのexplicit `referrerUrl`は採用され、Media3へ`Referer` / `Origin`も付与されているにもかかわらずHTTP 403となるケースが確認された。

この状態では、配信側が埋め込みplayerのpathまで参照元として検証している可能性が残る。一方、全Web動画でpathを自動送信するとADR-0245で維持したprivacy boundaryを不要に広げる。

## Decision

### Web抽出ルールへReferer path共有の明示opt-inを追加する

`WebVideoExtractorRule`へ`shareReferrerPathForPlayback`を追加する。既定値はfalseとする。

この設定はplayback extractorが返したexplicit `referrerUrl`が再生参照元として採用された場合だけ有効にする。

- OFF: ADR-0245どおりexplicit `referrerUrl`もorigin rootだけを`Referer`へ送る。
- ON: explicit `referrerUrl`のscheme / host / optional port / pathを`Referer`へ送る。
- WebViewでexact stream requestを観測した場合: 従来どおり観測済みReferer originを利用し、path共有設定で拡張しない。
- 元page URLへのfallback: 従来どおりoriginだけを利用し、path共有設定で拡張しない。

設定は`video_web_extractor_rules`のdurable booleanとして保存するが、実際の参照元URLやheader値は保存しない。

### query / fragment / userinfoは共有しない

ONの場合でも共有範囲はpathまでに限定する。

- `Referer`: scheme / host / optional port / path
- `Origin`: scheme / host / optional portのみ
- query: 除去
- fragment: 除去
- userinfo: 除去

空pathは`/`へ正規化する。

### 既存の参照元優先順位は変更しない

参照元URLの選択はADR-0245のままとする。

1. WebViewでexact stream requestを観測した場合、その実request Referer origin
2. playback extractorの`referrerUrl`
3. 元page URLのorigin

`shareReferrerPathForPlayback`は2のexplicit `referrerUrl`だけについて、Media3向け`Referer`へpathを残すかを制御する。

### Cookie共有とは独立させる

Cookie共有opt-inとは別設定とする。

- Cookie共有OFFでもReferer path共有をONにできる。
- Referer path共有OFFでもCookie共有をONにできる。
- `Authorization`や任意header共有へ一般化しない。

### schemaはadditive refinementとする

`video_web_extractor_rules`へ`share_referrer_path_for_playback INTEGER NOT NULL DEFAULT 0`を追加する。

fresh schemaはcolumnを最初から持つ。既存databaseはVideoのidempotent schema initializerが不足列だけを追加する。既存ruleはdefault 0によりOFFへ収束する。この追加だけを理由にapplication database versionは進めない。

## Consequences

- 埋め込みplayerのpathをRefererとして要求するstreamを、個別site固有コードをproduction実装へ追加せずルール単位で試せる。
- 既存ruleはOFFのため現在のorigin-only挙動を維持する。
- path共有はuser-defined extractorが明示した再生元だけに限定され、元pageや別requestから推測してprivacy boundaryを広げない。
- query token等をRefererへ送ることは引き続きできない。query共有が必要と確認された場合は別のprivacy boundary判断とする。
- 実参照元URLはtransientのままで、backup / export / log / error UIへ保存しない。

## Security / privacy invariants

- Referer path共有はruleで明示opt-inされ、かつplayback extractorのexplicit `referrerUrl`が採用されたforeground Web playbackだけで有効にする。
- 既定値はOFF、既存ruleを自動的にONへしない。
- HTTP(S)以外の参照元を利用しない。
- query / fragment / userinfoをMedia3へ送らない。
- `Origin`は常にorigin-onlyを維持する。
- WebView実request由来Refererと元page fallbackをpath共有設定で拡張しない。
- Cookie共有条件を変更しない。
- `Authorization` / arbitrary header共有へ一般化しない。
- 実URLやuser-authored extractor codeをpublic repositoryのfixture/documentへ保存しない。

## Verification

- OFFではexplicit参照元をorigin rootへ縮約することをunit testする。
- ONではexplicit参照元のpathを保持し、query / fragment / userinfoを除去することをunit testする。
- port付きURLをunit testする。
- `Origin`はON/OFFにかかわらずorigin-onlyであることをunit testする。
- repositoryでbooleanを保存・復元できることをtestする。
- 既存schemaへcolumnをadditiveに追加しdefault OFFになることをtestする。
- UIで既定OFF、編集時の復元、保存を確認する。
- Public repository / Architecture / Test / Lint / R8を通す。
