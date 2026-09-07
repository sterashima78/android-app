# Audio Playback

この文書は、Mosaic の保存済み要約をポッドキャスト形式で連続再生する Audio capability の current architecture を示す。設計判断の履歴は [ADR-0235](../adr/0235-summary-audio-playback.md) を参照する。

## Ownership

Audio は再生キュー、再生操作、TTS音声cache、Android media sessionとの接続を所有する。

Audio は次を所有しない。

- Content の identity / title / read state
- Curation の Bookmark / Read Later membership
- Summary の保存済み要約 / task lifecycle
- 再生履歴や再生済みフラグの durable state

現在の module は次のとおり。

```text
:feature:audio:domain
:feature:audio:data
:feature:audio:ui
```

`:feature:audio:domain` は `AudioQueueItem`、`AudioPlaybackState`、`AudioPlaybackController` を所有する。

`:feature:audio:data` は Android `TextToSpeech`、Media3、`MediaSessionService` を利用する concrete implementation を所有する。Summary については `SummaryReader` / `SummaryRequester` の公開 contract だけを利用し、Summary table を直接参照しない。

`:feature:audio:ui` は再生中タイトル、進捗、再生 / 一時停止、前後項目移動、15秒戻し、30秒送り、速度変更、終了の表示と操作を所有する。

application-scope の `AudioPlaybackController` は `:app:composition` が生成し、`:app:presentation` が RSS の Read Later UI と接続する。

## Data flow

```text
RSS Read Later presentation
          |
          | current visible order
          v
    AudioQueueItem[]
          |
          v
AudioPlaybackController
          |
          +---- SummaryReader ------> saved summary
          |
          +---- SummaryRequester ---> existing Summary queue
          |
          v
 Markdown -> speech text normalization
          |
          v
Android TextToSpeech.synthesizeToFile
          |
          v
 app cache / summary-audio
          |
          v
Media3 MediaSessionService / ExoPlayer
          |
          +---- app controls
          +---- notification / lock screen
          +---- media buttons / Bluetooth
```

再生経路には Content の read-state command を渡さない。再生完了、skip、停止、queue完了のいずれでも既読・未読状態を変更しない。

## Queue and summary preparation

- RSS の「あとで読む」画面で現在表示されている並び順を再生開始時のqueue順とする。
- 同じ `contentId` が重複した場合は最初の項目だけを利用する。
- 保存済み要約がある場合はそのまま利用する。
- 保存済み要約がない場合は既存 `SummaryRequester` へ通常requestを出す。
- 生成中または新規enqueueされた要約は、保存済み結果が利用可能になるまで限定時間pollする。
- 要約取得に失敗した項目は再生対象から除外し、Audio独自の要約生成経路は追加しない。

## Progressive TTS preparation

要約解決後のTTS音声はqueue順に1件ずつ生成する。最初の再生可能な音声ファイルが完成した時点でMedia3のqueueへ設定して再生を開始し、後続項目は再生中も生成を継続する。後続の音声ファイルが完成するたびに現在のMedia3 queue末尾へ追加し、全件の音声生成完了を再生開始条件にはしない。

生成が再生速度に追いつかずMedia3 queueの末尾へ到達した場合は、次の音声が追加されるまで再生可能項目がない状態になる。停止や新しいqueueでの再生開始時には進行中のTTS生成も既存のcontroller jobとともにcancelする。

この先行生成はAudio capability内のprocess-local preparationであり、新しいdurable queue、Worker、schedulerは追加しない。

## Speech text normalization and TTS cache

読み上げ対象は記事タイトルと保存済み要約を連結したテキストとする。ただし保存済み要約は表示用MarkdownをそのままTTSへ渡さず、Audio内で読み上げ用プレーンテキストへ正規化する。

正規化では、見出し、箇条書き、引用、強調、取り消し線、inline code、link、reference link、tableなどのMarkdown構文記号を読み上げ対象から除き、ユーザーが読む本文やlink labelは維持する。Markdown以外の通常の文字列まで広く削除する変換にはしない。

Android `TextToSpeech.synthesizeToFile` を利用し、生成ファイルは app cache directory 配下の `summary-audio` へ保存する。

cache key はspeech cache format version、`contentId`、正規化後の最終読み上げテキストから生成する。これにより要約やタイトルが変化した場合だけでなく、読み上げ正規化ルールを変更した場合も古い音声cacheを再利用しない。cache は再生成可能であり、database、backup、exportの対象にしない。

初期実装ではTTS engineの最大入力長を超えるテキストは最大長までを読み上げ対象とする。長文分割・結合は別の改善として扱う。

## Background media runtime

`AudioPlaybackService` は `MediaSessionService` を継承し、service内にExoPlayerとMediaSessionを置く。

manifest boundary:

```text
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
foregroundServiceType="mediaPlayback"
```

これはWorkManagerやAI task queueとは別の、ユーザーが開始したforeground media playback runtimeである。Audio再生をWorkManager taskへ変換しない。

Audio playback自体による新しいnetwork通信はない。端末のTTS engineとlocal cache、Media3 playerを利用する。

## Lifetime and durability

- `AudioPlaybackController` はapplication-scopeで共有する。
- Player / MediaSessionは`MediaSessionService` lifetimeで保持する。
- queue / current position / playback speedをMosaicのdurable user stateとして保存しない。
- processやserviceが破棄された後の「前回の続きから復元」は初期実装の対象外とする。
- TTS音声cacheはdurable source of truthではない。

## Invariants

- AudioからContentのread stateを書き換えない。
- AudioからCurationのRead Later membershipを書き換えない。
- AudioからSummaryのtableを直接read/writeしない。
- Audio用のdurable tableを追加しない。
- Audio再生のためのcloud TTS / external data egressを暗黙に追加しない。
- foreground media service以外のbackground runtimeを複製しない。

## Verification

- `:feature:audio:domain` unit testでqueue order / deduplication / current item semanticsを検証する。
- `:feature:audio:data` unit testで最初の音声準備完了を後続音声の準備完了より先に再生開始へ渡せることを検証する。
- `:feature:audio:data` unit testでMarkdown表示構文が読み上げテキストから除去され、通常の本文記号は維持されることを検証する。
- Architecture verificationでmodule metadataとapp/presentation/composition境界を検証する。
- Android実機では、最初の音声完成時の即時再生、再生中の後続音声生成、連続再生、background継続、通知・lock screen・Bluetooth control、seek、速度変更、再生後もread stateが変化しないことを確認する。

## Sources

- [ADR-0235](../adr/0235-summary-audio-playback.md)
- [module-map.md](module-map.md)
- [context-map.md](context-map.md)
- [platform.md](platform.md)
- `feature/audio/`
- `app/composition/src/main/java/dev/terashima/yomitorirss/composition/audio/`
- `app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppRssNavGraph.kt`
