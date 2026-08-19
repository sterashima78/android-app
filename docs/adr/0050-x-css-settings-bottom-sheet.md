# ADR-0050: X カスタム CSS 設定はボトムシートで表示する

- Status: Accepted
- Date: 2026-08-11

## Context

X のカスタム CSS 設定では、有効/無効、3つの CSS セット切り替え、セット間コピー、CSS 本文の編集を1つの画面で扱う。従来は Material 3 の `AlertDialog` を利用していたが、CSS 本文の編集領域を含むため表示可能な縦方向の領域が小さく、スマートフォン上で編集しにくい。

設定内容と保存方式は ADR-0115 および ADR-0022 で定義済みであり、今回の要件は保存モデルではなく表示コンテナの改善である。

## Decision

- X カスタム CSS 設定は `AlertDialog` ではなく Material 3 の `ModalBottomSheet` で表示する。
- シートは部分展開を使用せず、開いた時点で大きく展開して CSS 編集領域を確保する。
- シート内の主要コンテンツはスクロール可能とし、CSS 入力欄は従来より大きな最小高さを確保する。
- 「キャンセル」と「保存」はスクロール領域の外側に配置し、長い CSS を編集している場合でも操作しやすくする。
- ソフトウェアキーボード表示時にも操作領域が隠れにくいよう IME inset を考慮する。
- 保存、キャンセル、CSS セット切り替え、セット間コピーなどの既存の状態管理と永続化仕様は変更しない。
- CSS 設定の導線は X 画面右上の歯車に集約し、アプリ共通の設定画面には X セクションを置かない。

## Consequences

### Positive

- スマートフォンでも CSS 編集に使える縦方向の領域が広くなる。
- X 画面上で WebView を離れずに一時的な編集 UI を開ける。
- 保存・キャンセル操作が CSS 本文の長さに依存せずアクセスできる。
- X 固有の設定導線が X feature の利用文脈に閉じる。

### Negative

- `ModalBottomSheet` は Material 3 の Experimental API を利用するため、Compose Material 3 更新時に API 変更の影響を受ける可能性がある。
- 小さい画面やソフトウェアキーボード表示中は、設定項目の一部をスクロールして参照する必要がある。

## Relationship to other ADRs

- ADR-0115 の X WebView / CSS 注入方針は変更しない。
- ADR-0022 の3セット保存モデル、コピー、永続化方針は変更しない。
- ADR-0107 の repository 明示注入方針に従い、X feature host が CSS 設定シートへ repository を渡す。
