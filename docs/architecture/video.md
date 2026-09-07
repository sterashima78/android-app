# Video

この文書は Video feature の current architecture を示す。設計判断の履歴は [ADR-0237](../adr/0237-video-library-and-web-extraction.md) を参照する。

## Ownership

Video は SMB / Web / 将来の service adapter 由来動画を同じ catalog へ投影し、次を所有する。

- Video item identity / source type / title / thumbnail URL
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

`:feature:video:domain` は `VideoItem`、`VideoPlaybackState`、`WebVideoExtractorRule`、`VideoRepository`、`VideoPlaybackResolver`、`VideoByteSourceFactory` 等の contract を所有する。

`:feature:video:data` は Video-owned database schema、Web metadata取得、WebView extractor、SMB catalog projection、playback target resolutionを所有する。

`:feature:video:ui` は一覧、source filter、「続き」「視聴済み」、設定、Media3 foreground playerを所有する。

## Library SMB boundary

SMB server settings と credential は Library Context が引き続き所有する。Video は server host、username、password等を自身のtableへ複製しない。

Library Domain は `SmbMediaFileAccess` を read-only capability として公開する。

```text
Video Data
   |
   | SmbMediaFileAccess
   v
Library Data
   |
   +---- smb_library_servers
   +---- encrypted Library SMB credential
   |
   v
SMBJ
```

`SmbMediaFileAccess` は動画候補の列挙と、serverId/pathで指定したファイルのrandom-access readだけを公開する。credential値はcontractを越えない。

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

- `VideoPlaybackTarget.Stream`: HTTP(S)等のMedia3再生可能URL。Web item由来の場合は元pageのoriginをtransientなreferrer contextとして持てる。
- `VideoPlaybackTarget.Smb`: Library-owned SMB read capabilityを使うrandom-access source
- `VideoPlaybackTarget.WebPage`: アプリ内video playerではなくWeb pageを開くfallback

Web streamをMedia3で直接再生する場合、ブラウザ埋め込み再生と同等の最低限のHTTP文脈を再現するため、元pageのoriginのみを `Referer` として、Android WebViewのdefault user agentを `User-Agent` としてmanifest / segment requestへ付与する。path、query、fragmentはreferrerへ含めない。これらは再生時だけ生成・利用し、databaseへ保存しない。Cookie、Authorization、その他のcredentialはWebViewからMedia3へ引き継がない。

v1のVideo playerはforeground UI lifetimeとする。Audio featureの `MediaSessionService` を再利用または複製せず、background audio continuation、Cast、download、transcodingは対象外とする。

## Durable data

Video Dataは次のtableを所有する。

- `video_items`
- `video_playback_state`
- `video_web_extractor_rules`

`video_items` はcatalog projection、`video_playback_state` はユーザーの視聴継続状態、`video_web_extractor_rules` はユーザー設定として保存する。

stream URL、stream referrer、WebView user agent、SMB credentialはVideoのdurable stateに含めない。

新しいschema contributionはapp database schemaへ登録する。Video tableを既存installへ追加するためdatabase versionを28へ更新する。

## Playback state semantics

再生位置、duration、最終再生日時、completed stateをitem単位で保存する。

- durationが利用可能な場合、再生位置が95%以上になるとcompletedを自動設定する。
- completedはUIから手動変更できる。
- 手動で未視聴へ戻しても保存済みposition / durationは維持する。
- 「続き」はpositionが0より大きくcompletedでないitemを対象とする。

Video再生はRSS/Contentの既読状態、Bookmark / Read Later membership、Library book stateを書き換えない。

## Invariants

- VideoはLibrary-owned SMB credential/server tableを共同所有しない。
- VideoはYouTube/Library等のforeign durable tableを直接read/writeしない。
- stream URLをdurable source of truthにしない。
- Web streamのreferrerに元pageのpath / query / fragmentを含めない。
- WebView Cookie / Authorization等のcredentialをMedia3へ暗黙に複製しない。
- Video用の第二のSMB credential storeを作らない。
- foreground video playbackをAudioのbackground media sessionへ暗黙に統合しない。
- user-authored Web extractor functionや実URLをpublic repositoryのfixture/documentへ保存しない。

## Verification

- Web extractor ruleのglob matching / precedenceをunit testする。
- SMB catalog projection、stale item削除、playback state semanticsをrepository testする。
- Web page fallbackとSMB byte sourceのoffset read委譲をunit testする。
- Web streamのreferrerをoriginへ縮小し、Media3 HTTP request propertyへ変換することをunit testする。
- app database fresh schemaとschema versionをtestする。
- architecture verificationでmodule graph、table ownership、navigation ownershipを検証する。
- Android実機ではSMB MP4/MKV、Web stream、Web fallback、seek、resumeを確認する。

## Sources

- [ADR-0237](../adr/0237-video-library-and-web-extraction.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [persistence.md](persistence.md)
- [platform.md](platform.md)
- [web-content.md](web-content.md)
- [audio-playback.md](audio-playback.md)
- `feature/video/`
- `feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt`
