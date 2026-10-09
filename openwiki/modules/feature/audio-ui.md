---
type: module
title: Audio UI：準備と再生の共通コントロール
description: 共通の再生操作表示とAudioPlaybackStateの扱いを説明する。
tags:
  - audio
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-6dadfa8e868236ab5e695b8b
    resource: repo://feature/audio/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/ui/AudioPlayerControls.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Audio UI：準備と再生の共通コントロール

`:feature:audio:ui` は Domain の再生状態と操作callbackを受け取る共通Compose部品です。Summary/Podcastの表示に利用され、独自のcontroller・再生キュー・永続状態を所有せず、Audio Domainとdesignsystemへ依存します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `AudioPlayerControls`（関数） | state と callback を入力する共通 Compose コントロール。時間・速度の整形は内部補助。 | [AudioPlayerControls.kt](../../../feature/audio/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/ui/AudioPlayerControls.kt) |


## 公開関数と呼び出しフロー

`AudioPlayerControls(state, onTogglePlayPause, onPrevious, onNext, onSeekBack, onSeekForward, onSpeedChange, onStop, modifier)` が唯一の公開 Compose 関数です。UI から seek のミリ秒量を渡す設計ではなく、前後 seek callback の具体量を呼び出し元が決めます。`formatDuration` / `formatSpeed` は表示文字列だけを作る private helper です。

[PodcastRouteWithPlayback](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRouteWithPlayback.kt) が再生 dialog の状態を購読し、[PodcastPlaybackDialog](../../../feature/podcast/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastPlaybackDialog.kt) がこの部品へ state と操作を渡します。タップから ViewModel、Audio controller へ戻り、その StateFlow の更新が再描画へ戻る循環です。

## 表示責務と利用方法

AudioPlayerControlsはAudioPlaybackStateと操作callbackだけを受け取るCompose部品です。要約再生とPodcast再生に共通の操作表示を提供し、Media3やTTS、Repositoryへ直接アクセスしません。呼び出し元がcontrollerのStateFlowを購読して状態を渡し、callbackをcontrollerへ接続することで同じ再生基盤を複数画面から操作できます。独立したViewModelや永続状態は持ちません。

IDLEなら何も表示せず、PREPARINGでは進捗と停止、FAILEDではエラーと閉じる操作、READYではタイトル・取得元・再生位置と操作を表示します。準備中の停止も受け付けるため、音声完成を待たずに呼び出し側の処理を止められます。エラー文字列はcontrollerが渡したmessageを優先し、ない場合だけ一般的な表示へ戻します。

## 操作と検証の境界

READYの進捗は長さが正なら位置を割合へ変換して範囲内へ丸め、長さ不明ならゼロを表示します。前後項目移動、再生・一時停止、相対seek、速度変更、終了はすべてcallbackです。UIのボタンだけを変えてもcontrollerのseekや速度制約は変更されません。このモジュールに直接のテストはありません。状態の意味はAudio Domain test、逐次音声準備はData testを参照し、UIを変更した際はPREPARING、FAILED、長さ不明、再生末尾の表示を利用画面で確認してください。

## 調査と変更の入口

[AudioPlayerControls.kt](../../../feature/audio/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/ui/AudioPlayerControls.kt) を入口に、呼び出し側と保存先を併せて追います。 このモジュール内に直接のテストはなく、連携先の検証を使います。

[audio domain](audio-domain.md)、[audio data](audio-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Audio Playback](../../../docs/architecture/audio-playback.md)です。

状態モデルの確認には[Audio Domain test](../../../feature/audio/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/AudioPlaybackTest.kt)を利用できます。
