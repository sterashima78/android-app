---
type: module
title: Settings Domain — AIモデル管理と接続設定の契約
description: ローカルモデル設定・download進捗・benchmark、ChatGPTモデル選択とdebug操作を説明する。
tags: [settings, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-ee916e8cc59dff2d6cb38a2a
    resource: repo://feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Settings Domain — AIモデル管理と接続設定の契約

`:feature:settings:domain`

## 設定境界の責務

Settings Domainはアプリの設定画面が利用するAIモデル管理、ChatGPT provider選択、接続確認の契約を所有する。設定のUIモデルをcore runtimeの具象型から分け、JVM moduleとしてAndroid componentやHTTP clientを公開しない。SummaryやKnowledgeの実行先選択そのものは各featureの契約であり、このDomainへまとめて移さない。

## ローカルモデルと計測

AiModelRepositoryはモデル一覧、download進捗、推論進捗、推論設定をFlowで公開する。model statusはdownload量と選択状態だけでなく、license、量子化、memory注意、thinking・speculative decodingの対応、context token数を持つため、画面が選択に必要な条件を表示できる。backendはCPU/GPU、context sizeはAUTOまたは明示サイズというDomain enumで扱う。

downloadModel、selectModel、deleteModelは要求の入口で、ファイル取得・検証・削除の正本はDataとcore runtimeへ委ねる。download進捗のisActiveはqueued/downloading/verifyingに限定され、completedやfailedを作業中と扱わない。benchmarkは通常とspeculativeの比較や、context別の時間・メモリ・安全性を返す。speculative計測のみの失敗もreportに残せる。

ChatGptProviderRepositoryは候補取得と選択済みIDの管理を提供し、モデルにWeb検索対応を保持する。ChatGptDebugRepositoryは接続状態、login、logout、明示推論テストの境界である。変更時はDataのmodel変換とSettings UIの選択条件を揃える。AiModelProgressTestはactiveなdownload段階の判定を検証する。

## 調査・変更の入口

- [AiModelRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt)
- [ChatGptProviderRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptProviderRepository.kt)
- [ChatGptDebugRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt)
- [AiModelProgressTest.kt](../../../feature/settings/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelProgressTest.kt)

関連モジュール: [settings-data](settings-data.md)、[settings-ui](settings-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
