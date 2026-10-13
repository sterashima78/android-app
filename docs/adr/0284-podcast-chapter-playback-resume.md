# ADR-0284: Podcast episode chapter playback resume

- Status: Accepted
- Date: 2026-10-13
- Refines: [ADR-0255](0255-podcast-playback-chapters.md), [ADR-0256](0256-podcast-episode-archive-delete.md)

## Context

Podcast chapters use the shared Audio queue, but Audio playback state is process-local. Reopening a READY episode currently starts from the first chapter, even after playback was interrupted. Archived episodes should not retain or resume playback checkpoints.

## Decision

- Podcast owns an optional last-playing chapter number on its episode row. It is not an Audio queue checkpoint or a millisecond playback position.
- Only READY episodes can update this value. Podcast's application-scope runtime observes Audio's current item identity and persists chapter changes, including while the Podcast UI is absent. It ignores other Audio items and the unstructured full-episode fallback.
- Archive and delete clear the checkpoint atomically with the lifecycle change. Restoration starts from the beginning unless the user later plays a chapter.
- When a READY episode is started, the Podcast UI requests the stored chapter as the initial Audio item if it is still present; invalid or missing checkpoints fall back to the first chapter. Archived episodes always start from the beginning.
- Audio may prioritize TTS preparation at a requested queue index, while retaining the original playlist order once earlier media items are available. Default playback continues starting from index zero.
- The checkpoint is included in existing database snapshot backups; no new cache, playback service, external communication, or background worker is added.

## Compatibility

Add a nullable episode column through the next additive database migration. Legacy episodes have no checkpoint and start at the first chapter. Rollback to the earlier database schema requires restoring an older database snapshot; downgrades are not supported.

## Verification

Cover chapter progress persistence, archive / restore / delete behavior, migration, Audio preparation order, and fallback playback. Confirm build, architecture, public repository validation and CI before merge.
