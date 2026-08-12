# ADR-0009: Reddit を RSS から独立した feature として扱う

- Status: Accepted
- Date: 2026-08-09
- Updated: 2026-08-12
- Supersedes: ADR-0007 の module ownership に関する決定

## Context

Reddit の更新取得には公開 RSS/Atom endpoint を利用できるが、ユーザーにとって Reddit と一般 RSS は異なる情報源である。

Reddit には次の固有概念がある。

- subreddit / community の購読
- community 投稿の新着確認
- 特定 thread の明示的な更新購読
- thread 購読開始時点を基準にした新着 comment の追跡

これらを一般 RSS のフィード管理・未読一覧へ混在させると、取得 transport と product concept が同一視される。

一方で、未読を処理しながら一部を後回しにする「あとで読む」は情報源に依存しない仕分け概念であり、RSS と Reddit で同じ操作体系を利用したい。

## Decision

### 1. Reddit を独立した feature ownership にする

次の module を追加する。

```text
:feature:reddit:domain
:feature:reddit:data
:feature:reddit:ui
```

責務は以下とする。

- `reddit:domain`: Reddit URL 判定、community / thread subscription model、repository contract
- `reddit:data`: RSS transport を利用した Reddit repository 実装
- `reddit:ui`: Reddit 未読、あとで読む、community / thread 購読管理、thread subscription action

### 2. RSS/Atom は Reddit の transport 実装として再利用する

Reddit 専用 HTTP client や parser は複製しない。

`reddit:data` は既存 `FeedRepository` を内部依存として利用し、Reddit の公開 RSS/Atom endpoint を読み書きする。ただし `reddit:domain` と `reddit:ui` から RSS の `Feed` model や RSS UI は露出させない。

この依存は「Reddit is RSS」という意味ではなく、「現在の Reddit source adapter が RSS transport を利用する」という実装詳細である。

将来 Reddit API 等へ移行する場合は `RedditRepository` 実装を差し替え、Reddit UI / domain を維持できる形にする。

### 3. UI と操作対象を分離する

アプリの navigation drawer に RSS と同列の独立 section として `Reddit` を追加する。

Reddit section は次を持つ。

- 未読
- あとで読む
- 購読管理

RSS section は次を持つ。

- 未読
- あとで読む
- フィード管理

Reddit の未読画面では RSS と同じ仕分け操作を採用する。

- 左スワイプ: 既読
- 右スワイプ: ブックマーク
- 右への大きいスワイプ: あとで読む

「あとで読む」は RSS と同じ `BookmarkRepository` / あとで読むフォルダ状態を再利用する。新しい Reddit 専用保存テーブルは作らない。ただし Reddit のあとで読むタブでは Reddit source の記事だけを抽出し、RSS のあとで読むタブには Reddit 記事を混在させない。

Reddit のあとで読む一覧では RSS と同様に古い順・新しい順を切り替えられ、ブックマーク解除または未分類への移動を行える。

Reddit の記事・コメントは RSS の未読一覧、RSS のフィード管理、RSS の「すべて既読」、RSS ホームウィジェットには表示しない。

ブックマークと読書履歴は情報源をまたぐ共通概念なので、RSS / Reddit の双方を引き続き扱う。

統合ビューの「後回し」操作も Reddit ではこの「あとで読む」状態へ遷移させ、単なるブックマークとは区別する。

### 4. アプリ内の他の公開面でも RSS / Reddit を混在させない

取得・保存に同じ backend を利用していても、ユーザーやエージェントに見える境界では情報源を分ける。

- AI エージェントでは `rss-reader` Skill から Reddit を除外し、`reddit-reader` Skill を独立して提供する
- LAN Web UI では `RSS未読` と `Reddit` を別 view にし、`RSSフィード` 一覧から Reddit 購読を除外する
- RSS ホームウィジェットは RSS の未読と RSS feed refresh のみを対象にする

新しい公開面を追加する場合も、共有 DB を直接表示して RSS と Reddit を再混在させない。

### 5. 既存データは URL classification で引き継ぐ

現時点では DB migration や feed table の source column 追加を行わない。

既存の Reddit feed URL は `reddit.com` の canonical URL として識別できるため、`RedditRepository` が既存 `feeds` / `articles` を抽出して引き継ぐ。

これにより ADR-0007 で追加済みの community / thread subscription を失わず、アプリ更新直後から Reddit section に移動できる。

source の種類が増え URL classification だけでは曖昧になる場合は、`source_kind` の永続化を別 ADR で検討する。

### 6. Reddit 固有の購読規則は維持する

- community は `/new/.rss` に正規化し新着順で購読する
- thread はユーザーが明示的に購読した場合だけ comments RSS を追加する
- thread 購読開始時点の既存 comment は既読ベースラインにする
- 記事を開く、既読にする、ブックマークする、あとで読むにするだけでは thread を購読しない

## Consequences

### Positive

- RSS と Reddit の product concept が混在しない
- Reddit 固有 UI を今後拡張しやすい
- RSS parser / conditional request / persistence は再利用できる
- RSS と Reddit で「あとで読む」の操作感と保存 semantics を共通化できる
- あとで読む保存形式を追加せず既存バックアップ対象をそのまま再利用できる
- 既存 Reddit 購読データを migration なしで引き継げる
- AI Skill、LAN Web UI、ホームウィジェットでも同じ境界を維持できる
- 将来 Reddit API に移行しても domain / UI を保ちやすい

### Negative

- 現時点では storage layer は RSS feed table を共有する
- source 判定を URL に依存する
- あとで読むの storage は RSS / Reddit で共有するため、各 UI で source filter を必ず適用する必要がある
- Reddit と RSS が同じ DataChangeNotifier を共有するため変更通知は相互に発生する。ただし各 ViewModel が自分の source だけを reload する

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data 分離に従う
- ADR-0003 の `<feature-name>:{ui,domain,data}` 構成に従う
- ADR-0004 の concept ownership を優先し、transport の種類ではなく Reddit という product concept に module を割り当てる
- ADR-0007 の取得方式・thread opt-in 方針は維持し、module ownership の決定だけを置き換える
- ADR-0019 の feature 内表示切り替えを bottom tab に統一する方針に従い、Reddit の「あとで読む」も bottom tab として追加する
