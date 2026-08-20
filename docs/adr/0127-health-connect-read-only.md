# ADR-0127: Health Connect を読み取り専用 Health Context の外部データソースとする

- Status: Accepted
- Date: 2026-08-19
- Amended: 2026-08-20
- Refines: [ADR-0001](0001-layered-architecture.md), [ADR-0003](0003-multi-module-architecture.md), [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0126](0126-android-platform-baseline.md)
- Refined by: [ADR-0131](0131-workout-health-connect-export.md) for the one-way Workout -> Health Connect write path

## Context

アプリから歩数、運動、心拍、睡眠、体重、体脂肪率を参照したい。Android 14 以降では Health Connect が platform の健康・フィットネスデータ共有基盤であり、このアプリの最小 API 34 と整合する。

一方、既存の Workout Context はユーザーがこのアプリで記録するワークアウト状態を所有している。Health Connect 由来データを Workout の永続状態へコピーすると、所有権、重複、同期方向が曖昧になる。また健康データは機微性が高いため、初期実装で不要な永続化、バックアップ、バックグラウンドアクセス、AI 利用を増やすべきではない。

## Decision

- `:feature:health:{domain,data,ui}` を独立 feature として追加する。
- Health Connect は `:feature:health:data` の外部データソースとし、Health Connect の Record 型を Domain/UI へ公開しない。
- 対象は歩数、運動セッション、心拍、睡眠、体重、体脂肪率の読み取りとする。
- UI は今日、直近7日、直近30日の概要をオンデマンドで取得する。体脂肪率は選択期間内の測定値を時系列で表示し、最新値も確認できるようにする。
- Health Connect 由来データをアプリ DB へ永続化しない。Backup、AI task、外部 API にも渡さない。
- `READ_HEALTH_DATA_IN_BACKGROUND` と `READ_HEALTH_DATA_HISTORY` は要求しない。最大期間を30日に制限する。
- Health と Workout は別 Context とし、初期実装では相互の書き込み・同期を行わない。将来統合表示が必要なら目的別の read-only Query / Projection を追加する。
- 権限は利用時に Health Connect へ再確認し、ユーザーによる権限取り消しを通常状態として扱う。
- Android 14 以降の Health Connect permission usage から開ける利用説明 Activity を提供する。

## Consequences

### Positive

- 健康データをアプリ側へ複製せず、Health Connect を source of truth として扱える。
- Workout の ownership を変更せず導入できる。
- 要求権限とデータ流通範囲を最小化できる。
- Android platform / Health Connect 型が Data 層へ閉じる。
- 体脂肪率の推移を、追加の永続化や同期機構なしで確認できる。

### Negative

- Health Connect が利用できない状態や権限未付与時は表示できない。
- 30日より古い履歴は参照しない。
- オフラインキャッシュや長期トレンドのアプリ独自保存は行えない。
- 運動時間は stable 1.1.0 に総運動時間 aggregate がないため、取得した運動セッション時間を合計する。複数データソースが同一運動を重複記録する場合の正規化は将来課題とする。

## Verification

- Health ViewModel の権限未付与、読取成功、期間変更を unit test する。
- 体脂肪率グラフの表示範囲計算を unit test し、単一値と境界値を検証する。
- `verifyArchitecture` で UI -> Data の依存がないことを確認する。
- `:feature:health:ui:test`、全体 unit tests、release lint を実行する。
- final merged manifest に読み取り専用の6権限だけが追加されることをレビューする。
- 公開リポジトリへ実健康データ、credential、token が追加されていないことをレビューする。

## Public repository note

この ADR とテスト fixture は架空の集計値だけを使う。実ユーザーの健康データ、アカウント情報、credential、token はリポジトリへ保存しない。
