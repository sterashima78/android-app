---
type: module
title: Audio Domain：共有再生キューと操作
description: 要約とPodcastから使う再生キュー、状態、操作の共有契約を説明する。
tags:
  - audio
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-c50e553f8a523bc319609279
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/audio/AppAudioRuntimeDependencies.kt
  - id: openwiki-source-97e65664979c720b30363f97
    resource: repo://feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Audio Domain：共有再生キューと操作

`:feature:audio:domain` は共有音声再生の JVM 契約です。キュー入力、準備と再生の表示状態、操作 capability を定義し、状態の実際の所有者は注入される controller 実装です。Summary と Podcast の UI が利用し、Data が TTS/Media3 の具象を実装します。


## 主要な構成要素

| 型・関数 | 種類 | 役割と関係 | 実装 |
| --- | --- | --- | --- |
| `AudioQueueItem` | data class | ID・タイトル・source・任意のspeechTextを持つキュー入力。 | [AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) |
| `normalizeAudioQueue` | トップレベル関数 | contentIdの重複を除き、最初の項目と順序を保持する。 | [AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) |
| `AudioPreparationStatus` | enum | IDLE/PREPARING/READY/FAILEDで準備段階を区別する。 | [AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) |
| `AudioPlaybackState` | data class | キュー・現在index・再生位置/長さ/速度・準備件数・messageをまとめる。currentItemは範囲外ならnull。 | [AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) |
| `AudioPlaybackController` | interface | stateを公開しplay/toggle/skip/seek/speed/stopを定義する。Dataが実装する。 | [AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) |


## 主要 API と具象接続

`AudioPlaybackController.play(items)` がキュー開始、`togglePlayPause()` が再生切替、`skipNext()` / `skipPrevious()` が項目移動、`seekBy(deltaMs)` が相対位置変更、`setPlaybackSpeed(speed)` が速度変更、`stop()` が終了の契約です。戻り値で成否を返す API ではなく、画面は `state` を観測します。`AudioQueueItem.speechText` は任意の直接本文、`normalizeAudioQueue` は純粋な重複排除関数です。

具象は [DefaultAudioPlaybackController](audio-data.md)。[AppAudioRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/audio/AppAudioRuntimeDependencies.kt) が SummaryReader/SummaryRequester と Application を渡して一つの controller を構成し、[AppRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt) と Podcast の factory へ渡します。Domain の契約自身は速度の具体範囲や media resource 解放手順を定めません。

## 代表的な再生フロー

1. 呼び出し側が可視順の `AudioQueueItem` を `AudioPlaybackController.play` へ渡します。
2. Data実装が `normalizeAudioQueue` で重複を除き、キューと `PREPARING` を `AudioPlaybackState` に反映します。
3. Dataの本文解決・音声準備で再生可能な項目ができると `READY` と現在項目/位置を更新します。通常の準備失敗は `FAILED` とmessageへ戻ります。
4. 呼び出し側が `state` を購読して [AudioPlayerControls](audio-ui.md) に渡し、操作callbackを同じcontrollerへ戻します。

## 再生の公開境界

AudioQueueItem、AudioPlaybackState、AudioPlaybackControllerを通して、再生UIと端末音声実装を分離します。キュー項目にはコンテンツID、表示タイトル、取得元と任意の読み上げ本文があります。本文を渡せるため、保存済み要約だけでなくPodcastの生成済み章を同じ再生基盤へ渡せます。DomainはTTSエンジン、Media3、ファイル保存を実装しません。

再生開始時のキュー正規化はcontentIdで重複を除き、最初に現れた項目と順序を保ちます。画面が渡した可視順序を再生へ伝える際に、重複IDが独立した音声として繰り返されることを防ぐ契約です。現在項目はインデックスが有効な場合だけ返し、準備前や空キューではnullになります。

## 状態と変更の調査

StateFlowで準備状態、再生中か、位置、長さ、速度、準備件数とメッセージを公開します。IDLE、PREPARING、READY、FAILEDは準備と再生操作の表示に使う状態で、記事の既読状態や要約生成キューの状態を表すものではありません。controllerは前後移動、相対seek、速度変更、停止を公開しますが、再生履歴の永続化APIを持ちません。変更時は重複排除と範囲外の現在項目をDomain testで確認し、実際のキャンセルや音声準備の動作はData側の検証へ進みます。

## 調査と変更の入口

[AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [AudioPlaybackTest.kt](../../../feature/audio/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlaybackTest.kt) です。

[audio data](audio-data.md)、[audio ui](audio-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Audio Playback](../../../docs/architecture/audio-playback.md)です。
