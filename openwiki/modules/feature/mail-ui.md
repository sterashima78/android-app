---
type: module
title: メール UI：受信箱操作と制限付きHTML表示
description: Mailbox選択、トリアージ状態更新、同期進捗polling、HTMLメールのWebView境界を説明する。
tags:
  - mail
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-12ae3836698a672d49942894
    resource: repo://feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailMessageBody.kt
  - id: openwiki-source-3e868f812fc3acec942cf66c
    resource: repo://feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRoute.kt
  - id: openwiki-source-73b0a701e6a63a617114af56
    resource: repo://feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# メール UI：受信箱操作と制限付きHTML表示

`:feature:mail:ui` は`MailRoute`・`MailScreen`・`MailViewModel`でaccount選択、mailbox切替、検索、thread詳細とトリアージを提供する。Mail Domainと共通design systemに依存し、Gmail APIやdatabaseを直接操作しない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [NavigationDestination](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/NavigationDestination.kt) | `MAIL_ROUTE`と`MAIL_TITLE`がメール画面のrouteと表示名を公開する。 |
| [MailRoute](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRoute.kt) | `MailRoute`（Composable）はMailViewModelのstateを収集し、account認可要求を外側callbackへ渡して`MailScreen`の操作をViewModelへ接続する。 |
| [MailScreen](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailScreen.kt) | `MailScreen`（Composable）はaccountバー、検索、同期status、swipe thread一覧と詳細を組み合わせる。内部`MailSyncStatus`は同期中/待機/失敗accountをまとめ、`SwipeThreadRow`はMailbox別の操作callbackを割り当てる。 |
| [MailViewModel](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModel.kt) | `MailUiState`（data class）はaccount選択、Mailbox、query、一覧、詳細、labelと通知を保持する。`MailViewModel`（class）のaccount接続/削除、`selectAccount`/`selectMailbox`、`updateQuery`/`search`、`refresh`が一覧入口。`openThread`/`closeThread`が詳細状態、`toggleRead`/`toggleStarred`/`readLater`/`toggleReadLater`/`archive`/`trash`/`applyLabel`が操作入口。`dismissMessage`は通知解除。内部`forMailbox`拡張関数がmailboxの表示filter、`Factory`がRepository注入を担う。 |
| [MailMessageBody](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailMessageBody.kt) | `MailMessageBody`（internal Composable）はplain/HTML本文を分岐し、内部`MailWebView`が文書設定・高さ計測、renderer lifecycleが終了後の破棄を扱う。`htmlDocument`（internal関数）は断片/完全HTMLのhead・viewportと色を整える。外部リンクはContext helperへ委譲する。 |

## 公開APIと構成要素間の接続

[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)が`DefaultMailRepository`へapplication context、DatabaseConnection、GmailAuthorizationManagerを渡す。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`mailViewModelFactory`はMailRepositoryを注入し、認可画面との連携は`mailAuthorization`境界で別に公開する。

## 一覧から操作へ

ViewModelはRepositoryのaccountsと選択したmailboxのthreadsを読み、未読を受信トレイ内、アーカイブを受信トレイ外に絞る。スターとあとで読むは受信トレイ状態に限定しない。スター切替では未読mailboxから追加する場合だけ既読化する。`readLater()`はmailboxを問わず未読threadを既読化する一方、`toggleReadLater()`は未読mailboxから追加する場合だけ既読化する。処理後の一覧・詳細を取り直すことでremote/localの結果を表示へ反映する。

同期中またはnetwork待ちのaccountがある間はViewModel scopeでoverviewを定期再読込し、同期終了後にpollingを止める。再認証が必要な例外も通常のエラーと同じmessage経路で表示する。

## HTML表示の境界と検証

HTML本文は専用WebViewへ渡すが、JavaScript、file/content access、network load、mixed contentを禁止する。リンク遷移は外側で開く。HTML断片と完全なHTML文書は`htmlDocument()`で表示設定を整え、既存viewport/headを考慮する。

`MailViewModelTest`はmailboxのfilter、`MailHtmlDocumentTest`は文書ラップとhead/viewportの処理を確認する。トリアージの意味を変える場合はDomain操作と再取得順序を、HTML表示変更ではWebView設定とrenderer終了処理まで確認する。

## 調査・変更の入口

[MailRoute](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailRoute.kt)、[MailViewModel](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModel.kt)、[MailMessageBody](../../../feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/MailMessageBody.kt)、[MailViewModelTest](../../../feature/mail/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/MailViewModelTest.kt)、[MailHtmlDocumentTest](../../../feature/mail/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/mail/MailHtmlDocumentTest.kt) を起点に責務と呼び出し側を確認する。

関連: [mail domain](mail-domain.md) / [mail data](mail-data.md) / [全体構成](../../architecture/system.md)。
