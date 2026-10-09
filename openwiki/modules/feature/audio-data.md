---
type: module
title: Audio Data：オフラインTTSとMedia Session
description: 音声本文解決、逐次準備、cacheとMedia3 serviceの寿命を説明する。
tags:
  - audio
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-f83341fcf097c74224e3e350
    resource: repo://feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackController.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Audio Data：オフラインTTSとMedia Session

`:feature:audio:data` は共有音声再生契約の Android 実装です。controller が準備job・TTS・位置観測stateを所有し、Service がplayer/sessionを所有します。Summaryの公開能力へ依存し、compositionでApplicationに結び付けられた一つのcontrollerを複数UIが利用します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `AudioPlaybackService`（class） | Android が生成する MediaSessionService。player/session を生成し破棄時に解放する。 主なメソッド: `onCreate`、`onGetSession`、`onDestroy`。 | [AudioPlaybackService.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/AudioPlaybackService.kt) |
| `DefaultAudioPlaybackController`（class）、`prepareProgressively`（関数）、`isInstalledOfflineJapaneseVoice`（関数） | Summary 公開能力から本文を解決し、TTS ファイル生成と MediaController 操作を連携する実装。 主なメソッド: `play`、`togglePlayPause`、`skipNext`、`skipPrevious`、`seekBy`、`setPlaybackSpeed`、`stop`。 | [DefaultAudioPlaybackController.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackController.kt) |
| `markdownToSpeechText`（関数） | Markdown の装飾・引用リンク・URL を読み上げ用テキストへ正規化する補助処理。 | [SpeechTextNormalizer.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/SpeechTextNormalizer.kt) |


## API と状態更新の具体経路

`play` は空キューなら現状態を変えずに戻り、有効キューなら以前の準備・位置更新 job と TTS を停止し、player のキューを消して `PREPARING` にします。`resolveSpeechTexts` → `synthesize` → `prepareProgressively` → `ensureMediaController` が本文から player への経路です。`prepareProgressively` は null の準備結果を飛ばし、準備成功のたびに callback と成功件数を返します。合成自体が例外なら呼び出し元の失敗処理へ伝わります。

`skipPrevious` は前の項目がなければ現在項目の先頭へ戻ります。`seekBy` は負の位置をゼロへ、長さが既知なら終端へ丸めます。`setPlaybackSpeed` は実装の許容範囲へ丸め、controller 未接続でも state に速度を反映します。`stop` は準備・位置 job と TTS を停止し、media items を消して controller を release、state を初期状態へ戻します。TTS インスタンスと再生成可能な cache ファイルの寿命は controller release と同一ではありません。

player listener と `startPositionUpdates` が `syncPlayerState` を通じて index・再生中・位置・長さ・速度を同期します。Android-created `AudioPlaybackService.onCreate` が ExoPlayer/MediaSession を生成し、`onGetSession` で接続、`onDestroy` が両者を解放します。

## 本文から再生への流れ

DefaultAudioPlaybackControllerはapplication-scopeで共有する再生実装です。項目に読み上げ本文があれば直接使用し、なければSummaryReaderで保存済み要約を探します。未生成なら既存SummaryRequesterへ依頼し、限定時間だけ結果を待ちます。Summaryのtableを読む別経路は追加せず、利用可能な本文だけ音声準備へ進めます。

読み上げ本文はタイトルとMarkdownを正規化した本文を連結し、インストール済みのオフライン日本語TTSでファイル化します。本文・ID・正規化versionからcacheキーを作るため、本文が変化すると古い音声を再利用しません。TTS初期化失敗や利用可能な日本語音声がない場合は明示的なエラーになります。cacheは再生成可能なファイルです。

## 逐次準備と実行寿命

最初の音声完成時にMediaControllerへ項目を設定して再生を始め、後続の音声は順番に準備して末尾へ追加します。全件完成を開始条件にしないため、長いキューでも先頭から聴けます。音声ファイルが一つも準備できない場合は失敗になり、本文が解決できない項目は除外されます。AudioPlaybackServiceがExoPlayerとMediaSessionを所有し、通知やmedia操作へ接続します。逐次準備とオフライン音声選別はunit testで確認し、service継続や通知操作はAndroid上で確認してください。

## 調査と変更の入口

[DefaultAudioPlaybackController.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackController.kt)、[AudioPlaybackService.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/AudioPlaybackService.kt)、[SpeechTextNormalizer.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/SpeechTextNormalizer.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultAudioPlaybackControllerTest.kt](../../../feature/audio/data/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackControllerTest.kt)、[SpeechTextNormalizerTest.kt](../../../feature/audio/data/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/data/SpeechTextNormalizerTest.kt) です。

[audio domain](audio-domain.md)、[audio ui](audio-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Audio Playback](../../../docs/architecture/audio-playback.md)です。
