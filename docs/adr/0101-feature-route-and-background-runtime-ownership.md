# ADR-0101: feature route と background runtime の所有境界を明確化する

- Status: Accepted
- Date: 2026-08-19
- Amended by: ADR-0139, [ADR-0192](0192-settings-feature-owns-cross-feature-presentation.md)
- Clarified: 2026-08-27 Settings overlay ownership

## Context

ADR-0001 と ADR-0063 では、feature 固有の UI state と presentation は各 feature が所有し、`:app` は navigation、composition、platform wiring に限定する方針を定めている。

しかし実装の拡張に伴い、次の責務が再び `:app` に集まっていた。

- `YomitoriApp` が RSS、Reddit、Bookmark、Mail、Chat など複数 feature の `UiState` を直接 collect し、初期化表示、確認 dialog、フィード追加 dialog、ブックマーク編集 dialog などを管理していた
- Settings の AI タスクキュー画面が `DatabaseConnection`、Library の concrete Repository、WorkManager scheduler を Compose 関数内で直接生成していた
- Knowledge の WorkManager controller / Worker / queue state interpretation が `:app` にあり、Knowledge 固有の background runtime が composition root に漏れていた

この状態は、画面追加時に `YomitoriApp` の変更理由を増やし、UI layer から Data / WorkManager implementation を直接構築する経路を再導入する。また Knowledge の background 実装を変更する際に app module まで変更する必要があった。

## Decision

### 1. YomitoriApp を app shell に限定する

`YomitoriApp` は次の責務に限定する。

- app-level navigation state の購読
- drawer / scaffold / snackbar host の構成
- feature route への依存配線
- cross-feature overlay と platform integration の接続

RSS、Reddit、Bookmark、Mail、Chat の画面 state は各 `:feature:*:ui` の route が collect する。

上部バーから feature 固有 UI を起動する必要がある場合は、feature UI が所有する小さな controller を composition root が保持する。controller は dialog state の実装を feature 内に残し、app は `request...()` のような操作だけを呼ぶ。

ブックマークのタグ編集・フォルダ移動も `:feature:bookmark:ui` の `BookmarkEditHost` が state と dialog を所有する。RSS など他 feature からは controller callback を通して要求する。

Settings は Backup、Bookmark、AI Settings と Android Activity Result を束ねる必要があるため、`:app` に薄い `SettingsRoute` composition adapter を置く。ここには business rule や concrete data source を置かない。

Settings 内でも ownership を同じ基準で分ける。Models、ChatGPT Debug、AI Execution Settings に加え、Summary Prompt、AI Task Queue、Drive Backup を Settings から開くための overlay selection と presentation policy は `:feature:settings:ui` が所有する。各 overlay の再利用可能 UI と task semantics はそれぞれ Summary、AI Task Queue、Backup feature が所有し続ける。`:app` の `SettingsRoute` は Android Activity Result、backup restore 後の app-level navigation、feature ViewModel / Repository の依存配線に限定する。

全 feature からの Snackbar と Summary overlay は app-level cross-feature presentation であるため、薄い host として `:app` に残す。

### 2. AI task queue の concrete dependency は AppContainer で生成する

ADR-0069 の方針に従い、`TaskQueueScreen` は `AiTaskQueueRepository` のみを利用する。

`DatabaseConnection`、Library Repository implementation、WorkManager scheduler、Knowledge task controller の生成は `AppContainer` に移す。Compose 関数から Data / WorkManager implementation の生成を除去する。

### 3. Knowledge background runtime を feature data が所有する

ADR-0075 の Knowledge 固有 background queue 方針を module ownership に反映し、次を `:feature:knowledge:data` へ移す。

- `WorkManagerKnowledgeBuildTaskController`
- Knowledge build / charging resume Worker
- WorkInfo から task state への変換
- Knowledge queue state store と background runtime の接続

Worker が app container の concrete typeを参照しないよう、`:feature:knowledge:domain` に `KnowledgeRepositoryProvider` contract を置き、`YomitoriApplication` が composition root として実装する。

WorkManager は enqueue 済み work に Worker の完全修飾クラス名を永続化するため、アップデート前に作成された work との互換性を保つ必要がある。このため旧 app package の Worker 名には処理を持たない compatibility shim を残し、実処理は feature data の bridge に委譲する。新規 work は feature data の Worker を使用する。

## Consequences

### Positive

- `YomitoriApp` の変更理由が navigation と app shell に限定される
- feature 固有の state / dialog / loading presentation が所有 feature に戻る
- Settings の app adapter が platform wiring と dependency wiring に限定される
- Compose から concrete Repository / WorkManager dependency を生成しなくなる
- Knowledge background 実装を feature 内で変更できる
- Worker の module 移動でも既存の enqueue 済み work を壊さない

### Negative

- app-level navigation と feature-owned dialog を接続するため、小さな controller が増える場合がある
- cross-feature Snackbar / Summary / Settings platform wiring は app adapter として残る
- `:feature:settings:ui` から Summary / AI Task Queue / Backup UI への sibling feature dependency が増える
- 旧 Knowledge Worker FQCN の compatibility shim は、既存インストールとの互換性のため当面維持する必要がある

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data 責務と state ownership を具体化する
- ADR-0063 の feature UI ownership cleanup を継続し、root composable まで適用する
- ADR-0069 の AI task queue composition 方針を concrete dependency の生成場所まで明確化する
- ADR-0075 `background-knowledge-wiki-build-queue` の runtime ownership を `:feature:knowledge:data` に確定する
- ADR-0046 の自動アーキテクチャ検証では意味上の UI ownership を完全には検出できないため、route ownership はレビューでも確認する
- ADR-0192 により Settings の cross-feature overlay selection / presentation ownership を `:feature:settings:ui` に確定する
