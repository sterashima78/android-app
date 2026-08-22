# ADR-0127: Health Connect を読み取り専用 Health Context の外部データソースとする

- Status: Accepted
- Date: 2026-08-19
- Amended: 2026-08-22
- Refines: [ADR-0001](0001-layered-architecture.md), [ADR-0003](0003-multi-module-architecture.md), [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0126](0126-android-platform-baseline.md)
- Refined by: [ADR-0131](0131-workout-health-connect-export.md) for the one-way Workout -> Health Connect write path

## Context

アプリから歩数、活動消費カロリー、運動、心拍、睡眠、体重、体脂肪率、栄養情報を参照したい。Android 14 以降では Health Connect が platform の健康・フィットネスデータ共有基盤であり、このアプリの最小 API 34 と整合する。

活動量については、ワークアウトの回数や時間からアプリ独自に消費カロリーを推定せず、Health Connect に記録された `ActiveCaloriesBurnedRecord` を参照する。これは基礎代謝を含まない活動由来の消費エネルギーであり、Workout Context が所有する運動記録とは別の健康指標として扱う。心拍数も同様に Health Connect の測定値を Health Context で参照する。

運動については合計時間だけでなく、Health Connect に記録された個々の運動セッションと、そのセッションが持つ種目内訳を確認したい。`ExerciseSessionRecord` は運動種別、タイトル、メモ、開始・終了時刻、`ExerciseSegment` を持ち、`ExerciseSegment` は種目、開始・終了時刻、反復回数を持つ。一方で、提供元アプリが segment を書き込まないセッションも存在する。

栄養情報については、Health Connect の `NutritionRecord` から摂取カロリー、たんぱく質、脂質、炭水化物を参照し、日単位の推移と一般的な摂取目安を比較したい。比較基準は厚生労働省「日本人の食事摂取基準（2025年版）」を使用し、代表ケースとして30〜49歳男性・身体活動レベル「普通」を扱う。減量時の目安は公的な個別基準ではないため、標準エネルギーから500 kcal/日を差し引いたアプリ内の比較参考値として明示する。

一方、既存の Workout Context はユーザーがこのアプリで記録するワークアウト状態を所有している。Health Connect 由来データを Workout の永続状態へコピーすると、所有権、重複、同期方向が曖昧になる。また健康データは機微性が高いため、不要な永続化、バックアップ、バックグラウンドアクセス、AI 利用を増やすべきではない。

当初は「今日・直近7日・直近30日」のローリング期間だけを扱っていたが、Health Connect 側で過去データが後から修正・追加された場合、対象日を明示して再読込できない。また日次の詳細確認と週・月の傾向確認では適切な表示粒度が異なる。選択日を基準に日・週・月を移動でき、表示中の期間を Health Connect から明示的に再取得できる必要がある。

Health Connect の通常の読み取り範囲は、最初に権限が付与された時点から30日前までに制限される。より古い他アプリ由来データを選択可能にする場合、対応する Health Connect 実装では `READ_HEALTH_DATA_HISTORY` を要求する必要がある。この capability は Android OS の API level だけではなく Health Connect feature status で確認する。

## Decision

- `:feature:health:{domain,data,ui}` を独立 feature とする。
- Health Connect は `:feature:health:data` の外部データソースとし、Health Connect の Record 型を Domain/UI へ公開しない。
- 読み取り対象は歩数、活動消費カロリー、運動セッション、心拍、睡眠、体重、体脂肪率、栄養情報とする。
- 活動消費カロリーは `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL` を選択期間で集計し、kcal として表示する。基礎代謝を含む総消費カロリーは現時点の表示対象にしない。
- 活動消費カロリー、心拍数、歩数などの健康指標は Health Context の責務とする。Workout Context ではこれらを推定、計算、永続化しない。
- 運動セッションは選択期間内の record を新しい順に表示し、タイトルまたは運動種別、開始・終了時刻、所要時間を確認できるようにする。
- 運動セッションに `notes` または `segments` がある場合は、セッションを展開して詳細を表示する。segment は標準化された種目名、開始・終了時刻、所要時間、反復回数を表示する。提供元が segment を記録していない場合は「内訳データなし」として通常状態で扱う。
- Health Connect の Record/segment type の整数値は Data 層でアプリ向けの表示名へ変換し、Domain/UI に platform 定数を漏らさない。
- 運動合計時間と運動履歴は同じ `ExerciseSessionRecord` 読み取り結果から生成し、セッションごとの分丸めではなく duration を合計してから分へ変換する。
- 現在の `androidx.health.connect:connect-client:1.1.0` で安定して利用できる title、notes、segments、segment repetitions を対象とする。1.2.0 alpha で追加された segment の重量、set index、RPE は安定版へ更新する別判断まで利用しない。
- 栄養情報は `NutritionRecord` の摂取カロリー、たんぱく質、脂質、炭水化物を読み取り、record の開始日時を利用者が経験したタイムゾーンの日付へ変換した上で日単位に合算する。
- 栄養情報は Health Connect 上の対象 record を合算し、特定の提供元アプリ package には依存しない。複数提供元が同一食事を重複記録する場合の正規化は将来課題とする。
- UI の期間は `日`、`週`、`月` のカレンダー単位とする。日は選択日の0時から翌日0時、週は月曜日から翌月曜日、月は月初から翌月初を期間境界とする。現在を含む期間の終了時刻は現在時刻へ切り詰める。
- 任意の日付を選択でき、前後の期間へ移動できる。未来のみの期間は表示しない。
- 表示中の期間には「選択期間を Health Connect から再取得」を提供し、アプリ内キャッシュを介さず同じ期間を Health Connect へ再問い合わせする。最終取得時刻を画面上で確認できるようにする。
- 日表示は当日の指標と運動セッションなどの詳細確認を優先する。週表示は期間合計・平均に加えて日別内訳と運動セッションを表示する。月表示は期間合計・平均と日別内訳・時系列グラフを中心とし、長大になり得る個別運動セッション一覧は表示しない。
- 週・月の日別指標は `AggregateGroupByPeriodRequest` を1日単位で使用し、歩数など累積型を個別 record の単純合算で二重計上しない。
- 体脂肪率は選択期間内の測定値を時系列で表示し、最新値も確認できるようにする。
- 栄養情報は熱量・P・F・Cを切り替える時系列グラフで表示し、標準目安と減量参考を同じグラフ上で比較できるようにする。
- 標準目安は「日本人の食事摂取基準（2025年版）」の30〜49歳男性・身体活動レベル「普通」の推定エネルギー必要量2,750 kcal/日を代表値とする。たんぱく質13〜20%E、脂質20〜30%E、炭水化物50〜65%Eをグラムへ換算して比較帯を作る。たんぱく質推奨量65 g/日も説明として表示する。
- 減量参考は2,250 kcal/日（標準代表値から500 kcal/日減）とし、同じP/F/Cエネルギー比率から比較帯を再計算する。これは厚生労働省による個別の減量基準ではなく、アプリ内の比較用参考値であることをUIに明示する。
- Health Connect 由来データをアプリ DB へ永続化しない。Backup、AI task、外部 API にも渡さない。期間再取得のための永続キャッシュも追加しない。
- `READ_HEALTH_DATA_IN_BACKGROUND` は要求しない。画面表示・明示更新時の foreground read のみとする。
- `READ_HEALTH_DATA_HISTORY` は `HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY` が利用可能な場合だけ権限要求候補へ含める。通常の読み取り範囲より古い期間を選択し、履歴権限が未付与なら専用状態を表示して権限付与を促す。feature 自体が利用できない場合は古い期間を利用不可として扱う。
- Health と Workout は別 Context とする。Workout から Health Connect への一方向 write は ADR-0131 に従い、Health Connect の read data を Workout へ同期しない。
- 権限は利用時に Health Connect へ再確認し、ユーザーによる権限取り消しを通常状態として扱う。
- Android 14 以降の Health Connect permission usage から開ける利用説明 Activity を提供する。

## Consequences

### Positive

- 健康データをアプリ側へ複製せず、Health Connect を source of truth として扱える。
- Health Connect で後から修正・追加された過去日も、対象期間を選んで明示的に再取得できる。
- 日の詳細、週の短期傾向、月の長期傾向を同じ画面で適切な粒度に切り替えられる。
- 30日より古い履歴も Health Connect が対応し、利用者が権限を付与した場合に参照できる。
- Workout の ownership を変更せず導入できる。
- Android platform / Health Connect 型が Data 層へ閉じる。
- 歩数、活動消費カロリー、心拍数などをヘルスケアへ集約し、Workout を運動内容の記録に集中させられる。
- 合計運動時間だけでなく、どの運動をいつ行い、Health Connect にどの種目内訳が記録されているかを同じ画面で確認できる。
- アプリ内 Workout から書き出した title、notes、segments も再読込時に詳細として確認できる。
- 体脂肪率と栄養摂取の推移を、追加の永続化や同期機構なしで確認できる。
- 栄養の実績と公的基準由来の比較帯を同一画面で確認でき、固定の第三者アプリ依存を持たない。

### Negative

- Health Connect が利用できない状態や権限未付与時は表示できない。
- 30日より古い他アプリ由来データは Health Connect の履歴読み取り capability と追加権限に依存する。
- オフラインキャッシュや長期トレンドのアプリ独自保存は行えないため、表示や再取得には Health Connect が利用可能である必要がある。
- 活動消費カロリーは Health Connect に対応 record が存在しない期間では表示できない。Workout の回数や時間からの補完推定は行わない。
- 運動の segment は任意情報なので、提供元アプリが書き込まない場合はセッション単位の情報しか表示できない。
- stable 1.1.0 のままでは segment の重量、set index、RPE は表示できない。
- 栄養情報も複数データソースが同じ食事を重複して書き込んだ場合は二重計上し得る。
- 標準目安は代表的な年齢・活動量の比較値であり、個人の必要量を表さない。年齢、身体活動量、体格、減量速度に応じた個別目標は将来の設定機能として扱う。

## Verification

- Health ViewModel の権限未付与、読取成功、日・週・月のカレンダー境界、任意日選択、未来日制限を unit test する。
- 30日より前を含む期間について、履歴権限未付与と history feature 非対応を別状態として unit test する。
- 通常の read 権限8種類と Workout export の write 権限を増やさず、履歴用途だけ `PERMISSION_READ_HEALTH_DATA_HISTORY` を追加することを unit test・レビューで確認する。
- 週・月の日別内訳は Health Connect の1日単位 aggregate group を利用し、期間にデータのない日も日付軸を維持することを確認する。
- 運動セッションがない場合の合計時間を未取得として扱い、複数セッションの duration を合計してから分へ変換することを unit test する。
- 主要な session/segment type がアプリ向け表示名へ変換されることを Data 層の unit test で確認する。
- 運動履歴の同日・日跨ぎ時刻表示と秒を含む duration 表示を UI 層の unit test で確認する。
- 体脂肪率グラフの表示範囲計算を unit test し、単一値と境界値を検証する。
- 栄養 record の日次集計を unit test し、同日複数recordの合算と日付順を検証する。
- 標準目安と減量参考のP/F/C換算値を unit test する。
- `verifyArchitecture` で UI -> Data の依存がないことを確認する。
- `:feature:health:domain:test`、`:feature:health:data:test`、`:feature:health:ui:test`、全体 unit tests、release lint を実行する。
- 公開リポジトリへ実健康データ、credential、token が追加されていないことをレビューする。

## Public repository note

この ADR とテスト fixture は架空の集計値・日時だけを使う。実ユーザーの健康データ、運動メモ、アカウント情報、credential、token はリポジトリへ保存しない。
