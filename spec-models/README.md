# Formal specification models

このdirectoryは、`docs/spec/*.md` の自然言語仕様から選択した性質を機械検査するmodelを保持する。

- `quint/`: 状態遷移、順序、safety / temporal property
- `alloy/`: 構造、relation、cardinality、uniqueness

運用ルールは [`docs/formal-modeling.md`](../docs/formal-modeling.md) を正本とする。

## Current coverage

| Natural-language specification | Quint | Alloy |
| --- | --- | --- |
| backup schedule / background execution | [`quint/backup_schedule.qnt`](quint/backup_schedule.qnt) | [`alloy/backup_schedule.als`](alloy/backup_schedule.als) |
| backup restore eligibility | [`quint/backup_restore.qnt`](quint/backup_restore.qnt) | — |
| backup archive membership | — | [`alloy/backup_archive.als`](alloy/backup_archive.als) |
| background request cleanup | [`quint/background_request_cleanup.qnt`](quint/background_request_cleanup.qnt) | — |
| podcast episode lifecycle | [`quint/podcast_episode_lifecycle.qnt`](quint/podcast_episode_lifecycle.qnt) | — |
| Web library / bookmark durable ownership | — | [`alloy/web_library_ownership.als`](alloy/web_library_ownership.als) |
| Summary task lifecycle / duplicate prevention | [`quint/summary_task_lifecycle.qnt`](quint/summary_task_lifecycle.qnt) | [`alloy/summary_task_uniqueness.als`](alloy/summary_task_uniqueness.als) |
| RSS recommendation revision queue | [`quint/rss_recommendation_queue.qnt`](quint/rss_recommendation_queue.qnt) | — |
| Podcast chapter checkpoint / bounded retry | [`quint/podcast_chapter_checkpoint.qnt`](quint/podcast_chapter_checkpoint.qnt) | — |

modelは自然言語仕様の置き換えではない。各spec documentから対応modelとcoverageへ直接linkする。
