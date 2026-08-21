# ADR-0134: SMB 書籍の書誌・ファイル名正規化をマルチモーダル候補レビューとして扱う

- Status: Accepted
- Date: 2026-08-21
- Refines: ADR-0056, ADR-0065, ADR-0066, ADR-0071, ADR-0079, ADR-0104, ADR-0108, ADR-0111, ADR-0133

## Context

ファイルサーバ由来の書籍は、既存ファイル名にスキャン時の仮名、表記揺れ、巻数表現の不統一などが含まれることがある。ファイル名だけでは正しい書誌を判別しにくい一方、ADR-0133 により一覧用の表紙画像は SMB 本体の同期と分離した永続キューで取得できる。

ローカル AI には Gemma 4 系の LiteRT-LM モデルを使用しており、現在の runtime では画像とテキストを同時入力できる。表紙と現在のファイル名を組み合わせれば、タイトル、著者、シリーズ、巻数、出版社等の候補を端末内だけで生成できる。

ただし通常の蔵書 AI 整理と異なり、ファイル名の反映は SMB 上の実ファイル rename という外部副作用を伴う。誤推論を自動反映すると、ファイルサーバ上の名前が大量に誤変更される危険がある。また `library_items` は SMB 同期時に再構築される同期キャッシュなので、ユーザーが確定した書誌情報や「却下済み」という判断をそこだけに保存すると次回同期で失われる。

## Decision

### 1. Library Context が書誌正規化ワークフローを所有する

SMB 書籍の書誌・ファイル名正規化は Library の責務とし、`feature:library:data` が永続バッチ、候補、確定判断、WorkManager worker を所有する。

次の Library-owned table を追加する。

```text
smb_metadata_normalization_batches
smb_metadata_normalization_items
smb_metadata_normalization_decisions
```

`library_items` は従来どおり再構築可能な同期キャッシュとし、確定判断の source of truth にはしない。app database version は 27 とする。

### 2. 入力は現在のファイル名と ADR-0133 のローカル表紙キャッシュに限定する

正規化 worker は SMB 書籍本体を直接読み込んで表紙を抽出しない。

表紙が未取得の対象は `WAITING_FOR_COVER` とし、ADR-0133 の `smb_cover_prefetch_queue` に取得を委ねる。表紙キャッシュが利用可能になった後だけ AI 推論へ進む。これにより ZIP / CBZ の 64 MiB streaming 上限、PDF の 128 MiB 一時取得上限、Wi-Fi 条件、credential 境界を重複実装しない。

AI入力は次の2点だけとする。

- 現在のファイル名
- アプリ private cache に保存された一覧用表紙画像

### 3. LiteRT-LM の同一 process-wide runtime で画像入力する

`core:ai-runtime` の `LocalModelManager` に汎用的な画像入力 API を追加し、Gemma 4 への画像＋テキスト推論も既存の process-wide inference lock と retained engine を使用する。

画像推論では `EngineConfig.visionBackend` に GPU backend を指定する。画像推論中は speculative decoding を無効化し、画像入力用 engine configuration を通常の text-only engine cache key と分離する。

Library 側は LiteRT-LM の `Engine` を直接所有せず、書誌推定の prompt、schema、validation policy だけを所有する。これは ADR-0056 と ADR-0079 の責務境界を維持する。

### 4. AI は構造化書誌情報だけを提案し、ファイル名は決定的に生成する

モデル出力は JSON object に固定し、少なくとも次を扱う。

- title
- authors
- publisher
- publishedDate
- isbn10 / isbn13
- seriesName
- seriesPosition
- confidence
- reason

追加 field、型不一致、件数・文字数制約違反は失敗として扱う。不正出力時は validation error だけを返して1回だけ再生成し、不正出力本文そのものは再入力しない。

AI に SMB path や変更後ファイル名を自由生成させない。変更後ファイル名はアプリ側で title と seriesPosition から決定的に生成し、元拡張子を維持する。`/`、`\`、制御文字等の path / filename 危険文字を拒否・正規化し、レビュー時にユーザーが編集した名前にも同じ検証を適用する。

### 5. SMB rename は必ずユーザー確認後に行う

一括解析の正常経路は自動 rename しない。

候補状態は次を持つ。

- `WAITING_FOR_COVER`: 表紙先読み待ち
- `QUEUED`: AI解析待ち
- `PROCESSING`: AI解析中
- `PENDING_REVIEW`: 候補生成済み・未確認
- `DEFERRED`: 保留
- `APPLIED`: ユーザーが反映済み
- `REJECTED`: ユーザーが却下して確定
- `FAILED`: AI解析等の失敗
- `SKIPPED`: 入力変更や対象消失等で対象外

レビュー画面では、現在の表紙、元ファイル名、提案ファイル名、書誌候補、確信度、理由を確認し、「反映」「編集して反映」「保留」「却下」を行える。失敗・対象外は再解析または「却下して確定」を選べる。

`APPLIED` と `REJECTED` は確定状態であり、後続の一括解析対象から除外する。再解析を行う場合は明示的な操作を必要とする。

### 6. 反映直前に入力 revision を検証する

候補生成時には source ID に加え、元ファイル名、remote file size、modified time を保持する。

反映時に現在の SMB 蔵書情報と一致しなければ rename を行わず、古い候補として拒否する。レビュー待ちの間にユーザーや別プロセスがファイルを変更した場合、古い AI 結果で上書きしない。

rename 自体は既存の `SmbLibraryRepository.renameBook` を再利用し、SMB rename、`sourceId`、Library identity、reader cache、cover cache の既存移行規則を維持する。

### 7. 確定書誌は同期キャッシュへ投影する read model とする

`APPLIED` の書誌情報は `smb_metadata_normalization_decisions` に保持する。Library snapshot 読み込み時に SMB 書籍へ overlay するため、次回 SMB 同期が `library_items` の title / authors 等をファイル由来の値へ再構築しても、ユーザーが確定した書誌を表示上失わない。

シリーズ情報は既存の Library-owned `library_item_series` に反映する。

手動 SMB rename で `sourceId` が変わる場合、確定判断も新 identity へ移行する。書籍削除、サーバ削除、重複除去では対応する正規化判断・候補も削除する。

### 8. 一括推論は共通 AI 実行ゲートへ低優先度で参加する

書誌正規化 worker は WorkManager で永続実行し、ローカル推論は1冊ずつ行う。`LocalAiBackgroundTaskGate` に `LOW` priority で参加し、要約等の高優先度タスクと同時にモデル推論しない。

共通 AI タスクキューへ1冊単位で投影し、「AIタスクを一時停止」「充電時に自動再開」に従う。process death 等で残った `PROCESSING` は次回 worker 開始時に `QUEUED` へ戻す。

### 9. 公開 repository へ実ユーザーデータを持ち込まない

本機能は端末内モデルだけを使用し、表紙画像、ファイル名、書誌候補を外部 AI service へ送信しない。

source、test fixture、ADR、PR説明、log には実在するユーザー蔵書名、実 SMB host / share / path、username、password、credential、実表紙画像、実 AI 候補を含めない。テストには架空の書名・著者・パスだけを使用する。

## Consequences

### Positive

- ファイル名だけでなく表紙の視覚情報を使って、乱れた SMB 蔵書の書誌候補を一括生成できる。
- AI誤認が即座にファイルサーバの rename へ波及せず、人が確認してから反映できる。
- 却下も確定判断として残るため、同じ本を一括実行のたびに再解析しない。
- SMB 同期後も確定済みのタイトル・著者等が保持される。
- 表紙取得の通信量・Wi-Fi・credential 規則を ADR-0133 と二重管理しない。
- 既存の process-wide AI runtime と共通 AI queue の直列化・一時停止を再利用できる。

### Negative

- 書誌候補、確定判断、レビュー状態の永続 table が増える。
- 表紙未取得本は表紙先読み完了まで推論を開始できない。
- 画像推論では GPU vision backend を使うため、text-only 推論と異なる engine configuration の初期化が必要になる。
- AIによる誤認、OCR失敗、表紙だけでは判別不能な書籍は人による編集・却下が必要になる。
- 外部でファイルが rename された場合は source identity が変わるため、アプリ外変更を確定判断へ自動追跡しない。

## Alternatives considered

### AI出力のファイル名をそのまま使用する

path separator、拡張子変更、命名規則の揺れをモデル出力へ委ねることになるため採用しない。書誌推定とファイル名 policy を分離する。

### 候補を自動反映する

ADR-0111 のタグ・コレクション整理では大量承認を避けるため自動反映しているが、SMB rename は外部ストレージへの破壊的副作用を伴う。影響範囲が異なるため採用しない。

### 書誌確定値を `library_items` だけへ保存する

SMB 同期で再構築されるため確定判断が失われる。ユーザー管理状態を同期キャッシュから分離する ADR-0108 の方針にも反するため採用しない。

### 正規化 worker が SMB 本体から直接表紙を読む

ADR-0133 の転送量制限、Wi-Fi制約、credential管理、失敗状態を重複実装し、通常の表紙先読みと競合するため採用しない。

## Sources

- [ADR-0056](0056-feature-owned-local-ai-policies.md)
- [ADR-0065](0065-smb-library-and-built-in-book-reader.md)
- [ADR-0066](0066-background-library-ai-organization-review-queue.md)
- [ADR-0071](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0079](0079-process-wide-local-ai-inference-sessions.md)
- [ADR-0104](0104-ai-task-queue-feature-ownership.md)
- [ADR-0108](0108-library-organization-and-ai-suggestions.md)
- [ADR-0111](0111-auto-apply-validated-series-aware-library-organization.md)
- [ADR-0133](0133-smb-cover-prefetch-queue.md)
