# ADR-0279: RSS除外学習の適用結果を明示し未反映feedbackを保持する

- Status: Accepted
- Date: 2026-10-05
- Amends: [ADR-0270](0270-rss-recommendation-scoring.md), [ADR-0273](0273-background-only-non-interactive-ai-inference.md)

## Context

RSSの「除外参考」は、pending feedbackを一定時間まとめて学習AIへ渡し、現在の学習条件を改善する。

従来のstructured resultは更新後の `condition` だけを返していた。この契約では、AIが現在の学習条件と同じ文字列を返した場合でも学習成功として扱われ、今回利用したfeedbackが消費される。したがって、利用者から見ると「既存条件ですでにカバーされていた」のか、「新しいfeedbackが条件へ反映されなかった」のかを区別できない。

特に1回目の学習で条件が作成された後、後続のfeedbackに対してAIが保守的に同じ条件を返すと、条件は変化せずfeedbackだけが消費される。この状態は学習処理自体の停止と見分けにくい。

## Decision

### 学習結果に適用結果を含める

RSS除外学習のstructured toolは、次の2値の `outcome` と適用後の `condition` を返す。

- `updated`: 今回のfeedbackを反映して学習条件を更新した。
- `already_covered`: 今回のfeedbackは現在の学習条件ですでにすべてカバーされている。

`updated` はsnapshot時点の学習条件と返却条件が実際に異なる場合だけ有効とする。`already_covered` は返却条件がsnapshot時点の学習条件と同一の場合だけ有効とする。

outcomeとconditionが矛盾する場合、tool callを適用可能な学習成功として扱わない。既存条件とpending feedbackを保持し、background retry policyへ委ねる。

### 新しいfeedbackを明示的に評価させる

learning promptは今回のfeedbackを1件ずつ確認するよう要求する。

現在の学習条件で明確にカバーされないfeedbackが1件でもある場合は `updated` として条件を改訂する。すべてのfeedbackが現在条件ですでに明確にカバーされている場合だけ `already_covered` を返す。

固有タイトルや一時的な固有名詞の単純列挙と過剰な一般化を避ける規則は維持する。

### 処理状態を既存pending feedbackから表示する

新しいdurable status stateは追加しない。既存のpending feedback件数を設定画面で常に表示する。

- 1件以上: まだ消費されていないため、学習待ちまたは再試行対象。
- 0件: 未処理feedbackはない。直前に条件が変わらなかった場合は、structured resultで `already_covered` と確認されたうえで処理済みである。

これにより、background runtimeやdatabase schemaを増やさず、少なくとも「未処理のまま残っている」状態を利用者が判別できる。

## Consequences

- 同じcondition文字列が返っただけでは新しいfeedbackを黙って消費しない。
- 既存条件で十分な場合は `already_covered` を明示したときだけfeedbackを消費できる。
- 学習条件を実際に変更したときだけ従来どおりpolicy revisionが進む。
- RSS-owned durable state、background Worker、provider routing、backup contractは変更しない。
- 既存の `RSS-FEEDBACK-LEARNING-001` が定めるdebounce、失敗保持、stale result拒否、学習中追加feedbackの持越しという状態遷移は変わらないため、形式モデルの状態空間は変更しない。

## Verification

- learning tool parserで `updated` と `already_covered` を区別し、outcomeを持たない旧形式を拒否する。
- domain serviceで `updated` + 同一conditionを拒否してfeedbackを保持する。
- domain serviceで `already_covered` + 同一conditionだけを成功としてfeedback消費する。
- 設定画面のpending件数表示で未処理状態と処理済み状態を区別する。
- public repository、architecture、unit test、lintを通常のquality gateで確認する。
