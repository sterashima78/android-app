---
type: module
title: Summary UI — 非同期要約要求とレビュー表示
description: 要約ダイアログ、queue投入の通知、保存済み結果を待つレビュー状態を説明する。
tags:
  - summary
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-c326182dfa6898224aab7bca
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt
  - id: openwiki-source-5ef34b20493d1df075d7352e
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt
  - id: openwiki-source-9c6048de23f433bffabd4b0e
    resource: repo://feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Summary UI — 非同期要約要求とレビュー表示

`:feature:summary:ui`

## 責務と入口

Summary UIはSummaryViewModelと要約・prompt dialogを提供し、consumer画面からの記事操作を表示へ変換する。SummaryUiStateは対象Article、要約文字列、通知と別のreview状態を持つ。Repositoryをconstructorで受け取り、DB保存や推論adapterを画面へ持ち込まない。article domainへの依存は対象記事を表示・識別するためで、具体的な記事Data実装は使わない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [SummaryDialog](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryDialog.kt) | `SummaryDialog`（Composable）は対象Article、結果textと閉じるcallbackを表示する。推論や永続化の寿命を所有しない。 |
| [SummaryProgressLabels](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryProgressLabels.kt) | `summaryProgressLabel(stage,modelName)`（トップレベル関数）は取得、モデル準備、chunk/reduction/final、cloud生成の保存stageを日本語ラベルへ変換する。 |
| [SummaryPromptDialog](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPromptDialog.kt) | `SummaryPromptDialog`（Composable）は現在promptを編集し、保存・reset・dismissを外側callbackへ渡す。 |
| [SummaryViewModel](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryViewModel.kt) | `SummaryReviewUiState`/`SummaryUiState`（data class）はレビューと通常dialogを別々に保持する。`SummaryViewModel`（class）の`summarize`は通常要求/refreshを選び結果を表示へ変換する。`prepareReview`/`retryReview`は要約を要求して保存済み結果を待ち、`stopReview`は待機jobを取消す。`dismissSummary`/`dismissMessage`は表示を閉じる。`Factory`がSummaryRepositoryを注入する。 |

## 公開APIと構成要素間の接続

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)が`SummaryExecutionPreferences`、`SummaryPromptStore`、`ChatGptSummaryCloudInference`を各Domain契約に接続し、`DefaultSummaryRepository`へdatabase・local model reader・execution settings・cloud inferenceを渡す。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`summaryViewModelFactory`がこのRepositoryをUIへ注入する。Workerは`SummaryWorkerFactory`から`SummaryRuntimeDependencies`のContent/Curation契約を受け取る。

## 要求結果の表示

summarizeは通常要求かタグ置換付きの補完refreshを選ぶ。タグ置換はforceRefreshを伴わなければ拒否する。Cachedはその場で要約dialogの本文へ反映し、Processingは背景処理中、PreviousFailureは前回失敗、Enqueuedは受理と重複の違いを通知する。enqueueが受理されたことを完成結果として表示せず、生成の寿命はData側のqueueへ委ねる。

reviewは通常dialog状態とは別に対象IDとloading/error/textを保持する。prepareReviewは同じ記事の結果・進行中job・エラーが既にあれば重複開始を避け、retryReviewはrefreshでやり直す。保存済み結果を待つjobはstopReviewでcancelできる。この取消は表示の待機を終了するもので、Repositoryの背景taskを取り消すcommandはここでは呼ばない。

通知・結果dialogの閉じ方はViewModel、prompt編集はSummaryPromptDialog、進捗の文言はSummaryProgressLabelsから変更する。SummaryUiStateTestは対象も結果も持たない初期状態、SummaryProgressLabelsTestは段階の表示規則を確認する。Workerやqueueの失敗規則はUI文言だけで判断せず、DataとDomainの状態を併せて確認する。

## 調査・変更の入口

- [SummaryViewModel.kt](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryViewModel.kt)
- [SummaryDialog.kt](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryDialog.kt)
- [SummaryPromptDialog.kt](../../../feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPromptDialog.kt)
- [SummaryUiStateTest.kt](../../../feature/summary/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryUiStateTest.kt)
- [SummaryProgressLabelsTest.kt](../../../feature/summary/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryProgressLabelsTest.kt)

関連モジュール: [summary-domain](summary-domain.md)、[summary-data](summary-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
