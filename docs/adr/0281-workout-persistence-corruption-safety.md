# ADR-0281: Workout の保存データ破損時に既存記録を保護する

- Status: Accepted
- Date: 2026-10-10
- Refines: [ADR-0276](0276-workout-ai-review-history.md), [ADR-0280](0280-workout-set-effort-and-form.md)

## Context

従来のRepositoryは保存済みJSONのdecodeに失敗すると初期WorkoutSnapshotを返していた。ViewModelは初期化時にsnapshotを再保存するため、破損した既存payloadを初期データで上書きする可能性があった。

## Decision

- payloadが存在しない新規利用時のみ初期状態を生成する。
- 既存payloadの破損・必須field欠落・非対応versionは正常な空状態と見なさず、例外として呼び出し元へ通知する。
- 読み込み失敗時にはViewModelを編集可能にせず、元の保存済みpayloadを一切変更しない旨を表示する。自動初期化・上書きは行わない。
- 現行payloadの主要な構造を検証する。新たな永続状態、backup形式、復旧用の第二source of truthは設けない。
- 明示的な復旧・初期化操作は別途仕様を決定するまで追加しない。
- 現行更新互換性baselineから外れた旧versionは拒否し、保存済みbytesを保持する。

## Consequences

- 破損したデータは自動復旧されず、利用者は読み込みエラーを見る。しかし、記録を意図せず消去するより安全であり、データの取り出し・復元可能性を残す。
- Repository契約は未作成と不正payloadを区別できる。
- 読み込み失敗時の上書き防止はRepository unit testとViewModel state testで検証する。

## Verification

- 破損したJSON、必須field不足、非対応versionの各ケースでpayloadが変わらないこと。
- ViewModelが編集可能な初期状態へ遷移せず保存も行わないこと。
- 現行versionの正常payloadは従来どおり往復すること。
