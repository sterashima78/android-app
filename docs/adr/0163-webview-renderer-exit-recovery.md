# ADR-0163: WebView renderer 終了を app process 障害と分離して復旧する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0011](0011-mail-html-rendering.md), [ADR-0143](0143-web-library-source-and-bookmark-transfer.md), [ADR-0149](0149-sanitize-shareable-crash-diagnostics.md), [ADR-0154](0154-web-library-rendered-metadata-fallback.md), [ADR-0161](0161-android17-main-process-memory-diagnostics.md)

## Context

Android WebView は renderer を application main process とは別の sandbox process で実行する。Android がメモリ圧迫時にこの renderer を終了すると、`ApplicationExitInfo` には `REASON_LOW_MEMORY` の終了として残ることがあるが、これは Mosaic main process や Mosaic が明示的に所有する subprocess の memory leak を直接示すものではない。

既存の `StartupCrashStore` は package に関連する historical process exit 全体から memory-related exit を選択していたため、WebView sandbox renderer の終了を `Mosaic process exit report` として表示できた。また、より前に存在する app-owned memory exit より新しい WebView renderer exit が診断対象として選ばれる可能性もあった。

同時に、Mail HTML、X viewer、Web collector、Web Library rendered metadata fallback、MangaONE renderer の各 `WebViewClient` は renderer termination を明示的に処理していなかった。Android WebView の契約では renderer が終了した `WebView` を再利用せず、`onRenderProcessGone` で終了を処理し、必要なら新しい `WebView` を生成する必要がある。renderer crash の場合は同じ内容を無条件に即時再読込すると crash loop を作る可能性がある。

## Decision

### 1. startup process-exit diagnostics は app-owned process だけを障害候補にする

`StartupCrashStore` が process-exit report として選択する process name は次に限定する。

- application package name と完全一致する main process
- `applicationPackageName:` で始まる、manifest 等で Mosaic が明示的に所有する subprocess

WebView sandbox process や dependency が所有する別 package name の process は Mosaic の process-exit report 対象にしない。

`LAST_EXIT_TIMESTAMP_KEY` 自体は従来通り未確認履歴全体の最大 timestamp まで進める。外部 renderer の終了を次回起動時に繰り返し評価しない一方、その履歴範囲に app-owned memory exit があれば app-owned exit の中から最新を選ぶ。

### 2. production WebView は renderer termination を必ず処理する

custom `WebViewClient` を持つ production WebView は `onRenderProcessGone` を override し、終了を処理した場合は `true` を返す。

対象は現在次の5系統である。

- Mail HTML rendering
- X viewer
- secure Web collector
- Web Library rendered metadata fallback
- MangaONE page renderer

renderer が終了した既存 `WebView` は再利用しない。通常 dispose 用の `stopLoading` や navigation 操作を終了後の renderer に対して継続せず、所有 UI / coroutine が参照を切り替えたうえで `destroy` する。

### 3. system low-memory kill と renderer crash で復旧方法を分ける

`RenderProcessGoneDetail.didCrash()` が `false` の場合は system による low-memory kill として扱う。

- Mail HTML は同じローカル HTML document から新しい `WebView` を生成する。
- X viewer は最後に正常表示できた X URL から新しい `WebView` を生成する。
- Web collector は collection の途中状態を破棄し、許可済みの現在 URL から新しい `WebView` を生成する。
- Web Library と MangaONE の headless renderer は現在の取得処理を明示的な retryable failure として終了し、呼び出し側の通常 retry に委ねる。

`didCrash()` が `true` の場合は、同じ remote page の自動即時再読込を行わない。

- X viewer と Web collector は明示的な retry UI を表示し、それぞれ X home / collector start URL から再開する。
- Mail HTML は remote network load を行わないため、HTML表示失敗を plain UI で示し画面再表示時の再生成に委ねる。
- headless renderer は現在処理を失敗として終了する。

これにより system の正常な memory reclaim からは復旧しつつ、renderer 自体または page content が原因の crash loop を避ける。

### 4. source regression test で WebView の追加を監査する

Android framework の renderer termination を通常 JVM test だけで直接発火することは難しいため、production source を走査する source regression test を追加する。

`WebView(` と custom `webViewClient` を持つ production Kotlin source が `onRenderProcessGone` を持つことを検査し、今後 WebView が追加された際の対応漏れを CI で検出する。

個別の business semantics は既存の unit test と、process ownership の pure predicate test で検証する。

## Consequences

### Positive

- WebView renderer の low-memory eviction が Mosaic main process の memory failure として表示されなくなる。
- explicit app subprocess の memory diagnostics は維持される。
- renderer が終了した `WebView` の再利用を避け、host application まで巻き込む障害を抑制できる。
- system low-memory kill では UI を継続でき、renderer crash では crash loop を避けられる。
- 新しい production WebView の termination handler 追加忘れを CI で検出できる。

### Negative

- UI WebView は renderer の low-memory kill 後に navigation / scroll 等の transient state を完全には復元しない。
- headless renderer は途中から再開せず、現在の取得単位を失敗させて retry するため再取得コストが発生する。
- source regression test は Android framework の実動作そのものを検証するものではなく、handler の存在を guardrail として固定する。

## Verification

- `StartupCrashStoreTest`: main process / explicit subprocess / WebView sandbox process の ownership 判定
- `WebViewRendererTerminationSourceTest`: production WebView の renderer termination handler coverage
- affected feature unit tests
- app unit tests
- release lint
- `verifyArchitecture`
- ADR integrity verification
- public repository verifier

Android 17 実機では WebView renderer が system に回収された場合に Mosaic の startup process-exit report として表示されないこと、表示中 WebView が app process を終了させず新しい WebView または retryable failure へ移行することを確認する。

## Documentation

- `docs/architecture/platform.md` に app-owned process-exit filtering と WebView renderer lifecycle を現在形として追記する。
- ADR index に本 ADR を追加する。

## Public repository review

renderer termination handling では URL、Cookie、Web page content、mail body、collector payload 等を diagnostic report に追加しない。ユーザー由来の process-exit report や実 URL も test fixture / ADR へ保存しない。UI と exception message は renderer の終了種別だけを示す一般的な文言とする。

## References

- Android Developers: `WebViewClient.onRenderProcessGone`
- Android Developers: Handle WebView renderer process termination
- Android Developers: `RenderProcessGoneDetail`
- Android Developers: `ApplicationExitInfo.REASON_LOW_MEMORY`
