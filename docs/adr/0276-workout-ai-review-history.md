# ADR-0276: Workout の完了レビューを日付単位で永続化し次回提案へ再利用する

- Status: Accepted
- Date: 2026-09-30
- Amended: 2026-10-03
- Refines: [ADR-0194](0194-workout-ai-advisor.md), [ADR-0198](0198-workout-chat-tab-and-shared-chat-ui.md), [ADR-0261](0261-workout-menu-presets-and-daily-plan.md), [ADR-0273](0273-background-only-non-interactive-ai-inference.md)

## Context

Workout の完了後レビューは、直近14日間の実績、当日メモ、方針、登録済み種目、プリセットメニューから生成している。

現在の実装では、完了時に `WorkoutDay.menu` が `WorkoutHistory` へ保存されない。そのため完了後レビュー時には「その日に選択していたメニュー」と「登録されている候補メニュー」を区別できず、未実施の登録種目を当日の予定だったものとして扱う余地がある。

また、完了後レビューの生成結果は background task の未消費結果として一時的に保持されるだけで、利用者が後から履歴として閲覧したり、次回のメニュー提案へ継続的に反映する durable state にはなっていない。

レビューは実績そのものではなく AI が実績とメモを解釈した二次情報であるため、次回提案で利用するときは一次情報と明確に区別する必要がある。

## Decision

### 完了履歴へ当日メニューの snapshot を保存する

- `WorkoutHistory` は、その workout を完了した時点の `WorkoutMenu` snapshot を任意で保持する。
- 完了後レビューでは、登録済みプリセット全体を「当日予定」とみなさず、完了履歴または進行中 day に保存された menu snapshot だけを当日の予定として扱う。
- Workout persistence payload を version 3 へ更新し、現在の version 2 payload は menu snapshot がない履歴として version 3 へ読み込む。
- 未知 version を推測解釈しない既存方針は維持する。
- version 2 decode path は current compatibility baseline のためだけに保持する一時的な互換処理とする。version 2 payload を書き込む配布版が current compatibility baseline から外れた時点で、version 2 decoder と v2→v3 専用 fixture/test を削除し、version 2 を unsupported version として扱う。

### 完了後レビューを Workout-owned user data として永続化する

- 完了後レビューは日付単位の `WorkoutAiReview` として保存する。
- 同じ日付でレビューを再実行した場合は、その日の最新レビューで置き換える。
- review は生成日時、生成時 provider、本文を保持する。
- review persistence は既存の Workout AI 設定・メモと同じ feature-owned preferences file を利用する。別 Context や別 database tableを新設しない。
- review はユーザー所有データとして既存 backup 対象に含める。background task の transient state は引き続き backup 対象にしない。
- review の保存は Worker の生成成功時に行い、画面が存在することを前提にしない。

### レビュー履歴を閲覧できるようにする

- Workout のチャット画面から保存済みレビューを新しい順に閲覧できるようにする。
- 画面再生成や process 再生成後も review history は durable state から読み直す。
- background task の一時結果を review history の source of truth にはしない。

### 次回メニュー提案へ過去レビューを利用する

- メニュー提案では直近14日間に保存されたレビューを追加 context として利用する。
- review は「AIが生成した過去の評価」であり、Workout の実績・メモより優先度が低い二次情報として prompt 内で明示する。
- 過去レビューと現在の一次情報が矛盾する場合は、現在の実績・メモ・方針を優先する。
- cloud provider を選択している場合、過去レビュー本文も提案入力として外部送信される。UI の送信内容説明にこれを含める。
- Health Context 由来の read data を AI input に含めない既存境界は維持する。

### 完了後レビュー prompt の判断境界を明示する

完了後レビューでは次を明示する。

- 登録済み種目・他のプリセットは候補であり、その日に選択した menu snapshot だけを予定として扱う。
- 当日メモは利用者の主観的所感として扱い、客観的な負荷測定値と同一視しない。
- 重量、RPE、休憩時間等の未記録情報を補完して負荷を断定しない。
- 記録されたセット数、回数、時間、当日予定、直近の同種目実績から確認できる範囲で比較する。
- 情報不足がある場合は不足項目と判断可能な範囲を明示する。

## Consequences

### Positive

- 「登録されているが当日は予定していない種目」と「予定していたが未実施の種目」を区別できる。
- review が一時的な会話表示ではなく後から参照できる Workout user data になる。
- 過去の評価を次回提案へ継続的に反映しつつ、一次情報と二次情報の優先順位を保てる。
- review persistence と生成 lifetime を Workout Context 内に閉じ、ViewModel lifetimeへ依存しない。

### Negative

- Workout persistence payload が version 3 になり、version 2 からの読み込み path を追加する。
- review 本文を保存するため `workout_ai` preferences のサイズが増える。
- cloud provider 選択時の外部送信内容に過去レビューが追加される。
- 日付単位で最新 review に置き換えるため、同じ日の複数世代レビューは保持しない。

## Formal modeling

今回追加する永続状態は「日付 -> 最新レビュー」の単純な map と、既存 WorkoutHistory への menu snapshot 追加である。cross-context relation、concurrent lifecycle、scheduler ordering は追加しない。

一意性は保存形式そのものと repository unit test で固定できるため、Quint / Alloy model は追加しない。background task lifecycle 自体は ADR-0273 の既存 boundary を変更しない。

## Verification

- payload version 2 を version 3 として読み込み、既存履歴の menu が null になることを unit test する。
- version 3 の保存・再読込で history menu snapshot が保持されることを unit test する。
- Workout 完了時に active menu が history へ保存されることを test する。
- review repository で日付単位の保存・置換・新しい順の読込を unit test する。
- Worker の完了後レビュー成功時だけ durable review が保存されることを test する。
- prompt builder で当日 menu snapshot と登録済みプリセットを区別することを test する。
- prompt builder で過去 review がメニュー提案に入り、完了後レビューには不要な二次情報として混入しないことを test する。
- UI で review history を閲覧でき、cloud 選択時の送信内容説明に過去 review が含まれることを確認する。
- public repository verification、architecture verification、unit test、lint を実行する。

## Compatibility retirement (2026-10-10)

現行配布baselineのWorkout書込形式はversion 4となったため、一時的なversion 2 decoderと専用testを退役した。これにより旧payloadを新形式へ推測変換せずunsupportedとして拒否し、保存済み文字列を保全する。version 3の互換読込も後続ADR-0280の終了条件に基づき同時に退役した。
