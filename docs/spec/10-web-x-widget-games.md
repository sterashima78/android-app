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
- 暴走炉は縦持ちのインクリメンタルゲームとし、タップによる直接獲得と、複数種類の設備によるエネルギーの直接生産を提供する。設備同士が別の設備を生成する多段生産は行わない。各設備は、タップ報酬への寄与、暴走中の追加出力、強い購入マイルストーン、他種類の設備保有による出力上昇など異なる固有性を持つ。25タップで6秒間の自動生産×20暴走を発動し、低確率のタップ報酬×50ジャックポットを持つ。1周の途中では周回累計の節目ごとに複数候補から1つの周回効果を選択し、選択中は進行を停止する。候補には汎用的なルール変更に加えて進行段階に対応する設備固有の効果を含めるが、ユーザー向けには系統や推奨ビルドを表示せず、効果説明と組み合わせから使い方を発見できるようにする。選択済みの周回効果はPrestigeで失う。Prestigeでは周回生産量に応じて逓減的にコアを獲得し、コア自体には自動倍率を持たせない。コアは出力、設備固有効果、タップ、暴走時間等の恒久アップグレードから選択して消費し、一定数の恒久強化後に開始設備や設備マイルストーン短縮等の一回限り強化を解禁する。初期実装では進行状態を永続化しない。
- 数独は Godot Engine を既存 Android アプリへ組み込んだ正式実装とする。盤面を大きく表示し、編集可能なマスを選ぶとその近くに数字入力パネルを表示する。入力途中では正解・不正解を表示せず、全マス入力後にだけ完成判定する。進行状態は永続化しない。
- クロンダイクは同じ Godot runtime 上の専用 scene で実装し、横向きの盤面優先表示とする。山札は1枚めくり、捨て札は回数制限なく再利用でき、タップで選択したカードから合法な場札・組札を強調する。場札の移動で露出した伏せ札は自動で表向きにし、進行状態は永続化しない。


## 形式モデル

- [Quint: `lan_web_auth_lifecycle.qnt`](../../spec-models/quint/lan_web_auth_lifecycle.qnt) — LAN Webのone-shot bootstrap、session認証、address rotation、停止・再起動時のcredential失効を検査する。
