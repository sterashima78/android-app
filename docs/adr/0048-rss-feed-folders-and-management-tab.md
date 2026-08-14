# ADR-0048: RSSフィードをフォルダ管理し、管理画面をRSSタブに置く

- Status: Accepted
- Date: 2026-08-11

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

## Consequences

### Positive

- 登録フィードを用途や分野ごとに整理できる
- フィード管理がRSS feature内で完結し、設定画面の責務が明確になる
- ADR `0019-feature-bottom-tab-navigation.md` で定めたfeature内ナビゲーションの一貫性を維持できる
- OPMLが持つフォルダ情報をインポート後も利用できる
- フォルダ削除で購読そのものを失わない

### Negative

- データベースversionとバックアップ形式versionの更新が必要になる
- OPMLの入れ子構造はアプリ内ではフラットなパス名として表現され、階層UIにはならない
- 1フィードを複数フォルダへ所属させることはできない

## Relationship to existing ADRs

- ADR `0019-feature-bottom-tab-navigation.md` の「feature内の主要表示は画面下部タブで切り替える」方針をRSSへ適用する
- ADR-0001およびADR-0003の層・モジュール境界に従い、フォルダのモデルとrepository契約は `:feature:rss:domain`、SQLite実装は `:feature:rss:data`、管理画面は `:feature:rss:ui` に置く
