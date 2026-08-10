# ADR-0008: X は WebView とローカル CSS 注入で表示する

- Status: Accepted
- Date: 2026-08-09

## Context

X の閲覧では独自クライアントを構築するのではなく、x.com が提供する Web UI を利用しつつ、不要な表示要素を CSS で非表示にしたい。

初期要件は表示上の調整だけであり、X API や独自タイムライン取得は必要ない。一方、カスタム CSS を手書きするだけでは X の DOM 構造や selector を利用者が調査する必要があり、モバイル端末上では調整が難しい。このため、ユーザーが WebView 上の要素をタップして非表示ルールを作れる補助機能を追加する。

ADR-0003 では feature ごとに必要な layer だけを Gradle module として作り、小さな feature に不要な domain/data module を設けない方針としている。

初期実装では Android WebView の標準 User-Agent と Cookie policy のまま x.com を開いていたが、ログイン画面が正常に描画されないケースが確認された。Android WebView は通常のモバイルブラウザーとは異なる User-Agent トークンを含み、modern target SDK の WebView では third-party cookie が既定で無効になるため、外部サービスのログインフローとの互換性を明示的に扱う必要がある。

ログイン画面上部が欠ける問題については、WebView の clip、viewport 初期化、アプリ共通 TopAppBar の除去まで試したが、実機ではフォーム上部が欠ける状態が継続した。このため、X 側へ注入している独自 CSS が影響している可能性も切り分けられるよう、CSS をユーザーが無効化・編集できる必要が生じた。

X は外部 Web アプリであり DOM は随時変更され得る。CSS をアプリ更新なしで調整できることは、障害切り分けと表示調整の双方に有効である。

X WebView を全高表示した状態では、縦スクロールに小さな横方向成分が含まれるだけでも、外側の Compose ナビゲーションドロワーが touch stream を横取りし、WebView 側のスクロール操作が中断される可能性がある。X 画面にはドロワーを開く明示的なメニューボタンが既に存在するため、WebView 上で開始した swipe をアプリのドロワー操作にも兼用する必要はない。

## Decision

X ビュワーは `:feature:x:ui` が所有する Android WebView として実装する。

- 初期 URL は `https://x.com/` とする
- X API は利用しない
- WebView では x.com の動作に必要な JavaScript と DOM storage を有効にする
- JavaScript を有効にする一方、不要な `file://` / `content://` アクセスは明示的に無効化する
- X のログイン画面との互換性のため、system WebView の User-Agent から WebView 固有の `wv` / `Version/4.0` トークンのみを除去し、通常のモバイルブラウザーに近い User-Agent として送信する
- Chrome のバージョンを固定した User-Agent は持たず、端末の system WebView が提供する現在の User-Agent を基に変換する
- third-party cookie はアプリ全体ではなく X 専用 WebView に対してのみ有効化する
- X の responsive layout が `<meta name="viewport">` を通常のモバイルブラウザーと同様に利用できるよう `useWideViewPort = true` とする
- overview mode による自動縮小は行わず `loadWithOverviewMode = false` とする
- 初期 `loadUrl()` は WebView が Compose から非ゼロのレイアウトサイズを受け取った後に実行する
- Android の text zoom は上書きせず、ユーザーのアクセシビリティ設定を維持する
- X セクションではアプリ共通の `TopAppBar` を表示せず、WebView にその縦領域を返す
- X セクションでも `Scaffold` の system bar insets は維持し、ステータスバーやナビゲーションバーの安全領域には侵入させない
- アプリのナビゲーションドロワーは X WebView 上に重ねる小さなメニューボタンから開けるようにする
- X WebView 上で開始した touch stream は `requestDisallowInterceptTouchEvent(true)` により親 Compose 側のジェスチャーインターセプトから保護し、`ACTION_UP` / `ACTION_CANCEL` で解除する
- X セクションでは WebView 上の明示的なメニューボタンをナビゲーションドロワーへの正式な導線とし、WebView 上の swipe gesture を drawer open の導線として利用しない
- X 以外の画面では従来どおりグローバル `TopAppBar` を表示する
- hosted WebView には `ViewOutlineProvider.BOUNDS` と `clipToOutline = true` を設定し、native View 自身の矩形境界で描画を clip する。これは防御的な境界制御として維持する
- 初期 CSS は `src/main/assets/x_viewer.css` に配置する
- 設定画面に「X > カスタム CSS」を追加し、CSS の有効/無効、CSS 本文の編集、デフォルト CSS への復元を可能にする
- 初回は asset の CSS を使用し、ユーザーが保存した後はその CSS と有効状態をローカル設定として保持する
- CSS を無効化した場合は空の style 内容を適用し、独自 CSS の影響を切り分けられるようにする
- 保存した設定は次回 X 画面を開いた際に読み込み、新しい WebView に適用する
- CSS 設定は X の表示にだけ関係する presentation preference であり、独立した domain contract や data dependency を必要としないため、`:feature:x:ui` 内の internal な `SharedPreferences` 実装として保持する
- ページ読み込み完了後、CSS を `<style>` 要素として document head へ注入する
- CSS 注入対象は x.com / twitter.com 配下に限定する
- 同一 style ID を再利用し、ページ再読み込み時は重複した style 要素を増やさない
- X 画面には「要素を非表示」操作を追加し、明示的に選択モードへ入った場合だけ DOM 選択用 JavaScript を注入する
- 選択モード中は Web ページの capture phase の click listener で通常の click を抑止し、タップ対象を outline で可視化する。通常閲覧時にはこの listener を常駐させない
- 要素 selector は `data-testid`、`aria-label`、`role`、`name`、`title` など比較的意味のある属性を優先し、単独で一意にならない場合のみ親子構造と `nth-of-type()` を組み合わせて一意な selector を生成する
- class 名は X 側で変更されやすいため、要素選択機能が生成する selector の主要な識別子として使用しない
- 選択した selector は Web 側から自発的に Android へ送信せず、ユーザーが native UI の「選択した要素を非表示」を押した時だけ `evaluateJavascript()` の戻り値として取得する
- 取得した selector は `display: none !important` の CSS rule として現在のカスタム CSS 末尾に追加し、`SharedPreferences` へ保存したうえで現在のページにも即時再注入する
- 同一の生成済み rule が既に存在する場合は重複追加しない
- カスタム CSS が無効な場合、要素選択機能から暗黙に再有効化せず、設定画面で明示的に有効化するよう案内する
- 選択確定またはキャンセル時には click listener、選択 outline 用 style、選択状態を除去する
- `addJavascriptInterface` は使用せず、Web ページから Android API を呼び出せる bridge を公開しない
- WebView は Compose の画面破棄時に `destroy()` する
- 現段階では domain/data module を作らず `:feature:x:ui` のみとする

デフォルト CSS では、タイムラインの閲覧を妨げずに削除できる範囲として以下だけを非表示にする。

- 右側のおすすめ・トレンド列 (`data-testid="sidebarColumn"`)
- Grok のフローティング drawer (`data-testid="GrokDrawer"`)

X の DOM は外部サービス側の変更対象であるため、セレクタ変更は設定画面または `x_viewer.css` で調整する。

## Consequences

### Positive

- X API や外部 SDK なしで既存の Web UI とログイン状態を利用できる
- カスタム CSS を完全に無効化できるため、表示不具合が X 本体か注入 CSS かを実機で切り分けられる
- CSS selector の調整を APK 更新なしで試せる
- DOM inspector を別途用意しなくても、端末上のタップ操作から非表示 CSS rule を作成できる
- 選択用 JavaScript はユーザーが明示的に選択モードへ入った間だけ有効で、通常の X 操作への干渉を限定できる
- Web ページから native API を呼ぶ bridge を公開せずに selector を取得できる
- デフォルト CSS を保持しつつ、いつでも初期状態へ戻せる
- X 画面ではアプリの固定ヘッダー分だけ WebView の縦領域を失わず、外部 Web アプリが想定する viewport に近づけられる
- system bar insets は維持するため、アプリ全体の edge-to-edge 方針と整合する
- ナビゲーションドロワーへの導線は維持できる
- X WebView の縦スクロールや X 内 gesture が親ナビゲーションドロワーに奪われにくくなる
- X 以外の画面のヘッダー構成には影響しない
- system WebView の更新に追従しながら、WebView 判定による表示崩れを避けやすくなる
- third-party cookie の許可範囲を X 専用 WebView に限定できる
- Android の text zoom / accessibility 設定を維持できる
- JavaScript bridge とローカルファイルアクセスを公開しないため Web コンテンツとの権限境界を小さく保てる

### Negative

- ユーザーが不正な CSS を保存すると X の表示を崩せる
- X の DOM 変更後は保存済み CSS が古くなり、デフォルト CSS 更新だけでは自動的に上書きされない
- 自動生成 selector は class 名への依存を避けても X の DOM 構造変更で無効になる可能性がある
- `nth-of-type()` を含む fallback selector は DOM の並び順変更に弱い
- 誤った要素を選択すると必要な UI を非表示にできるため、既存の CSS 編集・デフォルト復元機能を復旧手段として維持する必要がある
- X 画面だけ他の画面とヘッダー構成が異なる
- X WebView 上からのスワイプではアプリのナビゲーションドロワーを開かず、右上のメニューボタンを使用する必要がある
- WebView 上のメニューボタンが X 側 UI と重なる可能性があるため、位置は必要に応じて調整する必要がある
- x.com の DOM や viewport 前提の変更によって再び表示が崩れる可能性がある
- WebView が X の公式サポート対象として保証されるわけではない
- User-Agent 判定やログイン実装が X 側で変更された場合は追加の互換性対応が必要になる可能性がある
- X WebView 内では third-party cookie を許可するため、既定の WebView policy より privacy 上の許可範囲が広がる
- Firefox 拡張 API のようなネットワークリクエスト制御や高度な browser extension API は利用できない

## Future changes

複数 stylesheet の切り替え、設定の export/import、より高度な selector 編集・親要素への選択範囲拡張、X 固有の userscript、CSS 設定をバックアップ対象へ含めるなどの要件が生じた場合は、この ADR を更新し、責務に応じて domain/data module の追加を再検討する。

DOM 選択結果を native UI へリアルタイム送信する必要が生じた場合は、origin を x.com / twitter.com に限定できる Web message API を検討する。`addJavascriptInterface` を安易に導入しない。

X のログイン要件が変わり、通常の credential login を WebView 内で維持できなくなった場合は、Custom Tabs 等へ認証を分離する案を再検討する。ただし browser と WebView の cookie store は自動共有されないため、単純な置き換えは行わない。

## Relationship to other ADRs

- ADR-0001 の UI / Domain / Data の責務境界を維持する
- ADR-0003 の feature-first 構成と「必要な layer のみ module 化する」方針に従う
