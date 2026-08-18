# ADR-0049: RSSフィードをフォルダ管理し、管理画面をRSSタブに置く

- Status: Accepted
- Date: 2026-08-11
- Updated: 2026-08-18

## Context

RSSの登録済みフィードが増えると、単一の一覧だけでは購読対象を整理しにくい。

既存のOPMLパーサーは `outline` の階層からフォルダ名を読み取っていたが、インポート時にはその情報を保存していなかった。また、フィード管理画面とOPMLインポートは設定画面配下にあり、RSSというfeature自身の主要表示から分離されていた。

ADR `0019-feature-bottom-tab-navigation.md` では、同一feature内の複数の主要表示を原則として画面下部の `NavigationBar` で切り替える方針を定めている。フィード管理はアプリ全体の設定ではなく、RSSの購読対象を管理する主要表示である。

## Decision

### 1. RSSに「フィード管理」タブを追加する

RSS feature の主要表示を次の3タブとする。

- 未読
- あとで読む
- フィード管理

「フィード管理」は既存の `MainTab.FEEDS` を利用するが、所属するトップレベルsectionを `SETTINGS` から `RSS` へ変更する。

フィード追加、OPMLインポートなど現在の管理画面に対するactionは、ADR `0019-feature-bottom-tab-navigation.md` に従いフィード管理タブの上部actionとして配置する。

設定画面からはフィード管理とOPMLインポートを削除する。

### 2. RSS専用のフィードフォルダを永続化する

`feed_folders` テーブルを追加し、フィードは `feeds.folder_id` によって0個または1個のフォルダへ所属できるようにする。

フォルダは次の属性を持つ。

- ID
- 表示名
- 正規化済み名称
- 作成日時

同じ正規化済み名称のフォルダは重複して作成できない。

フォルダに所属しないフィードは「未分類」として表示する。

フォルダ削除時は `feeds.folder_id` の外部キーを `ON DELETE SET NULL` とし、そのフォルダ内のフィード自体は削除せず「未分類」へ戻す。

初期実装ではフォルダ階層を持たない1階層モデルとする。RSSフィードの整理という用途に対して、移動・名称変更・削除の操作を単純に保つためである。

### 3. OPMLのフォルダ情報を保持する

OPMLではフォルダが入れ子になり得る。一方、アプリ内のフォルダは1階層なので、インポート時には階層パスを ` / ` で連結した1つのフォルダ名として保存する。

例:

`Technology` → `Android` → feed

は、アプリ内では `Technology / Android` フォルダとして扱う。

これにより階層情報を捨てずに、内部データモデルを1階層のまま維持する。

### 4. バックアップ形式へフォルダを追加する

バックアップ形式をversion 3へ更新し、次を保存する。

- RSSフィードフォルダ
- 各フィードの `folderId`

version 1および2のバックアップは引き続き復元可能とする。それらにはRSSフィードフォルダ情報がないため、復元したフィードは「未分類」となる。

### 5. 取得元タイトルとユーザー表示名を分離する

`feeds.title` は取得元RSSのタイトルとして維持し、フィード取得のたびに最新の `<title>` へ更新する。

ユーザーが編集した表示名は nullable な `feeds.custom_title` に保存する。画面や記事で使用する表示名は `COALESCE(custom_title, title)` とし、未編集フィードは従来どおり取得元タイトルへ追従し、編集済みフィードだけユーザー設定名を優先する。

フィード管理画面から表示名を変更できるようにする。名称変更時は、そのフィードに紐づく既存記事の `articles.source_title` も同じ表示名へ更新する。

フィード更新では、取得元の `feeds.title` を更新した後にDB上の `COALESCE(custom_title, title)` を読み直して新規記事へ使用する。これにより、フィード更新と名称変更が並行した場合でも古い表示名を新規記事へ書き戻さない。

この変更では `feeds.custom_title` を追加するためdatabase versionを22へ更新する。ADR-0047に従いmigrationは `:feature:rss:data` が所有し、`:app` は全体database versionのみ更新する。

バックアップ形式はversion 8へ更新し、各フィードの `customTitle` を保存する。version 1〜7のバックアップは引き続き復元可能で、それらから復元したフィードは `custom_title = NULL` として取得元タイトルを表示する。

## Consequences

### Positive

- 登録フィードを用途や分野ごとに整理できる
- フィード管理がRSS feature内で完結し、設定画面の責務が明確になる
- ADR `0019-feature-bottom-tab-navigation.md` で定めたfeature内ナビゲーションの一貫性を維持できる
- OPMLが持つフォルダ情報をインポート後も利用できる
- フォルダ削除で購読そのものを失わない
- ユーザーが分かりやすいフィード表示名を設定でき、フィード更新後も維持できる
- 未編集フィードでは取得元RSSのタイトル変更を引き続き自動反映できる
- 取得元タイトルとユーザー表示名を両方保持できる
- 既存記事と新規記事で表示される配信元名を統一できる

### Negative

- データベースversionとバックアップ形式versionの更新が必要になる
- OPMLの入れ子構造はアプリ内ではフラットなパス名として表現され、階層UIにはならない
- 1フィードを複数フォルダへ所属させることはできない
- フィード名の編集後は取得元RSSのタイトル変更が表示名には反映されない
- `title` と `custom_title` のどちらを表示に使うかをデータアクセス層で一貫して扱う必要がある

## Relationship to existing ADRs

- ADR `0019-feature-bottom-tab-navigation.md` の「feature内の主要表示は画面下部タブで切り替える」方針をRSSへ適用する
- ADR-0001およびADR-0003の層・モジュール境界に従い、フィードのモデルとrepository契約は `:feature:rss:domain`、SQLite実装は `:feature:rss:data`、管理画面は `:feature:rss:ui` に置く
- ADR-0047に従い、`feeds.custom_title` のschemaとmigrationは `:feature:rss:data` が所有し、application-level database versionは `:app` が管理する
