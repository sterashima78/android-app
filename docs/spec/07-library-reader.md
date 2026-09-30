# 7. 蔵書とBook Reader

## 7.1 蔵書

- Kindle、Audible、Google Books、ファイルサーバー、Web URL 由来の蔵書情報を扱う。
- シリーズ、タイトル、著者、表紙などを表示・整理する。
- タイトル検索、シリーズ表示、source別の操作を提供する。
- `text/plain` の共有から HTTP / HTTPS URL を Web 蔵書として追加できる。
<!-- formal-requirement
id: WEB-LIBRARY-OWNERSHIP-001
models:
  - spec-models/alloy/web_library_ownership.als
-->
- Web 蔵書とブックマークは、重複する永続状態を残さず相互に移動できる。
<!-- /formal-requirement -->

## 7.2 SMB / ファイルサーバー

- SMB接続の表示名、host、port、username、domain、passwordはアプリの全体設定から接続プロファイルとして管理する。
- 蔵書設定では登録済みSMB接続を選び、蔵書として同期するshareとパスだけを個別に設定する。動画設定とは独立しているため、同じ接続先でも異なるshare / pathを指定できる。
- SMB server上のZIP / CBZ / PDF書籍を蔵書へ取り込む。
- Android 17 / API 37 targetでは、既存のSMB接続設定がある蔵書画面を利用する際にローカルネットワーク権限を要求する。拒否した場合はSMB同期・reader・表紙取得等のLANアクセスを利用できない。
- 蔵書の同期場所を解除しても全体設定のSMB接続とpasswordは削除しない。
<!-- formal-requirement
id: LIBRARY-COVER-PREFETCH-QUEUE-001
models:
  - spec-models/quint/library_cover_prefetch_queue.qnt
-->
- 表紙先読みキューはLibrary-ownedのbackup対象外処理状態として、`PENDING` / `RUNNING` / `FAILED` / `COMPLETED` / `SKIPPED`を保持する。
- `PENDING`だけを実行対象としてclaimし`RUNNING`へ進める。実行結果は`COMPLETED` / `FAILED` / `SKIPPED`のいずれかとし、キャンセルやprocess interruptionで残った`RUNNING`は`PENDING`へ戻して再開可能にする。
- `FAILED`は明示的な再試行で`PENDING`へ戻せる。`SKIPPED`は通常の自動投入では再試行せず、ユーザーが未取得表紙の再評価を明示した場合だけ`PENDING`へ戻せる。生成済み表紙がcache上限によってLRU削除された場合は、再生成対象として`SKIPPED`を記録できる。
- WorkManagerの実行状態とWi-Fi / battery / schedulerの待機理由はdurable queue statusとは別のruntime observationとして扱い、scheduler待機だけでqueue statusを`RUNNING`や失敗状態へ変更しない。
<!-- /formal-requirement -->
- SMB credentialはAndroid Keystoreを利用して保護し、画面へ再表示せず、アプリ独自backupへ含めない。

## 7.3 書誌正規化

- SMB書籍について、現在のファイル名と表紙画像を入力として書誌候補を端末内AIで生成できる。
<!-- formal-requirement
id: LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001
models:
  - spec-models/quint/library_metadata_normalization_lifecycle.qnt
-->
- SMB書誌正規化候補は`WAITING_FOR_COVER` / `QUEUED` / `PROCESSING` / `PENDING_REVIEW` / `DEFERRED` / `APPLIED` / `REJECTED` / `FAILED` / `SKIPPED`の状態を持つ。
- 表紙が未取得なら`WAITING_FOR_COVER`とし、表紙が利用可能になった場合だけ`QUEUED`へ進める。`QUEUED`だけをclaimして`PROCESSING`へ進め、候補生成成功で`PENDING_REVIEW`、失敗または対象外で`FAILED` / `SKIPPED`へ進める。process interruptionで残った`PROCESSING`は`QUEUED`へ戻す。
- `PENDING_REVIEW`は反映・保留・却下でき、`DEFERRED`はレビューへ戻せる。反映直前に入力revisionが候補生成時と一致しなければ外部ファイルを変更せず`SKIPPED`へ移して再解析可能にする。
- `PENDING_REVIEW` / `DEFERRED` / `REJECTED` / `FAILED` / `SKIPPED`は明示的な再解析で現在の入力revisionを取り直し、表紙があれば`QUEUED`、なければ`WAITING_FOR_COVER`へ戻せる。`APPLIED`はAI再解析の対象にせず、確定書誌の編集は`APPLIED`のまま行う。
<!-- /formal-requirement -->
- 候補はレビュー画面で確認し、適用または却下する。
- 確定済み判断を保持し、必要な状態では再解析できる。
- 再解析では直前の書誌候補を比較対象として引き継ぎ、表紙・現在のファイル名と照合して各項目を独立に再評価する。同じ結果が妥当なら同じ候補を返してよい。
- 再解析時には任意の補足情報を追加できる。補足は当該再解析の端末内AI入力だけに利用し、固定の構造化出力・validation・安全規則を変更しない。
- 生成結果は構造化出力として受け取り、アプリ側validationを通してから利用する。

- 蔵書の単冊AI整理候補生成とシリーズ単位のAI再整理はLibrary-owned background taskとして実行し、画面のlifecycleに推論を依存させない。

## 7.4 Book Reader

- 対応するローカル書籍をアプリ内readerで閲覧する。
- 読書位置などユーザー所有のreader設定を保持する。


## 形式モデル

- [Alloy: `web_library_ownership.als`](../../spec-models/alloy/web_library_ownership.als) — Web蔵書とブックマークのstableな永続状態が同一contentを同時所有しないことを検査する。
- [Quint: `library_cover_prefetch_queue.qnt`](../../spec-models/quint/library_cover_prefetch_queue.qnt) — 表紙先読みのdurable queue lifecycle、interrupt recovery、明示再試行、runtime waitとの分離を検査する。
- [Quint: `library_metadata_normalization_lifecycle.qnt`](../../spec-models/quint/library_metadata_normalization_lifecycle.qnt) — 表紙待ち、解析、レビュー、確定、revision不一致、再解析の状態遷移を検査する。
