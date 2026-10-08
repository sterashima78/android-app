---
type: module
title: "X Data：端末内カスタマイズ設定の保存"
description: "SharedPreferencesによるCSS3セットとJavaScriptの保存、初期CSSとindex補正を説明する。"
tags: [x, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-b8bcee87e6a3b85a9f3413fe
    resource: repo://feature/x/data/src/main/kotlin/dev/terashima/yomitorirss/feature/x/data/SharedPreferencesXViewerCssRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# X Data：端末内カスタマイズ設定の保存

`:feature:x:data` は`SharedPreferencesXViewerCssRepository`でX Domainの設定契約を実装する。Android libraryで、他featureやUIへ依存しない。端末内private SharedPreferencesと同梱assetを使う保存adapterであり、WebView、network、DOMの操作は行わない。

## 初回読込と保存

設定はapplication contextの`x_viewer_preferences`へ保存する。CSSの有効状態、選択index、3セットそれぞれの文字列、JavaScriptの有効状態と文字列を別keyで保持する。未保存の最初のCSSセットにはassetの`x_viewer.css`を読み、それ以外の未保存セットは空文字列にする。CSSは初期状態で有効、JavaScriptは無効・空文字列になる。保存されたindexが範囲外なら有効な範囲へ補正する。

saveではDomainの`persistedCssSets()`を使って現在編集中のCSSも含む一覧を保存し、editor.apply()で反映する。default CSSの読込はlazyで、providerを注入できる。保存済みの空文字列は未保存とは区別されるため、ユーザーがCSSを空にしてもassetへ戻らない。

## 確認と変更の影響

`SharedPreferencesXViewerCssRepositoryTest`は初回default、CSSセットと有効状態の再読込、JavaScript設定の再読込を確認する。key名や初期値を変更すると既存ユーザーの挙動に影響するため、Domain設定のset数とeditorの保存フローを合わせて確認する。script実行可否や外部hostへの遷移判定をこの保存adapterへ移さない。

## 調査・変更の入口

[SharedPreferencesXViewerCssRepository](../../../feature/x/data/src/main/kotlin/dev/terashima/yomitorirss/feature/x/data/SharedPreferencesXViewerCssRepository.kt)、[SharedPreferencesXViewerCssRepositoryTest](../../../feature/x/data/src/test/kotlin/dev/terashima/yomitorirss/feature/x/data/SharedPreferencesXViewerCssRepositoryTest.kt) を起点に責務と呼び出し側を確認する。

関連: [x domain](x-domain.md) / [x ui](x-ui.md) / [全体構成](../../architecture/system.md)。
