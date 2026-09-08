# ADR-0241: Video Context で購読型プロバイダを所有する

- Status: Accepted
- Date: 2026-09-08
- Supersedes: [ADR-0010](0010-separate-youtube-feature.md) の source-specific subscription / video persistence / UI ownership
- Refines: [ADR-0237](0237-video-library-and-web-extraction.md), [ADR-0240](0240-video-saved-items-and-folders.md)

## Context

Video は SMB と Web 由来動画を同じ catalog へ投影し、将来の service adapter を受け入れる設計になっている。一方、既存のチャンネル購読機能は独立 feature として subscription、更新取得、未読状態、あとで見る状態、専用 UI と durable table を所有している。

今回の要求では Web source は引き続き URL を1件ずつ登録する bookmark-like source とし、購読型 provider は別概念として扱う。ユーザーが provider を有効化し、その provider に source / channel を登録すると、background refresh で新着動画を取得し、Video の未読一覧へ追加できる必要がある。

購読型 provider を既存 feature のまま残して Video へ投影すると、subscription / unread / refresh の source of truth が provider ごとに分散し、今後 provider を追加するたびに Video 外へ同じ lifecycle を複製することになる。

## Decision

### Video が購読型 provider capability を所有する

Video Context に次の概念を追加する。

- `VideoProvider`: ユーザーが利用する購読型 provider の設定
- `VideoSubscription`: provider 内の購読 source / channel
- provider item identity と publish time
- provider由来 item の unread / read state
- provider refresh lifecycle

Web URL の単発登録は従来どおり `VideoSource.WEB` とし、subscription / unread semantics を持たせない。

### Provider configuration と provider implementation を分離する

ユーザー設定は provider identity、display name、enabled state 等の durable configuration を表す。外部 source 固有の取得 implementation 自体を任意コードや汎用 DSL として保存しない。

初期 provider implementation は既存のチャンネルフィード取得 capability を Video Data adapter へ移行して利用する。将来 provider を増やす場合も、Video が所有する subscription / unread / refresh lifecycle を再利用し、provider adapter は source-specific input normalization、endpoint resolution、response parsing、item projection だけを担当する。

### Provider item は Video catalog に保存する

provider が取得した動画は `video_items` へ `VideoSource.SERVICE` item として投影する。durable identity は provider ID と provider item ID の組み合わせで一意にする。

provider item には subscription ID と publish time を関連付ける。新規に発見された provider item は unread とする。既存 item の refresh では read state、playback state、saved state を保持する。

Web / SMB item には unread state を導入しない。

### Subscription と playback / saved state は独立させる

購読解除時はその subscription 由来 catalog item を削除できるが、保存済み item または playback state を持つ item を暗黙に失わないよう retention rule を Video 側で定義する。

初期実装では購読解除時に未保存かつ再生履歴のない provider itemだけを削除し、保存済みまたは再生履歴を持つ item は catalog に残す。残存 item は subscription への active membership を失うが、再生・保存は継続できる。

### 既存専用 ownership を Video へ収束させる

既存の source-specific feature が所有していた subscription / video durable tables と専用画面は、新しい Video provider model へ移行後に runtime source of truth として使用しない。

既存インストールについては database migration で subscription、video identity、publish time、read state を Video-owned tableへ移す。移行後は旧 table を current runtime から参照しない。

既存の bookmark 保存状態は Video の saved state へ自動変換しない。異なる ownership の状態を暗黙に同一視しないためである。

### Background refresh を Video provider refresh へ統合する

統合 background refresh は source-specific repository を直接更新せず、Video の provider refresh capability を呼ぶ。

provider 単位または subscription 単位の失敗は他 provider / subscription の更新を妨げない。更新結果として新規 provider item が追加された場合、その item は unread として Video から参照できる。

### Navigation を Video へ収束させる

provider の有効化、購読管理、未読動画の確認は Video UI から行う。独立した source-specific top-level navigation は移行後に削除する。

統合未読 presentation が provider動画を表示する必要がある場合は、Video Domain の read-only capability を利用し、provider-specific tableやData implementationへ依存しない。

## Consequences

### Positive

- Web の単発登録と subscription provider の意味が明確に分離される。
- provider が増えても subscription / unread / refresh / Video UI を再実装しなくてよい。
- Video playback、saved state、provider unread state を同じ VideoItem identity 上で独立管理できる。
- provider-specific durable table と generic Video state の二重 source of truth を避けられる。
- background refresh が Video provider lifecycle に収束する。

### Negative

- 既存 source-specific durable data の migration が必要になる。
- current database version を進める必要がある。
- 統合未読 UI / background refresh / navigation の接続変更が必要になる。
- provider adapter contract を追加するが、任意 provider DSL までは提供しないため、新しい取得方式には code-level adapter の追加が必要になる。

## Compatibility

- 現行 application database から次 version への upgrade 時に、既存 subscription と provider動画を Video-owned state へ一度だけ移行する。
- read state を保持する。
- playback / saved state は既存 Video state を正本として維持する。
- migration 完了後、旧 provider-specific table は runtime から参照しない。
- backup restore の exact-version policy は変更しない。

## Verification

- provider configuration の保存、enabled state、subscription CRUD を repository test する。
- provider refresh で新規 item が unread として追加され、既存 item の read / playback / saved state が保持されることを test する。
- provider/subscription/item identity の衝突を test する。
- 購読解除時の retention rule を test する。
- 現行 database から次 version への migration で既存 subscription / video / read state が移行されることを test する。
- background refresh が Video provider refresh を利用し、個別失敗を分離することを test する。
- architecture verification で旧 provider-specific table ownership / navigation dependency が残っていないことを確認する。
- Android UI で provider有効化、subscription追加、refresh、新着未読、既読化を確認する。

## Documentation

- `docs/spec.md`
- `docs/architecture/video.md`
- `docs/architecture/context-map.md`
- `docs/architecture/persistence.md`
- ADR-0010 relationship/status
