# 9. 資産

- dated snapshotとして資産情報を保存し、時系列で参照する。
- TSV等のデータをインポートできる。
- WebView / Web Collectorを利用するsource adapterから資産情報を取り込める。
<!-- formal-requirement
id: ASSET-NONNEGATIVE-SNAPSHOT-001
models:
  - spec-models/alloy/asset_nonnegative_snapshots.als
-->
- 資産スナップショットのインポートでは金額が負の明細を保存せず、0以上の明細だけを保存対象とする。置換対象の日付は負数除外前の入力から確定するため、ある日付の入力がすべて負数でも、その日付の既存スナップショットを削除して古い正数データを残さない。
- 既存databaseに負の資産明細が残っていても物理削除を前提にせず、資産総額、履歴、カテゴリ別構成、カテゴリ設定のread modelは金額が0以上の明細だけを利用する。
- 負数除外だけを理由にdatabase migrationやbackup format変更を追加せず、import boundaryとread modelで現行仕様へ収束させる。
<!-- /formal-requirement -->
- 資産項目をカテゴリ分類し、カテゴリ別の構成と推移を表示できる。


## 形式モデル

- [Alloy: `asset_nonnegative_snapshots.als`](../../spec-models/alloy/asset_nonnegative_snapshots.als) — snapshot再インポートの日付単位置換、負額import非保存、旧負額のread model除外、0額の保存対象維持を検査する。
