# ADR-0256: Podcast episode archive/delete lifecycle

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0249](0249-news-podcast-context.md), [ADR-0255](0255-podcast-playback-chapters.md)

## Context

Podcast episode は `podcast_episodes` が lifecycle と生成原稿を所有し、`podcast_episode_articles` が生成時の記事 snapshot、`podcast_consumed_articles` が番組単位の消費済み entry identity を保持している。

ユーザーが聴き終わった episode を整理するため、通常一覧から外すアーカイブと、不要になった episode の削除が必要になった。

ただし `podcast_consumed_articles.episode_id` は episode を参照しているため、episode row を物理削除すると consumed row も cascade delete される。そうすると、一度生成対象として予約した entry が再び新規 episode の候補になり、「同一番組では一度予約した entry を再利用しない」という既存 invariant を壊す。

## Decision

既存の `podcast_episodes.status` に `ARCHIVED` と `DELETED` を追加し、episode の整理状態も Podcast episode lifecycle の一部として扱う。column は制約なしの TEXT なので schema migration は追加しない。

- `READY` episode は `ARCHIVED` へ変更できる。
- `ARCHIVED` episode は `READY` へ復元できる。
- `READY` / `ARCHIVED` / `FAILED` episode は `DELETED` へ変更できる。
- `QUEUED` / `GENERATING` episode は整理操作の対象にしない。
- `ARCHIVED` は生成原稿と記事 snapshot を保持し、再生可能なままとする。
- `DELETED` は復元不能な tombstone とし、episode row と consumed identity の参照だけを残す。
- `DELETED` への変更時に title、script、error message、`podcast_episode_articles` snapshot を消去する。
- `podcast_consumed_articles` は削除しない。したがって削除済み episode の記事も新規 episode には再利用されない。
- 通常一覧は `ARCHIVED` / `DELETED` を除外し、アーカイブ一覧は `ARCHIVED` だけを表示する。

## Consequences

episode を削除しても、番組ID、episode ID、生成時刻、消費済み entry identity を維持するための最小 tombstone は database に残る。一方、生成原稿と feed body snapshot は削除されるため、削除済みコンテンツ本文を保持し続けない。

アーカイブは状態変更だけなので復元できる。削除は tombstone への不可逆な遷移とし、再生成・再生・復元の対象外になる。

schema version は変更しないため、既存 install への migration は不要である。古い build が新しい状態値を読む downgrade compatibility は保証しない。