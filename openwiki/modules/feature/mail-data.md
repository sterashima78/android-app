---
type: module
title: "メール Data：Gmail同期とdurable checkpoint"
description: "Gmail取得、local cache、ページ再開、Worker失敗分類とruntime認証tokenの扱いを説明する。"
tags: [mail, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-1affdf3ffee02e90e6a1163c
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt
  - id: openwiki-source-09d9f1cb80e0ff9dc68669dd
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAuthorizationManager.kt
  - id: openwiki-source-90df18d5a2d00232b6578177
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# メール Data：Gmail同期とdurable checkpoint

`:feature:mail:data` はGmail API、platform認証、Mail-owned database schema、WorkManager同期を接続する。`DefaultMailRepository`がDomainの操作を実装し、UIへtokenや具体的databaseを渡さない。`GmailAuthorizationManager`は必要時にplatformからtokenを取得し、再認証が必要ならDomain例外を返す。

## 初回同期の再開フロー

account接続から初回page workへ進み、Repositoryはdatabase上のpage checkpointと期待tokenを比較する。不一致はStaleとして処理しない。一致時は開始history idとgenerationを保持し、threadをbatch取得してgenerationとともに保存する。次ページがあればcheckpointを先に保存してContinueを返す。最終ページだけlabel更新・古いgenerationのthread整理・完了処理を行う。

WorkerはContinueから次workを予約し、Staleなら現在状態からsyncを調整する。初回同期の通信例外はnetwork待ちとしてretryし、再認証はERRORとfailureになる。API失敗もretry可能性に応じて待機とerrorを分ける。通常の定期同期では共通background取得policyを確認する。

## 本文と確認先

`MailBodyDecoder`はbase64url本文とMIME charsetを扱い、添付HTMLを表示本文から除外する。`MailBodyDecoderTest`がUTF-8、日本語charset、未知charset fallback、添付除外を確認する。checkpoint順序変更は`docs/spec/06-mail.md`とQuintモデル、credential保存先変更はAlloyモデルも確認し、tokenをdurable stateへ混ぜない。

## 調査・変更の入口

[DefaultMailRepository](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt)、[MailSyncWorker](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt)、[GmailAuthorizationManager](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAuthorizationManager.kt)、[MailBodyDecoder](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailBodyDecoder.kt)、[MailBodyDecoderTest](../../../feature/mail/data/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailBodyDecoderTest.kt) を起点に責務と呼び出し側を確認する。

関連: [mail domain](mail-domain.md) / [mail ui](mail-ui.md) / [全体構成](../../architecture/system.md)。
