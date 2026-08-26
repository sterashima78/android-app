# ADR-0179: Web Library の表紙 preview にブラウザ互換ヘッダーを使い foreground 不在時は取得を待機する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0154](0154-web-library-rendered-metadata-fallback.md), [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md), [ADR-0178](0178-web-library-custom-extractor-native-watchdog.md)

## Context

custom metadata extractor から `thumbnailUrl` を取得して保存できても、Library 設定の metadata 再取得結果は URL を診断文字列として表示するだけで、取得した表紙そのものを確認できなかった。

また Web の画像配信元には、通常のブラウザと同様に参照元やブラウザの User-Agent を要求するものがある。設定画面に preview を追加する場合、thumbnail URL だけを直接取得すると、metadata として URL が正しく取得・保存されていても画像ロードだけが失敗し得る。

一方、一括 metadata 再取得は foreground Activity を必要とする専用 WebView を逐次利用する。Application の Activity provider は resumed Activity だけを返すため、ユーザーが処理中に一時的にアプリを background へ移すと、次の rendered metadata 取得が `WebView metadata を取得できる画面がありません` として即時 fallback し、多数の warning が発生することがあった。これはページ固有の metadata 取得失敗ではない。

## Decision

### 設定画面で保存済み thumbnail を preview する

Web Library の設定画面にある各蔵書カードでは、保存済み `thumbnailUrl` がある場合に小さい表紙 preview を表示する。大量の Web 蔵書を一覧表示しても decoded image memory が過剰になりにくいよう、診断用途の固定サイズに抑える。

画像ロードに失敗した場合は URL が存在することと画像取得が失敗したことを区別できるよう、preview の近くに `表紙画像を読み込めませんでした` と表示する。metadata extractor の診断文字列は従来どおり保持する。

### 設定画面の Web Library preview は汎用的なブラウザ互換ヘッダーを送る

設定画面で Web Library の thumbnail を Coil で取得する際は、book の `infoUrl`、なければ `sourceId` から HTTP(S) origin を生成して `Referer` request header として付与する。加えて、Android WebView が提供する標準 User-Agent を `User-Agent` request header として付与する。

privacy のため Referer に page path、query、fragment、userinfo は含めない。例えば repository/test/docs では `https://example.com/books/1?token=value` に対して `https://example.com/` だけを送る例を用いる。

User-Agent は特定サイトや特定ブラウザバージョンを source code に固定せず、端末の `WebSettings.getDefaultUserAgent` から取得する。取得できない場合は User-Agent を省略する。画像配信元の host による分岐、サイト固有の request header、Cookie 共有、個別サイト向け fallback は追加しない。

この判断では既存の通常蔵書グリッドや非 Web source の画像ロードは変更しない。native bridge や WebView profile の外部公開も行わない。設定画面の preview で確認した結果を踏まえ、通常蔵書グリッドにも同じ request policy が必要なら別途適用範囲を広げる。

### foreground Activity がない場合は rendered metadata 取得を失敗させず待機する

app composition では `AndroidWebViewLibraryMetadataClient` を Library Data 所有の foreground gate client で包む。静的 HTTP metadata 取得は従来どおり foreground Activity を必要としない。

rendered metadata client の `fetch` / `fetchWithReport` が実際に呼ばれた時点で resumed Activity が利用可能か確認し、利用できない間は coroutine を短い間隔で suspend する。Activity が戻れば delegate の WebView 取得を開始する。`hasCustomExtractor` は待機せず delegate へそのまま委譲する。

これにより一括再取得中にアプリが background へ移っても、その後の各 rendered item を `WebView metadata を取得できる画面がありません` として静的 metadata fallback に進めず、foreground 復帰後に続きから処理できる。同時に、WebView が不要な静的 HTTP 取得まで background 中に止めることは避ける。

この待機時間は ADR-0177 の rule 別 WebView timeout に含めない。rule timeout は Activity が利用可能になり、delegate の実際の page navigation / DOM / metadata pipeline を開始してからの上限として扱う。

### durable background job には変更しない

一括再取得は引き続き Library 設定画面から開始する foreground 操作であり、durable job state にはしない。route/composition が破棄されて coroutine が cancel された場合は処理も終了する。

設定画面には、アプリが前面にない間は WebView 取得を待機し、前面復帰後に再開することを明示する。

## Consequences

- custom extractor で取得した thumbnail が実際に表示可能か設定画面から確認できる。
- Referer やブラウザ User-Agent を要求する画像配信元でも、設定画面 preview を通常ブラウザに近い条件で取得できる。
- Referer は origin のみに制限されるため、Web Library の具体的な page path/query を画像配信元へ追加送信しない。
- User-Agent は端末の WebView から取得するため、サイト固有条件や固定ブラウザバージョンの保守をアプリ本体へ持ち込まない。
- background へ移ったことだけを理由に大量の WebView fallback warning を生成しなくなる。
- background 中でも静的 HTTP 取得は可能で、WebView が必要になった地点だけ待機する。
- WebView security boundary、custom function contract、rule 別 timeout、Promise 10 秒上限は変更しない。

## Verification

- page URL から生成する Referer が origin のみで path/query/fragment を除去する unit test を追加する。
- 非標準 port を必要に応じて Referer に保持し、HTTP(S) 以外を拒否する unit test を追加する。
- 汎用 request header 生成が origin Referer と与えられた browser User-Agent を含むことを `example.com` と架空 User-Agent だけで unit test する。
- Referer を生成できない場合も User-Agent を単独で使用でき、空の User-Agent は送信しないことを unit test する。
- foreground availability が false の間は待機し、true になった時点で処理を続行する unit test を追加する。
- foreground gate client の `hasCustomExtractor` が delegate へ委譲されることを unit test する。
- metadata 再取得設定画面で保存済み thumbnail preview と画像ロード失敗表示を code review / CI で確認する。
- PR 前に public repository、architecture、test scope、documentation の独立レビューを行う。
