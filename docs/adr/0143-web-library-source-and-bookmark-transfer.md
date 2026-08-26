# ADR-0143: Web URL を Library source として扱い Bookmark と安全に移動する

- Status: Superseded by [ADR-0186](0186-bookmark-to-library-one-way-ownership.md)
- Date: 2026-08-23
- Refines: [ADR-0013](0013-library-sources-and-google-books-sync.md), [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0117](0117-cross-context-persistence-boundary-phase1.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md)

## Context

Library は Google Play Books、Kindle、Audible、SMB を同一の `LibraryBook` catalog として扱っている。一方、Web 上で閲覧する書籍・作品ページも蔵書として保持したい。入力は URL であり、Android の共有シートから直接追加できる必要がある。また、同じ URL ベースの情報を Bookmark と Library の間で移動できる必要がある。

この操作では Bookmark Context と Library Context の両方を書き換えるため、片方の table を他方の Repository から直接操作すると ADR-0106 / ADR-0117 の ownership に反する。さらに、ネットワークから metadata を取得する Web Library 追加は失敗し得るため、移動途中で元データを消さない順序が必要である。

ADR-0142 により feature Route の cross-feature / platform composition は app module が所有する。Web Library の表示部品は Library UI に残し、Web metadata 取得は Library data に閉じ、Bookmark と Library の command orchestration だけを app composition に置く必要がある。

## Decision

### Web を LibrarySource として追加する

`LibrarySource.WEB` を追加し、既存の `library_items` を利用する。新しい durable table は追加しない。

Web 蔵書では正規化した最終 URL を `sourceId` と `infoUrl` に保持する。URL fragment は同一ページ内の位置情報であるため identity から除外し、scheme と host を正規化する。

### Web metadata の取得は Library data layer が所有する

Library data layer の `WebLibraryMetadataClient` が HTTP(S) ページを取得し、次の優先順位で表示 metadata を生成する。

1. Open Graph (`og:title`, `og:image`, `og:description`)
2. Twitter Card metadata
3. HTML `title` / `description` / `author`
4. 共有元が提供した title
5. host 名

相対画像 URL はページ URL を基準に解決する。アプリの cleartext 制約と整合させるため、表紙として保持する URL は HTTPS のみとする。取得する HTML にはサイズ上限を設ける。

### 更新操作は narrow capability にする

Web 蔵書の追加・削除は `WebLibraryMutator` capability として公開し、Library の広い Repository contract に Web 固有操作を混在させない。

Bookmark 用の既存共有 target は変更せず、「蔵書へ追加」を独立した共有 target として登録する。専用の `LibraryShareActivity` は framework dependency provider を直接 lookup せず、共有 payload を既に監査済みの `MainActivity` へ専用 action で転送する。URL 解析と `WebLibraryMutator` の実行は `MainActivityDependencies` を通じた app composition で行う。

Library の app-owned Route は `LibraryRouteDependencies` からこの capability と cross-context callback を受け取り、feature UI の `WebLibraryActions` を composition する。feature UI 自体は app/container や Bookmark implementation に依存しない。

### Bookmark と Library の移動は application service が調停する

Bookmark と Web Library の相互移動は app module の `BookmarkLibraryTransferService` が調停する。各 Context の所有 API だけを利用し、foreign table へ直接アクセスしない。

Bookmark → Library は次の順序にする。

1. Web Library へ追加する
2. 追加成功後に Bookmark を解除する

Library → Bookmark は次の順序にする。

1. Bookmark へ保存する
2. 保存成功後に Web Library item を削除する

Context を跨ぐ単一 DB transaction は作らない。途中失敗時には元側を残すことで、ユーザーデータの消失を避ける。

Library UI では Web 蔵書カードの長押し操作メニューに「ブックマークへ移動」を表示する。この操作の実装を feature 側へ移さず、`LibraryFeatureRoute` が app-owned callback を CompositionLocal 経由でカード UI に供給し、既存の `BookmarkLibraryTransferService` へ委譲する。追加・再取得ダイアログからの既存移動導線も同じ callback を共有する。

### 公開リポジトリ上の情報境界

実装やテストには実ユーザーの URL、認証情報、cookie、取得ページ本文を含めない。Web ページ本文は metadata 抽出の入力として一時利用し、Library には既存 schema が持つ書誌 metadata と URL だけを保存する。

## Consequences

- Web の書籍・作品ページを既存 Library catalog、検索、source filter の対象として扱える。
- Android 共有シートでは既存 Bookmark target と Library target をユーザーが明示的に選択できる。
- Web 蔵書はカードの長押し操作メニューから直接 Bookmark へ移動できる。
- 共有専用 Activity を新たな dependency composition root にせず、framework provider lookup の監査対象を増やさない。
- Bookmark と Web Library の移動でネットワーク失敗が起きても、コピー先の作成前に元データが消えない。
- 最新の app Route ownership を維持し、Library feature UI / data と cross-context orchestration の責務が混在しない。
- Web page metadata はサイト側の OGP 品質に依存するため、完全な書誌情報は保証しない。
- JavaScript 実行後にのみ生成される metadata は対象外とし、通常の HTTP HTML response を解析する。

## Verification

- OGP、HTML title fallback、相対表紙 URL、HTTPS 表紙制約を unit test する。
- Bookmark ↔ Library の更新順序と、Library 追加失敗時に Bookmark を削除しないことを unit test する。
- Web 蔵書だけが長押し操作メニューの Bookmark 移動対象になることを unit test する。
- framework provider boundary test で共有専用 Activity が新しい provider lookup を追加していないことを検証する。
- architecture verification、public repository verification、既存 unit test、lint を CI で実行する。
