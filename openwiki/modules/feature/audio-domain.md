---
type: module
title: Audio Domain：共有再生キューと操作
description: 要約とPodcastから使う再生キュー、状態、操作の共有契約を説明する。
tags: [audio, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-97e65664979c720b30363f97
    resource: repo://feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Audio Domain：共有再生キューと操作

`:feature:audio:domain` は次の責務を持ちます。

## 再生の公開境界

AudioQueueItem、AudioPlaybackState、AudioPlaybackControllerを通して、再生UIと端末音声実装を分離します。キュー項目にはコンテンツID、表示タイトル、取得元と任意の読み上げ本文があります。本文を渡せるため、保存済み要約だけでなくPodcastの生成済み章を同じ再生基盤へ渡せます。DomainはTTSエンジン、Media3、ファイル保存を実装しません。

再生開始時のキュー正規化はcontentIdで重複を除き、最初に現れた項目と順序を保ちます。画面が渡した可視順序を再生へ伝える際に、重複IDが独立した音声として繰り返されることを防ぐ契約です。現在項目はインデックスが有効な場合だけ返し、準備前や空キューではnullになります。

## 状態と変更の調査

StateFlowで準備状態、再生中か、位置、長さ、速度、準備件数とメッセージを公開します。IDLE、PREPARING、READY、FAILEDは準備と再生操作の表示に使う状態で、記事の既読状態や要約生成キューの状態を表すものではありません。controllerは前後移動、相対seek、速度変更、停止を公開しますが、再生履歴の永続化APIを持ちません。変更時は重複排除と範囲外の現在項目をDomain testで確認し、実際のキャンセルや音声準備の動作はData側の検証へ進みます。

## 調査と変更の入口

[AudioPlayback.kt](../../../feature/audio/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlayback.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [AudioPlaybackTest.kt](../../../feature/audio/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlaybackTest.kt) です。

[audio data](audio-data.md)、[audio ui](audio-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Audio Playback](../../../docs/architecture/audio-playback.md)です。
