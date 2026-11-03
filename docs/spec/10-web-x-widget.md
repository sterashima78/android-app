# 10. Web、X、Widget、補助機能

- X向けWebView表示とカスタムCSS / JavaScript設定を提供する。
- X WebViewでは表示中ページを手動で再読み込みでき、再読み込み後は保存済みのカスタムCSS / JavaScriptを再適用する。
- X WebView内に戻れるWeb履歴がある場合、システムBackはアプリ共通のBack処理より先にWebView内の履歴を1段戻す。Web履歴がない場合はアプリ共通のBack処理へ委譲する。
- X WebView内の投稿に添付された動画はWeb UIのメディア表示を利用して再生できる。WebViewでmedia viewerのviewport高さが0へ誤解決される場合は、実際のviewport高を使って表示崩れを自動復旧する。
- 共通Web Collectorを利用するWebViewベースのimport機能を持つ。
- 内部に長い縦スクロール領域を持つ編集・閲覧overlayは、コンテンツのスクロールとdismiss gestureが競合しないフルスクリーンmodalで表示する。
- LAN内からアプリ情報へアクセスするためのlocal web server機能を持つ。Android 17 / API 37 targetでは、サーバー起動時にローカルネットワーク権限を要求し、拒否された場合は起動しない。
<!-- formal-requirement
id: LAN-WEB-AUTH-LIFECYCLE-001
models:
  - spec-models/quint/lan_web_auth_lifecycle.qnt
-->
- LAN Web認証はservice起動単位のbootstrap / session tokenを利用し、どちらのtokenも永続化しない。
- bootstrap tokenは一致する最初の1 requestでだけ成功し、その同じ同期処理でsession tokenを生成してbootstrap tokenを消費する。bootstrap成功後にquery tokenを再利用するrequestは、有効なsession Cookieを同時に送っても拒否する。
- 認証済みrequestはtoken queryを持たず、現在のsession tokenと一致するCookieだけを受け入れる。
- LAN IPv4 addressが変わった場合は新しいbootstrap tokenへrotationし、旧bootstrap tokenと既存session tokenを失効する。server停止時も両tokenを失効し、再起動後に旧tokenを受け入れない。
<!-- /formal-requirement -->
- RSS未読やTask等をホーム画面widgetへ表示する。
- 設定の通信量診断では7/30/90日の期間、モバイル/Wi-Fi/全回線を切り替え、通信量の期間合計と機能別・フィード別の集計を確認できる。
- 日別の記録は日本時間（JST、UTC+9）の0時区切りで新しい日付から表示し、各日付を選択するとその日の受信量・送信量・機能別内訳・フィード別取得状況を確認できる。通信量が記録されていない日付は一覧へ表示しない。

## 形式モデル

- [Quint: `lan_web_auth_lifecycle.qnt`](../../spec-models/quint/lan_web_auth_lifecycle.qnt) — LAN Webのone-shot bootstrap、session認証、address rotation、停止・再起動時のcredential失効を検査する。
