---
type: module
title: Book Reader UI：ページ式・縦式閲覧
description: 読書位置の更新、画像読み込み、ページcacheと閲覧操作を説明する。
tags: [book-reader, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-f794249933c378015781c4aa
    resource: repo://feature/book-reader/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/ui/BookPageMemoryCache.kt
  - id: openwiki-source-0d91b9cf05c85fe5ed6edc06
    resource: repo://feature/book-reader/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/ui/BookReaderScreen.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Book Reader UI：ページ式・縦式閲覧

`:feature:book-reader:ui` は次の責務を持ちます。

## 画面の状態とデータの流れ

BookReaderScreenは文書、開かれたBookPageSource、ReadingPositionStore、戻る操作を受け取ります。書籍IDで保存済み位置を読み、ページ式ではHorizontalPager、縦式ではLazyColumnから現在位置を取得します。ページとoffset、表示モード、読書方向の変更に合わせて保存契約を呼ぶため、画像を再ロードしても利用者の読書設定は維持できます。ファイル取得やZIP/PDF解析は画面で実装しません。

ページ式では読書方向に応じてpagerのreverseLayoutを切り替え、保存位置をページ範囲内へ収めます。縦式では最初に見えるページとスクロールoffsetを監視します。個々のページを表示する処理は画像ロード結果を使い、読み込み失敗やデコード失敗を画面で扱います。source自体を開閉する責任は渡し元にもあるため、Library側のreader routeを併せて追ってください。

## cacheと変更時の確認

BookPageMemoryCacheは画像をアクセス順で保持し、画像の合計サイズと枚数のどちらにも上限を設けます。大きすぎる画像を保持しない場合でも縦横比は別cacheへ記録し、画像削除後の縦レイアウトの寸法推定に使います。画像cacheと読書位置は寿命が異なる状態です。cacheテストは最近使ったページの保持、LRU削除、画像削除後の寸法保持を確認します。表示モードやページ計算を変える際は、スクロール位置の復元、方向切替、実際の大きい画像でのメモリ使用も確認してください。

## 調査と変更の入口

[BookReaderScreen.kt](../../../feature/book-reader/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/ui/BookReaderScreen.kt)、[BookPageMemoryCache.kt](../../../feature/book-reader/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/ui/BookPageMemoryCache.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [BookPageMemoryCacheTest.kt](../../../feature/book-reader/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/bookreader/ui/BookPageMemoryCacheTest.kt) です。

[book-reader domain](book-reader-domain.md)、[book-reader data](book-reader-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
