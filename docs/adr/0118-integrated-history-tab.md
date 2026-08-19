# ADR-0118: 閲覧履歴をブックマークから統合ビューへ移す

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0025, ADR-0062, ADR-0106, ADR-0117

## Context

従来の閲覧履歴は `BookmarkTab.HISTORY` としてブックマーク画面に配置されていたが、実際のデータは `BookmarkRepository` ではなく `ArticleRepository.listHistoryArticles()` から取得していた。

ADR-0106 では ContentItem の閲覧状態を Content context が所有し、Bookmark は保存・整理に関する Curation context として分離することを決めている。そのため、閲覧履歴を `BookmarkUiState` が保持し、Bookmark UI が「未読に戻す」操作を提供する構成は、画面上の違和感だけでなく Context の責務とも一致していない。

一方、ADR-0025 と ADR-0062 により、統合ビューは RSS、Reddit、YouTube、Mail の所有権を維持したまま、各 feature の状態を app-level composition で統合する責務を持つ。統合ビューには既に「未読」「あとで読む」という情報処理の状態を横断表示するタブが存在する。

閲覧履歴も「処理済みになった情報を確認し、必要なら未読へ戻す」という同じ情報処理フローに属するため、統合ビューで扱う方が UI と architecture の双方で自然である。

## Decision

### 1. ブックマークから閲覧履歴を分離する

`BookmarkTab` は次の3タブだけを持つ。

- 一覧
- フォルダ
- タグ

`BookmarkUiState` は閲覧履歴を保持せず、`BookmarkViewModel` は `ArticleRepository.listHistoryArticles()` や `markArticleUnread()` を履歴用途で呼び出さない。

app-level の `MainTab.HISTORY` も廃止する。履歴への入口は統合ビュー内のタブに一本化する。

### 2. 統合ビューに履歴タブを追加する

`IntegratedTab` に `HISTORY` を追加し、統合ビューを次の3タブとする。

- 未読
- あとで読む
- 履歴

履歴でも既存の `IntegratedSource` フィルタを利用し、RSS、Reddit、YouTube、Mail を個別または横断して確認できるようにする。

### 3. 履歴は各 Context が所有する状態を composition する

統合履歴専用の永続化 table、横断 Repository、共有 Aggregate は追加しない。

`IntegratedRoute` は ADR-0062 の composition adapter として、各 feature が公開する状態を `IntegratedItem` に射影する。

- RSS: Content context の `ArticleRepository.listHistoryArticles()` を RSS selector で絞った状態
- Reddit: 同じ Content history を Reddit article に絞った状態
- YouTube: YouTube context が所有する `is_read` から取得する既読動画
- Mail: Mail context が所有する受信トレイ内の既読 thread

これは ADR-0106 / ADR-0117 の「presentation / delivery adapter は owner の公開 API を利用し、durable Domain table を所有しない」という方針を維持する。

### 4. 履歴から各ソースを未読へ戻せるようにする

履歴アイテムには「未読に戻す」スワイプ操作を提供する。

操作は `IntegratedRoute` から各 feature の既存または feature-owned command へ委譲する。

- RSS: `RssViewModel.markUnread`
- Reddit: `RedditViewModel.markUnread`
- YouTube: `YouTubeViewModel.markUnread`
- Mail: `MailViewModel.toggleRead`

YouTube については `YouTubeRepository.markUnread()` を追加するが、YouTube-owned `videos` table 内の既存 `is_read` を更新するだけとし、schema は変更しない。

Mail の履歴は既読かつ受信トレイに残っている thread に限定する。アーカイブ済みメールは Mail のアーカイブとして扱い、統合履歴には含めない。これにより「未読に戻す」と統合ビューの未読タブへの復帰が一致する。

### 5. 履歴の時刻は source が既に所有する最も適切な時刻を使う

履歴は新しい順に表示する。

- RSS / Reddit: `Article.readAt` を優先し、取得できない場合は published / fetched time にフォールバックする
- YouTube: 現行 schema に読了時刻がないため published time を使う
- Mail: 現行 model に読了時刻がないため last message time を使う

統一された「処理日時」が必要になった場合は、まず各 source が自身の lifecycle に適した処理時刻を所有するかを検討する。表示都合だけで shared history table を追加しない。

## Testing

- `IntegratedScreenTest` で履歴タブの「未読に戻す」スワイプ契約を固定する。
- `IntegratedRouteAdapterTest` で4 source の履歴合成、Article の `readAt` 優先、Mail の履歴抽出条件を検証する。
- Bookmark の state test は履歴を Curation state として持たないことに追従する。
- YouTube の既読履歴取得と未読復元は YouTube Repository / ViewModel の責務として実装する。

## Consequences

### Positive

- ブックマークが保存・フォルダ・タグという Curation の責務に集中する。
- 未読、あとで読む、履歴という情報処理フローが統合ビュー内で完結する。
- RSS / Reddit / YouTube / Mail の履歴を同じ UI で確認できる。
- 新しい cross-context persistence を作らず、ADR-0106 / ADR-0117 の ownership を維持できる。

### Negative

- source ごとに履歴時刻の精度が異なる。
- Mail の履歴は受信トレイに残る既読メールに限定され、アーカイブは含まれない。
- RSS と Reddit は同じ Content history query を各 ViewModel が source selector で分けて利用するため、統合画面表示時に同じ query が2回実行される。

## Relationship to existing ADRs

- ADR-0025 の feature ownership を維持した統合ビュー方針を、履歴に拡張する。
- ADR-0062 の `feature:integrated:ui` と app-level composition adapter の責務分離を維持する。
- ADR-0106 の Content reading state / Curation state の分離に合わせ、Bookmark から閲覧履歴を除去する。
- ADR-0117 の cross-context persistence boundary に従い、統合履歴のための foreign table access や横断 Repository を追加しない。
