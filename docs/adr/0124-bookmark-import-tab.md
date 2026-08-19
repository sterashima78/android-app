# ADR-0124: Bookmark import を Bookmark セクションのタブとして配置する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0004, ADR-0049, ADR-0120, ADR-0123

## Context

Bookmark の CSV / HTML import は Curation の操作であり、実装上も `BookmarkViewModel` と ADR-0120 で定義した import application service に接続されている。一方、UI の入口を Settings に置くと、設定変更と Bookmark のデータ管理操作が混在し、Settings route が BookmarkViewModel と import 完了状態を購読する必要が生じる。

Bookmark 画面には一覧・フォルダ・タグという管理タブがあり、import だけを Settings に置く理由は弱い。RSS でも ADR-0049 により feed の管理操作を RSS セクション内へ寄せているため、Bookmark でも同じ原則を採用する。

また ADR-0123 による Content / Curation 永続化境界の変更は persistence ownership の変更であり、Bookmark import の UI ownership を Settings に戻す理由にはならない。永続化境界のリファクタリングと UI の配置判断を独立して維持する。

## Decision

Bookmark の bottom navigation に `インポート` タブを追加し、CSV / HTML import の入口をこのタブへ置く。

Bookmark のタブ構成は次の4つとする。

- 一覧
- フォルダ
- タグ
- インポート

Settings 画面から Bookmark import のセクションと callback を削除し、Settings route は BookmarkViewModel を参照しない。

Android の `OpenDocument` launcher は feature UI module へ移さない。launcher は app composition 層で保持し、Bookmark UI / Route には `onImportCsv` / `onImportHtml` callback として注入する。これにより ADR-0120 の framework boundary を維持する。

Import 完了状態の監視と consume は BookmarkRoute が担当し、完了後は Bookmark の一覧タブへ戻す。Settings route は import lifecycle を扱わない。

## Consequences

### Positive

- Bookmark に関するデータ管理操作が Bookmark セクション内にまとまる。
- Settings が BookmarkViewModel に依存しなくなり、設定と Curation 操作の責務が分離される。
- ADR-0123 の persistence 境界変更を維持したまま UI ownership を独立して保てる。
- import application service や data layer を変更せず、既存の import 挙動を維持できる。
- Android document picker 依存を app composition 層に残せる。

### Negative

- Bookmark bottom navigation が3タブから4タブになる。
- `MainTab` に Bookmark import 用の navigation state が1つ増える。

## Test strategy

- `AppNavigationSpecTest` の BookmarkTab 往復変換テストにより、追加した `IMPORT` と `BOOKMARK_IMPORT` の mapping を含む全 Bookmark tab を検証する。
- `MainTab.entries` を対象とする既存テストにより、新規タブの section mapping と screen title の定義漏れを検出する。
- 既存 `ImportBookmarksUseCaseTest`、CSV / HTML parser tests により import 自体の挙動を継続検証する。
- CI で unit test、lint、architecture verification、ADR integrity verification を実行する。

## Public repository safety

変更は画面構成、navigation enum、callback wiring、ADR のみで、token、credential、OAuth secret、実ユーザーの URL / メールアドレス / ファイル内容など公開してはいけない情報を追加しない。
