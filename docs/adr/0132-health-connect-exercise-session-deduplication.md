# ADR-0132: Health Connect の運動セッションを重複除去する

- Status: Accepted
- Date: 2026-08-21
- Refines: [ADR-0127](0127-health-connect-read-only.md)

## Context

Health Connect の `ReadRecordsRequest<ExerciseSessionRecord>` は個々の record を返すため、複数の提供元が同じ実運動を記録していると、開始・終了時刻や exercise type が少し異なる複数セッションとして返ることがある。

完全一致だけを重複として扱う実装では、詳細付き Workout と、別提供元が数分ずれた時刻で記録した Walking が同じ実運動について複数表示されるケースが確認された。

さらに、長い詳細付きセッションの中に複数の `ExerciseSegment` がある場合、別提供元がその一部を独立した Walking session として記録するケースがある。この場合、session 全体同士の duration 比だけを見ると別件と判定されても、segment と standalone session はほぼ同一の時間帯を表していることがある。

一方で、単に時間帯が重なるだけのセッションを全て統合すると、同じ提供元が意図的に記録した別セッションや、長い運動の一部として発生した別運動を誤って消す可能性がある。

また Health Connect の Aggregate API は activity data の重複をユーザーが設定したアプリ優先度に基づいて処理するため、合計運動時間を raw session 一覧の単純加算で求める必要はない。

## Decision

- Health Connect から取得した raw exercise session は `:feature:health:data` でアプリ向けモデルへ変換し、exercise type と `metadata.dataOrigin.packageName` は Data 層内の重複判定情報としてのみ保持する。
- 開始時刻・終了時刻・exercise type が完全一致する record は従来どおり同一候補として扱う。
- 提供元が異なる場合は、次の両条件を満たす強い時間的重なりも同一の実運動候補として扱う。
  - 重複区間が短い方のセッション時間の 80% 以上
  - 短い方のセッション時間が長い方の 55% 以上
- 詳細付き session に `ExerciseSegment` があり、別提供元の standalone session に segment がない場合は、session 全体だけでなく各 segment と standalone session の時間帯にも同じ 80% / 55% の判定を適用する。いずれかの segment と強く一致すれば、その standalone session は詳細付き session の一部を別提供元が重複記録したものとして統合する。
- segment 対 standalone の近似判定は、詳細付き session と standalone session の提供元が異なる場合に限る。standalone 側にも segment がある場合は、この部分一致規則では統合しない。
- 近似重複判定では exercise type の一致を必須としない。複数アプリが同じ実運動を `OTHER_WORKOUT` と `WALKING` など別 type で記録するケースを吸収するためである。
- 同一提供元の近似した時間帯は統合しない。提供元自身が別 record として記録した意図を優先する。
- 同一候補群からは segment 数、notes の情報量、title の情報量、セッション長の順で情報量が多い record を代表として残す。
- 履歴表示には上記の重複除去済み raw session 一覧を利用する。
- 合計運動時間には `ExerciseSessionRecord.EXERCISE_DURATION_TOTAL` の Aggregate API 結果を利用し、Health Connect が持つ activity data の重複除去とアプリ優先度を尊重する。
- exercise type、record ID、提供元 package は Domain/UI へ公開せず、この処理のための永続化も行わない。

## Consequences

### Positive

- 提供元ごとに数分の時刻差や type 差がある同一運動も履歴上で1件にまとめられる。
- 詳細付きの長時間 session と、その内部 segment を別アプリが短い standalone session として記録したケースも統合できる。
- 詳細な segment を持つアプリ内 Workout と自動検出 Walking が重複した場合、情報量の多い Workout を残せる。
- 同一提供元の近似 record は保持するため、単純な overlap 判定より誤統合を抑えられる。
- segment を持たない長時間 session に短時間 session が含まれるだけのケースは従来どおり duration 比率で除外できる。
- standalone 側にも segment がある場合は部分一致統合しないため、双方が構造化された別運動を過度にまとめない。
- 合計運動時間は Health Connect の Aggregate API に委ねるため、raw session の表示用 heuristic と集計値を分離できる。
- Health Connect 固有情報と判定ロジックは Data 層に閉じ、既存の Context 境界を維持できる。

### Negative

- 異なる提供元が、詳細付き session の segment と時間帯・長さが近い別の実運動を同時に記録した場合は誤って統合する可能性が残る。
- 80% / 55% の閾値と segment 部分一致は Health Connect 自体が提供する duplicate identity ではなく、履歴表示用のアプリ側 heuristic である。
- Aggregate API の合計時間と履歴カードの duration 単純合計は一致しない場合がある。合計時間では Health Connect の優先度ルールを正とする。

## Verification

- 完全一致する同一種別セッションが1件になることを unit test する。
- 提供元が異なり、時刻・exercise type が少し異なる強重複セッションが1件になることを unit test する。
- 長い詳細付き session の segment とほぼ一致する、別提供元の短い standalone session が統合されることを unit test する。
- segment と十分に一致しない standalone session は保持されることを unit test する。
- 同一提供元の近似セッションは別件として残ることを unit test する。
- 異なる提供元でも重複区間が小さい場合は別件として残ることを unit test する。
- segment を持たない長時間セッションに半分の長さの短時間セッションが含まれるだけのケースを統合しないことを unit test する。
- `ExerciseSessionRecord.EXERCISE_DURATION_TOTAL` を Aggregate request に含め、overview の合計運動時間に利用することを compile / CI で確認する。
- `verifyArchitecture` と Health feature の unit test を実行する。
- 変更差分に利用者固有の健康データ、提供元 package の実値、credential 等が含まれていないことをレビューする。
