# Video

この文書は Video feature の current architecture を示す。設計判断の履歴は [ADR-0237](../adr/0237-video-library-and-web-extraction.md)、[ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)、[ADR-0240](../adr/0240-video-saved-items-and-folders.md)、[ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md)、[ADR-0242](../adr/0242-video-subscription-providers.md)、[ADR-0243](../adr/0243-video-web-request-cookie-capture.md)、[ADR-0244](../adr/0244-video-intrinsic-saved-sources-and-file-browser.md)、[ADR-0245](../adr/0245-video-playback-explicit-referrer-origin.md)、[ADR-0246](../adr/0246-video-custom-provider-code.md)、[ADR-0248](../adr/0248-video-playback-referrer-path-opt-in.md) を参照する。

## Ownership

Video は SMB / Web / 購読型provider由来動画を同じ catalog へ投影し、次を所有する。

- Video item identity / source type / title / thumbnail URL
- Video用SMB同期場所（connection profile ID / share / root path）
- SMB source identityから再構築する保存画面用directory projection
- Web page URL identity
- Web video extractor rule
- 購読型provider設定、custom provider function code、subscription、provider item identity / publish time
- provider item の unread / read / watch-later state
- provider refresh lifecycle
- 再生位置 / duration / 最終再生日時 / completed state
- source lifecycleに基づく保存済み判定、明示保存状態、Video専用フォルダ
- foreground video playback presentation
- SMB動画から再生成できるthumbnail cache

現在の module は次のとおり。

```text
:feature:video:domain
:feature:video:data
:feature:video:ui
```

`:feature:video:domain` は `VideoItem`、`VideoSmbSource`、`VideoSmbFileIdentity`、`VideoSmbBrowserPath`、`VideoPlaybackState`、`VideoSavedState`、`VideoFolder`、`VideoProvider`、`VideoSubscription`、`VideoProviderVideo`、`WebVideoExtractorRule`、`VideoRepository`、`VideoProviderRepository`、`VideoPlaybackResolver`、`VideoThumbnailResolver`、`VideoByteSourceFactory` 等の contract と、SMB source identity / sync rootからbrowser pathを導出するpure ruleを所有する。

`:feature:video:data` は Video-owned database schema、Video用SMB同期場所、明示保存状態・フォルダ、Web metadata取得、WebView extractor、SMB catalog projection、組み込みprovider adapter / custom provider runtime / feed validation / refresh、playback target resolution、SMB動画のthumbnail cache生成を所有する。

`:feature:video:ui` は一覧、source filter、「続き」「保存済み」「視聴済み」、保存済み動画のfile browser、整理フォルダ管理、provider / subscription設定、custom provider function editor、provider未読確認、設定、Media3 foreground playerを所有する。一覧へ入ったSMB動画カードだけthumbnail resolutionを要求し、同期時に全動画を事前生成しない。

## Shared SMB connection boundary

SMB設定は接続情報と機能別同期場所を分離する。

- 接続プロファイル: name / host / port / username / domain / password
- Video同期場所: connection profile ID / share / root path

接続プロファイルとcredentialは Library Context の既存保護境界を継続利用する。Video は host、username、password等を自身のtableへ複製しない。接続プロファイルはアプリ全体設定から管理し、Video設定では登録済みprofileを選択してVideo用share / root pathだけを管理する。

Library Domain は `SmbMediaFileAccess` を read-only capability として公開する。

```text
Global Settings
   |
   v
Library-owned SmbConnectionProfileRepository
   |
   +---- smb_connection_profiles
   +---- encrypted SMB credential

Video Settings
   |
   v
video_smb_sources
   |
   | serverId / share / rootPath
   v
Video Data
   |
   | SmbMediaFileAccess(SmbMediaLocation)
   v
Library Data
```

`SmbMediaFileAccess` はcallerが指定した `SmbMediaLocation` の動画候補列挙と、その同期範囲内のファイルのrandom-access readだけを公開する。host、username、password等の接続詳細とcredential値はcontractを越えない。

SMB動画のdurable source identityにはconnection profile ID、share、pathを含める。同じserver/pathでもshareが異なるファイルを区別するためである。version 28で作成済みのshareを含まない旧source IDは読み取り互換を維持し、設定されたserver/rootから再生先を解決できる。

保存済み画面では、このdurable source identityのpathと `video_smb_sources.root_path` を利用して相対directoryをその都度導出する。SMB directory自体を別tableへ永続化しない。複数の同期場所が同じpathを包含する場合は最も深いrootを採用し、同期場所ごとに独立したroot entryとして扱う。root境界の判定では `..` を拒否する既存path invariantを維持する。

SMB再生では全動画を端末へ事前downloadせず、offset readを `VideoByteSource` / Media3 `DataSource` へ接続する。

SMB thumbnailも同じrandom-access boundaryを再利用する。`VideoThumbnailResolver` は `VideoByteSourceFactory` を Media3 `DataSource` へadapterし、`FrameExtractor` で動画の冒頭寄りの同期frameを取得する。プラットフォームの `MediaMetadataRetriever` は利用しない。元動画全体を端末へ保存せず、最大辺640pxのJPEGをapplication cache配下へ保存する。生成は一覧で表示対象になった項目に限定し、同時生成数を制限する。

thumbnail cacheはVideo item IDとファイルサイズをcache keyに含める。同じ同期対象を再同期しても通常は既存cacheを再利用し、サイズが変わった動画では再生成する。同一path・同一sizeの内容置換を厳密に検出するdurable fingerprintは持たず、cacheはあくまで再生成可能なbest-effort projectionとする。生成失敗はcatalog同期や動画再生の失敗へ昇格させない。

## Web source and extractor

Web itemのdurable identityはHTTP(S) page URLとする。stream URLは期限付きURLになり得るため保存しない。

登録時の標準経路は次のとおり。

```text
page URL
   |
   v
bounded HTTP fetch
   |
   v
HTML / OGP metadata
   |
   +---- matching custom rule ----> dedicated WebView extraction
   |
   v
Video catalog
```

custom ruleはVideo-owned durable user dataであり、URL glob pattern、任意のPromise-returning JavaScript function、再生時Cookie共有とReferer path共有のopt-in設定を保存する。

- title extractor
- thumbnail extractor
- playback extractor
- `shareCookiesForPlayback`: default false
- `shareReferrerPathForPlayback`: default false

playback extractorは `{ streamUrl, mimeType?, referrerUrl? }` を返せる。`referrerUrl` はexact stream requestをWebViewで観測できない場合に利用する任意の再生元URL hintであり、最終page URLから相対解決できる。既定ではMedia3へ渡す前にoriginへ縮約する。`shareReferrerPathForPlayback` がONで、かつこのexplicit `referrerUrl`が再生参照元として採用された場合だけpathを保持できる。query / fragment / userinfoは送らない。

WebViewは専用profileを利用し、HTTPS pageだけを対象とする。file/content access、mixed content、window生成、geolocation等を有効化しない。native JavaScript bridgeは公開しない。Cookie共有の設定にかかわらずthird-party Cookie acceptanceを暗黙に有効化しない。

custom title / thumbnail extractionが失敗した場合は静的metadataを維持する。playback extractorが利用できない、または結果を取得できない場合はpage URLをWeb表示するtargetへfallbackする。

Web URL登録は1件のVideo itemを明示的に追加する操作であり、購読、未読、background refresh semanticsを持たない。購読型providerとは別のlifecycleとして扱う。catalogへ明示登録されたWeb itemはsourceの性質として保存済みであり、`video_saved_items` rowがなくても保存済み画面へ表示する。

## Subscription providers

購読型providerは、provider設定・subscription・取得済みitem stateをVideo Context内で共通化する。組み込みadapterとユーザー定義custom providerはいずれも外部source固有の入力正規化、endpoint解決、response parsing、item projectionだけを担当し、subscription / unread / refresh lifecycleを独自実装しない。

```text
Video Settings
   |
   +---- provider enabled state
   +---- custom provider function code
   +---- subscription input
   |
   v
VideoProviderRepository
   |
   +---- video_providers
   +---- video_subscriptions
   |
   +---- built-in adapter -------> bounded feed fetch / parse
   |
   +---- custom JS runtime ------> bounded HTTPS api.fetch
   |                                   |
   |                                   +---- no implicit credentials
   v
video_provider_items + video_items
   |
   +---- unread / read / watch later
   +---- playback / saved state は既存Video stateを利用
```

custom providerは複数登録できる。function codeは `async (input, api) => ProviderFeed` 相当のJavaScript function expressionとして保存し、subscribe時は `input.mode = "subscribe"` とユーザー入力、refresh時は `input.mode = "refresh"` と保存済みsource IDを受け取る。戻り値はsource ID、表示タイトル、source URLと動画item配列へ正規化し、Video Dataがvalidation後に既存provider stateへ投影する。

custom providerの外部通信はhostが提供する `api.fetch` を利用する。requestはcredentialを含まないHTTPS URLに限定し、request回数、body size、response size、function size、実行時間をboundedにする。custom runtimeにはdatabase、filesystem、Android object、他Contextのrepositoryを公開しない。専用WebView profileではCookieを受け入れず、direct network loadも無効化する。

`Authorization`、`Cookie`、proxy credential、API key相当のcredential headerは初期custom provider contractでは受け付けず、既存WebView Cookie、mail credential、SMB credential、cloud token等を暗黙に注入しない。認証付きproviderを追加する場合は、function codeとは分離されたprotected credential capabilityとして別途設計判断する。

custom provider runtimeはActivityに依存せずapplication contextから構成し、統合background refreshからも利用できる。native JavaScript bridgeやWebView自身のnetwork loadは利用せず、`api.fetch` が返すPromiseをhost側のbounded HTTP responseで解決する。複数requestが同時に開始された場合もrequest IDとqueueで対応関係を保持し、rendererが終了した場合は当該provider実行を失敗として局所化する。

新しく取得したprovider itemは `VideoSource.SERVICE` の `video_items` として投影し、`video_provider_items` でprovider item ID、subscription、publish time、未読状態を保持する。既存itemのrefreshではread / watch-later stateを上書きしない。

provider itemのidentityはprovider IDとprovider固有item IDの組み合わせで一意にする。subscription identityはprovider IDとprovider固有source IDの組み合わせで一意にする。異なるprovider間のitem/source ID衝突を許容する。

providerを無効化するとbackground refresh対象から外すが、設定と取得済みitemは保持する。subscription解除時、未保存かつ再生履歴のないitemはcatalogから削除できる。保存済みまたは再生履歴を持つitemはsubscription membershipだけを失い、Video itemとして保持する。

統合background refreshは個別sourceのrepositoryを直接更新せず `VideoProviderRepository.refreshProviders()` を呼ぶ。subscription単位の失敗は他subscriptionの取得を妨げない。新規itemはVideo providerの未読として通知対象へ加わる。

移行前の専用subscription/video persistenceとtop-level navigationはcurrent runtimeのsource of truthとして使用しない。既存installの状態はversion 30 -> 31 migrationでVideo-owned provider stateへ一度だけ取り込む。

## Playback

再生targetはDomainで次の3種類へ正規化する。

- `VideoPlaybackTarget.Stream`: HTTP(S)等のMedia3再生可能URL。Web item由来の場合は抽出中に観測した実メディアrequestの参照元origin、観測できない場合はplayback extractorのexplicit `referrerUrl`、さらに取得できない場合は元page originを一時的に持ち、明示opt-in時だけCookie providerも持てる。explicit `referrerUrl`はルールで別途opt-inされた場合だけpathを保持できる。
- `VideoPlaybackTarget.Smb`: Library-owned SMB read capabilityを使うrandom-access source
- `VideoPlaybackTarget.WebPage`: アプリ内video playerではなくWeb pageを開くfallback

Web streamをMedia3で直接再生する場合、ブラウザ埋め込み再生と同等の最低限のHTTP文脈を再現する。dedicated WebView extractorの `shouldInterceptRequest` でHTTP(S) resource requestを観測し、playback extractorが返したstream URLと同一requestが存在する場合だけ、そのrequestの `Referer` をorigin rootへ縮約して利用する。stream URLと一致しない別requestの参照元は候補として流用しない。exact requestの参照元を取得できない場合はplayback extractorが返した `referrerUrl` をHTTP(S) URLとして解決して利用し、それもない場合だけ元pageのoriginを利用する。

選択した参照元から `Referer` と `Origin` を生成し、Android WebViewのdefault user agentを `User-Agent` としてmanifest / segment requestへ付与する。既定の`Referer`はorigin root URL、`Origin`は常にscheme / host / optional portだけとする。`shareReferrerPathForPlayback` がONで、かつplayback extractorのexplicit `referrerUrl`が採用された場合だけ、`Referer`へpathを残す。query / fragment / userinfoは常に除去する。WebView実request由来の参照元と元page fallbackはこの設定で拡張せずorigin-onlyを維持する。これらのrequest contextは再生時だけ利用してdatabaseへ保存しない。

一致した抽出ルールで `shareCookiesForPlayback` が有効な場合だけ、専用WebView profile由来のCookieを `VideoPlaybackCookieProvider` としてtransientに再生targetへ接続する。`WebViewFeature.COOKIE_INTERCEPT` が利用可能な環境では、extractor WebViewのrequest interceptionへCookie headerを含める設定を有効化し、playback extractorが返したstream URLと同一requestで観測した `Cookie` headerを初回stream request用として優先する。このrequest CookieはWebView自身がそのrequest contextに対して選択した値なので、partitioned CookieもWebViewのpartition contextに従う。

実request Cookieを観測していないURL、manifestから派生したsegment、redirect先などでは、ADR-0241の既存profile CookieManager lookupへfallbackする。観測済みCookieをhostやdirectoryだけを根拠に別URLへ流用しない。`COOKIE_INTERCEPT` 非対応環境でも既存providerだけで再生を継続する。

Cookie共有がOFFの場合はrequest Cookie captureを有効化せずproviderも作らない。Cookie値、Authorization、その他のcredentialをdatabase、backup、export、log、error UIへ保存・表示しない。request interceptionを `Authorization` 等の任意credential共有へ一般化しない。third-party Cookie acceptanceも暗黙に変更しない。

v1のVideo playerはforeground UI lifetimeとする。全画面表示では横向きへ切り替え、通常表示へ戻ると縦向きへ復帰する。Audio featureの `MediaSessionService` を再利用または複製せず、background audio continuation、Cast、download、transcodingは対象外とする。

## Saved items and folders

保存は動画本体のdownloadではなく、Video catalog itemを保存済み画面へ残すことと、その整理状態を表す。保存済み判定はsource lifecycleに合わせて次のように定義する。

- `VideoSource.SMB`: 同期済みcatalog itemは常に保存済み。
- `VideoSource.WEB`: ユーザーが明示登録したcatalog itemは常に保存済み。
- `VideoSource.SERVICE`: `video_saved_items` rowが存在するitemだけを明示保存済みとする。

`video_saved_items` はSERVICE itemの明示保存状態に加え、Web / SERVICE itemのVideo専用フォルダ所属を保持する。Web itemからこのrowを削除してもcatalog item自体は保存済みのままで、未分類へ戻る。SMB itemは元directory構造を優先し、Video専用フォルダへ移動しない。

保存済み画面はfolder filterではなくfile browserとして構成する。rootでは次を同じ一覧へ表示する。

- SMB同期場所ごとのroot directory
- Video専用フォルダ
- folder未所属のWeb item
- folder未所属の明示保存済みSERVICE item

SMB root directoryを開くと、同期元pathから派生した子directoryと直下動画を同じgridへ表示し、directoryを順に辿れる。breadcrumbから上位directoryへ戻れる。SMB directoryはtransient projectionでありdurable rowを持たない。

Video専用フォルダは単一階層のまま維持し、Web itemと明示保存済みSERVICE itemを最大1つのfolderへ所属させる。

- `folder_id = NULL`: 保存済み画面rootの未分類
- `folder_id` が存在: 対応する `video_folders` directoryへ所属

フォルダ削除時は保存rowを削除せず `folder_id` をNULLへ戻す。SERVICE itemは明示保存状態を維持し、Web itemはsourceの性質として保存済みのまま維持する。保存、保存解除、保存先変更、フォルダ操作によって `video_playback_state` は変更しない。

Curation-owned `bookmark_folders` / `article_folders` は利用しない。Videoの保存状態はContent/Bookmarkの保存状態とは別の概念としてVideo Context内に閉じる。

## Durable data

Video Dataは次のtableを所有する。

- `video_items`
- `video_playback_state`
- `video_smb_sources`
- `video_folders`
- `video_saved_items`
- `video_web_extractor_rules`
- `video_providers`
- `video_subscriptions`
- `video_provider_items`

`video_items` はcatalog projectionであり、SMB / Webではrowの存在自体が保存済み画面への所属も意味する。`video_playback_state` はユーザーの視聴継続状態、`video_smb_sources` はVideo用SMB同期場所とbrowser root境界、`video_folders` はWeb / SERVICEの整理folder、`video_saved_items` はSERVICEの明示保存とWeb / SERVICEのfolder所属、`video_web_extractor_rules` はユーザー設定、`video_providers` / `video_subscriptions` / `video_provider_items` は購読型providerの設定・membership・item stateとして保存する。custom providerのfunction codeは `video_providers.function_code` に保存する。Cookie共有とReferer path共有のboolean設定はruleのdurable stateだが、Cookie値や実参照元URLそのものは保存しない。

SMB directory hierarchy用の新しいdurable tableは持たない。directoryは `video_items.source_id` と `video_smb_sources.root_path` から再構築できるprojectionとする。

stream URL、stream request context（referrer / origin / user agent / cookie value）、動画ファイル本体、SMB credential、生成済みSMB thumbnail fileはVideoのdurable stateに含めない。SMB thumbnailはapplication cache内の派生データでありbackup / export対象にしない。

SMB接続プロファイルはLibrary-owned `smb_connection_profiles` に保存し、Library用SMB同期場所は既存 `smb_library_servers` のshare / root pathとして維持する。Videoはこれらのforeign tableを通常runtimeで直接read/writeしない。

ADR-0239で `video_smb_sources` と `smb_connection_profiles` を追加してapplication database versionを29とし、version 28 -> 29 migrationで従来Videoが暗黙利用していた `smb_library_servers` のshare / root pathを初期 `video_smb_sources` として一度だけ取り込む。このmigrationだけは明示されたforeign-table read allowlistを利用する。

ADR-0240でapplication database versionを30へ進め、`video_folders` と `video_saved_items` を追加する。version 29 -> 30では既存Video itemを自動保存せず、保存状態とフォルダは空から開始する。ADR-0244ではschemaを変更せず、SMB / Webの保存済み判定をcatalog lifecycleへ移し、既存tableをSERVICEの明示保存とWeb / SERVICEの整理に再利用する。

ADR-0241では `video_web_extractor_rules` に `share_cookies_for_playback INTEGER NOT NULL DEFAULT 0` をadditiveに追加する。fresh schemaはcolumnを最初から持ち、既存version 30 databaseはVideoのidempotent schema initializerが不足列だけを追加する。既存rowはdefault 0でCookie共有OFFとなる。このadditive refinementだけを理由としたdatabase version bumpは行わない。

ADR-0248では同じtableへ `share_referrer_path_for_playback INTEGER NOT NULL DEFAULT 0` をadditiveに追加する。fresh schemaはcolumnを最初から持ち、既存databaseではidempotent schema initializerが不足列だけを追加する。既存rowはdefault 0でReferer path共有OFFとなり、この追加だけを理由としたdatabase version bumpは行わない。

ADR-0242でapplication database versionを31へ進め、購読型provider用tableを追加する。version 30 -> 31 migrationでは、旧専用subscription/video tableが存在する場合だけprovider設定、subscription、item identity、publish time、read / watch-later stateをVideo-owned stateへ取り込む。旧tableはmigration inputとしてのみ参照し、fresh schemaでは作成せずcurrent runtimeからも参照しない。version 31 schema initializationでもADR-0241とADR-0248のadditive rule column refinementはidempotentに適用される。

ADR-0246でapplication database versionを32へ進め、`video_providers.function_code` を追加する。version 31 -> 32 migrationは既存provider row、subscription、provider item stateを保持したまま不足columnだけを追加する。組み込みproviderはfunction codeを持たず、custom providerだけがfunction codeをdurable user configurationとして保持する。backup restoreのexact-version policyは変更しない。

## Playback state semantics

再生位置、duration、最終再生日時、completed stateをitem単位で保存する。

- durationが利用可能な場合、再生位置が95%以上になるとcompletedを自動設定する。
- completedはUIから手動変更できる。
- 手動で未視聴へ戻しても保存済みposition / durationは維持する。
- 「続き」はpositionが0より大きくcompletedでないitemを対象とする。
- 保存済み判定 / 明示保存 / folder移動では再生状態を変更しない。

Video再生はRSS/Contentの既読状態、Bookmark / Read Later membership、Library book stateを書き換えない。

providerのread stateとplayback completed stateは別の状態である。provider itemを既読にしてもcompletedへ変更せず、再生完了によってprovider itemを自動既読にしない。

## Invariants

- VideoはLibrary-owned SMB credential/connection profileを共同所有しない。
- Videoのshare / root pathはVideo-owned `video_smb_sources` に保存する。
- Videoは通常runtimeでLibrary-owned SMB tableを直接read/writeしない。
- `SmbMediaFileAccess` の外へcredentialやhost/user情報を公開しない。
- SMB thumbnail生成のために第二のSMB read implementationを作らず、既存 `VideoByteSourceFactory` / `SmbMediaFileAccess` boundaryを再利用する。
- SMB thumbnailはdurable source of truthにせず、生成失敗をcatalog同期・再生失敗へ昇格させない。
- SMB directory hierarchyを第二のdurable source of truthとして保存せず、source identityと同期rootから派生させる。
- SMB directory projectionでも `..` を拒否する同期root boundaryを弱めない。
- SMB / Web itemはcatalogに存在する限り保存済みとして扱い、`video_saved_items` rowの有無だけで保存画面から除外しない。
- SMB itemをVideo専用folderへ移動せず、元directory階層を優先する。
- stream URLをdurable source of truthにしない。
- Web streamの実request参照元を利用する場合もstream URLとの同一requestだけを採用し、別requestの参照元を推測で流用しない。
- exact stream requestの参照元を観測できない場合だけplayback extractorのexplicit `referrerUrl`を利用する。既定ではorigin-onlyとし、ruleの明示opt-in時だけexplicit参照元のpathを保持できる。
- Referer path共有の既定値はOFFとし、既存ruleを自動的にONへしない。
- Referer path共有をONにしてもquery / fragment / userinfoを送らず、`Origin`は常にorigin-onlyを維持する。
- WebView実request由来Refererと元page fallbackをReferer path共有設定で拡張しない。
- WebView request CookieをcaptureするのはCookie共有ONかつ `COOKIE_INTERCEPT` 対応時だけとする。
- 観測したrequest Cookieはstream URLとの完全一致requestだけで優先し、別URLへ推測で流用しない。
- request interceptionを `Authorization` や任意credential headerのcaptureへ一般化しない。
- WebView CookieをMedia3へ共有するのはruleで明示opt-inされたforeground playbackだけとする。
- Cookie共有の既定値はOFFとし、既存ruleを自動的にONへしない。
- Cookie値 / Authorization等のcredentialをdurable state、log、error UIへ複製しない。
- default WebView profileのCookieをVideo extractor profileへ混ぜない。
- Video用の第二のSMB credential storeを作らない。
- Video保存状態にCuration-owned tableを利用しない。
- フォルダ削除で明示保存済みSERVICE itemを自動的に保存解除しない。
- 保存状態の変更でplayback stateを変更しない。
- Web単発登録へsubscription / unread semanticsを持ち込まない。
- provider adapterごとにsubscription / unread / refresh persistenceを複製しない。
- custom provider codeへdatabase、filesystem、他Context repository、既存credentialを公開しない。
- custom providerの外部requestはboundedなHTTPS `api.fetch` 経由とし、credential headerを暗黙または設定値から送らない。
- custom provider function codeをcredential storeとして扱わない。
- provider refreshで既存itemのread / playback / saved stateを上書きしない。
- subscription解除で保存済みまたは再生履歴を持つVideo itemを暗黙に削除しない。
- migration完了後のcurrent runtimeで旧provider専用tableを参照しない。
- foreground video playbackをAudioのbackground media sessionへ暗黙に統合しない。
- user-authored Web extractor function、custom provider function、実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Web extractor ruleのglob matching / precedenceをunit testする。
- Video用SMB sourceの保存・列挙・削除、SMB catalog projection、stale item削除、playback state semanticsをrepository testする。
- SMB / Web / SERVICEの保存済み判定をDomain unit testする。
- SMB source identityと同期rootから相対directoryを導出し、重複rootでは最も深いrootを採用することをDomain unit testする。
- 保存済みbrowser projectionでroot、SMB child directory、custom folder、Web / SERVICE混在をunit testする。
- SERVICEの保存 / 保存解除、Web / SERVICEの保存先変更、folder CRUD、folder削除時の未分類化、同名folder拒否をrepository testする。
- 保存状態とplayback stateが独立していることをrepository testする。
- provider設定、enabled state、subscription CRUD、refresh、未読 / 既読 / watch-later stateをrepository testする。
- 組み込みprovider feed adapterのURL検証、feed endpoint、response parsing、要求source IDとの一致をunit testする。
- custom providerを複数保存でき、function code更新でprovider identityを維持することをrepository testする。
- custom providerのsubscribe / refreshが共通 `VideoProviderRepository` lifecycleを利用し、source IDとitem stateを共通projectionへ渡すことをrepository testする。
- custom provider runtimeのHTTPS制約、credential header拒否、request / response size、request回数、timeout、戻り値validationをruntime boundary testの対象とする。
- provider refreshで既存item stateを保持し、新規itemだけを未読として追加することをrepository testする。
- subscription解除時のretention ruleをrepository testする。
- SMB source IDがserver/share/pathを区別し、旧shareなしIDも読み取れることをunit testする。
- Web page fallbackとSMB byte sourceのoffset read委譲をunit testする。
- SMB thumbnailは同じcache keyならSMBを再読込しないこと、表示時のresolution成功を一覧へ反映すること、生成失敗を一覧エラーへ昇格させないことをunit testする。
- Web streamは実requestとstream URLが一致する場合だけ観測Referer originを優先し、exact requestがない場合はexplicit `referrerUrl`、それもない場合は元page originへfallbackすることをunit testする。
- Referer path共有OFFではexplicit参照元をoriginへ縮約し、ONではexplicit参照元のpathだけを保持してquery / fragment / userinfoを除去することをunit testする。
- path付きRefererでも `Origin` はorigin-onlyであることをunit testする。
- Cookie共有OFFではproviderとrequest Cookie captureを作動させず、ONでは完全一致requestの観測Cookieをprofile lookupより優先することをunit testする。
- 観測CookieがないURLではprofile CookieManager lookupへfallbackし、別URLの観測Cookieを流用しないことをunit testする。
- `video_web_extractor_rules` のCookie共有booleanとReferer path共有booleanを保存・復元し、既存schema refinementでともにdefault OFFになることをrepository testする。
- app database fresh schema、version 28 -> 29、version 29 -> 30、version 30 -> 31、version 31 -> 32をtestする。
- architecture verificationでmodule graph、table ownership、migration foreign-read allowlist、navigation ownershipを検証する。
- Android実機では保存済みタブでSMB同期root / 子directory / 動画を辿れること、directoryと動画が同じ一覧へ並ぶこと、Web動画が未分類または整理folderへ表示されること、SERVICE動画の明示保存 / 保存解除、folder CRUDに加え、LibraryとVideoで異なるSMB share/path、SMB動画thumbnail表示、Web stream、Cookie共有OFF/ON、Referer path共有OFF/ON、custom providerの追加・購読・background refresh、全画面、seek、resumeを確認する。

## Sources

- [ADR-0237](../adr/0237-video-library-and-web-extraction.md)
- [ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)
- [ADR-0240](../adr/0240-video-saved-items-and-folders.md)
- [ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md)
- [ADR-0242](../adr/0242-video-subscription-providers.md)
- [ADR-0243](../adr/0243-video-web-request-cookie-capture.md)
- [ADR-0244](../adr/0244-video-intrinsic-saved-sources-and-file-browser.md)
- [ADR-0245](../adr/0245-video-playback-explicit-referrer-origin.md)
- [ADR-0246](../adr/0246-video-custom-provider-code.md)
- [ADR-0248](../adr/0248-video-playback-referrer-path-opt-in.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [persistence.md](persistence.md)
- [platform.md](platform.md)
- [web-content.md](web-content.md)
- [audio-playback.md](audio-playback.md)
- `feature/video/`
- `feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt`
