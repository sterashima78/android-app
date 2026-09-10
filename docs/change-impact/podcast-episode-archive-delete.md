# Podcast episode archive/delete Change Impact Brief

## Goal

生成済みニュースポッドキャストのエピソードを通常一覧からアーカイブし、必要なら復元または削除できるようにする。

## Current system touched

- Podcast Context の episode lifecycle
- Podcast repository の episode persistence
- Podcast UI の episode 一覧

## Ownership / dependencies

- Context ownership: 変更なし
- module dependency direction: 変更なし
- external communication / permission / credential boundary: 変更なし
- background execution: 変更なし

## Durable state

`podcast_episodes.status` の既存 TEXT 値に `ARCHIVED` と `DELETED` を追加する。schema version と table 構造は変更しない。

`DELETED` は物理削除ではなく tombstone とする。削除時に生成原稿、エラー、episode article snapshot を消去するが、`podcast_consumed_articles` が参照する episode row は残す。これにより、削除したエピソードの記事が新規エピソードへ再利用されないという既存 invariant を維持する。

## UI behavior

- 通常一覧には `ARCHIVED` / `DELETED` を表示しない。
- READY episode はアーカイブまたは削除できる。
- アーカイブ一覧から READY 状態へ復元できる。
- アーカイブ済み episode も削除できる。
- 削除は確認ダイアログを経由する。

## Removal / rollback

schema migration は追加しない。機能を戻す場合も database migration は不要で、追加した状態値を読むコードだけを維持する必要がある。