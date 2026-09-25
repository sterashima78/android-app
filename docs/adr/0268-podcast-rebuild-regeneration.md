# ADR-0268: ニュースポッドキャストの再生成を現在条件での再構築にする

- Status: Accepted
- Date: 2026-09-25
- Refines: [ADR-0257](0257-podcast-chapter-generation-jobs.md), [ADR-0259](0259-podcast-news-clustering.md), [ADR-0267](0267-podcast-news-exclusion-filter.md)
- Refined by: [ADR-0269](0269-podcast-durable-foreground-generation.md)

## Context

ニュースポッドキャストの既存episodeには「同じ記事で再生成」があり、保存済みの記事・cluster snapshotを固定したまま原稿だけを再生成している。

番組へ自然文のニュース除外条件を追加した後も、この再生成経路は除外条件を再評価せず、cluster境界も変更しない。そのため、番組設定を変更して不要なニュースを除外しても、既存episodeの再生成には反映されない。

利用目的として原稿だけを同じ入力から再生成する必要はなく、明示的な再生成では現在の番組条件を反映した内容へ作り直す方が一貫している。

## Decision

### 明示的な再生成は保存済み記事からepisodeを再構築する

READYまたはFAILED episodeへの再生成ではfeedを再取得しない。episodeに保存済みの記事snapshotを候補として、現在の番組設定を次の順序で適用する。

1. 保存済み記事snapshotを候補へ戻す。
2. 現在の除外条件があれば `PodcastNewsExcluder` で再判定する。
3. 除外後の候補を現在の生成providerで `PodcastNewsClusterer` へ渡し、cluster境界を再計算する。
4. 同じepisode IDのarticle / cluster snapshotを再構築し、chapter checkpointを新しいattemptの `PENDING` とする。
5. 新しいclusterから原稿を生成し、完了時にepisode scriptを置き換える。

feed URLへの再アクセスや新しいfeed entryの取り込みは行わない。再生成対象はそのepisodeがすでに保持している記事だけとする。

### 除外履歴は追加しない

再生成時に除外された記事は、すでにその番組で `podcast_consumed_articles` に記録済みである。新規episode候補へ再利用されないため、再生成で `podcast_excluded_articles` へ重複記録しない。

### 再構築開始前までは既存episodeを保持する

除外判定またはcluster判定がfallbackする場合は、新規生成と同じfail-open / fallback規則を適用する。

現在の除外条件によって保存済み記事が全件除外された場合は、空episodeへ置き換えず再生成を開始しない。既存の再生可能なepisode内容を保持し、対象記事がないことを利用者へ返す。

候補が残って再構築を開始する場合は、Repository transactionで同じepisode IDの記事・cluster snapshotを置き換え、既存scriptを消去して `GENERATING` へ戻す。以降の失敗は通常生成と同じ `FAILED` として扱い、旧原稿へ戻さない。これにより古い原稿と新しい記事対応が混在しない。

## Consequences

- 番組の除外条件変更が既存episodeの再生成にも反映される。
- 再生成時に同一ニュースclusterも現在のモデル判断で再計算される。
- 原稿だけを固定入力で再生成する操作は廃止する。
- feed再取得を行わないため、過去episodeへ後から新しい記事が混入しない。
- 作り直しで除外された記事はepisode snapshotから外れ、後から条件を緩めても自動復帰しない。これは除外済みentryを自動復活させない既存方針と揃える。
- database schema、durable table、backup format、provider boundaryは変更しない。

## Verification

- Domain testで、現在の除外条件が再生成へ適用されることを確認する。
- Domain testで、再生成時にcluster境界を再計算することを確認する。
- Domain testで、全記事除外時に既存episodeを保持して再生成を開始しないことを確認する。
- Repository testで、同一episode IDの記事snapshotを再構築し旧scriptを消去して `GENERATING` へ戻すことを確認する。
- architecture verification、unit test、lint、public repository verificationを実行する。
