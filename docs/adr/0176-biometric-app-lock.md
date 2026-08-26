# ADR-0176: app shell で生体認証ロックを適用する

- Status: Accepted
- Date: 2026-08-26
- Refines: ADR-0063, ADR-0126, ADR-0150

## Context

Mosaic は RSS、メール、蔵書、ヘルスケアなど端末内の個人データを表示するため、端末自体が解除済みでもアプリへ戻る際に追加の認証を要求できる選択肢が必要になった。

設定 UI は `:feature:settings:ui` が所有する一方、ロックは特定 feature の画面ではなく `MainActivity` 以下の app shell 全体を保護する必要がある。ロック判定を Settings feature 内へ置くと navigation より内側でしか適用できず、起動直後やクラッシュ診断表示、ウィジェットや共有 Intent からの entry point を保護できない。

最小プラットフォームは Android 14 / API 34 であるため、Android framework の `BiometricPrompt` と `BiometricManager` を直接利用できる。

## Decision

- 生体認証ロックは既定で無効とし、設定画面の「セキュリティ」から明示的に有効化する。
- 有効化時は `BIOMETRIC_STRONG` が利用可能であることを確認し、その場で認証を成功させてから設定を保存する。
- 解除時は `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` を許可する。生体認証が一時的に利用できない場合でも、端末 PIN / パターン / パスワードで回復可能にするためである。
- ロック設定は app shell の platform policy なので `:app` の private SharedPreferences に保存する。認証済み状態は永続化せず process 内のメモリ状態だけに保持する。
- ロック中は feature content を構成せず、ロック画面だけを描画する。通常画面だけでなく起動時のクラッシュ診断も認証後に表示する。
- ロック中に受け取った共有・ウィジェット等の Intent は認証前には処理せず、認証成功後に現在の Intent を処理する。これにより外部記事表示やデータ変更がロック境界を迂回しないようにする。
- Activity がバックグラウンドへ移動した場合は、設定が有効なら次回表示時に再認証を要求する。ただし認証プロンプト自身や configuration change による遷移では不要な再ロックを行わない。
- ロック有効時に Activity が非表示になる際は `FLAG_SECURE` を設定し、最近使ったアプリ等に直前の内容が残らないようにする。認証済みで前面へ戻った場合だけ解除する。
- 設定画面の表示仕様は引き続き `:feature:settings:ui` が所有し、`:app` は値と変更 callback の wiring および platform authentication のみを担当する。

## Consequences

### Positive

- アプリ内容が表示される前に認証境界を適用できる。
- ウィジェット・共有 entry point を含め、認証前に UI 遷移やデータ変更を実行しない。
- バックグラウンド中の画面内容をシステムの最近使ったアプリ表示から保護できる。
- 生体情報や認証済みトークンをアプリ自身で保存しない。
- 端末 credential fallback により biometric enrollment の変化や一時的なロックアウトから回復できる。
- Settings UI ownership と app shell / platform wiring の既存境界を維持できる。

### Negative

- `MainActivity` が app-wide security gate の lifecycle と deferred Intent handling を扱う責務を持つ。
- アプリから外部画面へ移動して戻る操作でも再認証が発生する。
- framework API に依存する認証ダイアログ自体は JVM unit test だけでは完全には検証できないため、永続化の unit test と CI build に加えて実機確認が必要になる。

## Security and privacy

- リポジトリへ秘密鍵、認証情報、生体情報を追加しない。
- 保存するのはロックを有効化したかという boolean のみとする。
- 認証成功状態は process 終了後に復元しない。
- ロック中の Intent は認証成功前に副作用を起こさない。
- バックグラウンドへ移行した画面内容は `FLAG_SECURE` で保護する。

## Relationship to existing ADRs

- ADR-0063 の Settings UI ownership を維持する。
- ADR-0126 の Android 14 baseline を利用して framework biometric API を採用する。
- ADR-0150 の app shell ownership を拡張し、navigation より外側に必要な security gate を `:app` が所有する。
