# ADR-0023: AI 生成 Markdown は Compose 共通 renderer で描画する

- Status: Accepted
- Date: 2026-08-11
- Updated: 2026-08-12

## Context

ローカル AI のチャット応答や記事要約は Markdown を含むことが多い。Markdown を Material 3 の `Text` へそのまま渡すと、見出し、箇条書き、コードブロック、引用、リンクなどが Markdown 記号を含むプレーンテキストとして表示され、長い生成結果ほど読みづらい。

当初はチャット応答だけが対象だったため Markdown renderer を `:feature:chat:ui` に置いていた。その後、記事要約も同じ形式の AI 生成 Markdown を表示することになり、feature ごとに parser / renderer を重複させると表示仕様と安全性の制約が分岐する。

チャット履歴と要約結果は Markdown を含む生の文字列として保存される。表示上の都合で保存形式を HTML などへ変換すると、将来別の UI で再利用しづらくなり、ストリーミング途中の不完全な Markdown も扱いにくくなる。

また、AI が生成した内容は未信頼入力として扱う必要がある。HTML/WebView に変換して表示すると、不要な HTML 解釈や URL scheme の取り扱いが増え、単なるテキスト装飾に対して責務が大きすぎる。

## Decision

- AI が生成した本文は、従来どおり Markdown を含む生の文字列として保存する。
- Markdown の解釈と装飾は表示時だけ行い、保存形式や domain / data のモデルは変更しない。
- チャットの AI 応答と記事要約は同じ Markdown renderer を利用する。
- 共通 renderer は横断的な Compose UI capability として `:core:designsystem` に置く。
- `:feature:chat:ui` の `MarkdownMessage` は既存の呼び出し API を維持する薄い adapter とし、共通 renderer へ委譲する。
- ユーザー自身が入力したチャットメッセージは、入力内容をそのまま確認できるようプレーンテキスト表示を維持する。
- Markdown 表示は Compose の UI 要素で直接構成し、HTML 変換や WebView は使用しない。
- 初期実装では、AI 生成結果で頻出する以下の Markdown を対象とする。
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
- parser / renderer のテストも `:core:designsystem` に置き、利用 feature に依存しない表示契約として検証する。
- 現時点では第三者 Markdown UI ライブラリを追加せず、必要な範囲を小さな renderer として実装する。将来、CommonMark 互換性や画像・複雑なネスト等が必要になった場合はライブラリ導入を再評価する。

## Consequences

### Positive

- チャット応答と記事要約で、見出し、リスト、コード、表などの表示が一貫する。
- Markdown parser と安全な URL scheme の制約を一か所で保守できる。
- 保存形式を変更しないため、既存チャット履歴や要約結果の migration は不要である。
- ストリーミング中と保存済み応答、記事要約で同じ renderer を利用できる。
- HTML を実行可能な表示コンテキストへ渡さず、リンク scheme も明示的に制限できる。
- feature 固有の domain / data へ Markdown UI の依存を持ち込まない。

### Negative

- CommonMark 全仕様を実装するものではなく、複雑な nested list、escaped table pipe、画像、raw HTML などは完全には再現しない。
- 独自 renderer のため、対応する Markdown 構文を増やす場合は parser と UI の両方を保守する必要がある。
- table と code block は狭い画面では横スクロールが必要になる場合がある。
- `:core:designsystem` の公開 API に `MarkdownText` が追加される。

## Relationship to other ADRs

- ADR-0003 の `core` を共有 capability として利用する方針に従い、複数 feature から利用される Markdown 表示を `:core:designsystem` に配置する。
- ADR-0003 の依存ルールに従い、`:core:designsystem` は feature に依存せず、chat / summary の UI から一方向に参照する。
- ADR-0005 の agent / skill harness により将来チャット応答の種類が増えても、生成内容の保存形式と presentation を分離する方針を維持する。
