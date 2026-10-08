---
type: module
title: 蔵書 UI：一覧・書誌レビュー・Reader接続
description: 蔵書画面の状態、同期操作、AI結果復旧とBook Readerへの接続を説明する。
tags: [library, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-1aeb7e781fd31e0e19b6adff
    resource: repo://feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryViewModel.kt
  - id: openwiki-source-35ef98bc318270b0e350799d
    resource: repo://feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModelTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 蔵書 UI：一覧・書誌レビュー・Reader接続

`:feature:library:ui` は次の責務を持ちます。

## 画面の入口と状態

LibraryFeatureRouteが蔵書画面、外部URL操作、取り込み画面、SMB readerを結びます。LibraryViewModelはカタログと取得元状態に加え、同期・書誌正規化・表紙先読みの操作中状態と表示メッセージをStateFlowへ公開します。依存はLibrary Domainを中心に、Book ReaderのDomain/UIとWeb collectorへ向き、蔵書Dataの具象実装は画面へ持ち込みません。

Google Booksの同期やSMB同期はViewModelから公開Repositoryへ依頼し、成功時にsnapshotを再読込します。SMB同期後は表紙先読みの投入と書誌正規化schedulerの起動を行うため、同期完了と表紙やAI候補の完成は同時ではありません。画面ではそれぞれの進捗を別の状態として表示します。

## AI結果と閲覧の寿命

整理画面はLibraryOrganizationViewModelがAI task controllerから復旧可能な受付を取得し、候補を利用者の操作単位で消費します。画面の再生成を理由に候補そのものを再生成する設計ではありません。SMB読書はLibrary側でファイル取得を仲介し、Book Readerへローカル文書とページsourceを渡す境界を追います。変更時は書籍タップの取得元別振り分け、候補保存・却下、エラー後のbusy解除、画面再生成後の候補復旧を優先して確認してください。

## 調査と変更の入口

[LibraryFeatureRoute.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt)、[LibraryViewModel.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryViewModel.kt)、[LibraryOrganizationViewModel.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModel.kt)、[SmbBookReaderRoute.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [LibraryOrganizationViewModelTest.kt](../../../feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModelTest.kt)、[LibraryFeatureRouteTest.kt](../../../feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRouteTest.kt) です。

[library domain](library-domain.md)、[library data](library-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
