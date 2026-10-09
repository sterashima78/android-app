---
type: module
title: メール Data：Gmail同期とdurable checkpoint
description: Gmail取得、local cache、ページ再開、Worker失敗分類とruntime認証tokenの扱いを説明する。
tags:
  - mail
  - data
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-1affdf3ffee02e90e6a1163c
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt
  - id: openwiki-source-09d9f1cb80e0ff9dc68669dd
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAuthorizationManager.kt
  - id: openwiki-source-90df18d5a2d00232b6578177
    resource: repo://feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# メール Data：Gmail同期とdurable checkpoint

`:feature:mail:data` はGmail API、platform認証、Mail-owned database schema、WorkManager同期を接続する。`DefaultMailRepository`がDomainの操作を実装し、UIへtokenや具体的databaseを渡さない。`GmailAuthorizationManager`は必要時にplatformからtokenを取得し、再認証が必要ならDomain例外を返す。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [DefaultMailRepository](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt) | `DefaultMailRepository`（class）はMailRepositoryを実装し、account/label/thread/messageをMail-owned tableに保存する。Domainの全操作を具体化し、`syncInitialPage`でcheckpointを検査する。通常同期はhistory差分を取得し、履歴期限切れでは全同期へ戻す。threadの既読・スター・archive・trash・labelはremote操作後にcacheへ反映し、あとで読むはlocal属性として扱う。 |
| [GmailApiClient](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailApiClient.kt) | `GmailApiClient`（internal class）はprofile history id、thread ID一覧・ページ・全件、thread本文、label、history差分とremote変更をHTTPへ変換する。`GmailThreadPage`/`GmailHistoryDelta`（internal data class）が取得結果、`GmailApiException`と`GmailHistoryExpiredException`がHTTP失敗と履歴期限切れを区別する。 |
| [GmailAccountProfileClient](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAccountProfileClient.kt) | `GmailAccountProfileClient`（internal class）の`email(accessToken)`は認証結果tokenからprofileのemailを取得する。account接続時のidentityを決める補助adapter。 |
| [GmailAuthorizationManager](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAuthorizationManager.kt) | `GmailAuthorizationManager`（class）の`requestAccount`は直接認可済み結果または解決Intentを返す。`resultFromIntent`は認可画面結果を`GmailAuthorizedAccount`（data class）へ変換し、`accessToken(email)`は同期時のtoken取得を行う。`GmailAuthorizationOutcome`（sealed interface）が認可済み/画面解決を区別する。 |
| [MailBodyDecoder](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailBodyDecoder.kt) | `decodeMailBody`と`isDisplayMailBodyPart`（internalトップレベル関数）がbase64url・MIME charsetの復号と表示する本文partの選択を担う。未知charsetのfallbackと添付本文除外をここに集約する。 |
| [MailDatabaseSchema](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailDatabaseSchema.kt) | `mailDatabaseSchema`はMail-owned tableの作成をdatabase bootstrapへ提供する。初回同期のcheckpoint/history/generationとthread/message/labelの保存領域を定義する。 |
| [MailSyncWorker](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt) | `MailSyncScheduler`（class）は`scheduleInitialPage`、定期同期、network policy再設定、account/定期work取消を提供する。`MailSyncWorker`（class）の`doWork`がRepositoryの初回ページ/通常同期を呼び、結果と例外をWorkManager success/retry/failureへ変換する。`MailWorkerFactory`（class）がRepository providerを接続する。 |

## 公開APIと構成要素間の接続

[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)が`DefaultMailRepository`へapplication context、DatabaseConnection、GmailAuthorizationManagerを渡す。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`mailViewModelFactory`はMailRepositoryを注入し、認可画面との連携は`mailAuthorization`境界で別に公開する。

## 初回同期の再開フロー

account接続から初回page workへ進み、Repositoryはdatabase上のpage checkpointと期待tokenを比較する。不一致はStaleとして処理しない。一致時は開始history idとgenerationを保持し、threadをbatch取得してgenerationとともに保存する。次ページがあればcheckpointを先に保存してContinueを返す。最終ページだけlabel更新・古いgenerationのthread整理・完了処理を行う。

WorkerはContinueから次workを予約し、Staleなら現在状態からsyncを調整する。初回同期の通信例外はnetwork待ちとしてretryし、再認証はERRORとfailureになる。API失敗もretry可能性に応じて待機とerrorを分ける。通常の定期同期では共通background取得policyを確認する。

## 本文と確認先

`MailBodyDecoder`はbase64url本文とMIME charsetを扱い、添付HTMLを表示本文から除外する。`MailBodyDecoderTest`がUTF-8、日本語charset、未知charset fallback、添付除外を確認する。checkpoint順序変更は`docs/spec/06-mail.md`とQuintモデル、credential保存先変更はAlloyモデルも確認し、tokenをdurable stateへ混ぜない。

## 調査・変更の入口

[DefaultMailRepository](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt)、[MailSyncWorker](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt)、[GmailAuthorizationManager](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/GmailAuthorizationManager.kt)、[MailBodyDecoder](../../../feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailBodyDecoder.kt)、[MailBodyDecoderTest](../../../feature/mail/data/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailBodyDecoderTest.kt) を起点に責務と呼び出し側を確認する。

関連: [mail domain](mail-domain.md) / [mail ui](mail-ui.md) / [全体構成](../../architecture/system.md)。
