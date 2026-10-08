---
type: module
title: 蔵書 Domain：取得元と整理の契約
description: 蔵書の取得元、手動整理、シリーズ再整理と非同期AI操作の境界を説明する。
tags: [library, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-137d1c3ac674ad074c5fc956
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizer.kt
  - id: openwiki-source-675d6990ce665987456914ce
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationAiTaskController.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 蔵書 Domain：取得元と整理の契約

`:feature:library:domain` は次の責務を持ちます。

## 責務と処理の流れ

蔵書の一覧取得、非表示と復元、手動シリーズ設定、外部サービス由来の取り込みを公開契約で表現します。UIが取得元の保存方法へ依存せず、Dataが同じ蔵書モデルへ変換できる境界です。SMBファイルアクセス、表紙先読み、書誌正規化、Web書誌抽出も、この領域の契約を通して呼び出されます。Androidの接続やデータベース実装は持ちません。

シリーズ再整理は対象を蔵書キーで重複排除し、シリーズ設定済みで同一シリーズであることを先に検証します。その後、保存済み分類と先行書籍で確定した分類を後続書籍のAI入力へ渡し、タグとコレクションを更新します。読書状態は元の値を保持するため、分類の再生成によって読書の進捗を初期化しません。

## 状態・失敗と拡張

通常の書籍ごとの解析失敗は失敗件数へ加算し、成功した書籍は保存されます。一方、キャンセルは握りつぶさず呼び出し側へ伝播します。単冊候補とシリーズ再整理はAI task controllerの受付・結果取得の契約があり、画面が長時間推論そのものを所有する必要はありません。取得元追加ではモデル変換だけでなく、蔵書キーの安定性、非表示の維持、整理分類の引き継ぎを確認してください。

## 調査と変更の入口

[LibraryRepository.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryRepository.kt)、[LibraryMetadataOrganizer.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizer.kt)、[LibraryOrganizationAiTaskController.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationAiTaskController.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [LibraryMetadataOrganizerTest.kt](../../../feature/library/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizerTest.kt) です。

[library data](library-data.md)、[library ui](library-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
