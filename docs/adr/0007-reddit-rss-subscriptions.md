# ADR-0007: Reddit を RSS source として扱いスレッド購読を opt-in にする

- Status: Superseded by ADR-0009
- Date: 2026-08-09
- Superseded: 2026-08-09

## Context

Reddit コミュニティと個別スレッドの更新を公開 RSS endpoint で取得し、Reddit API / OAuth を導入せずに購読する方針を決めた。

## Decision at the time

- コミュニティは `/new/.rss` で新着順に購読する
- 個別スレッドは明示的な opt-in で comments RSS を購読する
- スレッド購読開始時の既存コメントは既読ベースラインとし、その後のコメントだけを未読にする
- 既読・ブックマークとスレッド購読状態を分離する
- Reddit 専用 module は作らず `:feature:rss` が Reddit RSS を所有する

## Superseded decision

「Reddit 専用 module は作らず `:feature:rss` が Reddit RSS を所有する」という責務境界は ADR-0009 で廃止する。

取得 transport として RSS/Atom を再利用する点、コミュニティを新着順で購読する点、スレッド購読を opt-in とする点、既存コメントを購読開始時の既読ベースラインとする点は引き続き有効である。

Reddit はユーザーから見て RSS リーダーとは異なる情報源・操作モデルを持つため、独立した feature ownership と UI を持つ。詳細は ADR-0009 を参照する。
