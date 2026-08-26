# ADR-0179: Web Library の表紙表示に origin Referer を使い foreground 不在時は取得を待機する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0154](0154-web-library-rendered-metadata-fallback.md), [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md), [ADR-0178](0178-web-library-custom-extractor-native-watchdog.md)

## Context

custom metadata extractor から `thumbnailUrl` を取得して保存できても、Library 設定の metadata 再取得結果は URL を診断文字列として表示するだけで、取得した表紙そのものを確認できなかった。

また Web の画像配信元には、通常のブラウザと同様に参照元を要求するものがある。Library の既存 `AsyncImage` は thumbnail URL だけを直接取得するため、metadata として URL が正しく取得・保存されていても画像ロードだけが失敗し得る。

一方、一括 metadata 再取得は foreground Activity を必要とする専用 WebView を逐次利用する。Application の Activity provider は resumed Activity だけを返すため、ユーザーが処理中に一時的にアプリを background へ移すと、次の項目が `WebView metadata を取得できる画面がありません` として即時 fallback し、多数の warning が発生することがあった。これはページ固有の metadata 取得失敗ではない。

## Decision

### 設定画面で保存済み thumbnail を preview する

Web Library の設定画面にある各蔵書カードでは、保存済み `thumbnailUrl` がある場合に表紙 preview を表示する。

画像ロードに失敗した場合は URL が存在することと画像取得が失敗したことを区別できるよう、preview の近くに `表紙画像を読み込めませんでした` と表示する。metadata extractor の診断文字列は従来どおり保持する。

### Web Library の画像だけ page origin を Referer として送る

Web Library の thumbnail を Coil で取得する際は、book の `infoUrl`、なければ `sourceId` から HTTP(S) origin を生成し、`Referer` request header として付与できる image request を使用する。

privacy のため Referer に page path、query、fragment、userinfo は含めない。例えば repository/test/docs では `https://example.com/books/1?token=value` に対して `https://example.com/` だけを送る例を用いる。

非 Web source の画像ロードは変更しない。native bridge、Cookie 共有、WebView profile の外部公開は行わない。

### foreground Activity がない場合は Web Library mutation を失敗させず待機する

app composition で利用する Web Library mutator を Library Data 所有の foreground gate で包む。

`addWebBook`、`refreshWebBook`、`refreshWebBookWithReport` の開始時に resumed Activity が利用可能か確認し、利用できない間は coroutine を短い間隔で suspend する。Activity が戻れば同じ呼び出しを続行する。

これにより一括再取得中にアプリが background へ移っても、その後の各 item を `WebView metadata を取得できる画面がありません` として静的 metadata fallback に進めず、foreground 復帰後に続きから処理できる。

この待機時間は ADR-0177 の rule 別 WebView timeout に含めない。rule timeout は Activity が利用可能になり、実際の page navigation / DOM / metadata pipeline を開始してからの上限として扱う。

remove operation は WebView を必要としないため foreground gate の対象外とする。

### durable background job には変更しない

一括再取得は引き続き Library 設定画面から開始する foreground 操作であり、durable job state にはしない。route/composition が破棄されて coroutine が cancel された場合は処理も終了する。

設定画面には、アプリが前面にない間は WebView 取得を待機し、前面復帰後に再開することを明示する。

## Consequences

- custom extractor で取得した thumbnail が実際に表示可能か設定画面から確認できる。
- hotlink protection 等で page origin を要求する画像配信元でも、通常ブラウザに近い条件で thumbnail を取得できる。
- Referer は origin のみに制限されるため、Web Library の具体的な page path/query を画像配信元へ追加送信しない。
- background へ移ったことだけを理由に大量の WebView fallback warning を生成しなくなる。
- background 中は一括再取得の進捗が止まり、foreground 復帰後に再開する。
- WebView security boundary、custom function contract、rule 別 timeout、Promise 10 秒上限は変更しない。

## Verification

- page URL から生成する Referer が origin のみで path/query/fragment を除去する unit test を追加する。
- 非標準 port を必要に応じて Referer に保持し、HTTP(S) 以外を拒否する unit test を追加する。
- foreground availability が false の間は待機し、true になった時点で処理を続行する unit test を追加する。
- metadata 再取得設定画面で保存済み thumbnail preview と画像ロード失敗表示を code review / CI で確認する。
- PR 前に public repository、architecture、test scope、documentation の独立レビューを行う。
