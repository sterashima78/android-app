# ADR-0032: YouTube 保存は予約済み Bookmark システムフォルダを利用する

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0010

## Context

ADR-0010 では、YouTube の「保存」を YouTube DB の独自状態ではなく共通 Bookmark feature へ保存し、保存済み動画を YouTube UI から投影する方針を定めた。

一方、保存された YouTube 動画は通常の共有ブックマークと同じ未分類状態になり、Bookmark 画面から見たときに由来を把握しにくい。YouTube から保存した項目を一貫して整理できる保存先が必要である。

Bookmark feature には既に `system_kind` を持つシステムフォルダと、システムフォルダの改名・削除を禁止する仕組みがある。この仕組みを再利用し、YouTube 固有のフォルダ管理を YouTube DB に追加しない。

## Decision

Bookmark feature に `YouTube` という予約済みシステムフォルダを追加する。

- 表示名は `YouTube` とする
- `system_kind` は `youtube` とする
- ユーザーによる `YouTube` フォルダ名の新規作成・改名先指定を禁止する
- システムフォルダであるため、既存の保護機構により改名・削除を禁止する
- 既に通常フォルダとして `YouTube` が存在する場合は、フォルダ ID と所属ブックマークを維持したまま `system_kind=youtube` に昇格する
- YouTube の「保存」操作は共通 BookmarkRepository を通じてブックマークを保存し、その `YouTube` システムフォルダへ配置する
- 同じ URL が既にブックマーク済みの場合も、YouTube の「保存」を実行した時点で `YouTube` フォルダへ移動する
- 同一 URL の article が複数存在する場合は、Bookmark の共有保存処理と同じ選択規則で対象 article を決定し、その同じ article をフォルダへ移動する
- Bookmark の1記事1フォルダという既存モデルは変更しない。ユーザーが保存後に記事を別フォルダへ移動することは許可する
- YouTube の「保存済み」タブは従来どおり URL から YouTube 動画を判定する。`YouTube` フォルダだけに絞り込まないため、手動登録や後から別フォルダへ移動した YouTube ブックマークも参照できる
- YouTube からの保存・フォルダ移動後は Bookmark 画面の永続化変更と同様に `BackupChangeScheduler` を呼び、設定済み Google Drive バックアップへ変更を反映する

フォルダの作成・予約・昇格は Bookmark feature が所有する。YouTube UI は `system_kind=youtube` のフォルダを BookmarkRepository から取得し、フォルダ指定付きの共有ブックマーク保存 API を利用する。YouTube domain / data には Bookmark 依存を追加しない。バックアップの起動は `youtube:ui` から `feature:backup:domain` の `BackupChangeScheduler` を利用し、YouTube domain / data にバックアップ依存を追加しない。

## Consequences

### Positive

- YouTube から保存した動画が Bookmark 画面で自動的にまとまる
- `YouTube` という保存先の意味がユーザー操作で失われない
- 既存の Bookmark フォルダ機構を再利用し、YouTube DB に重複する保存状態を持たない
- 既存の同名フォルダを破棄せず、そのまま予約フォルダへ移行できる
- 手動で保存した YouTube URLや保存後に整理した項目も YouTube の「保存済み」から引き続き参照できる
- YouTube 保存によるフォルダ移動も他の Bookmark 変更と同様にバックアップ対象として速やかにスケジュールされる

### Negative

- `YouTube` というフォルダ名はユーザー定義フォルダとして利用できなくなる
- 既存の通常 `YouTube` フォルダは更新後にシステムフォルダへ昇格し、フォルダ自体の改名・削除ができなくなる
- 既に別フォルダへ保存済みの同一 URL を YouTube から再度保存すると、所属フォルダが `YouTube` に変更される
- `youtube:ui` から `feature:backup:domain` への feature 間依存が1本増える

## Relationship to existing ADRs

- ADR-0010 の「YouTube の保存データ ownership は Bookmark feature に置く」方針を維持し、保存先の規約を追加する
- ADR-0003 の `UI -> 他 feature の Domain` を許容する依存ルールに従い、`youtube:ui` だけが Bookmark domain と Backup domain を利用する
- ADR-0004 の concept ownership に従い、Bookmark のフォルダ予約・永続化は Bookmark feature が所有する
