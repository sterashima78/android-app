# Video

この文書は Video feature の current architecture を示す。設計判断の履歴は [ADR-0237](../adr/0237-video-library-and-web-extraction.md)、[ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)、[ADR-0240](../adr/0240-video-saved-items-and-folders.md)、[ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md)、[ADR-0242](../adr/0242-video-subscription-providers.md) を参照する。

## Ownership

Video は SMB / Web / 購読型provider由来動画を同じ catalog へ投影し、次を所有する。

- Video item identity / source type / title / thumbnail URL
- Video用SMB同期場所（connection profile ID / share / root path）
- Web page URL identity
- Web video extractor rule
- 購読型provider設定、subscription、provider item identity / publish time
- provider item の unread / read / watch-later state
- provider refresh lifecycle
- 再生位置 / duration / 最終再生日時 / completed state
- 保存済み動画とVideo専用フォルダ
- foreground video playback presentation
- SMB動画から再生成できるthumbnail cache

現在の module は次のとおり。

```text
:feature:video:domain
:feature:video:data
:feature:video:ui
```

`:feature:video:domain` は `VideoItem`、`VideoSmbSource`、`VideoPlaybackState`、`VideoSavedState`、`VideoFolder`、`VideoProvider`、`VideoSubscription`、`VideoProviderVideo`、`WebVideoExtractorRule`、`VideoRepository`、`VideoProviderRepository`、`VideoPlaybackResolver`、`VideoThumbnailResolver`、`VideoByteSourceFactory` 等の contract を所有する。

`:feature:video:data` は Video-owned database schema、Video用SMB同期場所、保存状態・フォルダ、Web metadata取得、WebView extractor、SMB catalog projection、provider adapter / feed parsing / refresh、playback target resolution、SMB動画のthumbnail cache生成を所有する。

`:feature:video:ui` は一覧、source filter、「続き」「保存」「視聴済み」、保存フォルダ管理、provider / subscription設定、provider未読確認、設定、Media3 foreground playerを所有する。一覧へ入ったSMB動画カードだけthumbnail resolutionを要求し、同期時に全動画を事前生成しない。

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

SMB再生では全動画を端末へ事前downloadせず、offset readを `VideoByteSource` / Media3 `DataSource` へ接続する。

SMB thumbnailも同じrandom-access boundaryを再利用する。`VideoThumbnailResolver` は `VideoByteSourceFactory` を `MediaDataSource` へadapterし、`MediaMetadataRetriever` で動画の冒頭寄りの代表frameを取得する。元動画全体を端末へ保存せず、最大辺640pxのJPEGをapplication cache配下へ保存する。生成は一覧で表示対象になった項目に限定し、同時生成数を制限する。

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

custom ruleはVideo-owned durable user dataであり、URL glob pattern、任意のPromise-returning JavaScript function、再生時Cookie共有のopt-in設定を保存する。

- title extractor
- thumbnail extractor
- playback extractor
- `shareCookiesForPlayback`: default false

WebViewは専用profileを利用し、HTTPS pageだけを対象とする。file/content access、mixed content、window生成、geolocation等を有効化しない。native JavaScript bridgeは公開しない。Cookie共有の設定にかかわらずthird-party Cookie acceptanceを暗黙に有効化しない。

custom title / thumbnail extractionが失敗した場合は静的metadataを維持する。playback extractorが利用できない、または結果を取得できない場合はpage URLをWeb表示するtargetへfallbackする。

Web URL登録は1件のVideo itemを明示的に追加する操作であり、購読、未読、background refresh semanticsを持たない。購読型providerとは別のlifecycleとして扱う。

## Subscription providers

購読型providerは、provider設定・subscription・取得済みitem stateをVideo Context内で共通化する。provider adapterは外部source固有の入力正規化、endpoint解決、response parsingだけを担当し、subscription / unread / refresh lifecycleを独自実装しない。

```text
Video Settings
   |
   +---- provider enabled state
   +---- subscription source URL
   |
   v
VideoProviderRepository
   |
   +---- video_providers
   +---- video_subscriptions
   +---- provider adapter ----> bounded feed fetch / parse
   |
   v
video_provider_items + video_items
   |
   +---- unread / read / watch later
   +---- playback / saved state は既存Video stateを利用
```

新しく取得したprovider itemは `VideoSource.SERVICE` の `video_items` として投影し、`video_provider_items` でprovider item ID、subscription、publish time、未読状態を保持する。既存itemのrefreshではread / watch-later stateを上書きしない。

provider itemのidentityはprovider IDとprovider固有item IDの組み合わせで一意にする。subscription identityはprovider IDとprovider固有source IDの組み合わせで一意にする。異なるprovider間のitem/source ID衝突を許容する。

providerを無効化するとbackground refresh対象から外すが、設定と取得済みitemは保持する。subscription解除時、未保存かつ再生履歴のないitemはcatalogから削除できる。保存済みまたは再生履歴を持つitemはsubscription membershipだけを失い、Video itemとして保持する。

統合background refreshは個別sourceのrepositoryを直接更新せず `VideoProviderRepository.refreshProviders()` を呼ぶ。subscription単位の失敗は他subscriptionの取得を妨げない。新規itemはVideo providerの未読として通知対象へ加わる。

移行前の専用subscription/video persistenceとtop-level navigationはcurrent runtimeのsource of truthとして使用しない。既存installの状態はversion 30 -> 31 migrationでVideo-owned provider stateへ一度だけ取り込む。

## Playback

再生targetはDomainで次の3種類へ正規化する。

- `VideoPlaybackTarget.Stream`: HTTP(S)等のMedia3再生可能URL。Web item由来の場合は元pageのoriginと、明示opt-in時だけ一時的なCookie providerを持てる。
- `VideoPlaybackTarget.Smb`: Library-owned SMB read capabilityを使うrandom-access source
- `VideoPlaybackTarget.WebPage`: アプリ内video playerではなくWeb pageを開くfallback

Web streamをMedia3で直接再生する場合、ブラウザ埋め込み再生と同等の最低限のHTTP文脈を再現するため、元pageのoriginだけから `Referer` と `Origin` を生成し、Android WebViewのdefault user agentを `User-Agent` としてmanifest / segment requestへ付与する。`Referer` はorigin root URL、`Origin` はscheme / host / optional portだけとし、元pageのpath、query、fragmentはどちらにも含めない。これらは再生時だけ生成・利用し、databaseへ保存しない。

一致した抽出ルールで `shareCookiesForPlayback` が有効な場合だけ、抽出に使った専用WebView profileのCookieManagerを `VideoPlaybackCookieProvider` としてtransientに再生targetへ接続する。Cookie文字列をtargetの通常data fieldへコピーせず、Media3のHTTP DataSourceが各request URLを処理する時点でproviderへ問い合わせる。CookieManager自身のhost / path / Secure等の選択に従い、そのURLへ適用されるCookieだけを `Cookie` headerとして付与する。provider取得が失敗した場合はCookieを付けずにrequestを継続する。

Cookie共有がOFFの場合はproviderを作らず、従来どおりCookieを送らない。Cookie値、Authorization、その他のcredentialをdatabase、backup、export、log、error UIへ保存・表示しない。`Authorization` 等の共有へ一般化しない。

v1のVideo playerはforeground UI lifetimeとする。全画面表示では横向きへ切り替え、通常表示へ戻ると縦向きへ復帰する。Audio featureの `MediaSessionService` を再利用または複製せず、background audio continuation、Cast、download、transcodingは対象外とする。

## Saved items and folders

保存は動画本体のdownloadではなく、Video catalog itemに対する整理状態である。`video_saved_items` rowの存在を保存済みのsource of truthとし、再生位置やcompleted stateとは独立して扱う。

保存済み動画は最大1つのVideo専用フォルダへ所属する。

- `folder_id = NULL`: 未分類
- `folder_id` が存在: 対応する `video_folders` へ所属

フォルダ削除時は保存rowを削除せず `folder_id` をNULLへ戻す。保存、保存解除、保存先変更、フォルダ操作によって `video_playback_state` は変更しない。

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

`video_items` はcatalog projection、`video_playback_state` はユーザーの視聴継続状態、`video_smb_sources` はVideo用SMB同期場所、`video_folders` / `video_saved_items` は保存と整理状態、`video_web_extractor_rules` はユーザー設定、`video_providers` / `video_subscriptions` / `video_provider_items` は購読型providerの設定・membership・item stateとして保存する。Cookie共有のboolean設定はruleのdurable stateだが、Cookie値そのものは保存しない。

stream URL、stream request context（referrer / origin / user agent / cookie value）、動画ファイル本体、SMB credential、生成済みSMB thumbnail fileはVideoのdurable stateに含めない。SMB thumbnailはapplication cache内の派生データでありbackup / export対象にしない。

SMB接続プロファイルはLibrary-owned `smb_connection_profiles` に保存し、Library用SMB同期場所は既存 `smb_library_servers` のshare / root pathとして維持する。Videoはこれらのforeign tableを通常runtimeで直接read/writeしない。

ADR-0239で `video_smb_sources` と `smb_connection_profiles` を追加してapplication database versionを29とし、version 28 -> 29 migrationで従来Videoが暗黙利用していた `smb_library_servers` のshare / root pathを初期 `video_smb_sources` として一度だけ取り込む。このmigrationだけは明示されたforeign-table read allowlistを利用する。

ADR-0240でapplication database versionを30へ進め、`video_folders` と `video_saved_items` を追加する。version 29 -> 30では既存Video itemを自動保存せず、保存状態とフォルダは空から開始する。

ADR-0241では `video_web_extractor_rules` に `share_cookies_for_playback INTEGER NOT NULL DEFAULT 0` をadditiveに追加する。fresh schemaはcolumnを最初から持ち、既存version 30 databaseはVideoのidempotent schema initializerが不足列だけを追加する。既存rowはdefault 0でCookie共有OFFとなる。このadditive refinementだけを理由としたdatabase version bumpは行わない。

ADR-0242でapplication database versionを31へ進め、購読型provider用tableを追加する。version 30 -> 31 migrationでは、旧専用subscription/video tableが存在する場合だけprovider設定、subscription、item identity、publish time、read / watch-later stateをVideo-owned stateへ取り込む。旧tableはmigration inputとしてのみ参照し、fresh schemaでは作成せずcurrent runtimeからも参照しない。version 31 schema initializationでもADR-0241のCookie列refinementはidempotentに適用される。

## Playback state semantics

再生位置、duration、最終再生日時、completed stateをitem単位で保存する。

- durationが利用可能な場合、再生位置が95%以上になるとcompletedを自動設定する。
- completedはUIから手動変更できる。
- 手動で未視聴へ戻しても保存済みposition / durationは維持する。
- 「続き」はpositionが0より大きくcompletedでないitemを対象とする。
- 保存 / 保存解除 / folder移動では再生状態を変更しない。

Video再生はRSS/Contentの既読状態、Bookmark / Read Later membership、Library book stateを書き換えない。

providerのread stateとplayback completed stateは別の状態である。provider itemを既読にしてもcompletedへ変更せず、再生完了によってprovider itemを自動既読にしない。

## Invariants

- VideoはLibrary-owned SMB credential/connection profileを共同所有しない。
- Videoのshare / root pathはVideo-owned `video_smb_sources` に保存する。
- Videoは通常runtimeでLibrary-owned SMB tableを直接read/writeしない。
- `SmbMediaFileAccess` の外へcredentialやhost/user情報を公開しない。
- SMB thumbnail生成のために第二のSMB read implementationを作らず、既存 `VideoByteSourceFactory` / `SmbMediaFileAccess` boundaryを再利用する。
- SMB thumbnailはdurable source of truthにせず、生成失敗をcatalog同期・再生失敗へ昇格させない。
- stream URLをdurable source of truthにしない。
- Web streamの `Referer` / `Origin` に元pageのpath / query / fragmentを含めない。
- WebView CookieをMedia3へ共有するのはruleで明示opt-inされたforeground playbackだけとする。
- Cookie共有の既定値はOFFとし、既存ruleを自動的にONへしない。
- Cookie値 / Authorization等のcredentialをdurable state、log、error UIへ複製しない。
- default WebView profileのCookieをVideo extractor profileへ混ぜない。
- Video用の第二のSMB credential storeを作らない。
- Video保存状態にCuration-owned tableを利用しない。
- フォルダ削除で保存済み動画を自動的に保存解除しない。
- 保存状態の変更でplayback stateを変更しない。
- Web単発登録へsubscription / unread semanticsを持ち込まない。
- provider adapterごとにsubscription / unread / refresh persistenceを複製しない。
- provider refreshで既存itemのread / playback / saved stateを上書きしない。
- subscription解除で保存済みまたは再生履歴を持つVideo itemを暗黙に削除しない。
- migration完了後のcurrent runtimeで旧provider専用tableを参照しない。
- foreground video playbackをAudioのbackground media sessionへ暗黙に統合しない。
- user-authored Web extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Web extractor ruleのglob matching / precedenceをunit testする。
- Video用SMB sourceの保存・列挙・削除、SMB catalog projection、stale item削除、playback state semanticsをrepository testする。
- 保存 / 保存解除、保存先変更、folder CRUD、folder削除時の未分類化、同名folder拒否をrepository testする。
- 保存状態とplayback stateが独立していることをrepository testする。
- provider設定、enabled state、subscription CRUD、refresh、未読 / 既読 / watch-later stateをrepository testする。
- provider feed adapterのURL検証、通常動画feed endpoint、XML parsing、要求source IDとの一致をunit testする。
- provider refreshで既存item stateを保持し、新規itemだけを未読として追加することをrepository testする。
- subscription解除時のretention ruleをrepository testする。
- SMB source IDがserver/share/pathを区別し、旧shareなしIDも読み取れることをunit testする。
- Web page fallbackとSMB byte sourceのoffset read委譲をunit testする。
- SMB thumbnailは同じcache keyならSMBを再読込しないこと、表示時のresolution成功を一覧へ反映すること、生成失敗を一覧エラーへ昇格させないことをunit testする。
- Web streamの元page originを `Referer` / `Origin` のMedia3 HTTP request propertyへ変換し、path / query / fragmentを送らないことをunit testする。
- Cookie共有OFFではproviderを作らず、ONの場合だけrequest URLごとにprofile Cookie lookupすることをunit testする。
- `video_web_extractor_rules` のCookie共有booleanを保存・復元し、既存schema refinementでdefault OFFになることをrepository testする。
- app database fresh schema、version 28 -> 29、version 29 -> 30、version 30 -> 31をtestする。
- architecture verificationでmodule graph、table ownership、migration foreign-read allowlist、navigation ownershipを検証する。
- Android実機では保存タブ、未分類 / folder filter、保存先変更、folder CRUD、provider有効化・subscription追加・refresh・未読状態に加え、LibraryとVideoで異なるSMB share/path、SMB動画thumbnail表示、Web stream、Cookie共有OFF/ON、全画面、seek、resumeを確認する。

## Sources

- [ADR-0237](../adr/0237-video-library-and-web-extraction.md)
- [ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)
- [ADR-0240](../adr/0240-video-saved-items-and-folders.md)
- [ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md)
- [ADR-0242](../adr/0242-video-subscription-providers.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [persistence.md](persistence.md)
- [platform.md](platform.md)
- [web-content.md](web-content.md)
- [audio-playback.md](audio-playback.md)
- `feature/video/`
- `feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt`
