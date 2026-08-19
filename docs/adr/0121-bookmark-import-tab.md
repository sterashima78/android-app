# ADR-0121: Bookmark import を Bookmark セクションのタブとして配置する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0004, ADR-0049, ADR-0120

## Context

Bookmark の CSV / HTML import は Curation の操作であり、実装上も `BookmarkViewModel` と ADR-0120 で定義した import application service に接続されている。一方、UI の入口だけは Settings 画面に置かれていた。

この配置には次の問題がある。

- Bookmark に対する操作が Settings に混在し、設定変更とデータ管理操作の責務が曖昧になる。
- Settings route が BookmarkViewModel と import 完了状態を購読する必要があり、feature 間の不要な UI 依存が生じる。
- Bookmark 画面には一覧・フォルダ・タグという管理タブがすでにあり、import だけ別セクションへ移動する理由が弱い。

RSS では ADR-0049 により feed の管理操作を RSS セクション内の管理タブへ寄せている。Bookmark でも同じ原則を採用する。

## Decision

Bookmark の bottom navigation に `インポート` タブを追加し、CSV / HTML import の入口をこのタブへ移す。

Bookmark のタブ構成は次の4つとする。

- 一覧
- フォルダ
- タグ
- インポート

Settings 画面から Bookmark import のセクションと callback を削除し、Settings route は BookmarkViewModel を参照しない。

Android の `OpenDocument` launcher は feature UI module へ移さない。launcher は app composition 層で保持し、Bookmark UI / Route には `onImportCsv` / `onImportHtml` callback として注入する。これにより framework-owned UI API と feature UI の境界を維持する。

Import 完了状態の監視と consume は BookmarkRoute が担当し、完了後は Bookmark の一覧タブへ戻す。Settings route は import lifecycle を扱わない。

## Consequences

### Positive

- Bookmark に関するデータ管理操作が Bookmark セクション内にまとまる。
- Settings が BookmarkViewModel に依存しなくなり、設定と Curation 操作の責務が分離される。
- import application service や data layer を変更せず、既存の import 挙動を維持できる。
- Android document picker 依存を app composition 層に残せる。

### Negative

- Bookmark bottom navigation が3タブから4タブになる。
- `MainTab` に Bookmark import 用の navigation state が1つ増える。

## Test strategy

- `AppNavigationSpecTest` の BookmarkTab 往復変換テストにより、追加した `IMPORT` と `BOOKMARK_IMPORT` の mapping を含む全 Bookmark tab を検証する。
- `MainTab.entries` を対象とする既存テストにより、新規タブの section mapping と screen title の定義漏れを検出する。
- 既存 `ImportBookmarksUseCaseTest`、CSV / HTML parser tests により import 自体の挙動を継続検証する。
- CI で unit test、lint、architecture verification を実行する。

## Public repository safety

変更は画面構成、navigation enum、callback wiring、ADR のみで、token、credential、OAuth secret、実ユーザーの URL / メールアドレス / ファイル内容など公開してはいけない情報を追加しない。
