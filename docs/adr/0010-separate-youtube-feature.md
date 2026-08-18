# ADR-0010: YouTube を RSS から独立した feature として扱う

- Status: Accepted
- Date: 2026-08-09
- Amended by: ADR-0098

## Context

YouTube はチャンネルごとに公開 Atom endpoint を提供しており、API キーや Google アカウント連携なしでも公開動画の更新を取得できる。

一方、ユーザーにとって YouTube チャンネル購読と一般 RSS フィード購読は異なる product concept である。取得形式が Atom であることを理由に RSS の Feed / Article / Repository / UI へ統合すると、transport と product concept が再び混在する。

Reddit の分離で採用した ADR-0009 と同様に、情報源ごとのユーザー体験を独立させる必要がある。ただし YouTube については RSS 実装を transport としても再利用せず、YouTube 固有の adapter と persistence を持たせる。

## Decision

### 1. YouTube を独立した feature ownership にする

次の module を追加する。

```text
:feature:youtube:domain
:feature:youtube:data
:feature:youtube:ui
```

責務は以下とする。

- `youtube:domain`: YouTube channel / video model、repository contract
- `youtube:data`: channel URL 検証、公開 Atom endpoint の取得・解析、YouTube 専用永続化
- `youtube:ui`: 未読動画、あとで見る動画、保存済み動画の参照、チャンネル購読管理、既読・保存操作

RSS の domain / data / ui module には依存しない。

### 2. 共有するのは汎用 HTTP abstraction のみとする

YouTube のフィード取得では `:core:network` の `HttpClient` / `HttpRequest` を利用する。

YouTube Atom の parser、channel / video model、repository、SQLite table は YouTube feature が所有する。RSS の `FeedRepository`、RSS parser、RSS article table を再利用しない。

これにより、YouTube の取得方式が将来 Atom から別方式へ変わっても RSS feature へ影響を伝播させない。

### 3. 初期対応する購読 URL を channel ID URL に限定する

初期実装で受け付ける入力は次の形式だけとする。

```text
https://www.youtube.com/channel/UC...
```

`/@handle`、`/c/...`、`/user/...` は初期実装では受け付けない。

channel URL から channel ID を抽出し、次の公開 Atom endpoint を組み立てる。

```text
https://www.youtube.com/feeds/videos.xml?channel_id=<channel-id>
```

handle 解決のための HTML scraping や YouTube Data API は導入しない。

### 4. YouTube Data API と Google アカウント連携は利用しない

現在の要件は任意の公開チャンネル URL をアプリへ直接登録して更新を読むことであり、ユーザー自身の YouTube 登録チャンネルとの同期ではない。

そのため次は導入しない。

- YouTube Data API
- API key
- Google OAuth
- ユーザーの YouTube subscription list との同期

将来、非公開情報やアカウント連携が必要になった場合は別 ADR で再検討する。

### 5. 永続化 ownership を RSS から分離し、physical database は共有する

YouTube feature は少なくとも次の table と状態を `:feature:youtube:data` で所有する。

- 購読 channel
- channel title / canonical URL
- video ID / title / URL / publish time
- video read state
- video watch-later state

当初は専用の `youtube.db` を使用していたが、ADR-0098 により physical database は `yomitori-rss.db` に統合する。YouTube table は `DatabaseSchemaContribution` として公開し、RSS の Feed / Article table とは引き続き分離する。physical database の共有は YouTube と RSS の domain ownership を統合するものではない。

feed refresh では既存 video の read / watch-later state を保持し、新規 video のみ unread として追加する。

`watch-later` は RSS の「あとで読む」と同じく保存とは別の状態として扱う。watch-later に移動した動画は通常の未読一覧から外し、「あとで見る」一覧に表示する。watch-later を解除すると未読一覧へ戻す。既読化した場合は watch-later も解除する。

購読解除時はその channel に属する video も YouTube table から削除する。

### 6. UI を RSS と混在させない

navigation drawer に RSS / Reddit と同列の独立 section として `YouTube` を追加する。

YouTube section 内では次を切り替える。

- 未読
- あとで見る
- 保存済み
- 購読管理

「保存済み」は YouTube persistence に新しい保存状態を持たせず、共通ブックマークに保存された YouTube URL を `youtube:ui` で読み取り専用の一覧へ投影する。これにより保存データの ownership は Bookmark feature に維持しつつ、YouTube の文脈から保存済み動画へ戻る導線を提供する。ブックマーク画面でのフォルダ・タグ整理も引き続き利用できる。

YouTube video は RSS の未読一覧、RSS フィード管理、RSS の「すべて既読」、RSS ホームウィジェットには表示しない。

動画を開く操作では YouTube の read state を変更せず、外部の YouTube URL を開くだけにする。動画は一度の視聴操作で見終わるとは限らないため、個別動画の既読化は一覧上の明示的なスワイプ操作でのみ行う。保存済み一覧も同様に、開く際に YouTube の状態変更は行わない。

### 7. 動画リストの操作モデルは RSS と揃える

YouTube と RSS のデータ ownership は分離したまま、一覧上のトリアージ操作は同じジェスチャーに揃える。

- 動画行のタップ: 状態を変更せず動画を開く
- 左スワイプ: 既読
- 右スワイプ: 保存して既読
- さらに右へスワイプ: あとで見る / あとで見る解除

「保存」は YouTube persistence に重複保存せず、`youtube:ui` の ViewModel が `:feature:bookmark:domain` の `BookmarkRepository` を利用して共通ブックマークへ URL、動画タイトル、チャンネル名を保存する。これは ADR-0003 が許容する `UI -> 他 feature の Domain` 依存として扱い、`youtube:domain` / `youtube:data` には bookmark 依存を追加しない。`:app` は `YouTubeRepository` と `BookmarkRepository` の dependency wiring のみを行う。

サムネイルは YouTube video ID から標準サムネイル URL を生成して表示する。画像バイナリやサムネイル URL は YouTube table に永続化しないため、既存レコードにも即時適用できる。

## Consequences

### Positive

- RSS と YouTube の product concept が混在しない
- RSS の Feed / Article schema に YouTube 固有情報を持ち込まない
- API key や Google アカウント認可が不要
- YouTube の取得方式変更を `youtube:data` に閉じ込められる
- channel / video に固有の UI を独立して拡張しやすい
- RSS と YouTube の一覧操作を同じジェスチャーで学習できる
- 動画を開いただけでは未読状態が失われず、長い動画を途中まで視聴しても一覧から消えない
- 保存済み動画は共通ブックマーク画面で整理しながら、YouTube 画面からも再参照できる
- ADR-0098 により YouTube の durable data も共通 database backup / device transfer の対象になる

### Negative

- Atom parser と persistence を RSS transport から再利用しないためコード量は増える
- 初期実装では channel ID を含む URL をユーザーが取得する必要がある
- YouTube の公開 Atom endpoint が将来変更・廃止された場合は adapter の変更が必要
- YouTube schema 変更時は共有 database version の更新が必要になる
- `youtube:ui` から `bookmark:domain` への feature 間依存が1本増える
- AI Skill、LAN Web UI、ホームウィジェットなどへの YouTube 対応は別途明示的に追加する必要がある

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data 分離に従う
- ADR-0003 の `<feature-name>:{ui,domain,data}` 構成、および `UI -> 他 feature の Domain` を許容する依存ルールに従う
- ADR-0004 の concept ownership を優先し、Atom という transport ではなく YouTube という product concept に module を割り当てる
- ADR-0009 の「transport と product concept を分離する」考え方を踏襲するが、YouTube では RSS transport 自体も共有しない
- ADR-0098 は専用 `youtube.db` という physical persistence decision のみを置き換え、YouTube feature ownership は維持する
