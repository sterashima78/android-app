# ADR-0241: Web動画のCookie共有を抽出ルール単位の明示opt-inにする

- Status: Accepted
- Date: 2026-09-08
- Amends: [ADR-0237](0237-video-library-and-web-extraction.md)

## Context

Video Context のWeb動画は、専用profileのWebViewでuser-defined extractorを実行して一時的なstream URLを解決し、Media3でforeground再生する。

既存実装はWeb page origin由来の `Referer` / `Origin` とWebView相当の `User-Agent` をMedia3へ渡す一方、WebView Cookie / Authorization等のcredentialはMedia3へ渡さない境界としていた。

一部のWeb streamでは、ブラウザ側のsession Cookieがなければ配信側からHTTP 403等で拒否される。この互換性を改善する必要があるが、Cookieはcredentialになり得るため、すべてのWeb抽出ルールへ暗黙に共有してはならない。また、Cookie文字列をdurable stateやlogへ残したり、固定headerとして無関係なrequest先へ送ることも避ける必要がある。

## Decision

### Web抽出ルールにCookie共有の明示設定を追加する

`WebVideoExtractorRule` に再生時Cookie共有のboolean設定を追加する。

- 既定値は `false`。
- 既存ruleはschema refinement時に `false` として扱う。
- UIではrule編集時にcheckboxで明示的に変更する。
- 設定値自体はVideo-owned durable user dataとして `video_web_extractor_rules` に保存し、通常backup対象とする。

Cookie値そのものはdurable stateへ保存しない。

### Cookieは再生時だけ専用WebView profileから参照する

Cookie共有が有効なruleでWeb streamを再生する場合だけ、extractorが使用した専用WebView profileのCookieManagerをtransient playback capabilityとしてMedia3側へ渡す。

- default WebView profileの `CookieManager.getInstance()` は使わない。
- Cookie文字列を `VideoPlaybackTarget` の通常data fieldとして保持しない。
- Cookie値をdatabase、SharedPreferences、backup、export、log、error messageへ保存・表示しない。
- `Authorization` や任意headerの共有へ一般化しない。
- ruleがOFFの場合はCookie provider自体を再生targetへ渡さない。

### Media3 requestごとにCookieを解決する

Cookie共有が有効な再生では、Video UIが所有するHTTP DataSourceがrequest URLごとにjust-in-timeでCookieを取得する。

固定Cookie headerをmanifest / segment / redirect先のすべてへ一律設定せず、WebView profileのCookieManagerへそのrequest URLのCookie選択を委譲する。HTTP redirectはDataSource側で自動追従させず、各redirect先URLについてCookieを改めて解決してから次のrequestを送る。Cookie共有経路ではHTTPとHTTPSをまたぐcross-protocol redirectを許可しない。

これによりhost / path / Secure等の通常Cookie選択をWebView側へ維持し、redirect先を含む無関係なrequest先へ同じCookie文字列を複製することを避ける。

既存の `Referer` / `Origin` / `User-Agent` のtransient request contextは維持する。Cookie共有がOFFの場合は既存のHTTP DataSourceをそのまま利用する。

### schemaはadditive refinementとする

`video_web_extractor_rules` に `share_cookies_for_playback INTEGER NOT NULL DEFAULT 0` を追加する。

fresh databaseは `CREATE TABLE` でcolumnを持つ。既存databaseはVideoのidempotent schema initializerがcolumnの有無を確認し、不足時だけ `ALTER TABLE ... ADD COLUMN` する。

application database versionは30のままとする。既存ruleはdefault 0でCookie共有OFFとなり、version 30の既存snapshotを復元した場合もschema initializerで同じdefaultへ収束できる。

## Consequences

- session Cookieを要求するWeb streamを、ユーザーがrule単位で明示的に許可した場合だけMedia3で再生できる余地が増える。
- credential boundaryはVideo extractor WebViewからVideo foreground playbackへ限定的に拡張される。
- Cookie値の新しいdurable source of truthは増えない。
- rule設定はbackup対象なので、Cookie共有を許可したというユーザー設定は端末移行後も維持される。ただしCookie値自体はbackupされないため、必要なsessionは再度WebView側で成立する必要がある。
- Cookie共有ONの再生ではredirectを手動処理するため、redirect先ごとにCookie policyを再評価できる一方、cross-protocol redirectは従来の非Cookie再生より厳しく拒否する。
- partitioned Cookie等でWebViewの通常 `getCookie` semanticsだけでは再現できない場合は、request interception等の別設計を追加判断する。今回それを自動的に有効化しない。

## Security invariants

- Cookie共有はruleごとの明示opt-inでのみ有効にする。
- defaultはOFFとし、既存ruleを自動的にONへ移行しない。
- Cookie値をdurable state / log / error UIへ保存・表示しない。
- redirect先ではCookieを元requestから転送せず、そのURLに対して専用WebView profileから再解決する。
- Cookie共有時のcross-protocol redirectを許可しない。
- default WebView profileのCookieをVideo extractorへ混ぜない。
- `Authorization` 等へcredential共有を一般化しない。
- user-authored extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- fresh schemaと既存schema refinementで `share_cookies_for_playback` が存在し、既存rowがOFFになることをtestする。
- repositoryでruleのCookie共有booleanを保存・復元できることをtestする。
- ruleがOFFの場合にplayback cookie providerを作らないことをtestする。
- ruleがONの場合だけ専用WebView profile由来のproviderをplayback targetへ渡すことをtestする。
- playback HTTP requestとredirect先URLごとにCookie providerが評価されることをunit testする。
- Cookie値をfixture、log、documentへ含めずpublic repository verificationを通す。
- Android実機でOFF時は従来動作、ON時はCookieが必要なWeb streamの再生可否を確認する。
