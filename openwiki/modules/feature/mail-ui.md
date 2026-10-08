---
type: module
title: "メール UI：受信箱操作と制限付きHTML表示"
description: "Mailbox選択、トリアージ状態更新、同期進捗polling、HTMLメールのWebView境界を説明する。"
tags: [mail, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-12ae3836698a672d49942894
    resource: repo://feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailMessageBody.kt
  - id: openwiki-source-73b0a701e6a63a617114af56
    resource: repo://feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# メール UI：受信箱操作と制限付きHTML表示

`:feature:mail:ui` は`MailRoute`・`MailScreen`・`MailViewModel`でaccount選択、mailbox切替、検索、thread詳細とトリアージを提供する。Mail Domainと共通design systemに依存し、Gmail APIやdatabaseを直接操作しない。

## 一覧から操作へ

ViewModelはRepositoryのaccountsと選択したmailboxのthreadsを読み、未読を受信トレイ内、アーカイブを受信トレイ外に絞る。スターとあとで読むは受信トレイ状態に限定しない。スター切替では未読mailboxから追加する場合だけ既読化する。`readLater()`はmailboxを問わず未読threadを既読化する一方、`toggleReadLater()`は未読mailboxから追加する場合だけ既読化する。処理後の一覧・詳細を取り直すことでremote/localの結果を表示へ反映する。

同期中またはnetwork待ちのaccountがある間はViewModel scopeでoverviewを定期再読込し、同期終了後にpollingを止める。再認証が必要な例外も通常のエラーと同じmessage経路で表示する。

## HTML表示の境界と検証

HTML本文は専用WebViewへ渡すが、JavaScript、file/content access、network load、mixed contentを禁止する。リンク遷移は外側で開く。HTML断片と完全なHTML文書は`htmlDocument()`で表示設定を整え、既存viewport/headを考慮する。

`MailViewModelTest`はmailboxのfilter、`MailHtmlDocumentTest`は文書ラップとhead/viewportの処理を確認する。トリアージの意味を変える場合はDomain操作と再取得順序を、HTML表示変更ではWebView設定とrenderer終了処理まで確認する。

## 調査・変更の入口

[MailRoute](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRoute.kt)、[MailViewModel](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModel.kt)、[MailMessageBody](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailMessageBody.kt)、[MailViewModelTest](../../../feature/mail/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModelTest.kt)、[MailHtmlDocumentTest](../../../feature/mail/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/MailHtmlDocumentTest.kt) を起点に責務と呼び出し側を確認する。

関連: [mail domain](mail-domain.md) / [mail data](mail-data.md) / [全体構成](../../architecture/system.md)。
