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
- Gameでは数独、2048、ノノグラム、マインスイーパー、クロンダイク、スパイダーソリティア、暴走炉等の端末内ゲームを提供する。
- 暴走炉は縦持ちのインクリメンタルゲームとし、タップによる直接獲得、自動生産、上位設備から下位設備への多段生産、20タップで発動する10秒間の生産×1,000暴走、低確率のタップ報酬×10,000ジャックポット、設備の一括購入、Prestigeによる周回リセットと永久生産倍率を提供する。設備は25回購入ごとにその設備の生産を×10する。初期実装では進行状態を永続化しない。
- 数独は Godot Engine を既存 Android アプリへ組み込んだ正式実装とする。盤面を大きく表示し、編集可能なマスを選ぶとその近くに数字入力パネルを表示する。入力途中では正解・不正解を表示せず、全マス入力後にだけ完成判定する。進行状態は永続化しない。
- クロンダイクは同じ Godot runtime 上の専用 scene で実装し、横向きの盤面優先表示とする。山札は1枚めくり、捨て札は回数制限なく再利用でき、タップで選択したカードから合法な場札・組札を強調する。場札の移動で露出した伏せ札は自動で表向きにし、進行状態は永続化しない。


## 形式モデル

- [Quint: `lan_web_auth_lifecycle.qnt`](../../spec-models/quint/lan_web_auth_lifecycle.qnt) — LAN Webのone-shot bootstrap、session認証、address rotation、停止・再起動時のcredential失効を検査する。
