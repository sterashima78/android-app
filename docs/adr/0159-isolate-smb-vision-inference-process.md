# ADR-0159: SMB 画像推論を短寿命の専用プロセスへ隔離する

- Status: Accepted
- Date: 2026-08-23
- Amends: ADR-0145
- Refines: ADR-0079, ADR-0134

## Context

ADR-0145 では LiteRT-LM 0.14.0 の Android GPU/OpenCL 画像推論で native/GPU memory が Conversation 間に残留する upstream 問題への暫定対策として、SMB 書誌正規化の1冊ごとに retained Engine を閉じる方針を採用した。

実端末の追加診断では `Engine.close()` 自体は成功し、推論ピークから大きな量のメモリが回収される一方、複数冊を継続すると native heap / PSS の基準値が再び上昇するケースが確認された。Java heap は相対的に小さく、Kotlin object graph の通常のリークだけでは説明しにくい。LiteRT-LM 側の Android OpenCL/GPU memory retention issue も未解決である。

1冊ごとに Engine を再生成すると native memory の保持時間は制限できるが、model/Engine 初期化を毎回行うため処理時間への影響が大きい。一方、Engine を長時間保持すると upstream 問題の影響を再び受ける。

このため、UI・WorkManager・DB を持つ main process から GPU vision runtime を分離し、少数冊だけ Engine を再利用した後に専用 process 自体を終了することで、初期化コストとメモリ回収の確実性を両立させる。

## Decision

### 1. SMB 画像推論だけを `:local_ai_vision` process へ隔離する

SMB 書誌正規化の画像推論は `feature:library:data` が所有する非公開 bound Service へ委譲し、Service を `android:process=":local_ai_vision"` で実行する。

Service は `android:exported="false"` とし、外部アプリから呼び出せる API にしない。`isolatedProcess` は使用しない。model artifact と app-private の表紙キャッシュへ同一 UID でアクセスする必要があるためである。

main process は引き続き次を所有する。

- WorkManager の durable queue と foreground execution
- DB の claim / requeue / candidate 保存
- `LocalAiBackgroundTaskGate` による他の local AI task との優先度・直列化
- 表紙キャッシュの存在・入力メタデータの検証

専用 process は DB を開かず、画像推論と構造化書誌候補の生成だけを担当する。

### 2. Binder には画像本体を渡さない

Binder transaction size に画像 payload を依存させないため、main process から専用 process へは次の小さい入力だけを渡す。

- 現在のファイル名
- app-private cache 配下にある表紙ファイルの path
- ユーザー設定済みの書誌正規化 prompt template

Service 側で canonical path が app cache 配下であること、ファイルが存在すること、既存の8MiB入力上限内であることを再検証してから読み込む。

IPC payload、Logcat、memory diagnostics、ADR / fixture に表紙 bytes、SMB path、sourceId、AI 出力等を追加しない。

### 3. 1 process あたり2冊を処理してから process を終了する

専用 process 内では `LocalModelManager.shared(context)` と vision Engine を最大2冊の間だけ再利用する。

現在の書誌正規化は構造化出力の検証に失敗した場合、同じ書籍について1回だけ修復再推論を行う。そのため2冊バッチでは通常2会話、最悪でも4会話程度に retained Engine の寿命を制限できる。

2冊目の応答後、または Worker が終了・キャンセルされた時点で main process は Service を unbind する。Service は Engine を閉じた後に専用 process 自体を終了する。main process は process death を確認してから次バッチへ bind し、終了途中の process に次の Engine を載せない。

この process 終了を native/GPU/OpenCL allocation の最終的な回収境界とする。Java GC や `Engine.close()` だけを完全回収の前提にしない。

### 4. 専用 process では main application runtime を起動しない

Android は secondary process の生成時にも `Application` を生成するため、`YomitoriApplication.onCreate()` は現在の process name が application package name と一致する main process のときだけ、次を開始する。

- Activity lifecycle tracking
- startup crash/process-exit collection
- widget refresh observer
- bookmark enrichment backfill scheduling

これにより短寿命 AI process のたびに main process 向け runtime を重複起動しない。

### 5. memory diagnostics は専用 process 内の実測として維持する

`vision-before` は専用 process が各書籍の推論を開始する直前に記録する。Engine init / inference / release の既存診断も専用 process 内の `LocalModelManager` から記録する。

ADR-0145 の「1冊ごとの `vision-after-engine-release`」という解釈は本 ADR で変更し、Engine release は原則として process batch の終了時に発生する。

診断へ書籍タイトル、ファイル名、表紙 path、prompt、AI 出力を追加しない。

### 6. upstream 修正後に process isolation を再評価する

LiteRT-LM の Android GPU/OpenCL memory retention 問題が修正された版を採用し、実端末の連続画像推論で PSS/native memory の基準値が安定することを確認できた場合、専用 process と2冊バッチの継続要否を再評価する。

## Consequences

### Positive

- GPU/native memory が main process に累積して UI・WorkManager・DB と共倒れする経路を遮断できる。
- process death により、library/runtime が回収できない driver/OpenCL allocation も OS の process resource として回収できる。
- 1冊ごとの Engine 初期化から2冊ごとの初期化へ減らし、ADR-0145 より初期化コストを抑えられる。
- main process は durable queue と DB transaction ownership を維持するため、専用 process が異常終了しても未保存 item を再処理できる。
- Binder に大きな画像 payload を載せず、transaction size の問題を避けられる。

### Negative

- process 起動、Binder 接続、process 終了待ちのオーバーヘッドが追加される。
- 2冊バッチ内では upstream memory retention が発生し得る。バッチサイズは速度よりメモリ安全性を優先した保守的な値である。
- Service / IPC / secondary-process lifecycle という Android 固有の複雑性が増える。
- secondary process では `Application` の main-runtime 初期化を抑止する必要があり、今後別の secondary process を追加する場合も process ownership を明示する必要がある。

## Relationship to existing ADRs

- ADR-0079: process-wide Manager の原則は各 process 内で維持する。main process の text-only runtime と `:local_ai_vision` の vision runtime は process 境界で分離する。
- ADR-0134: SMB 書誌正規化の queue、候補レビュー、GPU vision backend は維持し、推論 execution boundary だけを専用 process へ移す。
- ADR-0145: 1冊ごとに Engine を閉じる暫定策を、2冊ごとの専用 process recycle に置き換える。privacy-preserving memory diagnostics は維持する。
- ADR-0149: 専用 process の異常終了を含む共有可能な診断は、既存 sanitizer の privacy boundary を維持する。

## References

- LiteRT-LM issue #2699: Android OpenCL GPU memory is not released across conversations while retaining an Engine
- Android Developers: Processes and app lifecycle
- Android Developers: Bound services / Messenger
