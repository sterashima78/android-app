---
type: module
title: Audio Data：オフラインTTSとMedia Session
description: 音声本文解決、逐次準備、cacheとMedia3 serviceの寿命を説明する。
tags: [audio, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-f83341fcf097c74224e3e350
    resource: repo://feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackController.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Audio Data：オフラインTTSとMedia Session

`:feature:audio:data` は次の責務を持ちます。

## 本文から再生への流れ

DefaultAudioPlaybackControllerはapplication-scopeで共有する再生実装です。項目に読み上げ本文があれば直接使用し、なければSummaryReaderで保存済み要約を探します。未生成なら既存SummaryRequesterへ依頼し、限定時間だけ結果を待ちます。Summaryのtableを読む別経路は追加せず、利用可能な本文だけ音声準備へ進めます。

読み上げ本文はタイトルとMarkdownを正規化した本文を連結し、インストール済みのオフライン日本語TTSでファイル化します。本文・ID・正規化versionからcacheキーを作るため、本文が変化すると古い音声を再利用しません。TTS初期化失敗や利用可能な日本語音声がない場合は明示的なエラーになります。cacheは再生成可能なファイルです。

## 逐次準備と実行寿命

最初の音声完成時にMediaControllerへ項目を設定して再生を始め、後続の音声は順番に準備して末尾へ追加します。全件完成を開始条件にしないため、長いキューでも先頭から聴けます。音声ファイルが一つも準備できない場合は失敗になり、本文が解決できない項目は除外されます。AudioPlaybackServiceがExoPlayerとMediaSessionを所有し、通知やmedia操作へ接続します。逐次準備とオフライン音声選別はunit testで確認し、service継続や通知操作はAndroid上で確認してください。

## 調査と変更の入口

[DefaultAudioPlaybackController.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackController.kt)、[AudioPlaybackService.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/AudioPlaybackService.kt)、[SpeechTextNormalizer.kt](../../../feature/audio/data/src/main/kotlin/dev/terashima/yomitorirss/feature/audio/data/SpeechTextNormalizer.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultAudioPlaybackControllerTest.kt](../../../feature/audio/data/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackControllerTest.kt)、[SpeechTextNormalizerTest.kt](../../../feature/audio/data/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/data/SpeechTextNormalizerTest.kt) です。

[audio domain](audio-domain.md)、[audio ui](audio-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Audio Playback](../../../docs/architecture/audio-playback.md)です。
