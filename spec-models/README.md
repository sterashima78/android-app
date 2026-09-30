# Formal specification models

このdirectoryは、`docs/spec/*.md` の自然言語仕様から選択した性質を機械検査するmodelを保持する。

- `quint/`: 状態遷移、順序、safety / temporal property
- `alloy/`: 構造、relation、cardinality、uniqueness

運用ルールは [`docs/formal-modeling.md`](../docs/formal-modeling.md) を正本とする。

## Current coverage

| Natural-language specification | Quint | Alloy |
| --- | --- | --- |
| backup schedule / background execution | [`quint/backup_schedule.qnt`](quint/backup_schedule.qnt) | [`alloy/backup_schedule.als`](alloy/backup_schedule.als) |

modelは自然言語仕様の置き換えではない。各spec documentから対応modelとcoverageへ直接linkする。
