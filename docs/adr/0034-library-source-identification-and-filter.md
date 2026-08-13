# ADR-0034: 蔵書一覧では由来サービスを明示して同じ軸でフィルタする

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0013

## Context

ADR-0013 では `LibraryBook.source` と `LibrarySource` により Google Play Books、Kindle、Audible をサービス非依存の蔵書モデル上で区別している。

複数サービスの蔵書を同じ「全体」「シリーズ」画面へ表示すると、同一・類似タイトルが混在した際にどのサービス由来かを一覧から判断しづらい。また、特定サービスだけを確認するための絞り込み手段がなかった。

## Decision

- 書籍カードには常に `LibrarySource.label` を表示し、フィルタ未使用時でも由来を判別できるようにする。
- 「全体」と「シリーズ」は共通の由来フィルタ状態を使う。
- フィルタ候補は「すべて」と `LibrarySource.entries` から生成し、Google Play Books / Kindle / Audible の追加・名称変更とUI定義を二重管理しない。
- フィルタは表示上の絞り込みだけとし、Repository、DB、同期・インポート済みデータは変更しない。
- 「設定」の非表示蔵書にも同じ書籍カードを使うため由来表示は行うが、復元作業の対象を誤って隠さないよう設定画面にはフィルタを適用しない。

## Consequences

### Positive

- 混在一覧でも各書籍の由来を直接確認できる。
- Google Play Books / Kindle / Audible の蔵書だけを「全体」「シリーズ」で確認できる。
- 既存の `LibrarySource` を利用するためDBマイグレーションや再インポートは不要になる。
- フィルタ後にシリーズを構築するため、件数と展開内容が選択中サービスの蔵書だけを反映する。

### Negative

- 書籍カードに由来ラベルが1行追加され、縦方向の情報量が増える。
- フィルタ状態は画面状態であり、アプリ再起動後まで永続化しない。

## Relationship to existing ADRs

- ADR-0013 の `LibraryBook.source` / `LibrarySource` を表示・絞り込みの正規軸として利用する。
- サービス固有の取得方式は引き続き data layer に閉じ込め、UIフィルタから同期・インポート処理へ依存を追加しない。
