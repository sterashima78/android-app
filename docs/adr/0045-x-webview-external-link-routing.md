# ADR-0045: X WebView の外部リンクはシステムブラウザーへ委譲する

- Status: Accepted
- Date: 2026-08-14

## Context

ADR-0107 では X を専用 WebView で表示し、`x.com` / `twitter.com` 配下だけを CSS 注入対象としている。

一方、X の投稿に含まれる外部サイトへのリンクまで同じ WebView 内で開くと、X 閲覧用に限定した WebView が一般 Web ブラウザーとして振る舞うことになる。戻る導線や閲覧コンテキストも X と外部サイトで混在する。

X の外部リンクでは `t.co` の短縮 URL を経由することがあるため、`t.co` を X 内ドメインとして扱うと外部サイトが WebView 内に残る可能性がある。

## Decision

X 専用 WebView のトップレベル HTTP/HTTPS ナビゲーションを次のように扱う。

- `x.com` とそのサブドメインは WebView 内で開く
- `twitter.com` とそのサブドメインは互換性のため WebView 内で開く
- それ以外の HTTP/HTTPS URL は `Intent.ACTION_VIEW` で Android の外部ハンドラーへ委譲する
- `t.co` は外部 URL として扱い、システム側でリダイレクトを解決させる
- iframe などメインフレーム以外の遷移は外部アプリ起動の対象にしない
- `about:` や `javascript:` など HTTP/HTTPS 以外の URL はこの外部リンクルーティングの対象にしない
- 外部ハンドラーを起動できない場合は WebView 内へフォールバックせず、ユーザーへエラーを通知する

ドメイン判定は完全一致またはサブドメイン一致で行い、`x.com.example.com` や `twitter.com.example.com` のような類似ドメインを X 内として扱わない。

## Consequences

### Positive

- X 閲覧用 WebView の責務を X の表示に限定できる
- 外部記事は通常のブラウザー環境で開かれる
- `t.co` 経由の外部リンクも WebView 内へ残りにくい
- iframe 読み込みで意図せずブラウザーが起動することを避けられる
- 類似ドメインを X 内として誤判定しない

### Negative

- 外部リンクを開くとアプリ外へ画面遷移する
- 外部ハンドラーが存在しない端末ではリンクを開けない
- X の認証フローが将来 X 以外のトップレベル HTTP/HTTPS ドメインを必須にした場合、許可ドメインの再検討が必要になる
