# Mosaic 現行仕様

- 更新日: 2026-09-09
- 対象: 現在の `main` 系列

## 1. 目的

Mosaic は、RSSを起点に、ブックマーク、外部コンテンツ、メール、蔵書、タスク、健康・運動、資産などの個人情報を端末内で整理・閲覧する Android アプリである。

主な方針は次のとおり。

- 主要なユーザーデータは端末内に保持する。
- Mosaic 独自のアカウントや同期サーバーを必須としない。
- RSS、Reddit、動画購読source、メール、蔵書など source 固有の意味を維持しつつ、必要な箇所だけ共通の閲覧・整理・AI処理へ接続する。
- 要約、チャット、書誌推定などのAI処理は、ダウンロード済みの端末内モデルを利用する。
- バックグラウンド処理は画面の寿命から分離し、必要に応じて WorkManager と永続キューを利用する。
- credential、token、外部サービスの認証情報は、通常のユーザーデータと分離して扱う。

## 2. この文書の責務

この文書はユーザーから見た現行機能と、互換性に影響する主要な振る舞いを説明する。

アーキテクチャ上の依存方向、module ownership、table ownership、テスト戦略、Android platform 基準は `docs/architecture/` を正本とする。設計判断の理由と変更履歴は `docs/adr/` を正本とする。

データベースの現在version、module一覧、table一覧、依存ライブラリversion、CIコマンドなどコードから一意に決まる値はこの文書へ複製しない。現在値は次を参照する。

- database schema: `app/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt`
- module一覧: `settings.gradle.kts`
- table ownership: `config/architecture/table-ownership.tsv`
- CI: `.github/workflows/`
- Android platform: `docs/architecture/platform.md`

## 3. 対象環境

- Android 15（API 35）以降を対象とする。
- compile / target API は Android API 36 系とする。
- 配布対象CPUは arm64-v8a とする。
- Kotlin と Jetpack Compose を主要実装技術とする。
- Game の数独では、既存 Android アプリへ組み込んだ Godot runtime を利用する。
- ユーザー向け名称は Mosaic とする。
- 既存インストールとの互換性のため application id `dev.terashima.yomitorirss` と内部 database file 名 `yomitori-rss.db` は維持する。

## 4. コンテンツ閲覧と整理

### 4.1 RSS

- RSS / Atom 系フィードを登録して記事を取得する。
- 未読、既読、履歴を管理する。
- フィードの追加、削除、手動更新、OPMLインポートを提供する。
- コンテンツ取得や分類は source 固有情報を維持しつつ Content Context へ接続する。

### 4.2 ブックマークとあとで読む

- コンテンツをブックマークとして保存できる。
- タグとフォルダで整理できる。
- 「あとで読む」は Curation Context が所有する system folder として扱う。
- RSS の「あとで読む」では、現在の並び順を基準にレビューを開始し、開始時点の対象を1件ずつ連続して確認できる。レビュー中に新しく追加された記事はそのセッションへ差し込まない。
- レビューでは記事タイトル、配信元、保存済み要約の全文を表示する。要約が未生成の場合は Summary が所有する既存の要約キューへ要求し、画面表示中は保存結果を反映する。要約待ちや失敗中でも記事の整理操作は継続できる。
- レビュー中は「記事を開く」「はてブを見る」を利用でき、戻ったときは同じレビュー対象と進捗へ復帰する。
- レビューの状態変更は固定ボタンの「保留」「未分類へ」「削除」で行い、「未分類へ」「削除」の後は自動的に次の記事へ進む。これらの変更は短時間表示される Snackbar の「元に戻す」から復元でき、操作しなければ Snackbar は自動的に閉じる。
- レビューの進捗は画面内の一時状態とし、新しい durable state として保存しない。最後まで進むと完了画面で現在の「あとで読む」残件数を表示する。
- 共有インテントやインポートからブックマークを追加できる。
- 保存済みコンテンツへAI要約・タグ等の補完処理を行える。
- 自動AI処理対象の保存済みブックマークについて、要約とタグ付けを一括再実行できる。
- 一括再実行でメタデータ生成まで成功した記事は、既存タグを新しく生成されたタグで置き換える。処理中の記事は重複してキューへ追加しない。
- 単記事の要約再生成では「ブックマークのタグも再生成」を選択でき、既定はOFFとする。ONの場合だけ、メタデータ生成成功後に既存タグを生成タグで置き換える。

### 4.3 Reddit / 動画購読source

- Reddit は source 固有の購読・表示・判定を自身のContextで扱う。
- 動画チャンネル購読は Video Context が購読型Providerとして扱い、provider設定、subscription、更新、未読状態を共通のVideo lifecycleで管理する。
- 動画Providerは組み込みProviderに加えて、ユーザーが任意のJavaScript functionを設定するcustom Providerを複数追加できる。custom Providerでもsubscription、未読、あとで見る、保存、再生状態は共通のVideo lifecycleを利用する。
- 初期の組み込み動画Providerでは通常動画だけを更新対象とし、短尺専用形式とライブ配信は更新対象外とする。
- 共通表示へ投影しても source の種類を失わない。
- 自動AI処理の対象可否は source / content type の方針に従う。

### 4.4 統合ビューと履歴

- 複数sourceのコンテンツを横断して閲覧するpresentationを提供する。
- 統合ビュー上部の source フィルターには通常feed、Reddit、動画購読、メール等の未読件数を表示し、一覧領域を優先するため重複する全体件数サマリーは表示しない。
- 動画購読sourceの未読 / 履歴は Video Context の公開APIから取得し、旧専用tableを直接参照しない。
- 統合ビュー下部の「未読 / あとで読む / 履歴」タブは、app shell が確保済みの system navigation 領域を重複して確保しない。
- 履歴や保存状態は owner Context の API を通して参照する。

### 4.5 要約の音声再生

- RSS の「あとで読む」から、現在表示している並び順の記事を「音声で聴く」で連続再生できる。
- 各項目は記事タイトルと保存済み要約を端末の音声合成機能で読み上げる。
- 保存済み要約がない記事は Summary が所有する既存の要約キューへ要求し、利用可能になった要約を読み上げ対象にする。Audio独自の要約生成は行わない。
- 要約を取得できない項目は再生対象から除外し、取得できた項目の順序は元の「あとで読む」の並び順を維持する。
- 音声ファイルは再生順に生成し、最初の再生可能な1件の音声が完成した時点で再生を開始する。全件の音声生成完了は待たず、再生中も後続記事の音声生成を継続して再生queueへ順次追加する。
- 再生が後続音声の生成に追いついた場合は、次の音声が完成してqueueへ追加された時点で連続再生を再開する。
- 再生、一時停止、前の記事、次の記事、15秒戻し、30秒送り、1x / 1.25x / 1.5x / 2x の再生速度変更を提供する。
- 画面を閉じてもmedia playbackとして再生を継続し、通知、lock screen、Bluetooth等の標準media controlから操作できる。
- 再生開始、再生完了、skip、停止、queue完了のいずれでも記事の既読 / 未読状態を変更しない。「あとで読む」の所属やブックマーク状態も変更しない。
- 再生queue、再生位置、再生済み状態はdurable user stateとして保存しない。生成した音声は再生成可能なcacheとして扱い、backup / export対象にしない。

### 4.6 動画

- トップレベルの「動画」画面で、SMBファイルサーバー由来、Web URL由来、購読型Provider由来の動画を同じcatalogから扱う。
- 一覧には「未視聴 / 続き / 保存済み / 視聴済み / 設定」を用意し、sourceを「すべて / SMB / Web / サービス」で絞り込める。「サービス」は購読型Providerが取得した動画を表す。
- 「未視聴」には保存済みではなく、視聴済みでもない動画を表示する。再生途中の未保存動画は「未視聴」と「続き」の両方に表示する。SMB由来とWeb URL由来の動画は常に保存済みのため「未視聴」には表示しない。
- 保存は動画本体のdownloadを意味しない。SMB由来とWeb URL由来のcatalog itemは常に保存済みとして扱い、購読型Provider由来の動画だけ明示的に保存 / 保存解除できる。
- 「保存済み」タブはファイルブラウザ型とし、フォルダと動画を同じ一覧へ表示する。フォルダを開くとその直下の子フォルダと動画へ移動し、breadcrumbから上位へ戻れる。
- SMB動画は同期元のディレクトリ階層に沿って表示する。保存済み画面のrootではSMB同期場所ごとのrootフォルダを表示し、その内部では元ファイルサーバー上の階層を順に辿れる。SMB動画をVideo専用フォルダへ移動しない。
- Web動画は追加した時点で保存済み画面へ入り、未分類またはVideo専用フォルダ1つへ整理できる。Web動画をフォルダから外しても保存済み状態は維持して未分類へ戻す。
- 明示保存した購読型Provider動画は未分類またはVideo専用フォルダ1つへ整理でき、保存解除すると保存済み画面から外れる。
- Video専用フォルダは設定画面から作成、名称変更、削除できる。フォルダを削除しても所属していたWeb動画と明示保存済みProvider動画は未分類へ戻し、再生状態は変更しない。
- 保存済み判定 / 保存 / 保存解除 / 保存先変更 / フォルダ操作では、再生位置や視聴済み状態を変更しない。
- Web URLを登録すると、通常はHTTP(S)ページのHTML / OGPからタイトルとサムネイルURLを取得する。
- Web URLの追加処理中は、登録が完了または失敗するまで「Web動画を追加中…」の進行中表示を出し、処理中であることを明示する。
- Web URL登録は1件を明示的に追加する単発操作であり、購読・未読・background refreshの対象にはしない。
- URL patternごとのWeb抽出ルールを設定でき、Promiseを返すJavaScript関数でタイトル、サムネイルURL、再生用stream URLとMIME typeを個別に取得できる。
- Web抽出ルールでは「再生時にWebViewのCookieを共有する」をルールごとに設定でき、既定はOFFとする。有効な場合だけ専用WebView profileのCookieを再生中のMedia3 HTTP requestへ一時的に利用し、Cookie値自体は保存・backup・logへ残さない。
- Web抽出ルールは端末内の専用WebViewで実行し、再生用stream URLは保存せず再生時に取得する。stream URLを取得できない場合はWebページ表示へfallbackする。
- 動画設定では購読型Providerを追加・有効化 / 無効化できる。Providerを無効化しても設定・subscription・取得済み動画は削除せず、background refresh対象からだけ外す。
- custom Providerは名前とJavaScript functionを設定して複数追加・編集できる。functionはsubscribe時の入力またはrefresh時のsource IDを受け取り、source ID、タイトル、source URL、動画一覧を返す。
- custom Providerのfunctionから外部データを取得するときは `api.fetch` を利用する。requestはHTTPSに限定し、実行時間、request回数、request / response sizeを制限する。
- custom Providerへ既存のCookie、token、SMB credential、他機能の認証情報を自動共有しない。初期contractではcredential headerも設定対象にせず、認証情報をfunction codeへ埋め込む用途を想定しない。
- custom Providerは画面を開いていないbackground refreshでも同じfunctionを実行できる。functionの失敗や不正な戻り値はそのsubscriptionの更新失敗として扱い、他Provider / subscriptionの更新を継続する。
- 有効なProviderへチャンネル等のsource URLまたはProvider固有の入力をsubscriptionとして追加し、手動またはbackgroundで更新できる。
- Provider更新で新しく発見した動画はVideo catalogへ追加し、provider由来の未読として表示する。既存動画の更新では未読 / 既読、あとで見る、保存、再生位置を保持する。
- provider由来の未読 / 既読状態と視聴済み状態は独立して扱う。既読化で視聴済みへ変更せず、再生完了で自動的に既読化しない。
- subscription解除時、未保存かつ再生履歴のない取得済み動画は削除できる。保存済みまたは再生履歴を持つ動画はsubscription membershipだけを外してcatalogへ残す。
- 旧専用購読画面はトップレベル導線から廃止し、購読設定は「動画」画面へ集約する。既存インストールのsubscriptionと未読状態は更新時にVideo-owned stateへ移行する。
- SMB接続の表示名、host、port、username、domain、passwordはアプリの全体設定から接続プロファイルとして登録・編集する。passwordは画面へ再表示しない。
- 動画設定では全体設定のSMB接続プロファイルを選び、動画として同期するshareとパスを個別に登録する。蔵書とは異なるshare / pathを指定でき、同じ接続プロファイルへ複数の動画同期場所を登録できる。
- 全体設定で削除された接続先を参照する動画同期場所は設定画面で無効として表示し、削除できる。無効な同期場所はSMB同期対象から除外し、他の有効な同期場所の同期を妨げない。
- SMB passwordやserver credentialをVideo側へ複製しない。SMB credentialはLibrary側が所有する保護領域を利用し、Video側は接続プロファイルIDと自身のshare/pathだけを保持する。
- SMB同期で登録した動画は、一覧に表示された時点で動画ファイルから代表フレームをサムネイルとして生成する。生成結果は再生成可能な端末内cacheとして扱い、生成失敗時も同期・再生は継続する。
- Media3で通常のHTTP動画とHLS streamを再生する。SMB動画は動画ファイル全体を事前downloadせずrandom-access readで再生する。
- 再生準備中 / 読み込み中 / 長時間読み込み / 再生エラーを画面上で区別して表示する。10秒以上の待機では経過時間を示し、30秒以上続く場合は再生エラーがまだ検出されていないことを明示して再試行できる。Media3が再生エラーを返した場合は再生不能としてエラーコードと再試行操作を表示する。
- 再生画面では全画面表示へ切り替えられる。全画面へ入ると横向き表示へ切り替えてsystem barsを隠し、画面端のスワイプで一時表示できる。全画面解除または再生画面を閉じると通常の縦向き表示へ戻る。
- 再生画面を開いている間は画面の自動消灯を抑止する。プレイヤー左半分をダブルタップすると15秒戻し、右半分をダブルタップすると15秒送りを行う。ダブルタップ時は再生コントローラや操作オーバーレイを表示せず、単タップ時だけ再生コントローラを表示 / 非表示する。
- 動画ごとに再生位置、duration、最終再生日時、視聴済み状態を保存し、次回再生時に保存位置から再開する。既存版で保存済みのSMB動画は、同じserver/pathの対応が一意に決まる場合、shareを含む新しい動画identityへ初回再同期時に再生状態を引き継ぐ。
- durationが取得できる動画は95%以上再生すると視聴済みとし、一覧から手動で視聴済み / 未視聴を変更できる。
- 初期実装の動画再生はforeground画面内で行う。画面を閉じた後のbackground音声継続、Cast、動画download、transcodingは提供しない。

## 5. 端末内AI

### 5.1 共通runtime

- LiteRT-LM を利用する端末内AI runtime を共有する。
- モデルのダウンロード、選択、削除、推論設定、端末上のベンチマークを管理できる。
- 長時間処理は foreground UI へ閉じず、feature 所有の background runtime または task queue へ委譲する。

### 5.2 要約

- コンテンツ本文を取得・前処理したうえで要約を生成する。
- 取得、前処理、推論、metadata生成は分離した処理段階として扱う。
- 要約結果とtask状態は永続化し、失敗taskの再実行や一時停止・再開を行える。
- ブックマークの自動補完、明示的な一括再実行、単記事のタグ再生成指定は Summary が所有する既存の要約キューへ投入する。

### 5.3 AIチャット

- 端末内モデルを利用してチャットできる。
- アプリ内情報を参照する場合は、定義済みの読み取り用tool / skillを利用する。
- 任意SQLや任意コード実行をAIへ公開しない。

### 5.4 Knowledge

- 保存済みコンテンツや要約を資料としてKnowledge pageを生成・更新できる。
- 自動生成は永続background taskとして実行し、既存pageの拡張と追加page作成を扱う。

## 6. メール

- Gmail連携を提供する。
- 認証済みアカウントのメールを取得し、未読、スター、アーカイブ等の状態を扱う。
- HTMLメールを表示できる。
- メールのlocal cache / stateは Mail Context が所有する。
- OAuth credentialやtokenを通常のdatabase backupへ含めない。

## 7. 蔵書とBook Reader

### 7.1 蔵書

- Kindle、Audible、Google Books、ファイルサーバー、Web URL 由来の蔵書情報を扱う。
- シリーズ、タイトル、著者、表紙などを表示・整理する。
- タイトル検索、シリーズ表示、source別の操作を提供する。
- `text/plain` の共有から HTTP / HTTPS URL を Web 蔵書として追加できる。
- Web 蔵書とブックマークは、重複する永続状態を残さず相互に移動できる。

### 7.2 SMB / ファイルサーバー

- SMB接続の表示名、host、port、username、domain、passwordはアプリの全体設定から接続プロファイルとして管理する。
- 蔵書設定では登録済みSMB接続を選び、蔵書として同期するshareとパスだけを個別に設定する。動画設定とは独立しているため、同じ接続先でも異なるshare / pathを指定できる。
- SMB server上のZIP / CBZ / PDF書籍を蔵書へ取り込む。
- 蔵書の同期場所を解除しても全体設定のSMB接続とpasswordは削除しない。
- 表紙画像は再生成可能なcacheとして扱い、backgroundで先読みできる。既存の表紙先読みキューの進捗・失敗・待機理由を確認し、必要に応じて再試行できる。
- SMB credentialはAndroid Keystoreを利用して保護し、画面へ再表示せず、アプリ独自backupへ含めない。

### 7.3 書誌正規化

- SMB書籍について、現在のファイル名と表紙画像を入力として書誌候補を端末内AIで生成できる。
- 候補はレビュー画面で確認し、適用または却下する。
- 確定済み判断を保持し、必要な状態では再解析できる。
- 再解析では直前の書誌候補を比較対象として引き継ぎ、表紙・現在のファイル名と照合して各項目を独立に再評価する。同じ結果が妥当なら同じ候補を返してよい。
- 再解析時には任意の補足情報を追加できる。補足は当該再解析の端末内AI入力だけに利用し、固定の構造化出力・validation・安全規則を変更しない。
- 生成結果は構造化出力として受け取り、アプリ側validationを通してから利用する。

### 7.4 Book Reader

- 対応するローカル書籍をアプリ内readerで閲覧する。
- 読書位置などユーザー所有のreader設定を保持する。

## 8. タスク、カレンダー、ワークアウト、ヘルス

### 8.1 Task

- 階層を持つタスクを管理する。
- 完了状態、期限、並び順などを扱う。
- タスクの説明文に含まれる HTTP / HTTPS URL はリンクとして表示し、タップして開ける。
- ホーム画面widgetからタスクを参照できる。

### 8.2 Calendar

- Calendar は日付軸のread-only projectionとして扱う。
- Android Calendar Provider の予定に加え、Task の期限や Workout の実績を共通 `CalendarEvent` として表示する。
- Calendar 自身は Task / Workout の永続状態を所有しない。

### 8.3 Workout

- アプリ内で種目、セット、回数、時間などの運動記録を作成する。
- Workout の記録を source of truth とする。
- 日付単位のメモへ当日の所感等を記録できる。
- 直近14日間のWorkout実績、当日メモ、事前設定した方針とメニュー候補を使い、「メニュー提案」と「完了後レビュー」の2種類のAI支援を実行できる。
- AI支援の実行先は Local / ChatGPT を明示選択し、既定はLocalとする。ChatGPT選択時はWorkout記録・メモ・方針・メニュー候補をクラウドへ送信することを画面上で明示し、自動fallbackは行わない。
- AI支援へHealth Connect由来のread dataを入力しない。
- 完了したWorkoutは、許可されている場合にHealth Connectへ一方向exportできる。
- Workoutから活動消費カロリーや心拍数を推定して保存・書き込みしない。

### 8.4 Health

- Health Connectから歩数、活動消費カロリー、運動、心拍、睡眠、体重、体脂肪率、栄養情報等を読み取る。
- Health Connect由来のread dataはアプリdatabaseへ複製せず、Health画面のread modelとして利用する。
- Health ConnectからWorkoutへのimport / 双方向同期は行わない。
- アプリ内Workoutのexport以外の健康データを書き込まない。

## 9. 資産

- dated snapshotとして資産情報を保存し、時系列で参照する。
- TSV等のデータをインポートできる。
- WebView / Web Collectorを利用するsource adapterから資産情報を取り込める。
- インポート元の金額が負の場合は資産データとして保存しない。既存databaseに負の金額が残っていても、資産総額・履歴・構成比・カテゴリ設定では無視する。
- 資産項目をカテゴリ分類し、カテゴリ別の構成と推移を表示できる。

## 10. Web、X、Widget、補助機能

- X向けWebView表示とカスタムCSS / JavaScript設定を提供する。
- X WebViewでは表示中ページを手動で再読み込みでき、再読み込み後は保存済みのカスタムCSS / JavaScriptを再適用する。
- 共通Web Collectorを利用するWebViewベースのimport機能を持つ。
- 内部に長い縦スクロール領域を持つ編集・閲覧overlayは、コンテンツのスクロールとdismiss gestureが競合しないフルスクリーンmodalで表示する。
- LAN内からアプリ情報へアクセスするためのlocal web server機能を持つ。
- RSS未読やTask等をホーム画面widgetへ表示する。
- Gameでは数独、2048、ノノグラム、マインスイーパー、クロンダイク、スパイダーソリティア等の端末内ゲームを提供する。
- 数独は Godot Engine を既存 Android アプリへ組み込んだ正式実装とする。盤面を大きく表示し、編集可能なマスを選ぶとその近くに数字入力パネルを表示する。入力途中では正解・不正解を表示せず、全マス入力後にだけ完成判定する。進行状態は永続化しない。

## 11. 永続化

- durable relational user dataは原則として単一のSQLite database `yomitori-rss.db` に保存する。
- database fileを共有していてもtable ownershipは共有しない。
- 各feature data moduleが自身のschema contributionとmigrationを所有し、`:app` がapplication-level schemaをcompositionする。
- 他Contextのtableへ直接writeしない。cross-context操作はowner API、command port、query APIを利用する。
- 現在のschema versionやtable一覧はコードとmachine-readable manifestを正本とし、この仕様書では固定値を持たない。

詳細は `docs/architecture/persistence.md` を参照する。

## 12. バックアップと復元

- アプリ独自backupは、統合SQLite databaseの整合したsnapshotを含むMosaic形式のZIP archiveとする。
- backupにはmanifest、database snapshot、allowlistされたuser preferencesを含む。
- checksum、SQLite application id、integrity check等を利用して復元前にarchiveを検証する。
- database snapshotは現在のapplication schema versionと一致する場合だけ復元対象とし、異なるschema versionのbackupは復元前に拒否する。
- Google Driveでは、保存先設定後にバックアップ対象変更から15分後と1日1回の自動バックアップを行い、手動実行も提供する。
- 「Wi-Fi接続時のみバックアップ」を有効にした場合、Google Driveへの自動・手動・初回バックアップはインターネット接続可能なWi-Fiが利用できる場合だけ実行する。既定はOFFとする。
- Wi-Fi限定設定はallowlistされたuser preferenceとしてbackup対象とするが、Google Drive保存先URI・表示名・実行履歴はbackup対象外とする。
- credential、token、SMB password、Google Drive保存先、端末依存benchmark、model cache等はbackup対象外とする。
- SMB表紙cacheやSMB動画thumbnail cacheのように再生成可能な派生ファイルはbackup本体へ含めず、復元後にowner featureの経路で再生成・再取得する。

詳細は ADR-0099、ADR-0100、ADR-0135、ADR-0138、ADR-0195、ADR-0217 と `docs/architecture/persistence.md` を参照する。

## 13. Background execution

- durableなbackground処理にはWorkManagerを利用する。
- feature固有Worker、scheduler/controller、queue state interpretationは原則としてowning featureのdata/runtimeが所有する。
- 統合更新では通常feed、Reddit、Video購読Provider、メール等の更新を個別に分離して実行する。Video購読はprovider-specific repositoryではなくVideo-owned provider refresh capabilityを利用し、1件のsubscription失敗で他sourceの更新を中断しない。
- custom Video Providerのfunction実行はforeground Activityに依存せず、Video-owned runtimeからbackground refreshでも実行する。
- ユーザーが開始したAudioの継続再生はWorkManagerではなくforeground `MediaSessionService`を利用し、durable taskへ変換しない。
- `:app` はbackground business logicの恒久的な所有場所とせず、compositionとframework wiringに限定する。
- Android framework が直接生成し constructor injection を差し込めない entry point だけ、監査済みProvider contractからapplication-level dependencyを取得できる。
- WorkManager Worker は Provider lookup の例外に含めず、owning feature の `WorkerFactory` から constructor injection し、`:app` の WorkerFactory composition が application graph へ接続する。
- frameworkが永続化した旧class nameとの互換が必要な場合だけ、ADRで根拠を持つcompatibility shimを残す。

## 14. 更新互換性

- 現在配布中の最新版を次版への更新互換性baselineとする。
- 移行完了が確認された一時的migrationや旧形式fallbackは恒久的に保持しない。
- databaseとアプリ独自backupは、現在利用中の最新版へ収束した状態を基準に互換性範囲を定める。
- 現在のユーザーデータを失う可能性がある形式変更では、現行形式へ安全に収束してから旧処理を削除する。
- frameworkがclass name等を永続化する場合は、必要な期間だけ明示的compatibilityを維持する。
- application idと内部database file名は既存インストールの継続性のため維持する。

## 15. Privacy / security

- 公開リポジトリへcredential、token、OAuth secret、実ユーザーのメールアドレス、健康データ、バックアップ、SMB接続情報等を保存しない。
- fixtureとtest dataには人工データを利用する。
- backup対象のSharedPreferencesはallowlist方式とし、将来追加される値を暗黙に外部backupへ含めない。
- Health Connect由来のread dataをBackup、AI task、外部APIへ流さない。
- AI処理は端末内runtimeを基本とし、任意のアプリ内データアクセス権限をモデルへ与えない。
- custom Video Provider codeには他Contextのcredentialやdatabase accessを公開せず、外部通信はboundedなHTTPS request capabilityに限定する。function code自体をcredential保存場所として扱わない。
- ユーザーがコピーして共有できるクラッシュ診断は保存前にサニタイズし、URL の path/query、メールアドレス、credential-like 値、端末内 private path を伏せる。

## 16. 現在の非目標

- Mosaic独自のユーザー登録 / ログイン基盤
- Mosaic独自serverを介した複数端末の常時同期
- durable user dataを必須のremote backendへ保存する構成
- Health ConnectとWorkoutの双方向同期
- AIからの任意SQL、任意コード実行、無制限の書き込みtool
- custom Video Provider functionからの既存credential / filesystem / databaseへの直接アクセス
- credentialやmodel artifactをアプリ独自backupへ含めること

feature追加・廃止に伴い非目標が変わる場合は、対応するADRまたは仕様変更と同じPRで更新する。

## 17. 関連文書

- `docs/architecture/README.md`: current architecture documentの入口
- `docs/architecture/principles.md`: layer / ownership / framework boundary
- `docs/architecture/context-map.md`: Domain ContextとContext間関係
- `docs/architecture/module-map.md`: Gradle module構成
- `docs/architecture/audio-playback.md`: 要約音声再生とMediaSessionService境界
- `docs/architecture/video.md`: SMB / Web / 購読型Provider動画カタログ、抽出、更新、custom Provider実行、Media3再生境界
- `docs/architecture/game.md`: Game と Godot 数独の runtime boundary
- `docs/architecture/persistence.md`: schema / migration / table ownership / backup関連境界
- `docs/architecture/testing.md`: testとarchitecture verification
- `docs/architecture/platform.md`: Android platform基準
- `docs/adr/README.md`: ADR索引
