# ADR-0023: AI チャット応答は保存時の Markdown を Compose で直接描画する

- Status: Accepted
- Date: 2026-08-11

## Context

ローカル AI のチャット応答は Markdown を含むことが多いが、現在の `AiChatScreen` は応答本文を Material 3 の `Text` へそのまま渡している。このため、見出し、箇条書き、コードブロック、引用、リンクなどが Markdown 記号を含むプレーンテキストとして表示され、長い応答ほど読みづらい。

チャット履歴は端末内に保存され、ストリーミング中の応答も同じ画面で逐次表示される。表示上の都合で保存形式を HTML などへ変換すると、将来別の UI で再利用しづらくなり、ストリーミング途中の不完全な Markdown も扱いにくくなる。

また、AI が生成した内容は未信頼入力として扱う必要がある。HTML/WebView に変換して表示すると、不要な HTML 解釈や URL scheme の取り扱いが増え、単なるテキスト装飾に対して責務が大きすぎる。

## Decision

- AI が生成したチャット本文は、従来どおり Markdown を含む生の文字列として保存する。
- Markdown の解釈と装飾は `:feature:chat:ui` の表示時だけ行う。
- ユーザー自身が入力したメッセージは、入力内容をそのまま確認できるようプレーンテキスト表示を維持する。
- Markdown 表示は Compose の UI 要素で直接構成し、HTML 変換や WebView は使用しない。
- 初期実装では、チャット応答で頻出する以下の Markdown を対象とする。
  - 見出し
  - 段落
  - 箇条書き / 番号付きリスト / task list
  - 引用
  - fenced code block と言語ラベル
  - 水平線
  - Markdown table
  - 太字、斜体、取り消し線、inline code
  - Markdown link と autolink
- ストリーミング途中で fenced code block が閉じていない場合でも、それまでの本文をコードブロックとして描画できるようにする。
- Markdown link のクリック対象は `http://`、`https://`、`mailto:` に限定する。`javascript:` や `intent:` など、それ以外の scheme はリンクとして有効化しない。
- Compose の `LinkAnnotation.Url` を利用し、URL をクリック可能なテキストとして表現する。
- Markdown parser / renderer は `:feature:chat:ui` 内の presentation concern として保持し、domain や data module には依存を追加しない。
- 現時点では第三者 Markdown UI ライブラリを追加せず、チャット表示に必要な範囲を小さな renderer として実装する。将来、CommonMark 互換性や画像・複雑なネスト等が必要になった場合はライブラリ導入を再評価する。

## Consequences

### Positive

- AI 応答の見出し、リスト、コード、表などが視覚的に分離され、プレーンテキストより読みやすくなる。
- 保存形式を変更しないため、既存チャット履歴の migration は不要である。
- ストリーミング中と保存済み応答で同じ renderer を使用できる。
- HTML を実行可能な表示コンテキストへ渡さず、リンク scheme も明示的に制限できる。
- Markdown 表示の責務を chat UI 内に閉じ込められる。

### Negative

- CommonMark 全仕様を実装するものではなく、複雑な nested list、escaped table pipe、画像、raw HTML などは完全には再現しない。
- 独自 renderer のため、対応する Markdown 構文を増やす場合は parser と UI の両方を保守する必要がある。
- table と code block は狭い画面では横スクロールが必要になる場合がある。

## Relationship to other ADRs

- ADR-0003 の feature-first 構成に従い、Markdown renderer は `:feature:chat:ui` に置く。
- ADR-0005 の agent / skill harness により将来チャット応答の種類が増えても、生成内容の保存形式と presentation を分離する方針を維持する。
