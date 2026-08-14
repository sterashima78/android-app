# ADR-0059: 現在配布中の最新版を更新互換性のベースラインとする

- Status: Accepted
- Date: 2026-08-14
- Amends: ADR-0047

## Context

このアプリの利用者は1人で、常に現在配布中の最新版へ更新している。過去バージョンから任意のバージョンへ直接更新できることを維持する必要はない。

一方、コードには移行済みの状態だけを救済する処理が残っていた。

- database version 6〜12 の feature migration
- Expo / React Native 版が使用していた `files/SQLite/yomitori-rss.db` から Android 標準 database 保存先へのコピー
- これらの互換処理を検証する過去バージョン向けテストと移行ドキュメント

完了済みの一時的な migration を保持し続けると、現在の schema と過去の状態を同時に理解する必要があり、機能追加や database 設計変更時の確認範囲が広がる。

## Decision

現在配布中の最新版を、次のバージョンへ更新する際の互換性ベースラインとする。

今回の database については version 12 をベースラインとする。version 12 より前から version 12 へ到達するためだけの migration は削除する。Expo 版の旧 database 保存場所からの自動コピーも削除する。

今後 database schema を変更するときは、version 12 以降の直前ベースラインから最新版へ更新できる migration を追加する。`DatabaseSchema`、`DatabaseMigration`、migration phase などの汎用機構はそのために維持する。

この方針は「legacy」「compatibility」と書かれた処理を無条件に削除することを意味しない。現在の最新版を利用していても永続データに旧形式が残り得る設定や、実際に現在の動作で利用される fallback は、移行完了を確認できるまで残す。

## Consequences

### Positive

- 現在利用しない過去 schema の知識を feature data module から除去できる
- database 初期化処理から Expo 時代の保存場所を意識する必要がなくなる
- 最新版の fresh schema と今後の migration にテストを集中できる
- 一時的な互換コードが恒久的な実装として残りにくくなる

### Negative

- database version 11 以下のアプリから最新版への直接更新は保証しない
- Expo / React Native 版から現在版へ直接更新して旧 database を自動移設することはできない
- 古い環境から移行する必要が生じた場合は、対応する中間版を経由するか、バックアップ・復元など別の移行手段が必要になる

## Relationship to ADR-0047

ADR-0047 の feature-owned schema / migration ownership と汎用 migration mechanism は維持する。

ADR-0047 採用時に互換性のため保持した version 6〜12 の migration は本 ADR により役目を終えたものとして削除する。今後追加する migration は引き続き、対象 feature の `data` module が所有する。
