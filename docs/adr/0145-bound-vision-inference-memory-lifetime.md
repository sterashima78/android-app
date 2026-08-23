# ADR-0145: Android 画像推論の Engine 寿命をメモリ安全性のため制限する

- Status: Accepted
- Date: 2026-08-23
- Refines: ADR-0079, ADR-0134, ADR-0139
- Refined by: [ADR-0149](0149-sanitize-shareable-crash-diagnostics.md)

## Context

ADR-0079 では、同一 process 内のローカル AI 推論が `LocalModelManager.shared(context)` を共有し、最後の推論から5分間は LiteRT-LM Engine を保持して連続タスクの再初期化を避ける方針を採用した。ADR-0134 の SMB 書誌正規化もこの process-wide runtime を利用し、表紙画像と現在のファイル名を入力する画像推論では `visionBackend = GPU` を使用する。

実端末で SMB 書誌正規化を継続しながら候補レビュー画面を操作すると、アプリの応答が徐々に悪化した後にプロセスが終了する現象が確認された。アプリ側では LiteRT-LM `Conversation` を `use` で閉じ、`LocalInferenceSession` も `finally` で解放しているため、Kotlin object graph 上の単純な Conversation / Session の保持漏れとは一致しない。

一方、LiteRT-LM 0.14.0 の Android GPU/OpenCL 画像推論では、Conversation を破棄しても同一 Engine を保持して次の画像推論へ再利用すると native/GPU memory が累積する既知問題が報告されている。現在の SMB 書誌正規化は複数冊を連続処理するため、この条件と一致する。

Android 17 では Memory Limiter によりメモリ圧迫時にプロセスが終了する場合があり、通常の uncaught exception handler を経由しない。そのため、クラッシュスタックだけでは原因を確認できず、次回起動時の `ApplicationExitInfo` と推論前後の process memory を合わせて確認する必要がある。

## Decision

### 1. process-wide `LocalModelManager` と推論直列化は維持する

SMB 書誌正規化は引き続き `LocalModelManager.shared(context)` を利用する。Library 側に独立した Manager / Engine を生成して process-wide inference lock を迂回しない。

ADR-0079 の「プロセス内で同一 Manager を共有する」という責務境界と、バックグラウンド AI の直列実行方針は維持する。

### 2. SMB 画像推論では1冊の処理終了ごとに retained Engine を解放する

LiteRT-LM の Android GPU/OpenCL 画像推論に関する upstream 問題が解消・実端末確認されるまで、SMB 書誌正規化は1冊の画像推論が終了した時点で共有 `LocalModelManager` の retained runtime を明示的に閉じる。

現在の `LocalModelManager.close()` / `LocalInferenceSessionTracker.close()` は Manager instance 自体を再利用可能なまま retained Engine と tokenizer cache を解放するため、次の書籍では同じ共有 Manager を再取得しつつ Engine を再初期化する。

この例外は画像入力を伴う SMB 書誌正規化に限定する。text-only Summary、Knowledge、Chat 等は ADR-0079 の5分 idle retention を維持する。

この変更により画像推論の各冊で `PREPARING_MODEL` が発生し、処理時間は増える。ただし数 GB 級 native runtime の累積によるプロセス終了を避けることを優先する。

### 3. 画像推論前後のメモリ指標を個人情報なしでリング保存する

`core:ai-runtime` が画像推論の直前と Engine 解放後に次の数値だけを記録する。

- PSS (KiB)
- RSS (KiB)
- native heap allocated (KiB)
- Java heap used (KiB)
- timestamp
- 固定 phase 名

直近24サンプルだけを app-private `SharedPreferences` に保持し、同じ内容を Logcat にも出力する。

書籍タイトル、ファイル名、SMB path、sourceId、serverId、URL、prompt、AI 出力等は診断データへ含めない。公開リポジトリの fixture / ADR にも実ユーザーデータを追加しない。

### 4. Android のメモリ関連 process exit を次回起動時に既存クラッシュ診断へ統合する

`StartupCrashStore` は起動時に `ActivityManager.getHistoricalProcessExitReasons()` を確認する。

次をメモリ関連終了として扱う。

- `ApplicationExitInfo.REASON_LOW_MEMORY`
- description に `MemoryLimiter` を含む終了

該当時は `reason`、`status`、`importance`、PSS、RSS、description と直近の local AI memory samples を既存の診断レポートへ保存する。

既に処理した process exit timestamp を記録し、同じ終了理由を起動ごとに再報告しない。通常の user-requested exit 等はクラッシュ診断へ変換しない。

共有可能な report の privacy boundary は ADR-0149 で追加され、uncaught exception / process-exit report の最終文字列を保存前に共通 sanitizer へ通す。

### 5. upstream 修正後は実端末の連続画像推論で再評価する

LiteRT-LM 側で GPU/OpenCL の Conversation/Engine 間メモリ解放問題が修正された版へ更新する場合、次を実端末で確認してから画像 Engine の再利用を戻す。

- 複数冊の連続画像推論後に Engine 解放前後の RSS/PSS が単調増加しない
- Android の process exit に MemoryLimiter が記録されない
- 書誌正規化の構造化 Tool Calling が維持される
- text-only 推論の既存 cache / session lifecycle に退行がない

## Consequences

### Positive

- Android GPU 画像推論の native memory 累積を1冊単位で打ち切れる。
- process-wide Manager と既存の推論直列化を維持できる。
- Android 17 の Memory Limiter による終了を uncaught exception と区別して確認できる。
- 実端末で修正効果を PSS/RSS の時系列として確認できる。
- 診断データへ書誌・SMB・prompt の個人情報を含めない。

### Negative

- SMB 書誌解析は各冊で画像 Engine の再初期化が必要になり、連続処理の速度が低下する。
- `LocalModelManager.close()` は共有 Manager の cancel state も更新するため、画像推論終了と同時期に別の foreground 推論が既に同じ lock を待っている競合では、その要求がキャンセル扱いになり再試行が必要になる可能性がある。独立 Manager へ分離して直列化を失うよりメモリ安全性を優先する一時的なトレードオフとする。
- upstream 修正を取り込んだ後に、この一時的な bounded lifecycle を再評価する作業が必要になる。

## Relationship to existing ADRs

- ADR-0079: text-only の process-wide Engine reuse と5分 idle eviction は維持し、Android GPU 画像推論に限って安全性優先の例外を追加する。
- ADR-0134: SMB マルチモーダル書誌正規化の GPU vision backend は維持するが、Engine retention は1冊ごとに打ち切る。
- ADR-0139: Android runtime 固有の process termination は `ApplicationExitInfo` を使って起動時診断へ取り込む。
- ADR-0149: 共有可能な crash / process-exit report は保存前にサニタイズし、個人情報や credential-like detail の accidental disclosure を抑える。

## References

- LiteRT-LM issue #2699: Android OpenCL GPU memory is not released across conversations while retaining an Engine
- Android Developers: Android 17 behavior changes / Memory Limiter
