# System Overview

この文書は、Mosaic の全体像を短時間で把握し、変更時にどこを調べるべきか判断するための入口である。

詳細仕様の正本ではない。各節から current architecture document、machine-readable manifest、production code へ降りるための index / projection として使う。

## 1. Product map

Mosaic は、端末内に個人情報を集約し、source 固有の意味を保ちながら閲覧・整理・AI処理へ接続する Android アプリである。

主要 capability は次のように分類できる。

| Area | Capability | 主な owner / context |
| --- | --- | --- |
| Content ingestion | RSS / Atom、Reddit、YouTube、Web由来コンテンツ | RSS / Reddit / YouTube / Content |
| Curation | Bookmark、あとで読む、Tag、Folder | Curation (`feature:bookmark`) |
| Generated content | Summary、metadata補完、Knowledge | Summary / Knowledge |
| Personal communication | Gmail閲覧・整理 | Mail |
| Library | Kindle / Audible / Google Books / SMB / Web、Book Reader | Library |
| Planning | Task、Calendar | Task / Calendar |
| Activity | Workout、Health Connect read model | Workout / Health |
| Personal finance | Asset snapshot / import / Web collection | Asset |
| Presentation / access | Integrated UI、Widget、LAN Web、X WebView | app presentation / owning feature |
| AI runtime | Local inference、ChatGPT明示選択、model管理 | core AI runtime + feature-owned policy |
| Operations | Backup、background refresh、AI task queue、diagnostics | owning feature + app composition |

ユーザーから見た現行仕様は [`../spec.md`](../spec.md)、Domain の意味と関係は [`context-map.md`](context-map.md) を参照する。

## 2. Runtime topology

アプリ全体は概ね次の依存方向で組み立てる。

```text
Android executable / framework entry points
              :app
                |
                +----------------------+
                |                      |
                v                      v
       :app:presentation        :app:composition
        navigation / UI        concrete runtime graph
                |                      |
                v                      v
      feature UI / Domain      feature Data / Domain
                \                      /
                 \                    /
                  +------ core -------+
                         technical capability
```

責務の基準:

- `:app`: Activity、Service、external Intent、app-only platform integration 等の executable shell
- `:app:presentation`: app-shell navigation、app-wide chrome、feature UI composition
- `:app:composition`: application-scope の high fan-in concrete dependency graph
- `:feature:<name>:ui`: feature presentation
- `:feature:<name>:domain`: business contract / model / capability
- `:feature:<name>:data`: persistence、network、Android adapter、feature-owned background runtime
- `:core:*`: database、network、design system、AI runtime 等の横断的技術 capability

Evidence:

- [`settings.gradle.kts`](../../settings.gradle.kts)
- [`principles.md`](principles.md)
- [`module-map.md`](module-map.md)
- [`code-organization.md`](code-organization.md)
- `app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt`
- `app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt`
- `app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt`

## 3. Core data flows

### 3.1 Content -> Curation -> Generated content

```text
RSS / Reddit / YouTube / shared Web content
                 |
                 v
             Content
        identity / metadata
                 |
       +---------+----------+
       |                    |
       v                    v
   Curation              Summary
Bookmark / ReadLater   summary / task
       |                    |
       +---------+----------+
                 |
                 v
              Knowledge
          generated wiki/source
```

重要な境界:

- source Context は source 固有 subscription / fetch state を所有する。
- Content が content identity と reading state を所有する。
- Curation が Bookmark / Tag / Folder / Read Later を所有する。
- Summary / Knowledge は owner API を通して Content / Curation を参照し、foreign table を直接操作しない。

Evidence:

- [`context-map.md`](context-map.md)
- [`persistence.md`](persistence.md)
- `config/architecture/table-ownership.tsv`

### 3.2 Local / Cloud AI

```text
Feature policy
 Summary / Chat / Knowledge / Library / Workout ...
                    |
                    v
        provider-neutral capability
                    |
          +---------+---------+
          |                   |
          v                   v
     Local runtime        Cloud adapter
      LiteRT-LM           explicit routing
```

重要な境界:

- technical inference runtime と feature 固有 prompt / policy を分離する。
- cloud 実行を選ぶ機能は送信データと実行先を明示し、暗黙 fallback を行わない方針を基本とする。
- 長時間処理は画面 lifecycle へ閉じず、feature-owned background runtime / durable queue へ移す。
- AI がアプリ内データを読む場合、定義済み narrow tool / Domain contract を利用し、任意 SQL や任意コード実行を公開しない。

Evidence:

- [`ai-runtime.md`](ai-runtime.md)
- [`chat-retrieval.md`](chat-retrieval.md)
- [`knowledge.md`](knowledge.md)
- `core/ai-runtime/`
- `feature/chat/domain/`
- `feature/chat/data/`

### 3.3 Library

```text
Kindle / Audible / Google Books / SMB / Web URL
                       |
                       v
                    Library
                catalog / metadata
                       |
             +---------+---------+
             |                   |
             v                   v
         Book Reader        AI normalization
```

SMB credential、WebView metadata extraction、cover cache、AI normalization は同じ Library capability の周辺にあるが、credential、cache、durable user data、AI input を同一種類のデータとして扱わない。

Evidence:

- [`context-map.md`](context-map.md)
- [`web-content.md`](web-content.md)
- [`persistence.md`](persistence.md)
- `feature/library/`

### 3.4 Task / Calendar / Workout / Health

```text
Task --------------------+
                         |
                         v
                    Calendar read model
                         ^
                         |
Workout -----------------+
   |
   +---- one-way export ----> Health Connect

Health <---- read-only ------- Health Connect
```

重要な境界:

- Calendar は Task / Workout の durable state owner ではない。
- Workout はアプリ内運動記録の source of truth。
- Health は Health Connect の read model を扱い、read data をアプリ DB へ複製しない。
- Workout -> Health Connect は一方向 export とし、Health Connect -> Workout 同期へ拡張しない。

Evidence:

- [`context-map.md`](context-map.md)
- `feature/task/`
- `feature/calendar/`
- `feature/workout/`
- `feature/health/`

### 3.5 Background execution

```text
UI / app startup / periodic trigger
              |
              v
 owning feature scheduler / controller
              |
              v
       WorkManager / durable queue
              |
              v
          WorkerFactory
              |
              v
  AppContainer existing runtime graph
```

feature 固有 Worker は owning feature が所有し、application-scope dependency は `:app:composition` が既存 graph へ接続する。Worker ごとに並行した Repository graph を再構築しない。

Evidence:

- [`background-refresh.md`](background-refresh.md)
- [`principles.md`](principles.md)
- `app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt`
- `app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/`

## 4. Persistence and trust boundaries

### Durable relational data

- 原則として単一 SQLite database を共有する。
- database file の共有は table ownership の共有を意味しない。
- durable table の owner は `config/architecture/table-ownership.tsv` が機械可読な正本。
- foreign table write は禁止する。
- cross-context read は owner API / named query / explicit read-only projection を利用する。

### Credentials and sensitive data

credential、OAuth token、SMB credential 等は通常 user data と同じ backup / export boundary に載せない。公開リポジトリにも実 credential、token、実ユーザーの URL・メールアドレス・個人データを置かない。

### External boundaries

変更時に特に確認する外部境界:

- HTTP / RSS / external API
- Gmail OAuth / API
- Health Connect
- Android Calendar Provider
- SMB
- WebView / JavaScript execution
- ChatGPT cloud execution
- Android Backup / document export
- LAN Web Server

Evidence:

- [`persistence.md`](persistence.md)
- [`web-content.md`](web-content.md)
- [`platform.md`](platform.md)
- `config/architecture/table-ownership.tsv`
- `scripts/verify_public_repository.py`

## 5. System invariants

変更時にまず確認する invariant を以下に集約する。詳細・例外・根拠は [`principles.md`](principles.md) を正本とする。

1. Domain は Android / DB / HTTP concrete implementation に依存しない。
2. `core -> feature`、`domain -> ui/data`、`ui -> concrete data` の逆向き依存を作らない。
3. app shell は feature data implementation を直接所有しない。
4. cross-context operation のために foreign table CRUD を公開しない。
5. durable table write は owner data module が行う。
6. application-scope runtime を Route / Worker ごとに重複構築しない。
7. Android 直生成 entry point の service locator exception は narrow provider contract に限定する。
8. long-running work は foreground Composable の lifetime に閉じない。
9. credential / token を通常 backup、fixture、architecture document、ADR に混入させない。
10. cloud data egress を暗黙に追加しない。
11. Health Connect read data を通常 durable user database へ複製しない。
12. compatibility のために維持すべき identity と、一時 migration を区別する。
13. architecture 上の意思決定は ADR に記録し、current architecture document を同時に更新する。
14. 機械検査可能な architecture rule は可能な限り CI / manifest で強制する。

## 6. Decision-sensitive surfaces

以下は「問題がある」という意味ではなく、変更が局所に見えても system-wide effect を持ちやすいため、Impact Brief で必ず確認したい領域である。

| Surface | 確認する理由 |
| --- | --- |
| `:app:composition` / `AppContainer` | high fan-in graph。新しい global dependency や重複 runtime を作りやすい |
| Content / Curation / Summary / Knowledge | cross-context read/write と task lifecycle が連鎖しやすい |
| SQLite schema / backup | migration、ownership、復元互換性へ影響する |
| WorkManager Worker identity | enqueue 済み request と class identity の互換性へ影響しうる |
| Local AI runtime | memory、process lifetime、model artifact、long-running task に影響する |
| Cloud AI routing | private data の external egress が変わる |
| WebView / JavaScript | untrusted Web content、process failure、bridge security を扱う |
| Android component / permission | OS version、entry point、navigation、background restriction に波及する |
| SMB / Gmail credentials | secret storage と backup boundary を誤ると情報漏えいにつながる |
| Shared concept / new module | ownership を誤ると feature 間依存と abstraction が長期固定される |

## 7. How to investigate a change

新しい要求を受けたら、コードを書く前に次の順で調べる。

```text
spec.md
  ユーザーから見た現在の振る舞い
      |
      v
system-overview.md
  capability / data flow / sensitive boundary
      |
      v
context-map.md + principles.md
  ownership / invariant
      |
      v
module-map.md + persistence.md + feature-specific architecture docs
  physical boundary / storage / runtime
      |
      v
ADR
  なぜそうなったか
      |
      v
production code + tests + machine-readable manifests
  実際の evidence
```

実装前レビューの具体的な出力形式は [`change-impact-review.md`](change-impact-review.md) を使う。

## 8. Questions humans should be able to answer

Architecture Control Plane が機能しているかは、次の質問にコード全文を読まず答えられるかで確認する。

- この要求は既存のどの capability / Context に属するか。
- 新しい concept を作らず既存 capability を拡張できないか。
- durable state は増えるか。増えるなら owner と migration は何か。
- 別 Context の table や concrete implementation に触れようとしていないか。
- background runtime や application-scope graph を新しく増やす必要が本当にあるか。
- 端末外へ新しいデータを送信するか。
- permission / credential / WebView / external Intent の trust boundary が変わるか。
- 既存の compatibility identity を壊すか。
- 変更を取り消すとき、何を削除・移行する必要があるか。
- この変更後、system map のどの矢印が変わるか。

## Sources

- [`../spec.md`](../spec.md)
- [`principles.md`](principles.md)
- [`context-map.md`](context-map.md)
- [`module-map.md`](module-map.md)
- [`code-organization.md`](code-organization.md)
- [`persistence.md`](persistence.md)
- [`testing.md`](testing.md)
- [`platform.md`](platform.md)
- [`background-refresh.md`](background-refresh.md)
- [`ai-runtime.md`](ai-runtime.md)
- [`web-content.md`](web-content.md)
- [`../adr/0228-human-architecture-control-plane.md`](../adr/0228-human-architecture-control-plane.md)
