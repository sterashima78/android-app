# ADR-0134: SMB 書籍の書誌・ファイル名正規化をマルチモーダル候補レビューとして扱う

- Status: Accepted
- Date: 2026-08-21
- Amended: 2026-08-23
- Refines: ADR-0056, ADR-0065, ADR-0066, ADR-0071, ADR-0079, ADR-0104, ADR-0108, ADR-0111, ADR-0133

## Context

ファイルサーバ由来の書籍は、既存ファイル名にスキャン時の仮名、表記揺れ、巻数表現の不統一などが含まれることがある。ファイル名だけでは正しい書誌を判別しにくい一方、ADR-0133 により一覧用の表紙画像は SMB 本体の同期と分離した永続キューで取得できる。

既存ファイル名はノイズだけではなく、ローマ字・英字のタイトル、著者名、シリーズ名、巻数など、表紙の日本語表記を同定するための有力な手掛かりを含む場合がある。この情報を「誤っている可能性がある入力」として弱く扱いすぎると、表紙だけでは判別しにくい書籍の精度を落とす。

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

表紙が未取得の対象は `WAITING_FOR_COVER` とし、ADR-0133 の `smb_cover_prefetch_queue` に取得を委ねる。表紙キャッシュが利用可能になった後だけ AI 推論へ進む。これにより ZIP / CBZ の 128 MiB streaming 上限、PDF の 512 MiB 一時取得上限、Wi-Fi 条件、credential 境界を重複実装しない。

AI入力は次の2点だけとする。

- 現在のファイル名
- アプリ private cache に保存された一覧用表紙画像

現在のファイル名と表紙画像は相互に照合する根拠として扱う。ファイル名にローマ字・英字でタイトル、著者、シリーズ、巻数が含まれる場合も捨てず、日本語書誌を同定する材料として積極的に利用する。両者が矛盾する場合は片方を機械的に優先しない。

### 3. LiteRT-LM の同一 process-wide runtime で画像入力する

`core:ai-runtime` の `LocalModelManager` に汎用的な画像入力 API を追加し、Gemma 4 への画像＋テキスト推論も既存の process-wide inference lock と retained engine を使用する。

画像推論では `EngineConfig.visionBackend` に GPU backend を指定する。画像推論中は speculative decoding を無効化し、画像入力用 engine configuration を通常の text-only engine cache key と分離する。

Library 側は LiteRT-LM の `Engine` を直接所有せず、書誌推定の prompt、schema、validation policy だけを所有する。これは ADR-0056 と ADR-0079 の責務境界を維持する。

### 4. AI は出力 Tool で構造化書誌情報だけを提出し、ファイル名は決定的に生成する

自由テキストとして JSON object を生成させない。`feature:library:data` は `submit_book_metadata` という出力専用 Tool を定義し、Gemma 4 には画像とファイル名を入力した同一 Conversation でこの Tool を1回だけ呼び出させる。

`core:ai-runtime` は feature 非依存の transport capability として、OpenAPI Tool 引数の `string` / `integer` / `number` / `boolean` / `string array` schema と、画像入力を伴う manual Tool Calling を提供する。`ConversationConfig.automaticToolCalling = false` とし、Tool の副作用を実行せず `Message.toolCalls` の引数を結果として受け取る。Tool 名、各 field の意味、必須性、validation policy は Library が所有する。

出力 Tool は次を扱う。

- `title`: 必須。シリーズ物でも巻数表現は含めない
- `authors`: 必須。判別不能なら空配列
- `publisher`: 任意
- `publishedDate`: 任意
- `isbn10` / `isbn13`: 任意
- `seriesName`: 任意。ただし巻数が判別できた場合は必須
- `seriesPosition`: 任意の整数。判別できた場合は `seriesName` とセットで指定する
- `confidence`: 任意の数値
- `reason`: 任意

判別不能な任意 field は null を強制せず Tool 引数自体を省略できる。これにより小型ローカルモデルへ不要な field の生成を強制しない。一方、巻数を判別できた候補は `seriesName` と数値の `seriesPosition` を同時に保持し、表示上の巻数表記と書誌メタデータを分離する。

シリーズ作品では巻数を `title` から除くだけで失わないことを固定 instruction と Tool field description の双方で要求する。独立したシリーズ名表記がなくても巻数が明確なら、巻数を除いた作品タイトルを `seriesName` としてよい。既定 prompt でも漫画・ライトノベル・小説のシリーズ名と巻数を優先的に確認し、`第N巻`、`Vol.N`、`NKan` と末尾の `08` のような数字を表紙と照合すべき巻数候補として扱う。ファイル名から巻数候補を決定的に抽出できる場合は、その候補値も固定 prompt とともにモデルへ提示する。

小型モデルがそれでも `seriesPosition` を省略した場合に備え、Library 側で保守的な補完を行う。`第N巻`、`N巻`、`Vol.N`、`NKan` のような明示的な巻数表現がファイル名にあり、相反する複数の巻数が存在しない場合は、その数値を `seriesPosition` として補完する。明示表現がない「作品名 + 末尾1〜3桁」は、数字を除いた文字列と AI が返した `title` が空白・記号を除いて一致し、かつ1〜300の場合だけ補完する。いずれも既に AI が返した `seriesPosition` は上書きせず、`seriesName` が未指定なら `title` を使用する。これによりローマ字ファイル名の `1Kan` のような強い情報も保持しつつ、数字自体がタイトルの一部であるケースや管理番号を巻数へ寄せすぎない。

追加 field、型不一致、件数・文字数制約違反、Tool 未呼び出し、複数 Tool Call は失敗として扱う。不正出力時は validation error だけを返して1回だけ再生成し、不正出力本文そのものは再入力しない。2回目も構造化できない場合は内部 schema 文言をそのまま UI へ露出せず、再解析可能な失敗として扱う。

LiteRT-LM の安定版で JSON Schema による constrained decoding の response format が利用可能になった場合は、同じ Library-owned schema / validation policy を維持したまま transport を置き換えられる。未リリース API へこの機能だけのために追従しない。

AI に SMB path や変更後ファイル名を自由生成させない。変更後ファイル名はアプリ側で `title` と `seriesPosition` から決定的に生成し、元拡張子を維持する。巻数がある場合は元ファイル名の表記に依存せず `title 第N巻.ext` を標準形とする。例えば `seriesPosition = 12` なら `架空冒険譚 第12巻.cbz` のように生成する。長いタイトルをファイル名上限へ切り詰める場合も ` 第N巻` と拡張子の領域を先に予約し、巻数ラベルを切り捨てない。これにより `title` は巻数を含まない正規化書誌のまま保持しつつ、SMB 上の各巻のファイル名は衝突せず、巻数表記も統一できる。

`/`、`\`、制御文字等の path / filename 危険文字を拒否・正規化し、レビュー時にユーザーが編集した名前にも同じ検証を適用する。

### 4.1. 書誌推定プロンプトは Library-owned 設定として調整可能にする

書誌推定の精度調整をアプリ更新から切り離すため、既定の user prompt は `feature:library:domain` が所有し、設定画面から編集・既定値へのリセットを可能にする。`feature:library:data` は SharedPreferences に保存し、UI は domain の `SmbMetadataNormalizationPromptRepository` を介して扱う。これは要約プロンプトと同様に feature 固有の AI policy を feature 内へ閉じる ADR-0056 の境界に従う。

テンプレートでは `{{fileName}}` を現在のファイル名の差し込み位置として使用できる。placeholder を省略した場合も現在のファイル名を入力から失わないよう、renderer が末尾へ自動追加する。プロンプトは空文字を拒否し、最大 4,000 文字とする。

ユーザーが変更できるのは user prompt のみとする。`submit_book_metadata` の Tool 名、schema、必須 field、validation、system instruction、修復再生成、巻数保持の固定 instruction、ファイル名安全化は固定し、カスタムプロンプトから構造化出力や rename の安全境界を変更できないようにする。

一括解析の待機中・実行中でもプロンプト設定は編集できる。worker は開始時に保存済みプロンプトを1回読み取り、同一 worker 実行中の全候補ではその snapshot を固定する。したがって保存済み設定の変更は保存後に開始する worker から反映され、すでに開始済みの worker の推論方針は途中で切り替わらない。保存するカスタムプロンプトは端末内設定であり、実ユーザーの設定値を source、test fixture、ADR、PR説明へコミットしない。

### 5. SMB rename は必ずユーザー確認後に行う

一括解析の正常経路は自動 rename しない。

候補状態は次を持つ。

- `WAITING_FOR_COVER`: 表紙先読み待ち
- `QUEUED`: AI解析待ち
- `PROCESSING`: AI解析中
- `PENDING_REVIEW`: 候補生成済み・未確認
- `DEFERRED`: 保留
- `APPLIED`: ユーザーが反映済み
- `REJECTED`: 却下
- `FAILED`: AI解析等の失敗
- `SKIPPED`: 入力変更や対象消失等で対象外

レビュー画面では、現在の表紙、元ファイル名、提案ファイル名、書誌候補、確信度、理由を確認し、「反映」「編集して反映」「保留」「却下」を行える。`PENDING_REVIEW`、`DEFERRED`、`REJECTED`、`FAILED`、`SKIPPED` は明示的な「再解析」操作を提供し、現在の入力 revision から候補を作り直せる。`APPLIED` は AI 再解析の対象外だが、「反映済み」一覧から確定済みの書誌情報を編集して再反映できる。

`APPLIED` の再編集は SMB rename を伴わない。反映済みファイル名は読み取り専用とし、ファイル名をさらに変更する場合は通常の SMB rename 操作へ分離する。これにより確定書誌の誤り修正で外部ストレージへ不要な破壊的変更を発生させない。

`APPLIED` と `REJECTED` は通常は確定状態であり、後続の一括解析対象から除外する。ただし `REJECTED` で「再解析」を選んだ場合は、対応する却下 decision を同一 transaction で削除し、古い候補 payload を破棄して最新バッチへ再投入する。

却下後に別の一括解析バッチが作られても再解析経路を失わないよう、レビュー用 snapshot は最新バッチの項目に加え、現在も `REJECTED` decision が存在する過去バッチの却下候補を source ID ごとに最新1件だけ投影する。過去の却下候補を再解析する場合は、古いバッチ自体を再開せず、現在の入力 revision を使った新しい item を最新バッチへ作成する。これにより worker とレビュー画面が常に同じ最新バッチを処理しつつ、却下済み書籍の回復可能性を維持する。

### 6. 反映直前に入力 revision を検証する

候補生成時には source ID に加え、元ファイル名、remote file size、modified time を保持する。

反映時に現在の SMB 蔵書情報と一致しなければ rename を行わず、古い候補を `SKIPPED` に移して再解析可能にする。再解析時は状態に関わらず現在のファイル名・size・modified time を再取得して保存し、古い候補 payload は破棄する。レビュー待ちの間にユーザーや別プロセスがファイルを変更した場合、古い AI 結果で上書きしない。

表紙キャッシュの `file:` URL が残っていても実体ファイルが失われている場合は、その stale `thumbnail_url` を解除して ADR-0133 の表紙先読みキューへ戻す。書誌候補の再解析時に表紙先読みが `FAILED` なら、表紙側の失敗キューも再試行してから正規化 worker を再開する。

rename 自体は既存の `SmbLibraryRepository.renameBook` を再利用し、SMB rename、`sourceId`、Library identity、reader cache、cover cache の既存移行規則を維持する。

### 7. 確定書誌は同期キャッシュへ投影する read model とする

`APPLIED` の書誌情報は `smb_metadata_normalization_decisions` に保持する。Library snapshot 読み込み時に SMB 書籍へ overlay するため、次回 SMB 同期が `library_items` の title / authors 等をファイル由来の値へ再構築しても、ユーザーが確定した書誌を表示上失わない。

`APPLIED` の再編集では同じ decision を更新し、AI 再解析や状態遷移を行わない。タイトル、著者、出版社、発売日、ISBN は確定書誌へ直接反映する。シリーズ名・巻数は編集前の正規化候補から変更された場合だけ既存の `library_item_series` projection を更新し、書誌反映後に別操作で行った手動シリーズ編集をタイトル等の再編集で巻き戻さない。シリーズを明示的に空へ変更した場合は、通常のシリーズ解除と同様に exclusion を記録する。

`REJECTED` も同じ decision table で確定判断を保持するが、ユーザーが明示的に再解析した場合はその decision を解除する。再解析失敗中や新しい候補のレビュー中は、再び却下するまで「確定済み」として扱わない。

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
- ローマ字・英字の元ファイル名を表紙と照合するため、表紙だけでは曖昧なタイトル・著者・シリーズを同定しやすくなる。
- 巻数は `seriesPosition` として正規化し、提案ファイル名では一律 `第n巻` 形式へ合成するため、巻ごとのファイル名衝突を避けつつ命名を統一できる。
- モデルが明示的な巻数表現や「作品名 + 末尾数字」から巻数を落とした場合も、入力形式に応じた保守的な補完で代表的な欠落を回復できる。
- JSON の Markdown fence、前置き文章、末尾説明など、自由テキスト出力由来の schema failure を避けられる。
- AI誤認が即座にファイルサーバの rename へ波及せず、人が確認してから反映できる。
- 未確認・保留・却下・失敗の候補を明示操作で再解析でき、prompt やモデル改善後に候補を作り直せる。
- 反映後に書誌の誤りを見つけても、AI 再解析や SMB rename を行わず確定値を修正できる。
- 却下後に新しいバッチが作られても、却下候補はレビューから失われない。
- 却下は通常の一括解析では確定判断として残るため、同じ本を意図せず再解析しない。
- SMB 同期後も確定済みのタイトル・著者等が保持される。
- 表紙取得の通信量・Wi-Fi・credential 規則を ADR-0133 と二重管理しない。
- 既存の process-wide AI runtime と共通 AI queue の直列化・一時停止を再利用できる。
- 書誌推定の指示をアプリ更新なしで調整しつつ、構造化 Tool と rename の安全規則は固定できる。

### Negative

- 書誌候補、確定判断、レビュー状態の永続 table が増える。
- レビュー snapshot は最新バッチだけでなく、確定中の過去の却下候補も投影する必要がある。
- 表紙未取得本は表紙先読み完了まで推論を開始できない。
- 画像推論では GPU vision backend を使うため、text-only 推論と異なる engine configuration の初期化が必要になる。
- Tool Calling 自体をモデルが遵守しない場合は、1回の修復再生成後に `FAILED` として人の再解析操作が必要になる。
- AIによる誤認、OCR失敗、表紙だけでは判別不能な書籍は人による編集・却下が必要になる。
- 巻数補完は明示表現またはタイトル一致した末尾数字へ限定するが、特殊な命名では誤補完の可能性が残るため、レビューで修正・却下できることを前提とする。
- 却下済み候補を再解析すると却下 decision は解除されるため、新しい解析が完了するまで一時的に未確定状態へ戻る。
- 外部でファイルが rename された場合は source identity が変わるため、アプリ外変更を確定判断へ自動追跡しない。
- カスタムプロンプトの内容によって推論精度が低下する可能性があるため、既定値へ戻す操作を提供する必要がある。

## Alternatives considered

### 自由テキストの JSON object を要求する

Markdown code fence、説明文、必須 null field の欠落など、モデルが内容を正しく推定していても serialization 形式だけで失敗しやすい。現行 LiteRT-LM が提供する Tool Calling を利用できるため採用しない。

### 未リリースの constrained decoding API へ追従する

将来的には JSON Schema constrained decoding がより直接的だが、現行の安定版依存から外れて runtime 全体の更新リスクを増やすため採用しない。安定版へ入った時点で再評価する。

### AI出力のファイル名をそのまま使用する

path separator、拡張子変更、命名規則の揺れをモデル出力へ委ねることになるため採用しない。書誌推定とファイル名 policy を分離する。

### 巻数表示形式を AI や元ファイル名へ委ねる

巻数の表示形式を Tool 出力や元ファイル名へ委ねると、同じ `seriesPosition` に対して `第3巻`、`Vol.3`、`3` などが混在する。書誌上は数値の `seriesPosition` を source of truth とし、ファイル名ではアプリ側が一律 `第N巻` へ決定的に整形する方が再現性と一覧性を保てるため採用しない。

### `REJECTED` を再解析不可の完全な終端状態にする

誤操作で却下した場合や、prompt・モデル改善後に同じ書籍を再評価したい場合に recovery path がなくなる。通常の一括解析からは除外しつつ、レビュー画面の明示操作に限って decision を解除して再解析できる方が、確定判断の意味と回復可能性を両立できるため採用しない。

### 過去バッチそのものを再開して却下候補を再解析する

最新バッチと過去バッチが同時に `RUNNING` になり、worker の対象順序やレビュー snapshot の意味が曖昧になる。過去の却下候補はレビュー用に投影するだけに留め、再解析時は最新バッチへ移す方が単一の実行対象を維持できるため採用しない。

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