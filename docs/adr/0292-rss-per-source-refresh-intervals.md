# ADR-0292: RSS取得間隔の設定配置とソース単位のoverride

- Status: Accepted
- Date: 2026-10-23
- Refines: [ADR-0183](0183-rss-settings-tab.md), [ADR-0291](0291-rss-refresh-freshness-and-concurrency.md)

## Context

RSSの画面表示時・失敗後の再試行間隔がアプリ全体設定に置かれ、購読ごとの更新頻度の違いに対応できない。Web取得ルールは1つのパターンが複数の購読に一致し得る。

## Decision

- 画面表示時の自動再取得間隔・失敗時の再試行間隔はRSS設定タブに移す。値は既存RSS-owned preferencesを再利用し、保存済み値を変更しない。
- RSSの購読フィードとWeb取得ルールそれぞれに、任意の自動取得間隔（分単位）を持たせる。未指定（null）は共通設定の継承を意味する。個別フィード > URLが一致する最も具体的なWeb取得ルール > 現在の自動取得コンテキストの共通間隔、という優先順位とする。
- 既存の統合バックグラウンドWorkManagerの周期とネットワーク制約はアプリ全体の設定として残し、独立したRSS schedulerは作らない。個別間隔はスケジューラの起動頻度を短縮せず、起動時に取得要否を判定する。
- 自動更新では最後の成功取得時刻（304含む）から経過した時間をチェックする。手動強制更新・インポートは既存どおりfreshnessと失敗クールダウンをバイパスする。失敗クールダウンは従来の独立設定を使う。
- RSS-owned SQLite列のnullable overrideに保存し、version 42から43へのmigrationで旧購読とルールの値をnullで維持する。snapshotバックアップ対象にする。

## Compatibility and rollback

既存設定値、購読、記事、取得ルール、ETag、Worker identityを維持する。削除する場合は保存済みoverrideとschema versionに注意する。

## Verification

優先順位・期限判定・強制更新・取得失敗・migration・設定値変更・画面導線を確認する。
