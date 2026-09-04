# ADR-0213: X 表示カスタマイズにユーザー JavaScript とフルスクリーン設定を導入する

- Status: Accepted
- Date: 2026-08-28
- Supersedes: ADR-0050
- Amends: ADR-0115, ADR-0211

## Context

X WebView の表示調整では、CSS による要素の非表示だけでなく、タブ選択のようにページ上の操作を伴うカスタマイズが必要になる場合がある。CSS は表示状態を変更できるが、DOM event を発火して X の内部状態を切り替える用途は表現できない。

一方、ADR-0206 から ADR-0210 で導入した app-owned semantic DOM rule は、X の DOM 構造をアプリが推測して専用モデルへ変換するため、実 DOM variation への追従コストと失敗経路が大きかった。ADR-0211 ではその仕組みを撤回し、要素 picker を CSS 生成へ戻した。この判断は維持する。

また、ADR-0050 で採用した `ModalBottomSheet` は、CSS のような長いテキストを内部スクロールしながら編集する際に swipe-dismiss gesture と競合し、意図せず閉じやすい。JavaScript 編集欄の追加により設定内容はさらに長くなるため、drag gesture を持たない表示コンテナが必要である。

## Decision

### User-authored JavaScript

X 表示カスタマイズに、ユーザーが直接編集する JavaScript を追加する。

- JavaScript は単一の文字列として保存し、CSS とは独立した有効/無効フラグを持つ
- 初期状態は無効かつ空文字列とし、既存ユーザーの動作を変更しない
- 保存先は既存の `x_viewer_preferences` とし、`custom_javascript_enabled` と `custom_javascript` を追加する
- JavaScript は top-level の X / Twitter ページの `onPageFinished` 後に、専用 WebView の `evaluateJavascript` で実行する
- X 以外へ遷移したページでは実行しない
- X 画面には表示中ページを明示的に再読み込みする操作を提供し、現在の WebView に対して `reload()` を実行する
- 手動再読み込み後も既存の `onPageFinished` 経路を利用して、保存済みのカスタム CSS と JavaScript を再適用する。これにより設定変更の再実行と、JavaScript が既に発生させた副作用のリセットを利用者が明示的に行える
- Android の `addJavascriptInterface` やその他の native bridge は公開しない
- script の内容、実行結果、例外内容をアプリのログやクラッシュ診断へ出力しない
- CSS の3セット、CSS の有効状態、要素 picker による CSS rule 生成は変更しない
- ADR-0211 で廃止した semantic DOM rule、fingerprint persistence、app-owned `MutationObserver` runtime は再導入しない

ユーザー JavaScript 自身が DOM observer や event handler を登録することは許容する。それらはアプリが X の構造を意味解釈して生成する rule ではなく、ユーザーが明示的に所有する script として扱う。

JavaScript は任意コードであるため、実行後の DOM や X の状態を一般的にロールバックすることはできない。無効化や script 変更は将来のページ読込に対して適用し、すでに発生した副作用を確実に消す場合はページ再読込を必要とする。

### Full-screen customization dialog

ADR-0050 の `ModalBottomSheet` を廃止し、X 画面前面を覆う full-screen `Dialog` へ置き換える。

- `usePlatformDefaultWidth = false` で画面全体を編集領域として使う
- outside tap では dismiss しない
- vertical swipe による dismiss gesture を持たせない
- CSS / JavaScript の編集領域だけを縦スクロール可能にする
- 「キャンセル」と「保存」はスクロール領域の外に固定する
- system safe drawing inset と IME inset を考慮する
- system back による dismiss は許可する
- X 画面右上の設定導線は維持する

## Architecture

ADR-0102 の X feature ownership を維持する。

- `:feature:x:domain`: CSS と JavaScript の永続設定、および注入対象文字列の有効判定
- `:feature:x:data`: SharedPreferences への CSS / JavaScript 保存
- `:feature:x:ui`: full-screen editor と WebView への CSS / JavaScript 注入

既存の `XViewerCssSettings` / `XViewerCssRepository` という名称は変更範囲を抑えるため今回は維持する。設定責務がさらに CSS 以外へ広がる場合は、別変更で customization-oriented な名称へ改名する。

## Privacy and public repository

JavaScript には利用者の X 上の DOM、URL、表示内容を扱うコードが含まれる可能性がある。そのため script 本文は端末内の SharedPreferences だけに保存し、自動的な export、ログ、repository への書き出しは行わない。

repository とテスト fixture には、DOM へ synthetic な属性を設定する程度の架空 script のみを使用し、実際の List 名、アカウント、URL、cookie、token を含めない。

ユーザー JavaScript は X Web page と同じ実行環境で page-visible data へアクセスできる。この能力は明示的な opt-in 設定として提供し、初期状態を無効とする。native bridge を公開しないことで Android 側の app API へ実行権限を拡張しない。

## Consequences

### Positive

- CSS では表現できないタブ選択などの状態ful なカスタマイズを、アプリ固有機能を増やさず利用者が記述できる
- X の DOM 変更に対する対応を app-owned semantic rule model へ取り込まずに済む
- JavaScript は設定画面から直接確認、編集、無効化できる
- full-screen editor により、長い CSS / JavaScript のスクロールが dismiss gesture と競合しない
- 既存 CSS 3セットと要素 picker の動作を維持できる

### Negative

- 任意 JavaScript は X ページの表示や動作を壊す可能性がある
- user script の副作用は一般的に自動 rollback できない
- script は X の DOM 変更に依存し得るため、利用者による保守が必要になる
- 現在の `XViewerCssSettings` / `XViewerCssRepository` という名称と実際の責務にずれが生じる

## Alternatives

### 「最初の固定タブを選択」の専用機能

却下。今回の用途には使えるが X の具体的 DOM 構造をアプリ機能として再び持つことになり、ADR-0211 の rollback 方針と逆行する。

### app-owned semantic DOM rule を再導入する

却下。実端末の DOM variation で複数回失敗しており、安定した識別根拠がない。ユーザー JavaScriptは明示的な escape hatch とし、アプリが意味的な X DOM model を持たない。

### ModalBottomSheet を維持する

却下。内部の長い編集領域をスクロールすると sheet dismiss gesture と競合する。設定 UI は画面全体を覆う drag-dismiss のない Dialog とする。

## Relationship to other ADRs

- ADR-0050 の bottom-sheet presentation を置き換える
- ADR-0115 の dedicated X WebView、CSS customization、native JavaScript bridge 非公開方針を維持しつつ user-authored JavaScript を追加する
- ADR-0211 の semantic DOM rule rollback は維持し、CSS-only persistence の制約だけを緩和する
- ADR-0102 の X UI / Domain / Data ownership を維持する
- ADR-0136 の公開 repository content verification を維持する
