---
type: module
title: Book Reader Domain：文書と読書位置
description: ローカル文書、ページsource、読書位置の保存契約を説明する。
tags:
  - book-reader
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-6cb24904ed073a193d6a2ef7
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/library/AppLibraryRuntimeDependencies.kt
  - id: openwiki-source-1b56d0b7a6c0e3e12e55f080
    resource: repo://feature/book-reader/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/BookReaderModels.kt
  - id: openwiki-source-1147e461969d759c1e6b575e
    resource: repo://feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Book Reader Domain：文書と読書位置

`:feature:book-reader:domain` は次の責務を持ちます。

## 責務と境界

書籍を開くUIと、実ファイルからページを取得するDataの間にある小さな契約です。BookDocumentは書籍の安定ID、タイトル、形式、ローカルパスを受け渡し、BookPageImageはページ画像のバイト列と寸法を表します。蔵書の同期やSMB接続、ファイルのダウンロードはこのモジュールの責務に含まれません。呼び出し元が準備したローカル書籍を閲覧するための境界です。

BookPageSourceFactoryで文書を開くと、ページ数を持つsourceが返ります。UIはページ番号と希望幅を指定して非同期に画像を要求します。sourceはAutoCloseableなので、実装が持つZIPやPDFのハンドルを画面の利用終了に合わせて解放できます。ただしDomainのclose既定実装は何もしないため、リソースを所有するData実装が適切な解放を実装する必要があります。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [BookReaderModels](../../../feature/book-reader/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/BookReaderModels.kt) | BookFormat/ReaderMode/ReadingDirectionは形式と表示設定のenum。BookDocumentはローカル文書、BookPageImageは画像bytesと寸法、ReadingPositionは読書位置。BookPageSource/BookPageSourceFactory/ReadingPositionStoreは画像取得・生成・位置保存の公開契約。 |

## 主要 API と実装・利用者

`BookPageSourceFactory.open(BookDocument)` はlocalPathとformatからsourceを返す。`BookPageSource.loadPage(index, targetWidth)` は非同期で画像bytesと寸法を返し、`pageCount` が有効範囲、`close` が資源解放の境界となる。`ReadingPositionStore.load/save` は書籍IDごとにページ番号・offset・表示モード・方向をまとめて扱う。

[AppLibraryRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/library/AppLibraryRuntimeDependencies.kt) がDataの `DefaultBookPageSourceFactory` と `SharedPreferencesReadingPositionStore` を生成する。Library UIの [SmbBookReaderRoute](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt) がSMBからlocal文書を準備し、sourceを開いてBook Reader UIへ渡し、離脱時にcloseする。Domain契約にはSMB認証やダウンロード処理を持ち込まない。

## 保存状態と変更の確認

読書位置はページ番号だけでなく縦スクロールのオフセット、ページ式または縦式の表示モード、左右の読書方向をまとめて保存します。書籍IDをキーに読み書きする契約のため、同じタイトルでも別IDなら読書位置を分けられます。Domain内に直接のテストはありません。変更を確認するときはDataの自然順・ZIP画像フィルタのテストとUIのページcacheテスト、Libraryから文書を渡す経路を参照してください。新形式追加ではモデルの列挙値だけでなく、factory、ページロード失敗、sourceの解放責任を併せて確認します。

## 調査と変更の入口

[BookReaderModels.kt](../../../feature/book-reader/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/BookReaderModels.kt) を入口に、呼び出し側と保存先を併せて追います。 このモジュール内に直接のテストはなく、連携先の検証を使います。

[book-reader data](book-reader-data.md)、[book-reader ui](book-reader-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。

直接テストがない契約の利用例は[ZIP画像選別テスト](../../../feature/book-reader/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookreader/data/ZipBookPageFilterTest.kt)と[Reader UI](book-reader-ui.md)を参照してください。
