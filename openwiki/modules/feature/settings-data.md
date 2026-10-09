---
type: module
title: Settings Data — core AI管理とbackground downloadの適合
description: core runtimeとの設定変換、download jobの永続進捗、ChatGPT候補の選別を説明する。
tags:
  - settings
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-d374fe9508c2bce8e4cdc3c4
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt
  - id: openwiki-source-4e060f30e928b86aed720ff0
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt
  - id: openwiki-source-9f96bec152cb6336a5d47ba6
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptDebugRepository.kt
  - id: openwiki-source-177833fd15f2f3ee9ca87586
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Settings Data — core AI管理とbackground downloadの適合

`:feature:settings:data`

## model管理への接続

DefaultAiModelRepositoryはLocalModelManagerのmodel・推論設定・進捗をSettings Domainへ変換する。download済みのモデルへの要求は一覧をrefreshして終了し、未取得modelはAiModelDownloadSchedulerへ登録する。選択と削除はcore managerへ委譲するので、artifact検証や推論engineの解放をSettingsに第二実装として持たない。

benchmarkは共通LocalAiBackgroundTaskGateのHIGH permitを取得し、IOでcoreのbenchmark runnerを呼ぶ。結果はSettings専用の比較・context reportへ変換する。UIから計測要求が来ても、実行競合やcoreのmodel lifecycleを迂回しない。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [DefaultAiModelRepository](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt) | 公開class。AiModelRepository実装。managerのFlowをDomainへ変換し、設定、モデル管理、benchmarkと背景取得を仲介する。 |
| [AiModelDownloadStateStore](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt) / [AiModelDownloadScheduler](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt) | internal class群。SharedPreferences進捗Flowと単一download schedulingを担う。current/markQueued/markFailed/update、scheduleで状態とJobSchedulerを対応させる。 |
| [AiModelDownloadJobService](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt) / [RunningDownload](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt) / [AiModelDownloadNotifications](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt) | 公開JobServiceと内部状態・通知object。onStartJob/onStopJob/onDestroyでUIDT、model manager、通知、停止/再試行を管理する。 |
| [DefaultChatGptDebugRepository](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptDebugRepository.kt) | 公開class。device login一時mapとstatus、startLogin/pollLogin/logout、runInferenceによるcloud接続診断adapter。 |
| [DefaultChatGptProviderRepository](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt) | 公開class。model preferenceとcloud listModelsを結び付け、候補外になった選択を解除する。 |
| [selectChatGptProviderModels](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt) / [shouldClearSelectedChatGptModel](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt) | internal関数。picker表示・API利用・Web検索対応を満たす候補だけを公開し、選択維持の可否を判定する。 |

## 主要なAPI・構成要素の接続

`DefaultAiModelRepository.downloadModel`は未知IDを拒否し、取得済みならcatalog refreshだけ、未取得ならschedulerへ渡す。`AiModelDownloadScheduler.schedule`は同一modelの実行中要求を無視し、別modelの競合を拒否する。予約失敗はfailedへ更新する。初期化時にはJobSchedulerに存在しないactive進捗をfailedへ戻す。

`AiModelDownloadJobService.onStartJob`は背景取得policyを再確認し、許可がなければqueuedのまま再予約する。実行時には専用LocalModelManagerで進捗と通知を更新し、IO障害をqueued+retry、その他をfailedへ変換する。`onStopJob`はqueuedへ戻してjobをcancelし、再実行を要求する。finallyでmanagerをcloseする。

二種benchmarkは`LocalAiBackgroundTaskGate.withPermit(HIGH)`内のIOでruntime runnerを呼ぶ。`DefaultChatGptDebugRepository`は新しいlogin開始で古い一時sessionを消し、認証完了とlogout時にも削除する。token保存はcloud clientへ委譲する。[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)が三Repositoryの具体依存を接続する。


## background取得とprovider設定

AiModelDownloadBackgroundはJobSchedulerのuser-initiated jobを使い、model IDと期待sizeで取得を予約する。進捗はapp-private SharedPreferencesへ記録し、callbackFlowは初期値を返した後にlistenerで変化を通知する。画面のFlow購読終了時はlistenerを解除する。download完了の進捗をRepositoryが観測するとmanagerの一覧をrefreshする。network制約・通知・retry可能な失敗の判定はこのjob/serviceを追って確認する。

DefaultChatGptProviderRepositoryはcore cloud clientからcatalogを得て、picker表示対象・API対応・Web検索対応をすべて満たす候補だけを公開する。保存済み選択IDがその一覧から消えた場合は選択を解除する。選択の永続化はcoreのmodel preferencesへ委譲し、接続debugもcore cloud adapterへ接続する。DefaultChatGptProviderRepositoryTestは候補のfilterと不正な保存済み選択の解除を検証する。Downloadやbenchmarkの詳細を変えるときはSettingsのadapterとcore runtimeの所有境界を併せて確認する。

## 調査・変更の入口

- [DefaultAiModelRepository.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt)
- [AiModelDownloadBackground.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt)
- [DefaultChatGptProviderRepository.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt)
- [DefaultChatGptProviderRepositoryTest.kt](../../../feature/settings/data/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepositoryTest.kt)

関連モジュール: [settings-domain](settings-domain.md)、[settings-ui](settings-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
