# ADR-0039: Kindle の構造化シリーズ情報を補助メタデータとして取り込む

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0017, ADR-0026, ADR-0031
- Supersedes: ADR-0026 の `Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` をシリーズ情報へ利用しない判断

## Context

ADR-0017 では、蔵書のシリーズ表示を「ユーザーの手動設定」と「タイトル末尾からの推定」で構成した。ADR-0026 では Amazon エクスポート内に `Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` が存在することは確認していたが、列スキーマと Kindle 書籍 ASIN との結合規則を実データで検証できていなかったため利用を見送った。

その後、実エクスポートの同 CSV を確認し、`series-ASIN`、`series-product-name`、`item-ASIN`、`item-position-in-series` の意味と結合規則を検証できた。`item-ASIN` は ownership JSON から取得している Kindle 書籍の source ID と結合でき、`item-position-in-series` は 0 始まりである。

## Decision

現在所有している Kindle 書籍の判定は引き続き `Digital.Content.Ownership*.json` のみを正規入力とする。SagaSeries CSV は蔵書を増減させず、ownership で取り込まれた書籍へシリーズ情報を付与する補助メタデータとしてのみ利用する。

シリーズの優先順位は、ユーザーの手動設定、ユーザーによる「シリーズ解除」、Kindle の構造化シリーズメタデータ、ADR-0017 のタイトル推定の順とする。シリーズ解除はタイトル推定だけでなく Kindle メタデータも抑止する。

Amazon 由来のシリーズ情報はユーザー編集状態と分離し、再構築可能な `library_source_series` テーブルへ保存する。保存対象は現在 `library_items` に存在する Kindle source ID のみとし、再インポート時は前回の Kindle source-series 行を置換する。`item-position-in-series` には 1 を加え、Domain の `LibrarySeries.position` では 1 始まりとして扱う。

既存の ownership インポート処理は変更せず、ownership のインポート成功後に同じ URI の InputStream を再度開き、シリーズ CSV だけをストリーミング走査する。ZIP 全体はメモリへ展開しない。ADR-0031 と同様に入れ子 ZIP は最大4階層、全階層合計最大100,000エントリとし、シリーズ CSV は1ファイル25 MB、対象CSV合計50 MBを上限とする。

シリーズ用の2回目の InputStream オープンや走査だけが失敗しても、確定済みの蔵書インポートは成功として扱う。ただし古いシリーズ情報が残らないよう、保存済み Kindle source-series 情報を削除し、UI へシリーズ情報だけ更新できなかった旨を表示する。対象CSVが存在しない場合は0件として置換する。

実ユーザーの Amazon ZIP、CSV内容、ASIN、シリーズ名、アカウント情報はログ、fixture、テストコード、ADRへ保存しない。テストは人工データだけで構成する。既存蔵書へ構造化シリーズ情報を反映するには、この変更後に Kindle ZIP を再インポートする必要がある。

## Consequences

- タイトル表記に依存せず Amazon の構造化シリーズ情報で Kindle 書籍をまとめられる。
- 手動シリーズとシリーズ解除を再インポート後も優先できる。
- ownership の所有判定とシリーズ補助情報の責務を分離できる。
- 大容量 Amazon ZIP を全体メモリ展開せず処理できる。
- Kindle インポート時に同じ ZIP を2回走査するため処理時間とストレージ I/O は増える。
- Amazon がファイル名や列名を変更した場合は追従が必要になる。
- シリーズ情報を得るには変更後の再インポートが必要になる。

## Relationship to existing ADRs

- ADR-0017 の手動設定・シリーズ解除・タイトル推定を維持し、その間に構造化 Kindle メタデータを追加する。
- ADR-0026 の ownership JSON を Kindle 蔵書の正規入力とする判断は維持する。一方、SagaSeries CSV を利用しないという限定的な判断は本 ADR で置き換える。
- ADR-0031 のストリーミング再帰 ZIP 走査、安全上限、実データを fixture に保存しない方針をシリーズ走査にも適用する。
- `docs/adr/0038-android-test-layers-and-e2e.md` の public repository policy に従い、テストは人工データのみを利用する。
- ADR-0003 / ADR-0004 に従い、Amazon 固有の解析・永続化は `feature:library:data` に閉じ込める。
