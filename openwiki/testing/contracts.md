---
type: testing
title: 契約と検証の選び方
description: 変更責務に応じた focused test、architecture enforcement、形式仕様、PR CI を案内する。
tags: [testing, ci, formal-models]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T11:54:35.312Z
sources:
  - id: openwiki-source-e05e3253cc972e8bd548b5cf
    resource: repo://.github/workflows/check.yml
  - id: openwiki-source-a68706cb98e1377073ef3a10
    resource: repo://docs/architecture/testing.md
  - id: openwiki-source-1924de206fec6afaeeb7812a
    resource: repo://docs/formal-modeling.md
  - id: openwiki-source-70183107346e11616ced0705
    resource: repo://feature/bookmark/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/MoveBookmarkToLibraryUseCaseTest.kt
  - id: openwiki-source-fdb61892509fe2f9fb40069a
    resource: repo://scripts/verify_public_repository.py
  - id: openwiki-source-084cefca2b303e7be6d721a8
    resource: repo://spec-models/README.md
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# 契約と検証の選び方

テストは実装詳細の階層ではなく、守る contract の owner に合わせる。[Testing Strategy](../../docs/architecture/testing.md) が正本であり、実行する CI コマンドは [.github/workflows/check.yml](../../.github/workflows/check.yml) を優先する。

## 変更から検証を選ぶ

| 変更 | 確認する契約 | 代表例 |
| --- | --- | --- |
| Domain rule | framework 非依存の値・状態遷移 | Domain module の JVM unit test |
| 複数 port の orchestration | 呼び出し順と失敗時の state | [MoveBookmarkToLibraryUseCaseTest](../../feature/bookmark/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/MoveBookmarkToLibraryUseCaseTest.kt) |
| DB・migration | owner schema、phase・version 順序 | [DatabaseSchemaTest](../../core/database/src/test/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchemaTest.kt) |
| source adapter | source 固有の変換・重複・metadata | [RssPodcastFeedContentSourceTest](../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSourceTest.kt) |
| module / table ownership | dependency、source ownership、manifest | verifyArchitecture と app architecture tests |
| Android runtime | OS entry point、service、permission の挙動 | 必要な framework test / 実機確認 |
| architecture docs | 正本・source・リンクの整合 | 文書レビューと metadata 検証 |

たとえば蔵書追加が失敗したときに Bookmark を残す contract は、SQLite の全体テストより UseCase の fake port test で直接確認できる。source adapter の重複判定なら実際の変換と focused test を読む。[コンテンツフロー](../workflows/content.md) と [媒体連携](../integrations/media.md) に具体的な入口がある。

## Architecture enforcement

dependency・source ownership は verifyArchitecture、table ownership は [manifest](../../config/architecture/table-ownership.tsv) と [table-ownership init script](../../gradle/table-ownership.gradle.kts)、module map / ADR integrity は [architecture-metadata init script](../../gradle/architecture-metadata.gradle.kts) で検証する。

[FrameworkProviderBoundaryTest](../../app/src/test/java/dev/terashima/yomitorirss/FrameworkProviderBoundaryTest.kt)、[AppBoundaryOwnershipArchitectureTest](../../app/src/test/java/dev/terashima/yomitorirss/AppBoundaryOwnershipArchitectureTest.kt)、[AndroidBackupRulesArchitectureTest](../../app/src/test/java/dev/terashima/yomitorirss/AndroidBackupRulesArchitectureTest.kt) が runtime・provider・backup boundary を補完する。機械検査は意味的な owner 判断や文書レビューの代替にはならない。

## 形式仕様

Quint は状態遷移・順序・safety / temporal property、Alloy は構造・関係・cardinality の projection を扱う。自然言語仕様全体を置き換えない。対象 spec から直接リンクされた model と [coverage 一覧](../../spec-models/README.md) を確認し、変更する性質に対応する model と production test を検証する。

```sh
bash scripts/verify_formal_models.sh
```

必要な toolchain と traceability 規則は [Formal Specification Workflow](../../docs/formal-modeling.md) を参照する。

## PR CI

現行 workflow は公開内容の検査と Architecture / Test / Lint / R8 の matrix を実行する。Architecture job では形式仕様も検証する。代表コマンドは次のとおり。

```sh
python3 scripts/verify_public_repository.py
./gradlew --no-daemon -I gradle/architecture-metadata.gradle.kts -I gradle/table-ownership.gradle.kts verifyArchitecture
./gradlew --no-daemon test
./gradlew --no-daemon :app:lintRelease
./gradlew --no-daemon :app:minifyReleaseWithR8
```

公開内容 verifier は tracked file を調べる。未追跡の新規文書も提出前に対象へ含め、credential や実ユーザーデータについて意味的なレビューを行う。merge と build の手順は [開発・運用](../operations/development.md) を参照する。
