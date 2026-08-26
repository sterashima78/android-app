# ADR-0178: Web Library custom extractor の headless scheduling と Promise watchdog を保証する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0173](0173-web-library-custom-metadata-extractors.md), [ADR-0177](0177-web-library-early-custom-extraction-and-rule-timeout.md)

## Context

ADR-0173 では custom function が返す Promise を最大 10 秒だけ待機し、Android 側が一時 JavaScript state を polling して resolve / reject を回収する方針とした。ADR-0177 では page 全体の読み込みを待たず custom extraction を開始し、site ごとの WebView 全体 timeout を 5〜120 秒で設定できるようにした。

metadata 用 WebView は `Activity` を context に生成するが、画面の View hierarchy には attach しない headless WebView として利用している。一方、ADR-0177 時点の実装は DOM ready の再確認、Promise state の再 polling、固定 metadata の再試行に `WebView.postDelayed` / `View.postDelayed` を利用していた。未 attach の View では delayed action が attach 待ちの run queue に保持され得るため、最初の Promise poll が `pending` になると次の poll が実行されず、custom execution が `RUNNING` のまま rule の WebView 全体 timeout まで残る経路があった。

また、10 秒の Promise deadline と polling は `WebView.evaluateJavascript` で start script を送信した後、その callback が返ってから開始されていた。page JavaScript が長時間 renderer thread を占有する場合や navigation 等により evaluate callback が戻らない場合も、Promise の 10 秒上限ではなく rule の WebView 全体 timeout まで待つことになる。

この状態では WebView 全体 timeout を 60 秒へ延長しても custom result は確定せず、単に待ち時間だけが伸びる。headless WebView の scheduling と、ADR-0173 が意図した「停止した user-authored Promise を10秒で打ち切る」という保証を View attachment / JavaScript callback availability から独立させる必要がある。

## Decision

### headless WebView の delayed task は main Handler が所有する

metadata WebView の delayed native task は `View.postDelayed` を使わず、`Looper.getMainLooper()` に紐づく `Handler.postDelayed` で schedule する。

対象は次を含む。

- `document.readyState` の再確認
- DOM settle delay
- custom Promise state の再 polling
- standard rendered metadata の retry
- `onPageFinished` 後の standard extraction delay
- custom Promise の native watchdog

各 task は既存どおり `completed`、page generation、custom state key を確認して stale execution を無視する。WebView を View hierarchy へ attach する変更は行わない。

### user-authored function の起動を start evaluation から分離する

custom start script は一時 state を `pending` として作成した後、user-authored function の評価と呼び出しを `setTimeout(..., 0)` の別 JavaScript task へ送る。

これにより start script 自体は user-authored function の同期処理を直接実行せず、`evaluateJavascript` の callback が custom function の同期処理に塞がれにくい構造にする。

function contract、実行 page context、`Promise<{ title, thumbnailUrl }>` の形式は変更しない。

### Promise deadline は start dispatch 時点から計測する

Android 側は custom start script を WebView へ送る時点で 10 秒の Promise deadline を確定する。start script の `evaluateJavascript` callback が返った時刻から10秒を数え直さない。

callback が正常に返れば従来どおり一時 state を polling し、Promise が resolve / reject した結果を処理する。polling が正常に進んだまま deadline に達した場合も、従来どおり `TIMED_OUT` として固定 rendered metadata extraction へ fallback する。

### native watchdog を JavaScript 応答から独立して動かす

start dispatch と同時に Android main looper 上へ native watchdog を登録する。watchdog は Promise の 10 秒上限に 1 秒の callback scheduling 猶予を加えた 11 秒で発火する。

その時点で同じ page generation / state key の custom execution がまだ active なら、JavaScript state polling 自体が進行できていないと判断する。

この場合は次を行う。

1. execution status を `TIMED_OUT` に更新し、WebView JavaScript 応答が停止したことを transient diagnostic message に残す。
2. rendered metadata client を `WebLibraryRenderedMetadataException` で終了する。
3. 既に取得済みの静的 HTTP metadata があれば、既存の Library metadata merge path がそれへ fallback する。

JavaScript 応答が停止した状態でさらに固定 rendered extractor の `evaluateJavascript` を待つことはしない。これにより rule の WebView 全体 timeout が 60 秒や120秒に設定されていても、custom script 開始後の JavaScript 応答停止は約11秒で終了する。

custom execution が正常終了、通常の Promise timeout、page navigation、renderer exit、client completion のいずれかに達した場合は active watchdog を main Handler から除去する。

### rule timeout と Promise timeout の責務を分離する

ADR-0177 の rule 別 WebView timeout は、page navigation、DOM 利用可能待ち、および正常な rendered metadata pipeline 全体の上限として維持する。

custom function を開始した後の Promise 実行上限は引き続き10秒であり、rule timeout を長くしてもこの値は延長しない。native watchdog はこの既存 semantics を実際に保証するための補助であり、新しい user setting は追加しない。

### security boundary は変更しない

native JavaScript bridge や WebMessage bridge は追加しない。既存の専用 WebView profile、HTTPS main-frame 制約、file/content access 無効、mixed content 無効、third-party Cookie 無効等は維持する。

watchdog と diagnostic は端末上の transient execution state だけを扱い、実 URL、user-authored function code、metadata 値を repository、永続ログ、telemetry に追加保存しない。

## Consequences

- headless WebView の attach 状態に依存せず、DOM polling / Promise polling / retry が main looper 上で進行する。
- custom start evaluation の callback が返らないケースでも、rule の長い WebView timeout まで待ち続けない。
- user-authored function の同期処理が start evaluation callback を直接塞ぎにくくなる。
- Promise の10秒上限が View attachment や native callback availability に依存しなくなる。
- JavaScript engine 自体が応答しない場合は rendered metadata も取得不能と判断し、既存 static metadata fallback を早期に利用する。
- rule timeout を長くする意味は残り、DOM が利用可能になるまで時間が必要な site には引き続き利用できる。
- native bridge を追加しないため、ADR-0173 の WebView security boundary は維持される。

## Verification

- generated custom start script が user-authored function の評価・呼び出しを `setTimeout(..., 0)` 内で行うことを unit test する。
- native watchdog delay が Promise 10 秒 + scheduling grace 1 秒の 11 秒であることを unit test する。
- headless metadata WebView の delayed native task が `View.postDelayed` ではなく main Handler で schedule されていることを code review と CI で確認する。
- existing Promise resolve / reject / pending parsing tests を維持する。
- existing static metadata fallback diagnostics tests を維持する。
- PR 前に public repository、architecture、test scope、documentation の独立レビューを行う。
