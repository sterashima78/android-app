---
type: module
title: "メール Domain：トリアージと同期再開の契約"
description: "メール状態、受信箱操作、初回同期のContinue・Complete・Stale契約と認証境界を説明する。"
tags: [mail, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-0a5260b342954502b41c6436
    resource: repo://feature/mail/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailModels.kt
  - id: openwiki-source-370d7e45c468654564f4167d
    resource: repo://feature/mail/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# メール Domain：トリアージと同期再開の契約

`:feature:mail:domain` はGmail連携を利用する側のメールモデルとRepository契約を定義するJVMモジュールである。account、label、message、threadを分け、threadには受信トレイ・未読・スター・あとで読むの状態を持たせる。Androidの認証画面やHTTP、databaseはこの層が所有しない。

## 同期とトリアージの入口

Repositoryはaccount接続・削除、mailboxとqueryでのthread取得、詳細・label取得、同期と状態変更を提供する。通常のsyncと初回同期の1ページ処理を分離し、`syncInitialPage(accountId, expectedPageToken)`は次ページへのContinue、最終ページのComplete、期待checkpoint不一致のStaleを返す。これによりbackground側はページ処理の結果から継続予約や再調整を選べる。

`MailSyncState`はIDLE、SYNCING、WAITING_FOR_NETWORK、ERRORを区別し、accountは進捗件数やエラーを保持する。再認証が必要な場合は`MailAuthorizationRequiredException`で示す。Repositoryの公開操作はaccount identityを受け取り、OAuth tokenをUIから渡す契約を持たない。

## 境界を変えるとき

Domainは同期checkpointのdatabase表現やretry回数を決めず、DataとWorkerが具体化する。契約追加ではMailboxのUIフィルタ、初回同期Worker、認証のruntime境界を確認する。本モジュール配下に専用testはなく、UIの`MailViewModelTest`、Dataの実装、および仕様に参照された初回同期・credential境界の形式モデルが確認先になる。

## 調査・変更の入口

[MailRepository](../../../feature/mail/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRepository.kt)、[MailModels](../../../feature/mail/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailModels.kt) を起点に責務と呼び出し側を確認する。

関連: [mail data](mail-data.md) / [mail ui](mail-ui.md) / [全体構成](../../architecture/system.md)。
