# ADR-0237: Video Context で SMB / Web 動画カタログと再生を所有する

- Status: Accepted
- Date: 2026-09-07
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0180](0180-rss-custom-web-scraping-rules.md), [ADR-0235](0235-summary-audio-playback.md)
- Amends: [ADR-0138](0138-database-v27-compatibility-baseline.md)
- Amended by: [ADR-0241](0241-video-web-stream-cookie-opt-in.md)

## Context

Mosaic で、SMB ファイルサーバー上の動画、Web ページ由来の動画、将来のサービス固有動画一覧を同じ Video surface で閲覧・再生したい。

Web ページは単なる動画ブックマークとして外部ページを開く場合と、ページ内 JavaScript から再生可能な stream URL を取得してアプリ内で再生できる場合がある。サムネイルは既定で OGP / HTML metadata を利用するが、URL pattern ごとに端末上の JavaScript で取得方法を上書きできる必要がある。

Library は SMB credential / server settings と Web metadata extractor を、RSS は Web scraping rule をそれぞれ owning Context 内で所有している。Audio は Media3 / foreground media playback runtime の既存例を持つ。

Video は新しい durable table を追加するため、ADR-0047 に従って application database version も更新する必要がある。ADR-0138 は version 27 を直前の更新互換性 baseline とし、backup restore は current application schema version と snapshot version の完全一致を要求している。

## Decision

### Video を独立 Context とする

`feature:video:{domain,data,ui}` を追加する。Video は次を所有する。

- Video catalog item と source type
- Web page URL identity
- Web video extractor rule
- Web metadata projection / thumbnail URL
- playback position / completed state
- SMB 動画の catalog projection
- Media3 video playback UI

Library / YouTube 等の foreign durable table を直接 read/write しない。

### Web item は page URL を durable identity とする

Web 由来 VideoItem は HTTP(S) page URL を source identity とする。stream URL は期限付き URL の可能性があるため durable source of truth にしない。

登録時は static HTTP metadata から title と OGP thumbnail を取得する。対応する custom rule が存在する場合は dedicated WebView で rule を実行し、取得値を override できる。

### Web extractor rule は Video-owned durable user data とする

Video Data は URL pattern ごとの extractor rule を保存する。

- rule ID
- HTTPS URL glob pattern
- optional title extractor function
- optional thumbnail extractor function
- optional playback extractor function
- timeout seconds
- updated timestamp

function は page context で実行する Promise-returning JavaScript function expression とする。実 URL / function code は user data であり public repository の source / fixture に保存しない。

pattern matching は ADR-0173 と同じ `*` / `?` glob semantics とする。複数一致時は wildcard 以外の文字数が多い rule を優先し、同じ具体度では更新日時の新しい rule を優先する。

### Metadata と playback extraction を分離する

既定 metadata path は static HTTP の OGP / HTML とする。

custom extractor capability は次を独立に設定できる。

- title extractor: `Promise<{ title: string? }>`
- thumbnail extractor: `Promise<{ thumbnailUrl: string? }>`
- playback extractor: `Promise<{ streamUrl: string?, mimeType: string? }>`

同じ page load で複数 extractor が必要な場合は同一 WebView session 内で順次実行する。

custom thumbnail / title extraction が失敗した場合は static metadata を維持する。playback extraction が失敗した場合は VideoItem 自体を失敗扱いにせず Web page playback へ fallback する。

### stream URL は transient resolution とする

再生時に playback extractor が設定されていれば、その時点で stream URL を解決し Media3 へ渡す。解決済み URL は保存せず、次回再生では再度解決する。

playback extractor が設定されていない場合、抽出に失敗した場合、または有効な stream URL を返さない場合は Web page を開く fallback target とする。

### SMB credential ownership は移動しない

Library が既に所有する SMB server settings / credential を Video のために複製しない。初期実装では Library Domain に SMB file listing / random access の narrow capability を追加し、Video Data がその contract を利用する。

credential storage、Keystore、server settings table の ownership は Library に維持する。Video は credential 値を永続化しない。

将来 SMB connection profile が Library / Video 以外でも広く利用される場合だけ独立 Context / core technical capability への ownership 移動を別 ADR で判断する。

### Video playback state は durable user state とする

Video は item ごとに再生位置、duration、最終再生日時、completed state を保存する。

95% 以上再生した item は completed とみなす。UI から手動で未視聴 / 視聴済みに変更できる。

### database version を 28 へ進める

Video-owned `video_items`、`video_playback_state`、`video_web_extractor_rules` を application schema contribution に追加し、database version を 28 とする。

version 27 の現在インストールから version 28 への更新を保証し、upgrade 時に全 feature schema contribution を再適用することで Video table を追加する。version 26 以下からの直接更新を再びサポートしない。

backup restore は ADR-0138 の exact-version policy を維持する。version 28 アプリは version 28 snapshot を復元対象とし、version 27 snapshot を直接復元しない。更新後に生成した通常の自動・手動backupを新しいrestore baselineとする。

### Media3 を利用する

アプリ内動画再生は既存 version catalog の Media3 を利用する。SMB file は全ファイルを端末へ事前 download せず、random-access capable DataSource 経由で再生する。

HTTP動画に加え、Web extractorが返すHLS streamを再生するためMedia3 HLS moduleを利用する。

v1 は foreground UI player を対象とし、background audio continuation / Cast / download / transcoding は追加しない。

## Consequences

- SMB / Web / 将来の service adapter を共通 VideoItem に投影できる。
- Web page bookmark と stream-capable page を同じ identity で扱える。
- サイト変更時は rule 更新だけで追従できる。
- Video-owned catalog、rule、playback state table が durable user data として追加される。
- Library の SMB credential ownership は維持され、duplicate credential state は増えない。
- Library/RSS/Video で user-authored Web extraction semantics は各 Context が所有する一方、WebView execution mechanism の共通化は今回行わない。重複が増えた場合は dedicated technical executor 抽出を別判断とする。
- version 27 のインストールから version 28 への更新は対応するが、version 27 snapshot は version 28 の exact-version restore 対象にはしない。

## Verification

- URL glob matching / precedence を unit test する。
- extractor result validation と Web fallback を unit test する。
- Video schema / repository の catalog、rule、playback state persistence を unit test する。
- version 27 database を version 28 schema で開き、Video table が追加される migration test を行う。
- Library の narrow SMB access contract が credential value を Video へ公開しないことを確認する。
- Media3 playback target mapping を unit test する。
- architecture verification で Video-owned table と module dependency を確認する。
- Android 実機で SMB MP4/MKV、HTTP/HLS stream、Web fallback、seek、resume を確認する。
