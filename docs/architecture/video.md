# Video

この文書は Video feature の current architecture を示す。設計判断の履歴は [ADR-0237](../adr/0237-video-library-and-web-extraction.md) と [ADR-0238](../adr/0238-shared-smb-connection-profiles-and-feature-locations.md) を参照する。

## Ownership

Video は SMB / Web / 将来の service adapter 由来動画を同じ catalog へ投影し、次を所有する。

- Video item identity / source type / title / thumbnail URL
- Video用SMB同期場所（connection profile ID / share / root path）
- Web page URL identity
- Web video extractor rule
- 再生位置 / duration / 最終再生日時 / completed state
- foreground video playback presentation

現在の module は次のとおり。

```text
:feature:video:domain
:feature:video:data
:feature:video:ui
```

`:feature:video:domain` は `VideoItem`、`VideoSmbSource`、`VideoPlaybackState`、`WebVideoExtractorRule`、`VideoRepository`、`VideoPlaybackResolver`、`VideoByteSourceFactory` 等の contract を所有する。

`:feature:video:data` は Video-owned database schema、Video用SMB同期場所、Web metadata取得、WebView extractor、SMB catalog projection、playback target resolutionを所有する。

`:feature:video:ui` は一覧、source filter、「続き」「視聴済み」、設定、Media3 foreground playerを所有する。

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

custom ruleはVideo-owned durable user dataであり、URL glob patternと任意のPromise-returning JavaScript functionを保存する。

- title extractor
- thumbnail extractor
- playback extractor

WebViewは専用profileを利用し、HTTPS pageだけを対象とする。file/content access、mixed content、window生成、geolocation等を有効化しない。native JavaScript bridgeは公開しない。

custom title / thumbnail extractionが失敗した場合は静的metadataを維持する。playback extractorが利用できない、または結果を取得できない場合はpage URLをWeb表示するtargetへfallbackする。

## Playback

再生targetはDomainで次の3種類へ正規化する。

- `VideoPlaybackTarget.Stream`: HTTP(S)等のMedia3再生可能URL
- `VideoPlaybackTarget.Smb`: Library-owned SMB read capabilityを使うrandom-access source
- `VideoPlaybackTarget.WebPage`: アプリ内video playerではなくWeb pageを開くfallback

v1のVideo playerはforeground UI lifetimeとする。Audio featureの `MediaSessionService` を再利用または複製せず、background audio continuation、Cast、download、transcodingは対象外とする。

## Durable data

Video Dataは次のtableを所有する。

- `video_items`
- `video_playback_state`
- `video_smb_sources`
- `video_web_extractor_rules`

`video_items` はcatalog projection、`video_playback_state` はユーザーの視聴継続状態、`video_smb_sources` はVideo用SMB同期場所、`video_web_extractor_rules` はユーザー設定として保存する。

stream URLとSMB credentialはVideoのdurable stateに含めない。

SMB接続プロファイルはLibrary-owned `smb_connection_profiles` に保存し、Library用SMB同期場所は既存 `smb_library_servers` のshare / root pathとして維持する。Videoはこれらのforeign tableを通常runtimeで直接read/writeしない。

`video_smb_sources` と `smb_connection_profiles` の追加によりapplication database versionは29である。version 28 -> 29 migrationでは、従来Videoが暗黙利用していた `smb_library_servers` のshare / root pathを初期 `video_smb_sources` として一度だけ取り込む。このmigrationだけは明示されたforeign-table read allowlistを利用し、version 28 upgrade baselineの退役時に削除する。

## Playback state semantics

再生位置、duration、最終再生日時、completed stateをitem単位で保存する。

- durationが利用可能な場合、再生位置が95%以上になるとcompletedを自動設定する。
- completedはUIから手動変更できる。
- 手動で未視聴へ戻しても保存済みposition / durationは維持する。
- 「続き」はpositionが0より大きくcompletedでないitemを対象とする。

Video再生はRSS/Contentの既読状態、Bookmark / Read Later membership、Library book stateを書き換えない。

## Invariants

- VideoはLibrary-owned SMB credential/connection profileを共同所有しない。
- Videoのshare / root pathはVideo-owned `video_smb_sources` に保存する。
- Videoは通常runtimeでLibrary-owned SMB tableを直接read/writeしない。
- `SmbMediaFileAccess` の外へcredentialやhost/user情報を公開しない。
- stream URLをdurable source of truthにしない。
- Video用の第二のSMB credential storeを作らない。
- foreground video playbackをAudioのbackground media sessionへ暗黙に統合しない。
- user-authored Web extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Web extractor ruleのglob matching / precedenceをunit testする。
- Video用SMB sourceの保存・列挙・削除、SMB catalog projection、stale item削除、playback state semanticsをrepository testする。
- SMB source IDがserver/share/pathを区別し、旧shareなしIDも読み取れることをunit testする。
- Web page fallbackとSMB byte sourceのoffset read委譲をunit testする。
- app database fresh schemaとversion 28 -> 29 migrationをtestする。
- architecture verificationでmodule graph、table ownership、migration foreign-read allowlist、navigation ownershipを検証する。
- Android実機ではLibraryとVideoで異なるSMB share/pathを指定し、MP4/MKV、Web stream、Web fallback、seek、resumeを確認する。

## Sources

- [ADR-0237](../adr/0237-video-library-and-web-extraction.md)
- [ADR-0238](../adr/0238-shared-smb-connection-profiles-and-feature-locations.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [persistence.md](persistence.md)
- [platform.md](platform.md)
- [web-content.md](web-content.md)
- [audio-playback.md](audio-playback.md)
- `feature/video/`
- `feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt`
