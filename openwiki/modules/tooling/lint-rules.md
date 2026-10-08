---
type: module
title: MainActivity の feature 境界を検査する lint
description: 独自 Android lint issue により executable Activity の feature ViewModel / concrete Data 依存を検出する。
tags: [lint, architecture, tooling, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-9ec36104d5be13a6b6e4403c
    resource: repo://lint-rules/src/main/kotlin/dev/terashima/yomitorirss/lint/MainActivityFeatureBoundaryDetector.kt
  - id: openwiki-source-cb8029667465f673a3e3874a
    resource: repo://lint-rules/src/test/kotlin/dev/terashima/yomitorirss/lint/MainActivityFeatureBoundaryDetectorTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# MainActivity の feature 境界を検査する lint

## build 時の architecture 制約

`:lint-rules` は Android runtime の機能ではなく JVM の lint plugin である。[YomitoriIssueRegistry](../../../lint-rules/src/main/kotlin/dev/terashima/yomitorirss/lint/YomitoriIssueRegistry.kt) と META-INF service 登録が custom issue を Android lint に公開し、[MainActivityFeatureBoundaryDetector](../../../lint-rules/src/main/kotlin/dev/terashima/yomitorirss/lint/MainActivityFeatureBoundaryDetector.kt) が UAST の import statement を走査する。app の lintChecks から使用され、MainActivityFeatureBoundary issue は CORRECTNESS の ERROR として報告する。

## 検出する依存と範囲

対象 file 名が MainActivity.kt のときだけ、feature package からの ViewModel import と `.data.` concrete implementation import を拒否する。framework が生成する Activity から feature を直接構築せず、[composition](../application/composition.md) と [presentation](../application/presentation.md) の狭い注入契約へ接続するための規則である。alias import でも importReference が元の名称を返すので検出対象になる。別 Activity file や app shell 自身の ViewModel import はこの detector の拒否対象ではない。

この detector は import に対する局所的検査であり、module 全体の dependency graph や feature の table ownership までは判断しない。より広い依存制約は verifyArchitecture、presentation boundary task、source architecture tests と組み合わせる。実行時 state や永続化は持たず、lint invocation が source を評価して diagnostic を出すだけである。

## 規則を変えるとき

[MainActivityFeatureBoundaryDetectorTest](../../../lint-rules/src/test/kotlin/dev/terashima/yomitorirss/lint/MainActivityFeatureBoundaryDetectorTest.kt) は通常・alias の feature ViewModel、concrete Data の拒否、app shell ViewModel の許可、別 Activity の除外を検証する。規則を広げる場合は issue explanation、対象 import、正例・負例を同じ変更で揃える。既存違反を suppress して通す前に、app の注入境界で同じ目的を満たせるかを確認する。

仕様・設計の正本: [architecture/principles.md](../../../docs/architecture/principles.md)、[architecture/testing.md](../../../docs/architecture/testing.md)。
