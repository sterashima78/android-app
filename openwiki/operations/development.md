---
type: workflow
title: 開発・ビルド・Wiki 更新
description: 正本の確認、Change Impact Review、PR と検証、APK 配布、OpenWiki 更新の手順を案内する。
tags: [development, build, openwiki]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T12:04:16.240Z
sources:
  - id: openwiki-source-7a80b79a6fb3618cbfab08a2
    resource: repo://.github/workflows/build.yml
  - id: openwiki-source-6d4b4e707b8d60b6ccfa3425
    resource: repo://.github/workflows/openwiki-update.yml
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-8da12f025407b7d429e8593b
    resource: repo://docs/development-workflow.md
  - id: openwiki-source-16b97cbfa50dd2e922fba80e
    resource: repo://docs/openwiki.md
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# 開発・ビルド・Wiki 更新

作業の共通入口は [AGENTS.md](../../AGENTS.md)、手順の正本は [Development Workflow](../../docs/development-workflow.md)。この Wiki はそれらを調べる案内であり、作業ルールを置き換えない。

## 要求から調査へ

ユーザー仕様の [目次](../../docs/spec.md) から対象節を読み、形式モデルが参照される場合は [Formal Specification Workflow](../../docs/formal-modeling.md) と対応 model を確認する。次に System Overview、Principles、Context Map、対象 architecture、関連 ADR、現在の実装・テスト・manifest を照合する。

owner、durable state、cloud egress、credential / permission boundary、compatibility 等を変える場合は、production code の変更前に Change Impact Brief を作る。人間の判断が必要な変更と routine な変更の扱いは Development Workflow の decision gate に従う。

## ブランチから成果物まで

最初の変更を push した段階で Draft PR を作り、実装・検証に合わせて本文を更新する。Ready for review の前に System Diff と独立レビュー結果を整理する。独立レビューと必要な CI が成功したら squash merge する流れである。

[検証の選び方](../testing/contracts.md) から focused test と CI へ進む。仕様・設計が変わる場合は正本を同じ変更で更新する。Wiki を直しただけで設計判断を記録したことにはならない。

## Android ビルドの入口

module は [settings.gradle.kts](../../settings.gradle.kts)、アプリ設定は [app/build.gradle.kts](../../app/build.gradle.kts)、platform contract は [Platform](../../docs/architecture/platform.md) を確認する。現行アプリは compileSdk / targetSdk 37、minSdk 35、Java 17、arm64-v8a を設定している。SDK や toolchain を変更する際は build file を再確認する。

通常のローカル debug build の入口は次のとおり。Android SDK と Java の設定が必要。

```sh
./gradlew --no-daemon :app:assembleDebug
```

main の [Android Build workflow](../../.github/workflows/build.yml) は公開内容を検査し、signed release APK を生成して署名を検証する。version を含む名前に変更して mosaic-android-apk artifact を upload する。documentation-only の変更でも main build の成果物共有を行うのがリポジトリの手順である。署名 credential は CI の設定で扱い、文書や repository file へ転記しない。

## OpenWiki を維持する

連携の準備・初期化・更新・閲覧は [OpenWiki の利用と更新](../../docs/openwiki.md)。Codex の OpenWiki 連携はホストのモデルセッションを使い、別のモデル API キーを要求しない。単独 CLI の init / update は別途 provider 設定を使う。

Wiki の変更は OpenWiki の begin → plan → next page → submit page → finish の手順で行い、索引・Claims・provenance を手書きしない。編集方針は [INSTRUCTIONS.md](../INSTRUCTIONS.md)。初期化は生成 Wiki を置き換えるので、通常の変更後は更新を依頼する。

アプリ内 Knowledge はユーザー資料から生成する機能であり、この開発用 Wiki とは生成対象も lifetime も別である。アプリ側の調査には [AI と background](../workflows/ai-background.md) を使う。
