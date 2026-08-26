# ADR-0180: RSS の Web スクレイピングを URL pattern と端末保存 script で拡張する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0090](0090-yanmaga-web-html-derived-rss.md), [ADR-0091](0091-mangaone-webview-derived-rss.md), [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md), [ADR-0178](0178-web-library-custom-extractor-native-watchdog.md)

## Context

RSS Context には通常の RSS / Atom discovery に加え、RSS を公開していない Web ページから synthetic feed を生成するサイト固有処理がある。ADR-0090 の静的 HTML 解析と ADR-0091 の WebView 解析は利用できているが、新しいサイトを追加するたびにアプリ本体へ URL 判定、DOM selector、取得ロジックを実装してリリースする必要がある。

Library Context では ADR-0173 以降、URL pattern と Promise を返す JavaScript function を端末 DB に保存し、専用 WebView でサイト固有 metadata を取得する方式を採用している。RSS の synthetic feed についても同じ変更容易性が必要である。

一方、既存の漫画向け取得処理は実運用済みである。汎用 mechanism を追加した時点で削除すると、WebView 条件や取得結果 contract の差によって既存購読を壊す可能性がある。そのため移行期間を設け、汎用 rule が実サイトで適切に動作することを確認してから site-specific client の削除を別変更で判断する。

また取得 script は DOM の変化に影響されやすいため、保存前の editor から実 URL に対して script を実行し、実際に生成される feed data を確認できる必要がある。

## Decision

### RSS Context が Web scraping rule を所有する

RSS Data に `rss_web_scraping_rules` を追加し、次を durable user data として保存する。

- `id`
- HTTPS の URL glob pattern
- Promise を返す JavaScript function code
- WebView pipeline 全体の timeout 秒数
- 更新日時

`*` は任意長、`?` は1文字として扱う。複数 rule が一致した場合は wildcard 以外の文字数が多い pattern を優先し、具体度が同じ場合は更新日時が新しい rule を優先する。

rule の CRUD、matching、test execution は RSS-owned `FeedRepository` capability を通す。他 Context が `rss_web_scraping_rules` を直接 read/write しない。

fresh database では RSS schema contribution から table を作成する。version 27 の既存 database では RSS rule repository の idempotent initializer でも同じ schema を確保する。この additive table 追加だけを理由に database version は上げない。table は通常の database snapshot backup に含める。

### Library の実装は依存せず、security policy と execution pattern だけを踏襲する

RSS Context から Library Context の extractor repository/client を参照しない。Context ownership を維持したまま、ADR-0173 / 0177 / 0178 で確立した次の方針を RSS 内で適用する。

- WebView で JavaScript と DOM storage を有効化する。
- file/content access、mixed content、複数 window、geolocation、third-party cookie は許可しない。
- main-frame navigation と取得結果 URL は HTTPS の標準 port に限定する。
- script は文字列として保存し、WebView 内で function expression として評価する。
- function は Promise を返すことを必須にし、Promise 完了を polling する。
- Promise の完了待ちには10秒上限と native watchdog を設ける。
- rule ごとの WebView pipeline timeout は 5〜120 秒、既定 15 秒とする。
- renderer process が終了した場合は取得失敗として WebView を破棄する。

### script contract は synthetic feed data を返す

登録 function は次の形で呼び出す。

```text
async ({ url }) => ({
  title,
  siteUrl?,
  items: [
    {
      title,
      url,
      externalId?,
      publishedAt?
    }
  ]
})
```

`title` と `items` は必須である。各 item の `title` と `url` も必須とする。`siteUrl` と item URL は相対 URL を許可するが、page URL を基準に解決した最終 URL は HTTPS の標準 port でなければならない。同一 URL の item は重複排除する。

`publishedAt` は ISO-8601 instant または ISO local date を受け付ける。指定がない、または解析できない場合は取得時刻を用いる。`externalId` があれば article identity に利用し、なければ article URL を identity source とする。

### custom rule を既存 site-specific client より優先する

`FeedRepository.inspect`、`addFeed`、`refreshFeed` では次の順で取得方法を決める。

1. URL に一致する user-defined Web scraping rule
2. ADR-0090 / ADR-0091 の既存 site-specific synthetic feed client
3. 通常の RSS / Atom discovery / fetch

これにより既存の漫画向け実装を残したまま、同じ URL に custom rule を登録して新方式を実運用評価できる。rule を削除すれば直ちに既存 site-specific client へ戻る。

既存の漫画向け client はこの ADR では削除しない。汎用 rule で同等の取得結果、安定性、更新挙動が確認できた後、ADR-0090 / ADR-0091 の廃止可否を別変更で判断する。

### editor から保存前 script を実行テストする

Web 取得 rule 一覧の現在の配置は [ADR-0182](0182-rss-settings-tab.md) に従い、RSS の設定タブとする。rule の追加・編集は下から開く bottom sheet で行う。

editor は URL pattern、timeout、function code に加え、test URL を入力できる。`実行テスト` は DB に保存済みの rule ではなく、画面上の現在の pattern / function / timeout を draft rule として実行する。test URL が pattern に一致しない場合は実行しない。

test 成功時は最低限次を表示する。

- feed title
- site URL
- item 件数
- 各 item の title / URL
- `publishedAt` / `externalId` があればその値

大量取得時の editor 描画負荷を避けるため item preview は先頭20件までとし、総件数は別に表示する。失敗時は WebView load、function evaluation、Promise、result validation 等から得られた理由を editor 内に表示する。

実行テストは保存の必須条件にはしない。ログイン状態や一時的な Web 状態などにより test が実行できない場合でも rule 自体の保存は可能とする。

### 公開 repository に個別サイトの user rule を保存しない

`rss_web_scraping_rules` に登録した実 URL pattern と function code は user data とする。repository source、test fixture、ADR、README に実利用中の user rule を転記しない。test/docs では `example.com` 等の架空・例示 URL と generic DOM script のみを使用する。

既に source/ADR で product feature として公開されている site-specific client の記述はこの制約の対象外である。

## Consequences

- RSS を公開していないサイトをアプリ更新なしで synthetic feed source として追加できる。
- DOM selector 等の変更も端末上の rule 更新で追従できる。
- 保存前に実 URL で結果を確認でき、壊れた script を登録する可能性を下げられる。
- custom rule が既存 site-specific client より優先されるため、新方式を既存購読 URL で段階的に検証できる。
- rule 削除だけで既存 site-specific client へ戻せるため、移行中の rollback が容易である。
- arbitrary JavaScript を user が登録して対象ページ内で実行するため、script は対象 page と同じ権限を持つ。専用 WebView の platform access を制限し、repository に rule を同梱しないことで境界を明確にする。
- RSS と Library は似た mechanism を持つが、Context を跨ぐ shared repository/client は作らない。共通化価値が明確になった場合は pure execution primitives の抽出だけを別途検討する。

## Verification

- URL glob matching、HTTPS normalization、specificity、timeout validation の unit test を追加する。
- script start/poll contract が Promise を要求し、非同期 state を polling することを unit test する。
- result parser が relative HTTPS URL を解決し、duplicate URL を除外することを unit test する。
- HTTP 等の安全でない result URL を拒否することを unit test する。
- fresh DB schema に `rss_web_scraping_rules` が含まれることを integration test する。
- table ownership verification に `rss_web_scraping_rules -> :feature:rss:data` を登録する。
- editor が保存前 draft を test 実行し、成功/失敗と item preview を表示することを code review / CI で確認する。
- 既存 ADR-0090 / ADR-0091 の client が削除されておらず、custom rule 不一致時の fallback として残ることを確認する。
- PR 前に public repository、architecture、test scope、documentation の独立レビューを行う。