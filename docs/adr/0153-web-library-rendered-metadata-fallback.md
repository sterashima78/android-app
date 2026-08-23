# ADR-0153: Web Library metadata は静的 HTTP を優先し WebView を不足時の fallback とする

- Status: Accepted
- Date: 2026-08-23
- Amends: [ADR-0143](0143-web-library-source-and-bookmark-transfer.md)
- Refines: [ADR-0126](0126-android-platform-baseline.md), [ADR-0136](0136-public-repository-content-verification.md)

## Context

ADR-0143 では Web Library の metadata を通常の HTTP response に含まれる Open Graph、Twitter Card、HTML title から取得し、JavaScript 実行後に生成される metadata は対象外とした。

しかし SPA、bot 向け response とブラウザ向け response が異なるサイト、JavaScript 実行後に head metadata を更新するサイトでは、HTTP response だけでは title が host 名 fallback になったり、thumbnail が取得できないことがある。Web Library は URL を直接開く source であり、この欠落は catalog の識別性を大きく下げる。また、既に不完全な metadata で登録済みの Web 蔵書を、後から取得し直す経路も必要である。

一方、任意 URL を WebView で常時読み込むと、追加処理の latency と resource 使用量が増え、WebView の JavaScript 実行面も広がる。既存の静的取得で十分なサイトまで WebView に切り替える必要はない。

## Decision

### 静的 HTTP 取得を primary path のまま維持する

`WebLibraryMetadataClient` による HTTP metadata 取得を最初に実行する。通常追加では、次のいずれかの場合に限り HTTPS URL に対して WebView の rendered metadata fallback を試行する。

1. HTTP metadata 取得自体が失敗した
2. thumbnail URL を得られなかった
3. title が最終 URL の host 名 fallback のままである

HTTP URL では rendered fallback を実行しない。JavaScript を有効化した WebView で cleartext content を扱う経路を追加しないためである。

### 既存 Web 蔵書は明示的に rendered metadata を再取得できる

`WebLibraryMutator.refreshWebBook` を追加し、既存項目の保存済み URL から metadata を取り直す。手動再取得では静的 metadata が一見揃っている場合でも WebView 取得を試し、rendered 側に有効な title / thumbnail / description / authors があれば優先する。rendered 取得に失敗した場合は静的取得結果を利用し、両方失敗した場合は既存項目を書き換えない。

再取得によって redirect 先が変化しても既存 `sourceId` は維持し、Library item identity と series 等の関連付けを壊さない。表示・open 用 `infoUrl` は新しい取得結果を利用できる。

UI では個別再取得と「すべて再取得」を提供する。一括再取得は WebView を複数同時起動せず 1 件ずつ直列に処理し、メモリ使用量を抑える。途中の失敗は残りの再取得を停止させない。

### rendered metadata は Library data layer が所有する

Android WebView を使う `AndroidWebViewLibraryMetadataClient` は Library data module に置き、`WebLibraryRenderedMetadataClient` capability として `DefaultWebLibraryMutator` に注入する。Android lifecycle の所有は app 側に残し、`YomitoriApplication` が `ActivityLifecycleCallbacks` で現在の Activity を弱参照で追跡する。cold-start の共有追加でも利用できるよう `onActivityPreCreated` から追跡し、pause / stop / destroy で参照を解除する。app composition は `() -> Activity?` provider を `AppContainer` から Library runtime へ注入し、data layer 自体は lifecycle を監視しない。実際の `WebView` は Android の要件に合わせてその Activity context で生成する。feature UI や Domain は WebView API を認識しない。

WebView はページ読み込み後に固定の `evaluateJavascript` script で次の DOM metadata だけを読む。

- Open Graph title / description / image
- Twitter Card title / description / image
- HTML document title
- author meta
- 最終 `location.href`

ページ本文、DOM 全体、Cookie、storage、script 実行結果のその他の値は Library に保存しない。

### WebView は fallback 用に制限して短命にする

rendered metadata 用 WebView では次を必須とする。

- JavaScript は metadata が DOM 実行後に生成されるページのため有効化する
- file access と content access は無効化する
- mixed content は許可しない
- Safe Browsing を有効化する
- geolocation は無効化する
- JavaScript による window open / multiple windows を許可しない
- third-party Cookie を受け入れない
- Android System WebView が multi-profile を提供する場合は専用 profile を使う
- native JavaScript bridge は追加しない
- main-frame navigation は HTTPS の標準 port に限定する
- timeout を設け、成功・失敗・cancel のいずれでも WebView を破棄する

`onPageFinished` 直後だけでなく短い settle/retry window を設け、SPA が head metadata を遅延更新する場合を吸収する。ただし background browser として長時間保持はしない。

### 通常追加では静的 metadata を authoritative とする

通常追加で HTTP と WebView の両方から metadata を取得できた場合、既に取得できている静的 metadata を優先する。WebView 結果は欠落している authors、description、thumbnail を補完し、静的 title が host 名 fallback の場合だけ rendered title へ置き換える。

これにより、通常追加では WebView 側の一時的な DOM 状態で安定した OGP metadata を不要に上書きしない。WebView fallback が失敗しても静的取得結果が存在する場合は、その結果で Web Library 追加を継続する。

### 公開リポジトリの情報境界を維持する

実装、test、ADR には実ユーザー URL、Cookie、認証情報、取得した実ページ本文を含めない。test URL は予約済みの `example.com` 等だけを使い、WebView の評価結果は合成 metadata で検証する。

## Consequences

- JavaScript 実行後に title / OGP が設定されるサイトでも Web Library の表示 metadata を取得できる可能性が上がる。
- 通常の OGP ページは従来どおり HTTP 取得だけで完了し、WebView の起動コストを負わない。
- HTTP client が browser 以外を拒否するサイトでも、HTTPS であれば WebView fallback から追加できる可能性がある。
- 不完全な状態で登録済みの Web 蔵書を個別または一括で修復できる。
- 任意 Web content で JavaScript を実行する surface は増えるため、WebView security setting と短い lifecycle が correctness と同等に重要になる。
- ログインが必要なサイトを自動認証する設計ではない。専用 profile は metadata 取得用であり、認証 UI や native bridge を提供しない。
- WebView implementation やサイト側の JavaScript timing により metadata 取得を保証はできないため、最後は従来の title hint / host fallback を維持する。

## Verification

- 静的 metadata が十分な通常追加では rendered fallback を呼ばない unit test を行う。
- thumbnail 欠落と host title fallback を rendered metadata で補完する unit test を行う。
- 明示再取得では静的 metadata が揃っていても rendered metadata を優先する unit test を行う。
- HTTP fetch failure から rendered fallback へ移行できることを unit test する。
- rendered fallback failure 時に取得済み静的 metadata を失わないことを unit test する。
- rendered metadata の相対 image URL 解決と HTTPS image 制約を unit test する。
- notifier decorator が `refreshWebBook` を delegate し、backup change を通知することを unit test する。
- architecture verification、public repository verification、Library data unit test、app unit test、lint、assemble を CI で実行する。
