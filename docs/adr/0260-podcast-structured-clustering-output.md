# ADR-0260: ニュース分類の構造化 tool output

## Status

Accepted

## Context

ADR-0259 でニュースポッドキャスト生成前に同一ニュースを分類し、分類結果を全候補をちょうど1回含む partition として検証する方針を採用した。

初期実装では分類モデルへ `GROUP: 1,3` のような行指向テキストを要求し、通常テキスト応答を parser で解釈していた。この方式では分類内容が妥当でも、説明文、Markdown、記号表記などモデルの出力揺れだけで parse failure となり、1記事1clusterへfallbackする。

一方、共通AI runtimeには `AiStructuredTextInference` があり、Library organizationでは通常テキストを解析せず、schema付きtool callのargumentsだけを構造化結果として受理している。

## Decision

Podcastのニュース分類も通常テキスト出力を使用せず、`AiStructuredTextInference` のtool callを利用する。

分類toolは `submit_podcast_news_clusters` とし、1-basedの記事indexを二次元整数配列 `groups` として要求する。通常テキストは分類結果として採用しない。

Local / Cloudのどちらの生成providerでも同じprovider-neutral structured inference contractを利用する。featureはprovider固有protocolを扱わず、compositionで対応するstructured inference adapterを選択する。

tool schemaで型を限定した後もPodcast feature側で次を検証する。

- groupが空でない
- 各groupが空でない
- indexが候補範囲内である
- 全候補がちょうど1回だけ含まれる

推論、tool call、argumentsのdecode、または最終validationに失敗した場合はADR-0259どおり1記事1clusterへfallbackする。coroutine cancellationはfallbackせず伝播する。

分類promptは「通常テキストを返さずtoolを1回だけ呼ぶ」ことを明示するが、正しさはprompt文言ではなくtool schemaとfeature側validationで担保する。

raw prompt、raw model text、raw tool argumentsはdiagnosticsやdurable stateへ保存しない。

## Consequences

- 説明文やMarkdownなど通常テキストの出力揺れによる分類fallbackを避けられる。
- Local / Cloudで分類結果の受け取り方が統一される。
- schemaに加えてfeature側でもpartition invariantを検証するため、不完全・重複した分類を受理しない。
- structured inference adapterが未対応またはtool callに失敗した場合も、episode生成全体は従来どおり安全に1記事1ニュースへ退化する。
- Podcast featureはprovider protocolへ依存せず、既存のAI runtime boundaryを再利用する。

## Relations

- Refines: ADR-0259
- Reuses: ADR-0199
