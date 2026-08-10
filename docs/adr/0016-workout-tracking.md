# ADR-0016: ワークアウト記録・計測を独立 feature として実装する

- Status: Accepted
- Date: 2026-08-10

## Context

Yomitori にワークアウトの記録・計測機能を追加する。機能要件は `sterashima78/fitness-app-android` の 2026-08-10 時点の `main` を参照する。

参照実装は、種目ごとのセット記録、回数・秒数・踏み台段数、セットメモ、休憩カウントダウン、プランク/踏み台用ストップウォッチ、当日のセッション、最大50件の履歴、種目の追加・削除・初期化を一つのアプリ状態として扱っている。

Yomitori 側では ADR-0001 と ADR-0003 により UI / Domain / Data の依存方向と feature-first のモジュール境界を採用しているため、参照実装の単一状態・単一ファイル構成をそのまま持ち込むべきではない。

## Decision

`workout` を独立した ownership とし、次の3モジュールで実装する。

```text
:feature:workout:domain
:feature:workout:data
:feature:workout:ui
```

- Domain は種目、セット、当日セッション、履歴、Repository contract、日付ロールオーバー規則を所有する。
- Data は Android の SharedPreferences を永続化手段として使い、Domain の `WorkoutRepository` を実装する。
- UI は Compose 画面、ViewModel、タイマー/ストップウォッチのライフサイクルを所有する。
- `:app` は composition root として Repository を生成し、ナビゲーションへ `WorkoutRoute` を接続する。
- ワークアウト内の主要画面である「記録」「タイマー」「履歴」「設定」は、上部の一時的な FilterChip ではなく Material 3 の下部 `NavigationBar` で切り替える。ワークアウト画面内の主要目的を常時同じ位置から切り替えられるようにし、RSS / Bookmark などの主要画面切り替えと操作位置を揃える。

参照実装と同様に、休憩タイマーは初期値90秒とし、30/60/90/120秒を選択できる。セット記録後は休憩タイマーをリセットして開始する。プランクと踏み台昇降はストップウォッチ計測値を直接セットとして保存する。履歴は新しい順で最大50セッションを保持する。

永続化は現時点でアプリ内完結の小規模データであり、RSS/Bookmark 等の共有SQLデータと結合しないため SharedPreferences を選ぶ。将来、集計クエリ、Health Connect 連携、バックアップ対象の細粒度管理などが必要になった時点で専用テーブルへの移行を再検討する。

タイマーの実時間計測には `SystemClock.elapsedRealtime()` を使い、端末時刻変更の影響を受けないようにする。計測中のプロセス終了を跨いだ復元は今回のスコープ外とする。

## Consequences

- ワークアウト固有の変更を既存の RSS / Bookmark / Task から分離できる。
- Domain の日付ロールオーバーは JVM 単体テストで検証できる。
- SharedPreferences のJSON形式は内部実装詳細であり、UI/Domainに露出しない。
- タイマー状態は画面プロセスの生存期間に限定される。永続タイマーが必要になった場合は別途設計する。
- ワークアウトの主要画面切り替えは画面下部に固定され、長い記録画面をスクロールしていても切り替え位置が変わらない。
- Health Connect やウェアラブル連携は含めず、まず参照アプリ相当の手入力・端末内計測を提供する。

## Relationship

ADR-0001 の UI / Domain / Data 境界、ADR-0002 の stateful object と interface の使い分け、ADR-0003 の feature-first module ownership、ADR-0004 の concept-oriented ownership を継承する。ADR-0015 で導入された `:core:designsystem` は横断 UI interaction primitive のための capability であり、現時点のワークアウト UI は固有の記録・計測画面のみを持つため依存しない。
