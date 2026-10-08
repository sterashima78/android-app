---
type: module
title: 許可 origin に限定した Web 収集 dialog
description: ログインを伴う Web 収集の WebView、message bridge、容量制限と renderer 終了を共通化する。
tags: [webview, security, collection, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-603f1af77361a17ca9dfa80b
    resource: repo://core/web-collector/src/main/kotlin/dev/terashima/yomitorirss/core/webcollector/SecureWebCollectorDialog.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 許可 origin に限定した Web 収集 dialog

## 収集 UI の共通境界

`:core:web-collector` は Compose dialog 内の WebView 収集機構を提供する。[SecureWebCollectorDialog](../../../core/web-collector/src/main/kotlin/dev/terashima/yomitorirss/core/webcollector/SecureWebCollectorDialog.kt) の設定には開始 URL、収集対象 prefix、許可 navigation host、許可 message origin、収集 script、結果容量・chunk 数などを渡す。provider ごとの何を収集するか、収集結果をどの repository に保存するかは caller の責務で、dialog は result と dismiss callback で返す。

## navigation と bridge の違い

WebView 内 navigation は HTTPS の許可 host またはその subdomain に限定する。bridge は明示した HTTPS origin を照合し、navigation で許した subdomain 全体へは広げない。JavaScript と DOM storage は収集用に有効だが file / content access、mixed content、自動 popup、multiple windows は無効、safe browsing は有効である。分割 payload は session、index、総数、宣言 byte length と上限を検証して組み立て、別 session や過大結果を拒否する。

## renderer と Compose lifetime

renderer 終了時は collection・continuation・chunk を破棄する。クラッシュならエラーを示し、メモリ不足による終了なら renderer generation を進めて新 WebView を作り再読込する。DisposableEffect で退出時に loading を停止し WebView を destroy するため、画面を閉じた後の収集 callback が元の状態へ流れないよう lifetime を揃える。

## 確認と拡張

[SecureWebCollectorDialogTest](../../../core/web-collector/src/test/kotlin/dev/terashima/yomitorirss/core/webcollector/SecureWebCollectorDialogTest.kt) は HTTPS host / subdomain、紛らわしい別 host、port、bridge origin、JSON 分割、過大宣言、別 session の拒否を確認する。収集先追加では navigation と bridge の allowlist をそれぞれ最小範囲で指定する。renderer 回復や実際のログインを変更するときは純粋関数 test に加えて WebView の端末確認が必要になる。

仕様・設計の正本: [architecture/web-content.md](../../../docs/architecture/web-content.md)、[spec/15-privacy-security.md](../../../docs/spec/15-privacy-security.md)。
