---
type: integration
title: 音声・Podcast・動画と SMB
description: Audio / Podcast / Video / Library の関係、再生の lifetime、source と credential の境界を説明する。
tags: [audio, podcast, video, smb]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T11:54:35.312Z
sources:
  - id: openwiki-source-010fff5c685533259d1bbdca
    resource: repo://docs/architecture/audio-playback.md
  - id: openwiki-source-735f02f4cb4a910668807f83
    resource: repo://docs/architecture/context-map.md
  - id: openwiki-source-b12cdd7bece87af45ebfc4fe
    resource: repo://docs/architecture/podcast.md
  - id: openwiki-source-872141f77f71851168245852
    resource: repo://docs/architecture/system-overview.md
  - id: openwiki-source-13f839442112f4a339e4f04e
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt
  - id: openwiki-source-c49c46b506f7a8080d70bc85
    resource: repo://feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSource.kt
  - id: openwiki-source-3db1c7676e7ad19da0c7bfca
    resource: repo://feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSourceTest.kt
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# 音声・Podcast・動画と SMB

Audio、Podcast、Video は媒体を扱うが、生成・再生・catalog の owner は別である。[全体構成](../architecture/system.md) と [Context Map](../../docs/architecture/context-map.md) を踏まえ、必要な capability を再利用する。

## Audio の処理と lifetime

Audio はキュー、操作、TTS cache、media session を所有する。記事の identity / read state、Bookmark membership、保存済み要約やその生成 task は所有しない。要約は SummaryReader / SummaryRequester から取得する。

読み上げテキストを Android TTS でファイル化し、MediaSessionService / ExoPlayer に渡す。最初の音声ができた時点で再生を始め、後続音声を順次追加する。再生完了や skip を Content の既読化へ変換しない。キュー・位置・速度は durable user state に保存せず、TTS ファイルも再生成可能な cache とする。

foreground media service はユーザーが開始した再生の platform runtime。WorkManager の AI queue とは lifetime が異なる。詳細と実機確認項目は [Audio Playback](../../docs/architecture/audio-playback.md)、focused test は [DefaultAudioPlaybackControllerTest](../../feature/audio/data/src/test/kotlin/dev/terashima/yomitorirss/feature/audio/data/DefaultAudioPlaybackControllerTest.kt) を参照する。

## Podcast の source は独立する

Podcast は source、番組、episode、snapshot、consumed state、schedule を所有し、RSS reader の購読・既読 state に従属しない。候補の取得には URL ベースの RSS / Atom reader capability を使い、生成した原稿の TTS 再生は Audio に渡す。

[RssPodcastFeedContentSource](../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSource.kt) は PodcastSource を URL reader の source に適合し、entry を PodcastFeedEntry へ変換する。タイトルを NFKC・空白・大文字小文字で正規化して重複をまとめ、category を統合する。[focused test](../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSourceTest.kt) がこの変換を確認する。

生成・消費・除外・再試行の lifecycle は [Podcast Architecture](../../docs/architecture/podcast.md) と [形式モデル一覧](../../spec-models/README.md) へ進む。RSS reader を再利用するために購読 table や Content read state を共用しない。

## Video と Library の SMB 境界

Video は動画 catalog、保存・視聴状態、extractor、Video 用の share / root path を所有する。接続 profile と credential は Library が所有し、Video は [SmbMediaFileAccess](../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt) を通して list / random-access read を利用する。公開 contract は location と file access で、password を渡す API ではない。

Web 動画は page URL を durable identity とし、stream URL は再生時に解決する。foreground Video player は Audio の background service と別である。extractor と Cookie の境界を変更するときは [Video Architecture](../../docs/architecture/video.md) を確認する。

## 変更時の調査先

- queue / 再生順 / TTS cache: Audio の contract と controller test。
- Podcast の候補と重複: source adapter test と eligibility / clustering model。
- SMB file access: Library の公開 contract と Video adapter。
- DB や backup の対象: [データ所有権](../architecture/persistence.md)。
- 長時間生成、provider failure: [AI とバックグラウンド](../workflows/ai-background.md)。
