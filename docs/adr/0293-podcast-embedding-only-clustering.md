# ADR-0293: Podcast news clustering uses embeddings only

## Status

Accepted

## Context

Podcast news grouping offers two alternative algorithms: an older structured-inference event classifier and an on-device embedding-based topic classifier. Mode selection, comparison generation, and separate execution paths increase complexity. The embedding classifier is now the sole intended grouping strategy.

## Decision

- New episode grouping, explicit rebuilds, and recent-archive comparison use the existing embedding-based `PodcastNewsClusterer`. Delete the structured-inference grouping adapter and mode-selection/alternative-generation affordances.
- Default cosine similarity threshold is 0.95. Keep the existing device-wide 0.70–0.99, 0.01-step setting and existing user overrides. Reset uses the new default.
- Continue to retain historic program/episode `clustering_mode` database columns and `EVENT` values as read compatibility metadata, avoiding an unnecessary schema migration. Always select the embedding path for newly classified articles even when existing program rows contain the legacy mode. Reserved episode chapter partitions remain unchanged on retry/resume.
- An archive comparison groups archived and new entries by embeddings and puts entries belonging to a mixed cluster in the previously-covered collection. The similarity threshold expresses topical similarity, not proven event identity; preserve bounded comparison and fail-open on errors.
- Always generate per-chapter scripts with the multi-article, distinct-events prompt. No alternate mode generation or comparison from the UI.
- Existing saved episodes remain playable; do not rewrite their article or chapter snapshots.

## Compatibility and verification

- Existing queued and interrupted episodes keep their stored clusters; explicit rebuild applies current embedding settings.
- Preference overrides and backup remain unchanged; only the default changes.
- Cover embedding grouping, threshold default, archive comparison, generation routing, and absence of old UI actions with regression tests.

## Relations

- Supersedes the mode-selection and comparative-generation decisions in ADR-0282.
- Refines ADR-0259, ADR-0268, and ADR-0288.
