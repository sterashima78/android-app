---
type: module
title: Settings Data — core AI管理とbackground downloadの適合
description: core runtimeとの設定変換、download jobの永続進捗、ChatGPT候補の選別を説明する。
tags: [settings, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-d374fe9508c2bce8e4cdc3c4
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt
  - id: openwiki-source-4e060f30e928b86aed720ff0
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt
  - id: openwiki-source-177833fd15f2f3ee9ca87586
    resource: repo://feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Settings Data — core AI管理とbackground downloadの適合

`:feature:settings:data`

## model管理への接続

DefaultAiModelRepositoryはLocalModelManagerのmodel・推論設定・進捗をSettings Domainへ変換する。download済みのモデルへの要求は一覧をrefreshして終了し、未取得modelはAiModelDownloadSchedulerへ登録する。選択と削除はcore managerへ委譲するので、artifact検証や推論engineの解放をSettingsに第二実装として持たない。

benchmarkは共通LocalAiBackgroundTaskGateのHIGH permitを取得し、IOでcoreのbenchmark runnerを呼ぶ。結果はSettings専用の比較・context reportへ変換する。UIから計測要求が来ても、実行競合やcoreのmodel lifecycleを迂回しない。

## background取得とprovider設定

AiModelDownloadBackgroundはJobSchedulerのuser-initiated jobを使い、model IDと期待sizeで取得を予約する。進捗はapp-private SharedPreferencesへ記録し、callbackFlowは初期値を返した後にlistenerで変化を通知する。画面のFlow購読終了時はlistenerを解除する。download完了の進捗をRepositoryが観測するとmanagerの一覧をrefreshする。network制約・通知・retry可能な失敗の判定はこのjob/serviceを追って確認する。

DefaultChatGptProviderRepositoryはcore cloud clientからcatalogを得て、picker表示対象・API対応・Web検索対応をすべて満たす候補だけを公開する。保存済み選択IDがその一覧から消えた場合は選択を解除する。選択の永続化はcoreのmodel preferencesへ委譲し、接続debugもcore cloud adapterへ接続する。DefaultChatGptProviderRepositoryTestは候補のfilterと不正な保存済み選択の解除を検証する。Downloadやbenchmarkの詳細を変えるときはSettingsのadapterとcore runtimeの所有境界を併せて確認する。

## 調査・変更の入口

- [DefaultAiModelRepository.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt)
- [AiModelDownloadBackground.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/AiModelDownloadBackground.kt)
- [DefaultChatGptProviderRepository.kt](../../../feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepository.kt)
- [DefaultChatGptProviderRepositoryTest.kt](../../../feature/settings/data/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultChatGptProviderRepositoryTest.kt)

関連モジュール: [settings-domain](settings-domain.md)、[settings-ui](settings-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
