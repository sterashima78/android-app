# ADR-0198: Workout AI を独立チャットタブ化し共通チャットUIを利用する

- Status: Accepted
- Date: 2026-08-27
- Supersedes in part: [ADR-0194](0194-workout-ai-advisor.md) Decision 1, Decision 2, Decision 3, Decision 5
- Refines: [ADR-0015](0015-shared-ui-interaction-primitives.md), [ADR-0019](0019-feature-bottom-tab-navigation.md), [ADR-0023](0023-chat-markdown-rendering.md)

## Context

ADR-0194 では Workout AI を Workout 画面上部の one-shot panel とし、AI 専用のメニュー候補を設定として保持した。実運用では次の問題が確認された。

- AI panel が記録・タイマー等の画面と同時表示され、Workout の主操作領域を圧迫する。
- provider / 方針 / メニュー候補の設定が panel 内にあり、Workout の「設定」タブと責務が分散する。
- AI 専用のメニュー候補は、Workout 設定で管理している登録済み種目と重複する。
- Workout 終了後は当日セットが `snapshot.history` に移るが、AI prompt の「今日の記録済みセット」は `snapshot.today` だけを参照していたため、同じ日の履歴が存在しても「記録済みセット: なし」となる場合がある。
- Workout AI response は Markdown 文字列を通常の `Text` で表示しており、一般チャットが利用する Markdown 表示と見た目・機能が一致していない。
- 一般チャットの message bubble は feature/chat 内に閉じており、他 feature が同じ会話表現を再利用できない。

## Decision

### 1. Workout AI を Workout の独立した「チャット」タブにする

Workout の feature-owned bottom navigation に `チャット` を追加する。記録画面の上部に AI panel を常時表示しない。

チャットタブでは ADR-0194 の2操作を維持する。

- 今日のメニュー提案
- 完了後レビュー

現時点では任意テキスト入力の自由会話へ拡張しない。操作と応答を chat message bubble で表現することで、画面構造と表示体験をチャットとして統一する。

### 2. Workout AI の設定は Workout の「設定」タブへ集約する

provider 選択、cloud 送信内容の説明、ワークアウト方針は Workout の設定タブに配置する。

日付単位の「今日のワークアウトメモ・所感」は設定値ではなく当日の AI context なので、チャットタブに残す。

### 3. AI 専用のメニュー候補入力を廃止し、登録済みトレーニングメニューを source of truth にする

AI prompt のメニュー候補は `WorkoutSnapshot.exercises` から毎回生成する。種目名だけでなく目標セット数と単位も含める。

既存 `workout_ai` SharedPreferences の `menu_candidates` は後方互換のため読み書き可能な legacy field として当面残すが、UI と prompt は参照しない。新たな migration は不要とする。

### 4. 当日の完了済み履歴と進行中セットを「今日」の context に統合する

AI prompt は対象日と同日の `snapshot.history` のセットと、`snapshot.today` のセットを統合し、set id で重複除去して「今日の記録済みセット」として渡す。

直近14日間の過去記録セクションから対象日を除外し、同じ当日記録を「過去履歴」と「今日」に二重掲載しない。

これにより Workout を終了して当日セットが history に移った直後でも、AI が当日実績を欠落させない。

### 5. chat message bubble を core/designsystem に置く

一般チャットが持っていた user / assistant message bubble の見た目と assistant Markdown rendering を `core/designsystem` の `ChatMessageBubble` として共通化する。

- feature/chat は共通 bubble を利用する。
- feature/workout も同じ bubble を利用する。
- assistant message は既存 `MarkdownText` で描画する。
- user message は plain text とする。

feature 固有の session 管理、入力欄、prompt 構築、provider state は designsystem に移さない。

## Consequences

### Positive

- Workout の記録画面から AI panel が外れ、記録操作の表示領域を確保できる。
- AI 設定とトレーニングメニュー管理を同じ設定タブで確認できる。
- AI 用メニューの二重管理がなくなり、登録済み種目が常に prompt に反映される。
- Workout 完了後でも当日実績を AI が参照できる。
- Workout と一般チャットの Markdown 表示・message bubble が一致する。
- 共通UIは designsystem、feature 固有の状態・domain policy は feature 所有という既存の module boundary を維持できる。

### Negative

- Workout bottom navigation が5項目になる。
- `menu_candidates` の legacy persistence key は当面残るため、完全削除は将来の cleanup になる。
- Workout AI は chat UI を使うが、自由入力チャットではないため一般チャットと操作モデルは完全には同一でない。

## Verification

- Workout bottom navigation に `チャット` が存在し、チャット画面が独立して表示されることを確認する。
- Workout AI provider / 方針が「設定」タブに表示され、チャット画面に設定展開UIが残っていないことを確認する。
- prompt builder の unit test で `WorkoutSnapshot.exercises` の種目、目標セット数、単位が prompt に含まれることを固定する。
- 当日完了済み history のセットが「今日の記録済みセット」に含まれ、「記録済みセット: なし」と矛盾しないことを unit test する。
- 同日の完了済み history が過去記録セクションへ重複掲載されないことを unit test する。
- feature/chat と feature/workout が `core/designsystem` の `ChatMessageBubble` を利用することを architecture review する。
- assistant response が `MarkdownText` 経由で表示されることを確認する。
- Architecture / Test / Lint / public repository verification を実行する。

## References

- [ADR-0015](0015-shared-ui-interaction-primitives.md)
- [ADR-0019](0019-feature-bottom-tab-navigation.md)
- [ADR-0023](0023-chat-markdown-rendering.md)
- [ADR-0194](0194-workout-ai-advisor.md)
