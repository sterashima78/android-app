# ADR-0224: Cloud Summary の Web 取得失敗を限定再試行する

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md), [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md), [ADR-0185](0185-normalize-chatgpt-provider-failures-in-core.md)

## Context

ChatGPT / Codex を使う Cloud Summary は、Android 側で記事本文を取得せず、記事 URL を provider の Web search に渡して対象ページを `open_page` させる。指定ページを開けなかった場合は推測要約を採用しない fail-closed 方針を ADR-0171 で採用した。

実運用では、同じ URL でも一度目はページを取得できず、手動で再実行すると取得できる場合がある。これは対象サイトが恒久的に取得不能とは限らず、provider 側の一時的な Web 取得失敗として発生し得る。一方、従来の `WEB_TARGET_NOT_OPENED` は non-retryable として即座に durable failure へ落としていた。

また、provider が `open_page` を試行した後に「ページを取得できませんでした」のような短い失敗文を通常の生成テキストとして返す場合、target URL の open action だけでは本文取得成功を十分に保証できず、その失敗文を要約として保存する可能性があった。

## Decision

### 1. Web target 取得失敗だけを Summary adapter 内で最大3回試す

`feature:summary:data` の ChatGPT adapter は `WEB_TARGET_NOT_OPENED` に限って最大3回再実行する。再試行間には短い増加待機を入れる。

HTTP 408 / 429 / 5xx や transport failure は既存 ADR-0172 の WorkManager retry policy を維持し、この限定再試行へ混ぜない。認証失敗、request rejected、unknown failure も追加試行しない。

3回すべて失敗した場合は durable task failure とし、WorkManager にさらに無制限再試行させない。ユーザーは必要に応じて明示的に再実行できる。

### 2. 本文を取得できない場合は明示 sentinel を返すよう prompt を補強する

Cloud Summary の provider prompt には、指定 URL の記事本文を実際に取得できなかった場合、検索 snippet・別ページ・事前知識へ fallback せず、専用 sentinel だけを返す指示を追加する。

adapter は sentinel を要約結果として扱わず、Web target 取得失敗と同じ限定再試行へ送る。

### 3. 短い取得失敗文も保存前に拒否する

provider が sentinel 指示に従わず自然言語で取得失敗を返す場合に備え、短い応答の先頭段落に典型的な日本語・英語の取得失敗表現がある場合も Web target failure とみなす。

長い通常要約に同じ語句が含まれた場合まで誤って拒否しないよう、判定対象の文字数を上限付きにする。この判定は記事本文や URL をログ・durable error へ保存しない。

## Consequences

### Positive

- 一時的な provider Web 取得失敗をユーザーの手動再実行なしで回復しやすくなる。
- 「ページを取得できませんでした」のような provider の失敗文を正常な要約として保存しにくくなる。
- retry 対象を Web target failure に限定し、既存の provider failure taxonomy と WorkManager retry policy を崩さない。
- 最大3回で打ち切るため、恒久的に取得不能なサイトへの無限 request を避けられる。

### Negative

- 同じ要約タスクで provider request が最大3回発生し得る。
- 自然言語の取得失敗判定は provider の文面に依存するため、sentinel を一次判定とし、自然言語判定は保守的な fallback に留める必要がある。
- `www` 付与や canonical redirect 等の URL 同一性判定は本変更では扱わない。

## Verification

- Web target failure が3回目まで再試行され、成功すれば結果を返す unit test を追加する。
- Web target 以外の provider failure が追加再試行されないことを unit test で固定する。
- sentinel と短い取得失敗文を検知し、通常要約と長い本文内の言及を誤検知しないことを unit test で確認する。
- Architecture / Test / Lint / public repository verification を PR CI で確認する。

## Public repository review

実装・テスト・ADR に access token、refresh token、account id、実ユーザー URL、実記事本文、provider の実レスポンスを保存しない。テストは合成文字列だけを使う。
