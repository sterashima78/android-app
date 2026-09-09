# ADR-0235: 保存済み要約を端末内TTSでポッドキャスト形式に連続再生する

- Status: Accepted
- Date: 2026-09-07
- Refines: [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0150](0150-app-shell-navigation-ui-ownership.md), [ADR-0200](0200-app-composition-module-boundary.md)
- Refined by: [ADR-0247](0247-news-podcast-context.md)

## Context

Mosaic は RSS / Bookmark / Summary を端末内で整理し、保存済み要約を持つ。ユーザーは記事を画面で読むだけでなく、移動中などに複数記事の要約をポッドキャストのように連続して聴きたい。

要約は Summary Context が所有し、記事 metadata / reading state は Content Context が所有する。音声再生を理由に Summary や Content の ownership を広げたり、再生完了を既読化へ結び付ける必要はない。

Android の background media playback は `MediaSessionService` 内に Player と MediaSession を置く構成が標準であり、foreground media playback service の宣言が必要になる。読み上げ音声は端末の `TextToSpeech` を利用し、クラウド音声生成や新しい外部通信を追加しない。

## Decision

- `:feature:audio:{domain,data,ui}` を追加し、要約音声再生を独立した delivery capability として所有する。新しい Domain Context は追加しない。
- Audio は Summary の公開 read/request contract を利用し、各 Context の table を直接参照しない。Content / Curation からは presentation が最小の queue item へ投影して渡す。
- 初期対象は「あとで読む」の記事群とし、再生開始時に対象記事をキュー化する。
- 保存済み要約がある記事を読み上げる。要約が未生成の場合は既存 Summary requester へ要求し、生成待ちの項目は再生可能になった時点で扱う。Audio 独自の要約生成は持たない。
- 読み上げは Android `TextToSpeech.synthesizeToFile` で app cache 内の一時音声ファイルへ生成し、Media3 ExoPlayer で再生する。
- Player と MediaSession は `MediaSessionService` に置き、バックグラウンド、通知、ロック画面、Bluetooth 等の media control を有効にする。
- manifest に `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` と `mediaPlayback` foreground service type を追加する。
- Media3 は安定版 1.11.0 を利用する。
- 再生キュー、再生位置、生成音声は durable source of truth にしない。音声ファイルは再生成可能な cache とする。
- 再生開始、再生完了、スキップ、停止、キュー完了のいずれでも Content の既読 / 未読状態を変更しない。Curation の Bookmark / Read Later 状態も変更しない。
- 再生速度、前後シーク、前後項目移動を Player capability として提供する。
- cloud TTS、音声ファイルのバックアップ、履歴永続化、再生済みフラグは初期実装の対象外とする。

## Change Impact Brief

### Goal

保存済みRSS要約を複数件キューに積み、画面を閉じても連続して聴けるようにする。再生は記事の既読状態から独立させる。

### Existing capability

- Content が記事 identity / title / reading state を所有する。
- Curation が Bookmark / Read Later membership を所有する。
- Summary が保存済み要約と生成 request を所有する。
- application-scope concrete graph は `:app:composition` が所有する。

Evidence:
- `docs/architecture/context-map.md`
- `feature/article/domain/`
- `feature/bookmark/domain/`
- `feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRepository.kt`
- `app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt`

### Affected system areas

Capabilities:
- Audio playback delivery capability を追加。

Contexts:
- Content / Curation / Summary の Context と ownership は変更しない。Audio は Domain Context ではなく delivery capability とする。

Modules:
- `:feature:audio:domain`
- `:feature:audio:data`
- `:feature:audio:ui`
- `:app:composition`
- `:app:presentation`

Persistence:
- unchanged。新規table / migrationなし。

Background runtime:
- MediaSessionService を追加。WorkManager / durable queue は変更しない。

External / trust boundary:
- 新規ネットワーク通信なし。
- foreground media playback service permission を追加。

### Boundaries that must not change

- Audio は Content / Curation / Summary の durable table を直接 read/write しない。
- Audio module は Content の read-state command や Curation mutation capability に依存しない。
- 再生操作は reading state / Bookmark / Read Later membership を変更しない。
- 再生キューを新しい durable source of truth にしない。
- TTS 音声を backup / export 対象にしない。

### Options

#### Option A: Audio feature + MediaSessionService + TTS cache + Media3

利点:
- Android の標準 media control と background playback を利用できる。
- 音声生成と再生責務を Summary から分離できる。
- 将来、要約以外の読み上げ source も同じ playback capability へ接続できる。

欠点:
- 新しい feature と Android Service、Media3 dependency が増える。

増える概念 / 状態 / 依存:
- Audio queue model、TTS synthesizer、MediaSessionService、Media3。

削除コスト:
- feature modules、service declaration、dependency を削除すればよく migration は不要。

#### Option B: Summary UI から TextToSpeech.speak を直接連続実行

利点:
- 実装量が少ない。

欠点:
- Activity / UI lifecycle と再生 lifecycle が結合する。
- media controls、playlist、seek、background playback の標準機構を活用しにくい。
- Summary が presentation / playback responsibility を抱える。

### Recommendation

Option A を採用する。既存 ownership を保ち、Android の標準 background media architecture を利用できるため。

### System delta

New concepts:
- Audio playback delivery capability
- process-local playback queue
- TTS audio cache
- MediaSessionService

Removed concepts:
- none

New durable state:
- none

New dependencies:
- AndroidX Media3 1.11.0

New external communication:
- none

Changed data flow:
- `Content/Curation/Summary -> UI` に加え、`Content/Curation presentation projection -> Audio -> Summary contract -> TTS cache -> Media3 playback` を追加。

### Compatibility / migration / rollback

- DB schema / backup format / worker identity は変更しない。
- rollback は Audio modules、service、Media3 dependency、UI entry point を削除するだけでよい。

### Verification plan

Unit:
- queue order / duplicate elimination / current item semanticsを検証する。

Integration / architecture:
- `verifyArchitecture`。
- Audio Data が Summary Domain contractだけをfeature間依存として利用し、Content/CurationのDataやmutation contractを参照しないことをdiffとdependency graphで確認する。

Android / E2E:
- MediaSessionService 起動、background継続、通知 control、Bluetooth/media button、速度変更、skip/seek、再生後もread stateが変わらないことを実端末で確認対象とする。

Public repository review:
- user content、生成音声、実URL等をfixture / artifactとしてcommitしない。

### Documentation

ADR:
- 本ADRを追加する。

Current architecture docs:
- `docs/architecture/audio-playback.md` を追加し、`module-map.md` と architecture index を更新する。

Spec:
- `docs/spec.md` に音声再生のuser-visible behaviorを追加する。

### Human decision

- foreground media playback service / permission boundary と独立 Audio capability の追加は、ユーザーが本実装方針を明示承認済み。

## Consequences

### Positive

- 再生と既読状態を完全に分離できる。
- UIを閉じても標準media sessionとして継続できる。
- 将来の音声source拡張時にも Summary ownership を汚さない。

### Negative

- Media3 dependency と foreground service を追加する。
- TTS engine / voice の端末差を考慮した失敗処理が必要になる。
- `synthesizeToFile` は非同期なので、キュー先読みと生成完了の同期制御が必要になる。
