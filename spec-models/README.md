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
| Library cover prefetch lifecycle | [`quint/library_cover_prefetch_queue.qnt`](quint/library_cover_prefetch_queue.qnt) | — |
| Library metadata normalization lifecycle | [`quint/library_metadata_normalization_lifecycle.qnt`](quint/library_metadata_normalization_lifecycle.qnt) | — |
| Summary task lifecycle / duplicate prevention | [`quint/summary_task_lifecycle.qnt`](quint/summary_task_lifecycle.qnt) | [`alloy/summary_task_uniqueness.als`](alloy/summary_task_uniqueness.als) |
| Knowledge page AI task lifecycle | [`quint/knowledge_page_ai_task_lifecycle.qnt`](quint/knowledge_page_ai_task_lifecycle.qnt) | — |
| RSS recommendation revision queue | [`quint/rss_recommendation_queue.qnt`](quint/rss_recommendation_queue.qnt) | — |
| RSS exclusion-feedback learning | [`quint/rss_feedback_learning.qnt`](quint/rss_feedback_learning.qnt) | — |
| Podcast chapter checkpoint / bounded retry | [`quint/podcast_chapter_checkpoint.qnt`](quint/podcast_chapter_checkpoint.qnt) | — |
| Podcast consumed / excluded entry eligibility | — | [`alloy/podcast_entry_eligibility.als`](alloy/podcast_entry_eligibility.als) |
| Podcast news exclusion partition | — | [`alloy/podcast_exclusion_partition.als`](alloy/podcast_exclusion_partition.als) |
| Podcast news clustering partition | — | [`alloy/podcast_clustering_partition.als`](alloy/podcast_clustering_partition.als) |
| Video saved/folder lifecycle | [`quint/video_saved_folder_lifecycle.qnt`](quint/video_saved_folder_lifecycle.qnt) | — |
| Video subscription retention | [`quint/video_subscription_retention.qnt`](quint/video_subscription_retention.qnt) | — |
| Web video playback privacy boundary | — | [`alloy/video_web_playback_privacy.als`](alloy/video_web_playback_privacy.als) |
| Custom video provider security boundary | — | [`alloy/video_custom_provider_security.als`](alloy/video_custom_provider_security.als) |
| Workout AI background lifecycle | [`quint/workout_ai_task_lifecycle.qnt`](quint/workout_ai_task_lifecycle.qnt) | — |
| Workout review latest-per-day | — | [`alloy/workout_review_uniqueness.als`](alloy/workout_review_uniqueness.als) |
| Workout / health-data one-way boundary | — | [`alloy/workout_health_data_boundary.als`](alloy/workout_health_data_boundary.als) |
| LAN Web bootstrap/session authentication | [`quint/lan_web_auth_lifecycle.qnt`](quint/lan_web_auth_lifecycle.qnt) | — |

modelは自然言語仕様の置き換えではない。各spec documentから対応modelとcoverageへ直接linkする。
