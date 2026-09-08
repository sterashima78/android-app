# ADR-0244: SMB / Web 動画を保存済みとして扱い保存画面をファイルブラウザ化する

- Status: Accepted
- Date: 2026-09-08
- Amends: [ADR-0240](0240-video-saved-items-and-folders.md)
- Refines: [ADR-0237](0237-video-library-and-web-extraction.md)
- Follows: [ADR-0239](0239-shared-smb-connection-profiles-and-feature-locations.md)

## Context

ADR-0240 では `video_saved_items` row の存在を全source共通の保存済み状態とし、保存画面では未分類 / Video専用フォルダをfilterとして切り替えることにした。

しかし SMB と Web は購読型sourceとは lifecycle が異なる。

- SMB動画はユーザーが同期場所を明示してcatalogへ取り込んだファイルであり、catalogに存在する時点で保存済みとして扱う方が自然である。
- Web動画はユーザーがURLを1件ずつ明示登録してcatalogへ追加するため、登録済みitem自体が保存済み動画である。
- 購読型Provider由来動画はbackground refreshで自動取得されるため、保存済みかどうかを明示的なユーザー状態として分ける意味がある。

また SMB には元ファイルサーバー上のディレクトリ階層がある。これを平坦な一覧へ投影し、別のVideo専用folder filterで整理すると、元のファイル配置と閲覧構造が一致しない。

## Decision

### SMB / Web はsourceの性質として保存済みとする

`VideoItem` の保存済み判定を次のように定義する。

- `VideoSource.SMB`: catalogに存在する全itemを保存済みとする。
- `VideoSource.WEB`: catalogに存在する全itemを保存済みとする。
- `VideoSource.SERVICE`: `video_saved_items` rowが存在するitemだけを保存済みとする。

`video_saved_items` は引き続きVideo-owned durable stateとして維持するが、SMB / Webの保存済み判定そのもののsource of truthにはしない。

Web itemでは `video_saved_items.folder_id` を任意の整理先として利用できる。rowを削除してもWeb item自体は保存済みのままで、未分類へ戻る。SMB itemは元ディレクトリ構造を優先し、Video専用folderへ移動しない。

### SMBのディレクトリ構造はcatalogから派生させる

SMB itemのdurable source identityに既に含まれる connection profile ID / share / path と、`video_smb_sources` の root path から、表示用の相対ディレクトリ階層を派生させる。

新しいdurable folder tableやSMB directory rowは追加しない。ディレクトリ構造は同期済みSMB itemと設定済み同期場所から再構築できるprojectionとする。

複数のSMB同期場所を混同しないため、保存画面のrootでは同期場所ごとに独立したroot directory entryを表示する。表示名は設定済みroot pathの末尾segmentを優先し、root pathが空の場合はshare名を使う。directory identityにはSMB source IDを含め、同名表示でも別同期場所を統合しない。

### 保存画面をファイルブラウザにする

保存画面ではfolderをfilter chipとして扱わない。

現在directoryの直下にある次のentryを同じ一覧へ表示する。

- 子directory
- 動画

子directoryを選択するとそのdirectoryへ移動し、breadcrumb / 上位directory操作から親へ戻れるようにする。

root directoryでは次を表示する。

- SMB同期場所のroot directory
- Video専用folder
- folder未所属のWeb動画
- folder未所属の明示保存済みProvider動画

Video専用folderを選択すると、そのfolderへ所属するWeb動画と明示保存済みProvider動画を表示する。Video専用folder自体のdurable modelはADR-0240の単一階層を維持し、今回の変更ではnested custom folderを追加しない。

### 保存操作の意味をsourceごとに合わせる

- SMB: 常に保存済み。保存解除やVideo専用folderへの移動は提供しない。
- Web: 常に保存済み。Video専用folderへの移動は可能。保存解除に相当する操作は未分類への移動となり、catalogから削除する操作とは分ける。
- SERVICE: 従来どおり明示保存 / 保存解除とVideo専用folderへの移動を提供する。

再生位置、completed、provider unread / read / watch-later stateはこの変更で変更しない。

## Persistence

新しいtable、column、database migrationは追加しない。

既存tableの意味は次のように更新する。

- `video_items`: SMB / Webではrowの存在自体が保存画面への所属も意味する。
- `video_saved_items`: SERVICEの明示保存状態、およびWeb / SERVICEのVideo専用folder所属を保持する。
- `video_folders`: 保存画面rootから辿るVideo専用folderを保持する。
- `video_smb_sources`: SMB directory projectionのroot境界を決める。

## Consequences

- SMB / Webを追加・同期した直後から保存画面で確認できる。
- SMBの元ディレクトリ階層を保ったまま動画へ辿れる。
- 保存画面のfolderは絞り込み条件ではなくnavigation entryになる。
- SMB directory用の重複durable stateを持たない。
- Webの「保存解除」はcatalog削除と混同しなくなる。
- custom folderは引き続き単一階層であり、nested custom folderは別判断とする。

## Verification

- DomainでSMB / Web / SERVICEの保存済み判定をtestする。
- SMB source pathと同期rootから相対directory pathを派生するrepository testを追加する。
- 保存画面のbrowser projectionについてroot / child directory / custom folder / Web / SERVICEの混在をpure unit testする。
- SMB / Webの保存済み扱いでplayback stateが変化しないことを既存repository testと合わせて確認する。
- database versionとtable ownershipが変わらないことをarchitecture verificationで確認する。
