# ADR-0246: 動画購読Providerでユーザー定義コードを実行できるようにする

- Status: Accepted
- Date: 2026-09-09
- Refines: [ADR-0242](0242-video-subscription-providers.md)
- Related: [ADR-0241](0241-video-web-stream-cookie-opt-in.md), [ADR-0243](0243-video-web-request-cookie-capture.md)

## Context

Video Context は購読型Providerの設定、subscription、取得済みitem、未読状態、refresh lifecycleを共通所有している。ADR-0242ではProvider implementationを組み込みadapterとして扱い、ユーザー設定へ任意コードや汎用DSLを保存しない方針を採用した。

しかし、外部動画サービスごとにsubscription入力、endpoint解決、複数request、response parsing、item mappingの形が異なり、宣言的な設定だけでは対応不能なサービスが生じる。組み込みadapterだけで対応すると、新しいサービスを利用するたびにapplication releaseが必要になる。

今回の要求では、ユーザーがVideo設定から独自Providerを追加し、そのProvider固有の取得処理を任意コードで記述できる必要がある。一方で、subscription / unread / watch-later / saved / playback / background refreshの共通lifecycleはProviderコードへ移さず、Videoが引き続き所有する必要がある。

## Decision

### ユーザー定義ProviderをVideo-owned durable configurationとして保存する

`VideoProvider` に custom type と function code を追加する。組み込みProviderは従来どおりcode-level adapterを利用し、custom Providerだけが保存済みfunction codeを実行する。

custom Providerは複数登録できる。Provider identityは個々の設定ごとに一意とし、typeだけを一意制約にはしない。

### function contract

custom Provider functionはJavaScript function expressionとし、次の概念的contractを持つ。

```text
async (input, api) => ProviderFeed
```

`input` は次を含む。

- `mode`: `subscribe` または `refresh`
- `sourceUrl`: subscribe時にユーザーが入力した値
- `sourceId`: refresh時に保存済みsubscriptionから渡す値

`api` はbounded HTTP request capabilityだけを公開する。

```text
api.fetch({
  url,
  method?,
  headers?,
  body?,
  contentType?
}) -> {
  status,
  ok,
  url,
  headers,
  body
}
```

`api.fetch` はPromiseを返す。複数requestが同時に開始された場合もhost側でrequest IDとqueueを保持し、responseを対応するPromiseへ返す。

戻り値は共通の `VideoProviderFeed` へ正規化できるobjectとする。

```text
{
  sourceId,
  title,
  sourceUrl,
  videos: [
    {
      id,
      title,
      url,
      thumbnailUrl?,
      publishedAtEpochMillis
    }
  ]
}
```

Providerコードはsubscription row、video row、未読状態、保存状態、再生状態を直接操作できない。これらは戻り値を受け取った `VideoProviderRepository` / Video Data が従来どおり更新する。

### background-safeな隔離実行環境を使う

custom Providerはforeground Activityに依存しない実行環境で動かす。これにより統合background refreshからも同じProvider functionを利用できる。

JavaScript runtimeにはアプリのContext、database、他Contextのrepository、filesystem、Android objectを公開しない。direct browser navigationをProvider APIとして扱わず、外部通信はhostが検証する `api.fetch` 経由を正規経路とする。

専用WebView profileではCookieを受け入れず、WebView自身のnetwork loadを無効化する。rendererが終了した場合は終了済みWebViewを再利用せず、当該Provider実行を失敗として局所化する。

1回のProvider実行にはtimeout、request回数、response sizeの上限を設ける。取消はcoroutine cancellationへ追従する。

### HTTP境界

custom ProviderのHTTP requestはHTTPSだけを許可する。request method、通常header、text bodyはProvider codeから指定できるが、credentialを暗黙に注入しない。

既存のWebView Cookie、mail credential、SMB credential、cloud token、その他Contextのsecretをcustom Providerへ渡さない。`Authorization`、`Cookie`、`Proxy-Authorization` 等のcredential headerをProvider設定へ埋め込む用途も初期scopeには含めない。

認証付きProviderが必要になった場合は、secretを通常のProvider function codeから分離して保護保存し、明示的なcredential capabilityとして別判断する。

### Provider codeはtrusted user configurationだが、失敗を局所化する

Provider codeはユーザー自身が登録するtrusted configurationとして扱う。ただしsyntax error、runtime error、不正な戻り値、HTTP失敗、timeout、request上限超過、renderer終了は当該subscriptionのrefresh失敗として扱い、他Provider / subscriptionの更新を妨げない。

Provider codeやHTTP response bodyをlogへ出力しない。error UIにはsecretを含み得るresponse本文やrequest headerを表示しない。

## Consequences

### Positive

- application releaseなしで新しい動画サービスへ対応できる。
- service固有の複数requestやresponse変換を任意コードで記述できる。
- subscription / unread / refresh / playback / saved stateは既存Video lifecycleへ収束したまま維持できる。
- Providerコードが他Contextのstateへ直接アクセスしないため、ownershipを維持できる。

### Negative

- arbitrary code executionという新しいtrust boundaryが増える。
- Provider function codeがdurable user dataになるためdatabase migrationとbackup version更新が必要になる。
- 不正なcodeによるCPU時間、request数、response sizeをboundedにするruntimeが必要になる。
- 初期実装ではcredentialをProvider codeへ渡さないため、認証必須サービスは対象外になる場合がある。

## Compatibility

- application database versionを32へ進める。
- 既存組み込みProvider rowはfunction codeなしでそのまま維持する。
- version 31 -> 32 migrationでは `video_providers` の既存row、subscription、provider itemを保持したまま `function_code` を追加する。
- backup restoreのexact-version policyは変更しない。
- custom Providerを削除した場合のitem retentionはADR-0242の既存ruleを再利用する。

## Verification

- custom Providerを複数保存でき、組み込みProviderの重複は引き続き拒否されることをtestする。
- subscribe / refresh inputとProviderFeed戻り値のvalidationをtestする。
- 複数HTTP requestを含むfunction executionをtestする。
- `api.fetch` がProvider側の通常のPromise / exception handlingと競合せず、並行requestのresponse対応を維持することをtest対象とする。
- HTTPS制約、credential header拒否、request回数、response size、timeoutをtestする。
- renderer終了を失敗として処理し、終了済みWebViewを継続利用しないことを確認する。
- Provider function失敗が他subscription refreshを妨げないことをtestする。
- existing read / watch-later / saved / playback stateがcustom Provider refreshでも保持されることをtestする。
- database migrationで既存Provider状態が保持されることをtestする。
- background refreshからActivityなしでcustom Providerを実行できることを確認する。

## Documentation

- `docs/spec.md`
- `docs/architecture/video.md`
- `docs/architecture/persistence.md`
- ADR-0242 relationship / decision refinement
