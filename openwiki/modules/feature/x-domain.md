---
type: module
title: "X Domain：CSSセットとJavaScript有効判定"
description: "3つのCSSセットの編集保持、独立したJavaScript設定と注入対象の判定を説明する。"
tags: [x, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-06a588811a00843f6375553e
    resource: repo://feature/x/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/x/XViewerCssSettings.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# X Domain：CSSセットとJavaScript有効判定

`:feature:x:domain` は専用X WebViewの表示カスタマイズ設定とRepository契約を定義するJVMモジュールである。CSSに加えてユーザーJavaScriptを扱うが、名称は`XViewerCssSettings`と`XViewerCssRepository`のまま維持されている。SharedPreferencesと実際のWebView実行は別層の責務である。

## CSS編集の保持

設定は3セットのCSS、選択index、現在の編集文字列とCSS有効状態を持つ。セット数とindexはconstructorで検証する。セット切替では編集中のcssを現在セットへ反映してから移動するため、未保存の編集を値モデル内で保持できる。別セットへのcopyはコピー先を上書きするが選択中のindexを変えない。`persistedCssSets()`は現在のcssを反映した保存用一覧を返す。

## 実行可否と境界

JavaScriptはCSSとは独立した有効flagと文字列を持ち、初期値は無効・空文字列である。`cssForInjection()`と`javaScriptForInjection()`はそれぞれ無効なら空文字列を返す。どのhostへ実行するか、scriptの副作用をいつ反映するかはUIが決め、Domainは文字列の安全解析やDOM推測を行わない。

Repositoryはload/save/defaultCssだけを提供する。`XViewerCssSettingsTest`はセット切替時の編集保持、copy、CSS/JavaScriptの有効判定とJavaScript状態の保持を確認する。設定値を拡張するときはDataの保存キーとUI editorを合わせて確認し、CSSセット切替でJavaScriptが意図せず変わらないようにする。

## 調査・変更の入口

[XViewerCssSettings](../../../feature/x/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/x/XViewerCssSettings.kt)、[XViewerCssSettingsTest](../../../feature/x/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/x/XViewerCssSettingsTest.kt) を起点に責務と呼び出し側を確認する。

関連: [x data](x-data.md) / [x ui](x-ui.md) / [全体構成](../../architecture/system.md)。
