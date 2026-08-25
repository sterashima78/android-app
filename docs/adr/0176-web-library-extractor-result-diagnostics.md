# ADR-0176: Web Library custom extractor の実取得値を再取得診断へ含める

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md)
- Refines: [ADR-0163](0163-webview-renderer-exit-recovery.md)

## Context

ADR-0173 では Web Library の custom metadata extractor について、URL pattern に一致した rule の実行結果と WebView fallback 理由を明示的な metadata 再取得の診断へ含めることを決定した。

ただし `APPLIED` という status だけでは、custom function が実際にどの title / thumbnailUrl を返したかを確認できない。最終的な `LibraryBook` は custom result、固定 rendered metadata、静的 metadata の merge 後の値であるため、最終値だけを表示すると custom function 自体の抽出結果を診断できない。

また、custom function の Promise が完了した後に固定 rendered metadata の抽出や再試行が続き、WebView 全体の timeout に到達する場合がある。この場合、custom function が値を取得済みでも従来は rendered path 全体の例外だけが残り、rule の実行結果が失われる。

## Decision

### custom function が実際に返した採用可能値を execution 診断へ保持する

`WebLibraryMetadataExtractorExecution` は既存の rule ID、URL pattern、status、message に加えて、次の transient diagnostic value を持つ。

- `extractedTitle`
- `extractedThumbnailUrl`

これらは custom Promise の result を parsing した後の値とする。title は空値を除外した値、thumbnailUrl は最終ページ URL に対して相対解決し、既存 security boundary を満たす HTTPS URL だけを対象とする。

`APPLIED` であっても custom function が返していない field は `null` のまま保持する。固定 rendered metadata や静的 metadata で補完された値を custom result として表示してはならない。

### 再取得 UI は custom result と最終 metadata を混同しない

明示的な Web Library metadata 再取得結果で custom extractor が `APPLIED` の場合、UI は rule が実際に取得した title / thumbnailUrl を表示する。取得していない field は「なし」と表示する。

表示が過度に長くならないよう、各 diagnostic value は UI 表示時だけ上限を設けて短縮する。保存されている metadata 自体はこの表示上限の影響を受けない。

### WebView 全体 timeout 時も取得済み execution snapshot を失わない

metadata 用 WebView は custom extractor の execution が確定するたびに最新 snapshot を呼び出し側へ伝える。ページ generation が変わった場合は旧 snapshot を破棄する。

WebView 全体が timeout した時点で custom extractor の execution がすでに確定している場合、timeout exception はその snapshot を内部診断情報として保持する。静的 metadata が利用可能で fallback する場合、Library Data は fallback reason とともにその execution を `WebLibraryMetadataRefreshResult` へ伝播する。

これにより、例えば custom function が title / thumbnailUrl を取得済みだが、その後の固定 rendered metadata 処理中に15秒の全体 timeoutへ達した場合でも、UIでは custom result と WebView fallback の両方を確認できる。

custom Promise 自体が未完了のまま全体 timeout に達した場合は、存在しない取得値を推測せず、従来どおり WebView failure のみを表示する。

### diagnostic value は新たに永続化・記録しない

`extractedTitle` / `extractedThumbnailUrl` は再取得操作中の transient result とする。次の保存先を追加しない。

- application database
- backup schema
- repository fixture / documentation の実値
- telemetry
- 永続ログ

公開 repository には実サイトの取得値やユーザー固有 URL を追加せず、test では `example.com` 等の架空値だけを使用する。

## Consequences

- custom script が期待した DOM 値を取得できているかを再取得画面だけで確認できる。
- static / standard rendered fallback の値を custom result と誤認しにくくなる。
- custom extraction 完了後に WebView 全体 timeout が発生しても、取得済み値を診断できる。
- `WebLibraryMetadataExtractorExecution` の責務は実行 status だけでなく、再取得時の transient custom result snapshot まで含む。
- 永続データ形式、backup schema、custom function contract、WebView security boundary は変更しない。

## Verification

- custom metadata から `WebLibraryMetadataExtractorExecution` に title / thumbnailUrl が保持される unit test を追加する。
- WebView failure が取得済み execution を伴う場合、静的 fallback result に execution と fallback reason の両方が伝播する unit test を追加する。
- UI が final `LibraryBook` の値ではなく execution の extracted value を表示する unit test を追加する。
- custom function が返していない field を「なし」と表示する unit test を追加する。
- test / docs に実サイト URL、credential、token、ユーザー固有情報が含まれないことを public repository verification で確認する。
