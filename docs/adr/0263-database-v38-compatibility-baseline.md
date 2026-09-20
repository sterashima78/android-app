# ADR-0263: application database version 38 を現在の更新互換性 baseline とする

- Status: Accepted
- Date: 2026-09-20
- Refines: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0060](0060-converge-to-current-persisted-data-formats.md), [ADR-0250](0250-podcast-owned-feed-sources.md), [ADR-0255](0255-podcast-playback-chapters.md), [ADR-0257](0257-podcast-chapter-generation-jobs.md), [ADR-0259](0259-podcast-news-clustering.md)

## Context

このアプリは現在配布中の最新版を次版への更新互換性 baseline とし、到達済みの一度限り migration を runtime へ恒久保持しない方針を採用している。

application database は version 38 に到達している。version 33 から 38 へ到達するために Podcast Context へ追加した migration は、それぞれ Podcast-owned source、記事 URL、chapter checkpoint、news cluster position、clustering diagnostics を現行 schema へ収束させるための一度限り処理だった。

fresh database はすでに version 38 の完全な schema を直接作成する。backup restore も current schema version と一致する snapshot だけを受け付ける。したがって pre-38 database から version 38 へ到達する runtime compatibility を現在の distribution baseline として保持する必要はない。

## Decision

- application database version 38 を現在の更新互換性 baseline とする。
- Podcast の target version 33〜38 migration implementation を current runtime から削除する。
- version 33→34 migrationだけが必要としていた RSS / Content table への foreign read allowlist を削除する。
- pre-38 migration専用 fixture / regression test を削除し、fresh version 38 schema と current behavior のtestへ集中する。
- database version自体は38のままとする。次にschema versionを上げる変更では、version 38から次versionへのmigrationだけを追加する。
- historical schema meaning と migration理由は既存ADRへ残し、current architecture documentではcurrent schemaとsupported baselineを正本とする。

## Change Impact Brief

- Changed capability: none.
- Changed Context / ownership: none.
- Changed durable schema: none. Current version 38 schema is unchanged.
- Removed compatibility: application database version 37以下からversion 38への直接update path.
- Removed cross-context exception: Podcast migrationからRSS / Content tableへのread-only access.
- Backup compatibility: current version 38 snapshot onlyという既存方針を維持する。

## Verification

- fresh databaseがversion 38で全current table / columnを作成すること。
- current repository / integration testsがversion 38 schemaで成功すること。
- table ownership verificationでforeign-table allowlistが空でも成功すること。
- Architecture / Test / Lint / release shrinker checksを実行する。

## Consequences

### Positive

- Podcast Data moduleが過去6段階のschemaを理解する必要がなくなる。
- foreign-table access exceptionを0件へ戻せる。
- migration専用fixtureを削減し、current schemaの検証へ集中できる。
- 次回schema変更の互換範囲がversion 38→次versionとして明確になる。

### Negative

- version 37以下のdatabaseを持つ古いインストールからcurrent versionへ直接更新する互換性は保証しない。
- 古いdatabaseを移行する必要が生じた場合は対応する中間版を経由する必要がある。
