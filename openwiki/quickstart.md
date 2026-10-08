---
type: guide
title: Mosaic Wiki の使い方
description: 目的別に仕様・設計・実装・テストへ進む、日本語のリポジトリ Wiki の入口。
tags: [quickstart, navigation, mosaic]
sources:
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-16b97cbfa50dd2e922fba80e
    resource: repo://docs/openwiki.md
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:10:52.576Z
---

# Mosaic Wiki の使い方

この Wiki は Mosaic Android アプリの現在のソースとテストを調べるための案内である。アプリ内 Knowledge がユーザー資料から作る Wiki とは別で、開発資料をリポジトリ内に保存している。

## 最初に読む正本

[AGENTS.md](../AGENTS.md) が作業の共通入口。[Development Workflow](../docs/development-workflow.md) に従い、[ユーザー仕様の目次](../docs/spec.md)、対応する形式モデル、[System Overview](../docs/architecture/system-overview.md)、Principles、Context Map、関連 ADR、現在のコードとテストへ進む。

Wiki は正本を置き換えない。重要な判断では各ページの source / test リンクと現在の正本を確認する。

## 目的別の入口

| 知りたいこと | ページ | 次の確認先 |
| --- | --- | --- |
| 全体像・どこへ変更を入れるか | [全体構成と責務](architecture/system.md) | Module Map、Context Map、各 feature spec |
| DB・owner・migration・backup | [永続化とデータ所有権](architecture/persistence.md) | schema、table manifest、backup spec |
| source 取り込み・Bookmark・蔵書への移動 | [取り込みから整理・生成まで](workflows/content.md) | owner port、UseCase、focused test |
| AI provider・Worker・Knowledge 生成 | [AI とバックグラウンド処理](workflows/ai-background.md) | inference adapter、WorkerFactory、task model |
| Audio・Podcast・Video・SMB | [媒体連携](integrations/media.md) | playback、source adapter、Library capability |
| どの検証を実行するか | [契約と検証の選び方](testing/contracts.md) | focused test、PR CI、形式仕様 |
| 開発手順・build・Wiki の更新 | [開発・ビルド・Wiki 更新](operations/development.md) | Development Workflow、build workflow |

## この Wiki の更新と閲覧

公式の Codex 連携と OpenWiki 0.7.1 を使う。[利用と更新](../docs/openwiki.md) に準備・依頼文・閲覧コマンドをまとめる。モデル API キーを別に設定する単独 CLI と、ホストのモデルセッションを使う Codex 連携を区別する。

編集方針は [INSTRUCTIONS.md](INSTRUCTIONS.md)。通常のコード変更後は初期化ではなく更新を依頼する。各ページの Claims と索引は OpenWiki が管理するため手で編集しない。

## 調査の例

「蔵書への移動で Bookmark が消える条件」を調べるなら、コンテンツフロー → UseCase → 失敗時の focused test を読む。「Podcast が RSS reader の未読に依存するか」なら、媒体連携 → Podcast source adapter → Podcast Architecture を確認する。具体的な質問を Codex に伝えて Wiki を検索し、関連 section を読ませることもできる。
