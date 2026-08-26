# ADR-0177: Web Library の custom extractor を DOM 利用可能時に開始し rule 別 timeout を持つ

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0176](0176-web-library-extractor-result-diagnostics.md)

## Context

ADR-0173 の custom metadata extractor は WebView の `onPageFinished` 後に実行していた。`onPageFinished` は main document の DOM が利用可能になった時点ではなく、画像や script 等を含むページ読み込み完了を待つため、広告・画像・長時間通信を持つページでは WebView 全体の 15 秒 timeout が先に発生する場合がある。

ADR-0176 で execution phase を診断できるようにした結果、このケースは「URL rule には一致したが custom script 開始前に WebView 処理が終了」と明示できるようになった。custom extractor の目的はページ全体の表示完了ではなく DOM / page context から metadata を取得することであるため、`onPageFinished` を実行開始条件にする必要はない。

またサイトごとにページ初期化時間が大きく異なるため、WebView 全体 timeout をアプリ固定値だけで運用すると、特定サイトだけ余裕を持たせることができない。

## Decision

### custom extractor はページ全体の読み込み完了を待たない

URL rule に一致するページでは `WebViewClient.onPageCommitVisible` を、custom extraction を開始する最初の契機とする。

page commit 後に `document.readyState` を短時間 polling し、`loading` ではなくなった時点で少量の DOM settle delay を置いて custom function を開始する。これにより main document の DOM が利用可能になるまで待ちつつ、画像・広告等の subresource 完了は待たない。

`onPageFinished` は custom extraction がまだ開始されていない場合の fallback trigger として残す。page generation ごとに extraction 開始を一度だけ許可し、`onPageCommitVisible` と `onPageFinished` の双方から二重実行されないようにする。

custom rule がないページの固定 rendered metadata extraction は従来どおり `onPageFinished` 後に実行する。

### URL rule ごとに WebView 全体 timeout を保存する

`WebLibraryMetadataExtractor` に `timeoutSeconds` を追加する。

- default: 15 秒
- minimum: 5 秒
- maximum: 120 秒

requested URL に一致する rule がある場合、その `timeoutSeconds` をページ読み込み開始から rendered metadata 取得完了までの WebView 全体 timeout として利用する。rule がない場合は従来の 15 秒 default を利用する。

この設定は WebView 全体の上限であり、custom function が返す Promise 自体の 10 秒上限は ADR-0173 のまま維持する。したがって timeout を長く設定しても、停止しない user-authored Promise を長時間保持し続けない。

設定画面では各 rule に現在の WebView timeout を表示し、追加・編集時に秒単位で変更できるようにする。

### timeout は Library-owned durable rule data とする

`web_library_metadata_extractors` に `timeout_seconds INTEGER NOT NULL DEFAULT 15` を追加する。

fresh database は Library schema initializer の `CREATE TABLE` でこの column を持つ。既存 database では同じ idempotent initializer が `PRAGMA table_info` で column の有無を確認し、存在しない場合だけ `ALTER TABLE ... ADD COLUMN` を実行する。既存 rule は 15 秒として扱う。

現行 database version 27 と同一 backup schema を対象とする方針は変更せず、この additive column のためだけの database version bump は行わない。database snapshot backup には既存 rule data と同様に `timeout_seconds` も含まれる。

### 診断 phase の意味を維持する

ADR-0176 の execution status は次の意味とする。

- `MATCHED`: rule は一致したが DOM 利用可能待ちで custom script は未開始
- `RUNNING`: custom script を開始し Promise result の確定待ち
- `APPLIED` 等: custom function の結果が確定済み

これにより rule 別 timeout に達した場合も、待機段階と script 実行段階を再取得結果から区別できる。

## Consequences

- ページ全体の読み込みが遅いサイトでも、DOM が利用可能なら 15 秒 timeout より前に custom script を開始できる。
- 特定サイトだけ WebView 全体 timeout を延長でき、全サイトの待機時間を増やさずに済む。
- custom function が DOM 要素の出現をさらに待つ必要がある SPA では、従来どおり async function 内で明示的に待機できる。
- rule data に column が1つ増えるが、ownership、backup、security boundary は変更しない。
- native JavaScript bridge、file/content access、mixed content、third-party Cookie 等の WebView security 制約は変更しない。

## Verification

- rule に一致した場合は rule の timeout を WebView 全体へ選択し、未一致時は default timeout を選択する unit test を追加する。
- timeout の 5〜120 秒 validation と 15 秒 default を unit test する。
- version 27 相当の旧 `web_library_metadata_extractors` schema に `timeout_seconds` が default 15 で追加されることを test する。
- fresh database schema に `timeout_seconds` が存在することを app schema test で確認する。
- UI から timeout を保存経路へ渡し、rule 一覧で現在値を確認できるようにする。
- PR 前に public repository、architecture、test scope、documentation の独立レビューを行う。
