---
type: module
title: 蔵書 Data：カタログ・同期・処理キュー
description: 取得元別蔵書の保存、手動状態、SMBとAI処理の実装を調べる入口。
tags: [library, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-342af0bd381afe21aa4c92d3
    resource: repo://feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 蔵書 Data：カタログ・同期・処理キュー

`:feature:library:data` は次の責務を持ちます。

## 責務と同期の境界

蔵書契約をSQLite、外部サービス、WebView、SMB、端末内AIへ接続します。単一の巨大な同期処理だけでなく、カタログ、整理分類、書誌候補、表紙先読み、WorkerをLibraryの実装として配置しています。依存先にはLibrary Domainとデータベース・ネットワーク・AI・backgroundのcore capabilityがあり、UIへは依存しません。

基本カタログではGoogle Booksの取得結果やAmazon由来JSONを蔵書へ変換し、取得元単位で入れ替えます。取得と解析が先に済んだ後、該当取得元の既存行削除、新しい蔵書の挿入、同期時刻の更新をトランザクションでまとめるため、更新途中のカタログを完成扱いしません。Kindleの通常本とPersonal Documentには別の置換範囲があり、混在インポートを拒否します。

## 手動状態と変更時の注意

非表示はカタログ行の削除ではなく、取得元とsource IDに対応する別状態として保存します。手動シリーズの一括更新では入力を検証してからトランザクションで反映し、シリーズ除外設定との整合も取ります。同期形式や書誌解析の変更では、外部データを再取得しても利用者の手動判断が残るかを確認してください。SMB処理の変更は関連する表紙キュー・正規化キューのテストも参照し、カタログ更新とバックグラウンド処理状態を混同しないよう追跡します。

## 調査と変更の入口

[DefaultLibraryRepository.kt](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt)、[LibraryDatabaseSchema.kt](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryDatabaseSchema.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultLibraryRepositorySeriesMergeTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepositorySeriesMergeTest.kt)、[SmbCoverPrefetchQueueStoreTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbCoverPrefetchQueueStoreTest.kt)、[SmbMetadataNormalizationQueueTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationQueueTest.kt) です。

[library domain](library-domain.md)、[library ui](library-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
