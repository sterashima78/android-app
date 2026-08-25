# ADR-0164: owner boundary と main quality gate の残存 P1 を収束する

- Status: Accepted
- Date: 2026-08-24
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0162](0162-current-architecture-cleanup-guardrails.md)
- Related: [ADR-0136](0136-public-repository-content-verification.md)

## Context

ADR-0162 で current architecture cleanup の主要な guardrail を追加した後も、次の P1 残差が確認された。

- Chat skill と LAN Web Server が Reddit owner の低レベル `isRedditArticle` / `isRedditFeedUrl` を直接 import し、consumer 側で source classification を行っていた。
- ADR-0069 は要約プロンプトのような feature 固有設定を共通化しないと決めていたが、`feature:settings:data` が `feature:summary:data` に依存し、`DefaultAiModelRepository` が `SummaryPromptStore` を直接構築していた。さらに `AiModelRepository` が user-editable summary prompt の read/write API まで所有していた。
- pull request では Architecture / Test / Lint を実行する一方、`main` push の signed APK build は unit test / lint を再実行しなかった。`main` branch protection だけを前提にすると、repository setting の変更や直接 push により未検証 commit から signed APK を生成できる余地がある。

いずれも既存の ownership / quality 方針を変更するものではなく、既存判断を repository 全体へ適用して抜け道を閉じる cleanup である。

## Decision

### 1. Reddit source classification は全 consumer で owner boundary を利用する

`:feature:reddit:domain` の `RedditSourceBoundary` を Reddit / non-Reddit の named classification capability とする。

Chat、Web、app composition 等の consumer は `isRedditArticle`、`isRedditFeedUrl`、`redditCommunityFeedUrl`、`redditThreadId` の低レベル関数を直接 import しない。これらの実装詳細は Reddit feature 内でのみ利用できるものとして扱う。

source regression test は特定の app Route だけではなく、Reddit feature 外の production Kotlin source を走査して低レベル classification import の再導入を拒否する。

### 2. user-editable Summary prompt は Summary が所有する

要約 prompt の default、normalization、永続化、mutable state は Summary Context の責務とする。

`:feature:summary:domain` は `SummaryPromptSettings` という narrow contract を公開し、`:feature:summary:data` の `SummaryPromptStore` が実装する。

Settings は prompt の編集 UI を提供してよいが、prompt persistence の owner にはならない。これは ADR-0069 の「AIセクションに表示しても feature 固有設定は共通化しない」という判断を実装境界にも反映するものである。

- `feature:settings:data` から `feature:summary:data` dependency を削除する。
- `AiModelRepository` から summary prompt の read/update/reset API を削除する。
- `AiSettingsViewModel` は model/inference 用 `AiModelRepository` と Summary-owned `SummaryPromptSettings` を別 dependency として受け取る。
- application composition は既存 application scope graph から両 capability を明示的に接続する。

新しい Gradle module や generic settings service は追加しない。

### 3. signed APK build は repository 内の main quality gate に依存する

`.github/workflows/build-apk.yml` の Android quality matrix を pull request だけでなく `main` push / main 上の workflow dispatch でも実行する。

signed release APK の `build` job は `quality_checks` を `needs` とし、Architecture / Test / Lint のいずれかが失敗した commit では実行しない。

branch protection は追加防御として利用できるが、signed artifact の品質保証を repository setting だけには依存させない。

public repository verification は従来どおり release keystore を復元する前に build job 内でも実行する。

### 4. cleanup 境界を source regression test で固定する

`ArchitectureCleanupSourceTest` は少なくとも次を検証する。

- Reddit feature 外の production source が低レベル Reddit classification API を import しないこと。
- Settings data が Summary data に依存せず、`DefaultAiModelRepository` が `SummaryPromptStore` を構築しないこと。
- `AiModelRepository` が Summary prompt state を所有しないこと。
- Settings route composition が Summary-owned prompt capability を明示注入すること。
- main の signed APK build が Android quality matrix に依存すること。

## Consequences

### Positive

- Reddit URL semantics の変更が Chat / Web / app shell へ複製されない。
- Summary prompt persistence の ownership と Settings の presentation responsibility が一致する。
- Settings Data から sibling feature Data への不要な dependency を除去できる。
- branch protection の有無にかかわらず、未テスト commit から signed APK を生成する CI 経路を閉じられる。
- 今回見つかった残差を repository-wide regression test で防止できる。

### Negative

- Settings UI は Summary Domain の narrow contract に依存する。
- main push でも Test / Lint / Architecture を実行するため、CI 使用量は増える。
- application composition に `summaryPromptSettings` capability が1つ増える。

## Verification

- `RedditSourceBoundaryTest` と `ArchitectureCleanupSourceTest` を実行する。
- `./gradlew --no-daemon test` で Settings / Summary を含む JVM unit test を実行する。
- `./gradlew --no-daemon :app:lintRelease` を実行する。
- `./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture` を実行する。
- `python3 scripts/verify_adr_integrity.py` と module map verifier を実行する。
- `python3 scripts/verify_public_repository.py` を実行し、PR 作成前に意味的な公開情報レビューも行う。

## Public repository review

この変更は architecture contract、synthetic source regression rule、CI 設定のみを追加・変更する。実ユーザー URL、メールアドレス、書籍情報、健康情報、credential、token、keystore、profiling / heap artifact を追加しない。
