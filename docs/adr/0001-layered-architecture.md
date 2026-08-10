# ADR-0001: UI・Domain・Data レイヤの責務を分離する

- Status: Accepted
- Date: 2026-08-07
- Updated: 2026-08-08
- Amended by: ADR-0003

## Context

現在のアプリは機能単位の `feature` を持つ一方、画面状態と操作の大部分が `MainViewModel` に集約されていた。

`MainViewModel` は UI state の保持だけでなく、SQLite への直接アクセス、RSS の HTTP 取得と並列制御、記事状態の更新、タグ・フォルダ操作、要約キュー、AI モデル管理、ファイル I/O、Google Drive バックアップ、Android `Intent` など複数の責務を持っていた。

この状態では以下の問題がある。

- 変更理由の異なる処理が同じ ViewModel に集中する
- 画面ごとの状態とライフサイクルが分離されない
- データ取得方式や外部サービスの詳細が UI layer に漏れる
- 単体テスト時に DB・ネットワーク・Android framework 依存を差し替えにくい
- 新機能追加に伴って共有 state が肥大化する

## Decision

アプリを UI、Domain、Data の3レイヤとして整理する。

この ADR が決定したレイヤ責務と依存方向は、ADR-0003 で採用した feature-first マルチモジュール構成でも維持する。Gradle module の配置・命名・feature 間依存の詳細は ADR-0003 を正とする。

依存方向は原則として次のとおりとする。

```text
Compose Screen
    ↓
Screen ViewModel
    ↓
UseCase（必要な場合のみ）
    ↓
Repository
    ↓
Local / Remote / Platform implementation
```

### UI layer

- ViewModel は Navigation destination / screen 単位を基本とする
- ViewModel はその画面の `UiState` とユーザー操作の受付を担当する
- ViewModel は Repository または UseCase を呼び出し、結果を UI state に変換する
- ViewModel から SQLite、HTTP client、`ContentResolver`、WorkManager 等を直接操作しない
- 新規 ViewModel は `Application` / `Context` を受け取らない
- UI 固有のナビゲーションや外部画面起動は可能な限り UI layer で扱う
- 状態公開は `StateFlow` を基本とし、UI は UDF（Unidirectional Data Flow）で更新する

### Domain layer

Domain layer は必須としない。

次のいずれかに該当する処理に UseCase を導入する。

- 複数 Repository をまたぐ
- 複数手順・並列実行・再試行などのオーケストレーションを持つ
- 複数 ViewModel から再利用する
- UI やデータ保存方式から独立してテストすべき業務ルールを持つ

Repository の1メソッドを呼ぶだけの UseCase は作らない。

### Data layer

- ViewModel からのデータアクセスは Repository を経由する
- Repository は扱うデータの責務ごとに分ける
- Repository implementation は必要に応じて local / remote / platform data source を利用する
- DB schema、HTTP conditional request、ファイル形式等の実装詳細は Repository より上位へ漏らさない

当面の Repository 境界は次を基準とする。

- `ArticleRepository`: 記事一覧、既読状態、記事本体
- `FeedRepository`: フィード一覧、検出、取得、追加、削除
- `BookmarkRepository`: 保存状態、タグ、フォルダ、ブックマーク整理
- `SummaryRepository`: 要約結果と要約キュー
- `BackupRepository`: バックアップ入出力と自動バックアップ
- AI モデル管理は settings / AI feature から利用する独立した abstraction とする

### State ownership

共有の巨大な `UiState` は段階的に廃止し、feature 固有 state へ移行する。

```text
RssViewModel       -> RssUiState
BookmarkViewModel  -> BookmarkUiState
SettingsViewModel  -> SettingsUiState
ChatViewModel      -> ChatUiState
TaskViewModel      -> TaskUiState
```

アプリ全体で共有すべき navigation state が必要な場合のみ app-level state holder を置く。

### Module policy

この ADR の初版では single Gradle module 内で package boundary を安定させる方針としていたが、その後 ADR-0003 により feature-first のマルチモジュール構成を採用した。

したがって module policy は ADR-0003 が上書きする。本 ADR は UI / Domain / Data の責務と依存方向のみを継続して規定する。

## Migration plan

既存機能を止めず、以下の順で段階的に移行する。

1. Repository interface と複雑な処理の UseCase を導入する
2. `MainViewModel` から DB / HTTP / platform の直接操作を順次除去する
3. screen-level ViewModel へ分離する
4. feature 固有 state を各 feature が所有する
5. app-level state holder を navigation と composition に縮小する
6. データ変更通知を Flow 化し、操作後の一括 reload を段階的に置き換える
7. ADR-0003 に従って feature-first module boundary を維持する

移行中は一時的に旧構造と新構造が共存することを許容する。ただし、新規の DB / HTTP 直接アクセスを app-level ViewModel に追加しない。

### Migration status

2026-08-08 時点で Repository / ViewModel 分離と feature-first マルチモジュール化を実施済みである。

残る移行では、古い package 名、app module に残った feature UI state、feature 固有ロジックが core capability に漏れている箇所を順次整理する。

## Consequences

### Positive

- ViewModel の責務と変更理由が小さくなる
- DB / HTTP / Android framework の詳細を UI から隔離できる
- feature 単位のテストが容易になる
- module 境界で依存方向を検証できる
- WorkManager、ウィジェット、Web サーバ等から同じ Repository / UseCase を再利用しやすくなる

### Negative

- 移行期間中は abstraction と既存実装が併存する
- 小規模な処理ではファイル数が増える
- Repository 境界が不適切な場合は再編が必要になる

これらは一括書き換えによる回帰リスクより小さいと判断する。
