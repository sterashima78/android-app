# Web Content Boundaries

この文書は、ユーザー閲覧用 Web content、RSS の Web scraping、Library の Web metadata 取得について現在有効な境界をまとめる。

## User-visible browsing

ユーザーが開く HTTP / HTTPS content と内部の Web 取得処理は別 capability として扱う。

- RSS / Bookmark の Article と Web 由来の Library item は `:app` の Custom Tabs launcher から開く。
- browser package は固定せず、Android の既定ブラウザ選択を尊重する。
- Custom Tabs launcher は HTTP / HTTPS のみを対象とする。
- Kindle、Google Play Books、Audible 等の source 固有 URI / app link は各 source の既存 routing を維持する。
- metadata 取得や scraping のための WebView はユーザー閲覧用 browser へ置き換えない。

Custom Tabs は navigation / platform wiring であり、feature module は `CustomTabsIntent` を直接所有しない。

## RSS acquisition

RSS Context の feed 取得経路は現在次の2系統だけとする。

1. URL に一致する user-defined Web scraping rule
2. 通常の RSS / Atom discovery / fetch

Web scraping rule は URL pattern、Promise ベースの JavaScript function、timeout を RSS-owned durable user data として保存する。組み込みの MangaOne / Yanmaga 等の site-specific synthetic feed client は廃止済みであり、custom rule が存在しない場合に旧 site-specific client へ fallback しない。

rule の編集・実行テストは RSS 設定画面に置く。実運用 URL pattern、DOM selector、function code はユーザーデータであり repository の source / fixture / ADR へ保存しない。

## Library Web metadata

Library Context は Web source の URL を identity とし、metadata 取得を Library 内に閉じる。

- 通常は static HTTP(S) の OGP / HTML metadata を優先する。
- metadata 不足時は短命な WebView で rendered DOM を取得する。
- URL に一致する `WebLibraryMetadataExtractor` がある場合は、Promise ベースの user-defined function を専用 WebView profile 内で実行し、取得値を site-specific override として利用する。
- extractor の draft は保存前に実 URL で実行テストできる。
- WebView へ native JavaScript bridge を公開しない。

RSS の scraping rule と Library の metadata extractor は execution pattern が似ていても共有 repository / table を作らない。RSS は feed semantics、Library は bibliographic metadata semantics を所有し、それぞれの Context 内で durable state と validation を管理する。

## WebView lifecycle and safety

内部 WebView は Custom Tabs と役割が異なるため、次を維持する。

- renderer 終了時は終了済み WebView instance を再利用しない。
- headless adapter は renderer loss や timeout を現在の取得単位の failure として返す。
- foreground Activity が必要な rendered metadata client は Activity 復帰まで待機し、background で無理に WebView を生成しない。
- scraping / extractor の timeout と native watchdog を持ち、JavaScript polling だけに停止責務を依存しない。

## Sources

- [ADR-0154](../adr/0154-web-library-rendered-metadata-fallback.md)
- [ADR-0163](../adr/0163-webview-renderer-exit-recovery.md)
- [ADR-0173](../adr/0173-web-library-custom-metadata-extractors.md)
- [ADR-0177](../adr/0177-web-library-extractor-execution-diagnostics.md)
- [ADR-0178](../adr/0178-web-library-headless-webview-scheduling.md)
- [ADR-0180](../adr/0180-rss-custom-web-scraping-rules.md)
- [ADR-0181](../adr/0181-web-library-extractor-editor-and-test.md)
- [ADR-0182](../adr/0182-open-user-web-content-with-custom-tabs.md)
- [ADR-0183](../adr/0183-rss-settings-tab.md)
- [ADR-0184](../adr/0184-remove-site-specific-manga-rss-clients.md)
