---
type: architecture
title: 全体構成と責務
description: Mosaic の機能、app shell と composition、feature 境界、変更時の調査先を説明する。
tags: [architecture, modules, contexts]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T11:54:35.312Z
sources:
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-735f02f4cb4a910668807f83
    resource: repo://docs/architecture/context-map.md
  - id: openwiki-source-d1d2160a3e9a979d5488e3ca
    resource: repo://docs/architecture/principles.md
  - id: openwiki-source-872141f77f71851168245852
    resource: repo://docs/architecture/system-overview.md
  - id: openwiki-source-e620d7484b72a53c7fa812cd
    resource: repo://settings.gradle.kts
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# 全体構成と責務

Mosaic は端末内のコンテンツや個人情報を閲覧・整理し、AI 処理へ接続する Android アプリである。ビルド上の project 名や package には YomitoriRss / yomitorirss が残る。名前だけで製品・Context・保存形式を判断せず、[System Overview](../../docs/architecture/system-overview.md) と [settings.gradle.kts](../../settings.gradle.kts) を照合する。

## 実行と依存の境界

| 層 | 責務 | 変更時の入口 |
| --- | --- | --- |
| app | Activity / Service 等の executable shell | [MainActivity](../../app/src/main/java/dev/terashima/yomitorirss/MainActivity.kt) |
| app:presentation | navigation、app-wide chrome、feature UI composition | [Code Organization](../../docs/architecture/code-organization.md) |
| app:composition | application-scope の concrete dependency graph | [AppContainer](../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) |
| feature UI | feature の画面・操作 | [Module Map](../../docs/architecture/module-map.md) |
| feature Domain | model、rule、公開 capability | [Context Map](../../docs/architecture/context-map.md) |
| feature Data | DB・network・Android adapter、owner runtime | [Persistence](persistence.md) |
| core | DB、network、AI runtime 等の技術 capability | [Principles](../../docs/architecture/principles.md) |

Domain から Android / SQLite / HTTP concrete implementation へ依存しない。core から feature、Domain から UI / Data、UI から concrete Data の逆向き依存を増やさない。すべての feature に同じ三層を機械的に追加する設計ではなく、実際の module と例外は Module Map で確認する。

## 機能から調査先を選ぶ

| 目的 | owner と正本 |
| --- | --- |
| RSS 等の取り込み、記事の identity・既読 | RSS 等の source Context と Content。[コンテンツフロー](../workflows/content.md) |
| Bookmark、Tag、Folder、あとで読む | Curation。[Context Map](../../docs/architecture/context-map.md) |
| 要約、Knowledge、AI queue | 各 feature と技術 runtime。[AI / background](../workflows/ai-background.md) |
| 蔵書、SMB、Book Reader | Library。[Web Content](../../docs/architecture/web-content.md) と [媒体連携](../integrations/media.md) |
| 音声、Podcast、動画 | Audio / Podcast / Video。[媒体連携](../integrations/media.md) |
| メール | Mail。[メール仕様](../../docs/spec/06-mail.md) |
| Task、Calendar、Workout、Health | 独立した owner。[仕様](../../docs/spec/08-task-calendar-workout-health.md) |
| 資産 | Asset。[仕様](../../docs/spec/09-assets.md) |
| LAN Web、X、Widget、Game | owning feature と platform boundary。[仕様](../../docs/spec/10-web-x-widget-games.md) |

Calendar は Task / Workout の read model で、それらの durable state を共同所有しない。Health は Health Connect を読み取り、Workout は自身の完了記録を一方向に Health Connect へ export する。新しい共通化の前に Context Map で owner と関係を確認する。

## 変更に入る前の確認

DB、cloud egress、permission、credential、background runtime、composition graph の変更は波及しやすい。[開発手順](../operations/development.md) から Change Impact Review と関連 ADR へ進み、実装と設計が食い違う場合は drift の可能性を確認する。構造の検証は [契約と検証](../testing/contracts.md) にまとめる。
