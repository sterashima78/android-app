# Video

この文書は Video feature の current architecture を示す。設計判断の履歴は [ADR-0237](../adr/0237-video-library-and-web-extraction.md)、[ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)、[ADR-0240](../adr/0240-video-saved-items-and-folders.md)、[ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md) を参照する。

## Ownership

Video は SMB / Web / 将来の service adapter 由来動画を同じ catalog へ投影し、次を所有する。

- Video item identity / source type / title / thumbnail URL
- Video用SMB同期場所（connection profile ID / share / root path）
- Web page URL identity
- Web video extractor rule
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

`:feature:video:domain` は `VideoItem`、`VideoSmbSource`、`VideoPlaybackState`、`VideoSavedState`、`VideoFolder`、`WebVideoExtractorRule`、`VideoRepository`、`VideoPlaybackResolver`、`VideoThumbnailResolver`、`VideoByteSourceFactory` 等の contract を所有する。

`:feature:video:data` は Video-owned database schema、Video用SMB同期場所、保存状態・フォルダ、Web metadata取得、WebView extractor、SMB catalog projection、playback target resolution、SMB動画のthumbnail cache生成を所有する。

`:feature:video:ui` は一覧、source filter、「続き」「保存」「視聴済み」、保存フォルダ管理、設定、Media3 foreground playerを所有する。一覧へ入ったSMB動画カードだけthumbnail resolutionを要求し、同期時に全動画を事前生成しない。

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
   |
   v
SMBJ
```

`SmbMediaFileAccess` はcallerが指定した `SmbMediaLocation` の動画候補列挙と、その同期範囲内のファイルのrandom-access readだけを公開する。host、username、password等の接続詳細とcredential値はcontractを越えない。

SMB動画のdurable source identityにはconnection profile ID、share、pathを含める。同じserver/pathでもshareが異なるファイルを区別するためである。version 28で作成済みのshareを含まない旧source IDは読み取り互換を維持し、設定されたserver/rootから再生先を解決できる。

SMB再生では全動画を端末へ事前downloadせず、SMBJのoffset readを `VideoByteSource` / Media3 `DataSource` へ接続する。

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

custom ruleはVideo-owned durable user dataであり、URL glob pattern、任意のPromise-returning JavaScript function、再生時Cookie共有のopt-in設定を保存する。

- title extractor
- thumbnail extractor
- playback extractor
- `shareCookiesForPlayback`: default false

WebViewは専用profileを利用し、HTTPS pageだけを対象とする。file/content access、mixed content、window生成、geolocation等を有効化しない。native JavaScript bridgeは公開しない。Cookie共有の設定にかかわらずthird-party Cookie acceptanceを暗黙に有効化しない。

custom title / thumbnail extractionが失敗した場合は静的metadataを維持する。playback extractorが利用できない、または結果を取得できない場合はpage URLをWeb表示するtargetへfallbackする。

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

`video_items` はcatalog projection、`video_playback_state` はユーザーの視聴継続状態、`video_smb_sources` はVideo用SMB同期場所、`video_folders` / `video_saved_items` は保存と整理状態、`video_web_extractor_rules` はユーザー設定として保存する。Cookie共有のboolean設定はruleのdurable stateだが、Cookie値そのものは保存しない。

stream URL、stream request context（referrer / origin / user agent / cookie value）、動画ファイル本体、SMB credential、生成済みSMB thumbnail fileはVideoのdurable stateに含めない。SMB thumbnailはapplication cache内の派生データでありbackup / export対象にしない。

SMB接続プロファイルはLibrary-owned `smb_connection_profiles` に保存し、Library用SMB同期場所は既存 `smb_library_servers` のshare / root pathとして維持する。Videoはこれらのforeign tableを通常runtimeで直接read/writeしない。

ADR-0239で `video_smb_sources` と `smb_connection_profiles` を追加してapplication database versionを29とし、version 28 -> 29 migrationで従来Videoが暗黙利用していた `smb_library_servers` のshare / root pathを初期 `video_smb_sources` として一度だけ取り込む。このmigrationだけは明示されたforeign-table read allowlistを利用する。

ADR-0240でapplication database versionを30へ進め、`video_folders` と `video_saved_items` を追加する。version 29 -> 30では既存Video itemを自動保存せず、保存状態とフォルダは空から開始する。

ADR-0241で `video_web_extractor_rules` に `share_cookies_for_playback INTEGER NOT NULL DEFAULT 0` をadditive refinementとして追加する。application database versionは30のままとし、Videoのidempotent schema initializerが既存version 30 databaseにも不足列を追加する。既存ruleはOFFへ収束する。

## Playback state semantics

再生位置、duration、最終再生日時、completed stateをitem単位で保存する。

- durationが利用可能な場合、再生位置が95%以上になるとcompletedを自動設定する。
- completedはUIから手動変更できる。
- 手動で未視聴へ戻しても保存済みposition / durationは維持する。
- 「続き」はpositionが0より大きくcompletedでないitemを対象とする。
- 保存 / 保存解除 / folder移動では再生状態を変更しない。

Video再生はRSS/Contentの既読状態、Bookmark / Read Later membership、Library book stateを書き換えない。

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
- foreground video playbackをAudioのbackground media sessionへ暗黙に統合しない。
- user-authored Web extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Web extractor ruleのglob matching / precedenceをunit testする。
- Video用SMB sourceの保存・列挙・削除、SMB catalog projection、stale item削除、playback state semanticsをrepository testする。
- 保存 / 保存解除、保存先変更、folder CRUD、folder削除時の未分類化、同名folder拒否をrepository testする。
- 保存状態とplayback stateが独立していることをrepository testする。
- SMB source IDがserver/share/pathを区別し、旧shareなしIDも読み取れることをunit testする。
- Web page fallbackとSMB byte sourceのoffset read委譲をunit testする。
- SMB thumbnailは同じcache keyならSMBを再読込しないこと、表示時のresolution成功を一覧へ反映すること、生成失敗を一覧エラーへ昇格させないことをunit testする。
- Web streamの元page originを `Referer` / `Origin` のMedia3 HTTP request propertyへ変換し、path / query / fragmentを送らないことをunit testする。
- Cookie共有OFFではproviderを作らず、ONの場合だけrequest URLごとにprofile Cookie lookupすることをunit testする。
- `video_web_extractor_rules` のCookie共有booleanを保存・復元し、既存schema refinementでdefault OFFになることをrepository testする。
- app database fresh schema、version 28 -> 29、version 29 -> 30をtestする。
- architecture verificationでmodule graph、table ownership、migration foreign-read allowlist、navigation ownershipを検証する。
- Android実機では保存タブ、未分類 / folder filter、保存先変更、folder CRUDに加え、LibraryとVideoで異なるSMB share/path、SMB動画thumbnail表示、Web stream、Cookie共有OFF/ON、全画面、seek、resumeを確認する。

## Sources

- [ADR-0237](../adr/0237-video-library-and-web-extraction.md)
- [ADR-0239](../adr/0239-shared-smb-connection-profiles-and-feature-locations.md)
- [ADR-0240](../adr/0240-video-saved-items-and-folders.md)
- [ADR-0241](../adr/0241-video-web-stream-cookie-opt-in.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [persistence.md](persistence.md)
- [platform.md](platform.md)
- [web-content.md](web-content.md)
- [audio-playback.md](audio-playback.md)
- `feature/video/`
- `feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt`
