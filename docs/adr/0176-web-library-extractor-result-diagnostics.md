# ADR-0176: Web Library custom extractor の実取得値を再取得診断へ含める

- Status: Accepted
- Date: 2026-08-26
- Amended: 2026-08-27
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md)
- Refines: [ADR-0163](0163-webview-renderer-exit-recovery.md)
- Refined by: [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md)

## Context

ADR-0173 では Web Library の custom metadata extractor について、URL pattern に一致した rule の実行結果と WebView fallback 理由を明示的な metadata 再取得の診断へ含めることを決定した。

ただし `APPLIED` という status だけでは、custom function が実際にどの title / thumbnailUrl を返したかを確認できない。最終的な `LibraryBook` は custom result、固定 rendered metadata、静的 metadata の merge 後の値であるため、最終値だけを表示すると custom function 自体の抽出結果を診断できない。

また、custom function の Promise が完了した後に固定 rendered metadata の抽出や再試行が続き、WebView 全体の timeout に到達する場合がある。この場合、custom function が値を取得済みでも従来は rendered path 全体の例外だけが残り、rule の実行結果が失われる。

さらに、WebView 全体 timeout が custom function の完了前に発生した場合、従来の診断では「rule が URL に一致していなかった」のか、「一致したが custom script 開始前だった」のか、「custom script を開始済みで結果待ちだった」のかを区別できない。

再取得対象一覧は「タイトルまたは表紙が未取得の Web 蔵書」から構成されるため、再取得で不足 metadata が埋まるとその蔵書は対象一覧から消える。実行結果を対象カードの内部だけに表示すると、成功した項目ほど診断結果も同時に見えなくなり、再取得が何を更新したか確認できない。

通常の蔵書一覧からも個別の metadata 再取得を開始できるため、設定画面へ移動しない限り処理が待機中なのか実行中なのか、完了後に更新されたのかを把握できない状態も避ける必要がある。

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

### 直近の再取得結果は再取得対象一覧から独立して表示する

`WebLibraryRefreshUiState.items` は、その実行で対象になった蔵書と `PENDING` / `RUNNING` / `UPDATED` / `UNCHANGED` / `WARNING` / `FAILED` の状態、detail を実行中の transient state として保持する。

設定画面ではこの `items` を「現在も不足 metadata が残っている蔵書一覧」と join して表示するだけにせず、独立した「直近の再取得結果」セクションとして表示する。これにより再取得成功後に蔵書が repair 対象一覧から消えても、同じ実行の status と detail は確認できる。

大量実行時に設定画面を過度に長くしないため、結果一覧は初期状態では先頭 10 件までを表示し、必要に応じて同一実行の全件を展開できるようにする。実行結果は route/composition 内の transient state のままとし、database、backup、telemetry、永続ログには保存しない。

通常の蔵書一覧では、Web 蔵書の表紙上に同じ `refreshState.items` から得た簡潔な status を表示する。表示は `待機中` / `取得中` / `更新あり` / `変更なし` / `要確認` / `失敗` に限定し、custom extractor の URL pattern や取得値などの詳細診断は設定画面の直近結果欄だけに表示する。設定画面の診断 preview 自体には一覧用 status overlay を重ねない。

新しい再取得を開始した場合は従来どおり `WebLibraryRefreshUiState` 全体を新しい実行対象で置き換える。履歴機能や durable job history はこの判断には含めない。

### WebView 全体 timeout 時も execution snapshot を失わない

metadata 用 WebView は custom extractor の execution 状態が変わるたびに最新 snapshot を呼び出し側へ伝える。ページ generation が変わった場合は旧 snapshot を破棄し、新しいページについて再判定する。

custom extractor の途中状態として次を追加する。

- `MATCHED`: 現在のページ URL または元の requested URL が取得ルールに一致し、custom script の開始条件を待っている状態
- `RUNNING`: custom script の `evaluateJavascript` を開始し、custom Promise の結果確定を待っている状態

`onPageStarted` では URL pattern に一致する rule があれば `MATCHED` を記録する。ADR-0177 以降はページ全体の読み込み完了を待たず、page commit 後に DOM が利用可能になった時点で custom script を開始する直前に `RUNNING` へ遷移する。`onPageFinished` は未開始時の fallback trigger とする。Promise の結果が確定した後は従来の `APPLIED`、`TIMED_OUT`、`REJECTED` 等の最終 status へ更新する。

WebView 全体が timeout した時点の最新 snapshot を timeout exception の内部診断情報として保持する。静的 metadata が利用可能で fallback する場合、Library Data は fallback reason とともにその execution を `WebLibraryMetadataRefreshResult` へ伝播する。

これにより、次を再取得 UI から区別できる。

- execution がない: timeout 時点までに一致する取得ルールを確認できていない
- `MATCHED`: 取得ルールには一致したが、custom script 開始前に WebView 全体 timeout
- `RUNNING`: custom script は開始したが、結果確定前に WebView 全体 timeout
- `APPLIED`: custom function の値取得は完了し、その後の rendered metadata 処理中に WebView 全体 timeout

`MATCHED` / `RUNNING` では custom function の結果は未確定なので、`extractedTitle` / `extractedThumbnailUrl` を推測して設定してはならない。

### diagnostic value は新たに永続化・記録しない

`extractedTitle` / `extractedThumbnailUrl` と途中 status は再取得操作中の transient result とする。次の保存先を追加しない。

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
- custom extraction 完了前の timeout でも、rule 不一致、script 開始前、script 実行中を区別できる。
- 再取得成功によって対象一覧から蔵書が消えても、その実行の結果は独立した結果欄で確認できる。
- 一覧から個別再取得を開始した場合も、対象の表紙上で進行状態と簡潔な完了状態を確認できる。
- `WebLibraryMetadataExtractorExecution` の責務は実行 status だけでなく、再取得時の transient custom result / phase snapshot まで含む。
- この ADR が追加する diagnostic value と実行結果一覧自体は永続化せず、backup schema や WebView security boundary を変更しない。rule 自体の timeout 永続化は ADR-0177 で別途追加する。

## Verification

- custom metadata から `WebLibraryMetadataExtractorExecution` に title / thumbnailUrl が保持される unit test を追加する。
- WebView failure が取得済み execution を伴う場合、静的 fallback result に execution と fallback reason の両方が伝播する unit test を追加する。
- `RUNNING` の途中 execution も WebView 全体 failure 後の静的 fallback result へ伝播する unit test を追加する。
- UI が final `LibraryBook` の値ではなく execution の extracted value を表示する unit test を追加する。
- custom function が返していない field を「なし」と表示する unit test を追加する。
- `MATCHED` と `RUNNING` の timeout 診断がそれぞれ「開始前」「結果確定前」と区別して表示される unit test を追加する。
- 一覧用の各 refresh status が簡潔な表示ラベルへ変換されることを unit test する。
- 直近結果の折りたたみ表示が先頭 10 件を返し、展開時は同一実行の全件を返す unit test を追加する。
- 再取得対象カードと独立した結果セクションが `refreshState.items` を表示すること、および通常一覧の Web 表紙が同じ state の簡潔な status を表示することを code review / CI で確認する。
- test / docs に実サイト URL、credential、token、ユーザー固有情報が含まれないことを public repository verification で確認する。
