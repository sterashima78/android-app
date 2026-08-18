# ADR-0094: 認証済み Web ページからのデータ取得を core:web-collector に分離する

- Status: Accepted
- Date: 2026-08-18

## Context

Amazon 系蔵書インポートでは ADR-0061 により、専用 WebView profile、許可 origin に限定した Web Message、`addJavascriptInterface` の禁止、Cookie/CSRF/セッション情報をネイティブへ渡さない境界を採用している。

MoneyForward ME から資産情報を取得する機能でも同じ種類の安全境界が必要になる。サイト固有 feature ごとに WebView の認証・ナビゲーション・bridge・サイズ検証を複製すると、セキュリティ設定の差異が生じやすい。一方、DOM selector や収集結果のデータ契約はサイト固有であり core が所有すべきではない。

## Decision

認証済み Web ページから collector script を実行し、明示的な結果だけをネイティブへ渡す横断 capability を `:core:web-collector` として分離する。

core が所有するもの:

- AndroidX WebKit Multi Profile を使った専用 profile
- HTTPS navigation の host allowlist
- Web Message Listener の origin allowlist
- `addJavascriptInterface` を使わない bridge
- file/content access、mixed content、複数 window の禁止
- collector result の最大サイズ検証
- direct result と chunked result の受信
- WebView の lifecycle と破棄

利用 feature が所有するもの:

- start URL
- profile 名
- navigation host / bridge origin
- collector を実行できる URL
- collector JavaScript
- 収集結果の JSON schema と検証・永続化

MoneyForward は最初の利用者として `feature:asset:ui` からこの capability を利用する。Amazon 系は既存 ADR-0061 の安全境界を維持したまま、別変更で共通基盤へ移行する。今回の変更では Amazon collector の挙動を変更しない。

## Public repository safety

profile 名、許可 host、collector の DOM 解析コードだけを source に置く。実ユーザーの Cookie、ログイン情報、資産名、口座名、残高、収集 JSON を source、fixture、ログ、ADR、PR 説明へ追加しない。

## Consequences

- WebView 由来データ取得のセキュリティ境界を一箇所で保守できる。
- サイト固有 DOM 変更は利用 feature の collector だけで対応できる。
- 新しい collector を追加するときに認証情報をネイティブ層へ露出しにくくなる。
- Android System WebView が Multi Profile / Web Message Listener に非対応の場合は利用できない。

## Relationship to existing ADRs

- ADR-0004 の「core は横断 capability を所有する」に従う。
- ADR-0061 の Amazon WebView セキュリティ判断を一般化するが、Amazon 実装自体はまだ置き換えない。
- ADR-0055 に従い新規番号 0094 を使用する。
