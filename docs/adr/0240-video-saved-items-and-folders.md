# ADR-0240: Video Context で保存済み動画とフォルダを所有する

- Status: Accepted
- Date: 2026-09-08
- Refines: [ADR-0237](0237-video-library-and-web-extraction.md)
- Follows: [ADR-0239](0239-shared-smb-connection-profiles-and-feature-locations.md)

## Context

Video では SMB / Web / 将来の service adapter 由来動画を共通 catalog として扱い、再生位置と視聴済み状態を保存している。一方、ユーザーが後から見返したい動画を視聴状態とは独立して保存し、フォルダへ整理する durable state はまだない。

Curation Context には Bookmark / Folder が存在するが、これらは ContentItem を整理するための Curation-owned state である。VideoItem を整理するために `bookmark_folders` 等を直接利用すると、Video から Curation-owned table への foreign write または Video/Curation 間の不要な共有概念を導入することになる。

ADR-0239 では Video-owned SMB location を追加して application database version を 29 へ進めるため、本変更はその schema を前提とする。

## Decision

### 保存状態とフォルダを Video が所有する

Video Context に次の durable user state を追加する。

- 保存済み動画
- Video 専用フォルダ
- 保存済み動画のフォルダ所属

Curation の Bookmark / Folder table は利用せず、Video Repository contract を通して操作する。

### 保存はダウンロードを意味しない

保存は VideoItem の整理状態だけを表す。動画本体、Web stream URL、SMB file を端末へ複製しない。

保存 / 保存解除によって playback position、completed state、source catalog identity は変更しない。

### 1動画は最大1フォルダへ所属する

保存済み動画は次のいずれかとする。

- folder_id が null: 未分類
- folder_id が存在: 1つの Video folder に所属

複数フォルダへの同時所属は初期仕様に含めない。タグのような多対多概念を追加せず、フォルダ移動の意味を単純に保つ。

### フォルダ削除では保存状態を維持する

フォルダを削除した場合、そのフォルダに所属していた保存済み動画は削除せず未分類へ戻す。VideoItem と playback state も削除しない。

### Video-owned tableを追加する

次の table を `:feature:video:data` が所有する。

- `video_folders`
- `video_saved_items`

`video_saved_items.video_id` を primary key とし、row の存在を保存済み状態の source of truth とする。`folder_id` は nullable とする。

### database version を 30 へ進める

ADR-0239 の version 29 schema を更新元 baseline とし、version 30 で Video saved/folder table を追加する。

既存 VideoItem は自動的に保存済みへ移行しない。upgrade 後の初期状態では保存済み動画と Video folder は空とする。

backup restore の exact-version policy は維持する。

## Consequences

- 視聴状態とは独立して動画を保存できる。
- 保存済み動画を未分類または1つのフォルダで整理できる。
- Curation Context の table ownership と Folder semantics は変更しない。
- Video-owned durable table が2つ増える。
- database version が 29 から 30 へ進む。
- 保存は metadata state のみであり、storage usage を動画ファイル容量分増やさない。

## Verification

- 保存 / 保存解除と playback state の独立性を repository test する。
- フォルダ作成、rename、削除、未分類への移動を repository test する。
- 同名フォルダの正規化と重複拒否を test する。
- version 29 -> 30 migration と fresh schema を test する。
- architecture verification で table ownership を検証する。
- UI では保存タブ、未分類 / folder filter、保存先変更、folder CRUD を確認する。
