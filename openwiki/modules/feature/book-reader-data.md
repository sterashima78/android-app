---
type: module
title: Book Reader Data：ZIP・PDFと読書位置保存
description: ZIP画像とPDFレンダリング、SharedPreferences読書位置の実装を説明する。
tags: [book-reader, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-bb879d4c6b559ad2f507919c
    resource: repo://feature/book-reader/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/data/DefaultBookReaderData.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Book Reader Data：ZIP・PDFと読書位置保存

`:feature:book-reader:data` は次の責務を持ちます。

## ページ取得の流れ

DefaultBookPageSourceFactoryは文書形式に応じてZIPまたはPDFのsourceを開きます。ZIPではディレクトリと非画像、macOSのメタデータを除き、数字部分を自然順で比較した一覧をページ順にします。そのためファイル名が2と10のページは、通常の文字列順による逆転を避けられます。表示可能なページがない文書や画像寸法が読めないページはエラーになります。

PDFではParcelFileDescriptorとPdfRendererを保持し、ページ読み出しをIO dispatcherで行います。rendererへの同時アクセスはMutexで直列化し、要求幅を許容範囲へ収めて縦横比を保ったbitmapへ描画します。画像のバイト列を返した後にはbitmapを解放し、sourceのcloseではrendererとdescriptorを閉じます。ZIP sourceもcloseでZipFileを閉じるため、呼び出し側の寿命管理が必要です。

## 読書位置と検証

SharedPreferencesReadingPositionStoreは書籍IDごとにページ、offset、表示モード、読書方向を保存します。負の位置はゼロへ丸め、未知のenum値は既定の表示設定へ戻すので、壊れた設定文字列で画面初期化を失敗させません。位置保存と再生成可能なページ画像を分けて考えることが変更時の要点です。既存のunit testはZIP内の対象選別と自然順比較を確認します。PDF描画や実ファイルの解放までを直接証明するものではないため、その変更ではAndroid上の閲覧と終了も確認してください。

## 調査と変更の入口

[DefaultBookReaderData.kt](../../../feature/book-reader/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookreader/data/DefaultBookReaderData.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [ZipBookPageFilterTest.kt](../../../feature/book-reader/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookreader/data/ZipBookPageFilterTest.kt)、[NaturalCompareTest.kt](../../../feature/book-reader/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookreader/data/NaturalCompareTest.kt) です。

[book-reader domain](book-reader-domain.md)、[book-reader ui](book-reader-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
