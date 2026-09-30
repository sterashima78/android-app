// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:fc91d27e5977f5d06144cacefe4b7b7ff09568591d0eea55c15c087ebf3c5316 -> unsubscribeWithoutRetainedState
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:fc91d27e5977f5d06144cacefe4b7b7ff09568591d0eea55c15c087ebf3c5316 -> unsubscribeWithRetainedState
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:fc91d27e5977f5d06144cacefe4b7b7ff09568591d0eea55c15c087ebf3c5316 -> retainedStateRequiresCatalog
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:fc91d27e5977f5d06144cacefe4b7b7ff09568591d0eea55c15c087ebf3c5316 -> removeSaved
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:fc91d27e5977f5d06144cacefe4b7b7ff09568591d0eea55c15c087ebf3c5316 -> catalogHasRetentionReason
//
// Abstraction:
// One provider video is modeled. "Playback history" is reduced to a durable
// playback-state flag. Unsubscribing always removes provider membership; catalog
// retention depends only on saved/playback state.

module video_subscription_retention {
  var membership: bool
  var catalog: bool
  var saved: bool
  var playbackHistory: bool

  action init = all {
    membership' = true,
    catalog' = true,
    saved' = false,
    playbackHistory' = false,
  }

  action saveVideo = all {
    catalog,
    membership' = membership,
    catalog' = catalog,
    saved' = true,
    playbackHistory' = playbackHistory,
  }

  action recordPlayback = all {
    catalog,
    membership' = membership,
    catalog' = catalog,
    saved' = saved,
    playbackHistory' = true,
  }

  action removeSaved = all {
    saved,
    membership' = membership,
    catalog' = if (membership) true else playbackHistory,
    saved' = false,
    playbackHistory' = playbackHistory,
  }

  action unsubscribeWithoutRetainedState = all {
    membership,
    not(saved),
    not(playbackHistory),
    membership' = false,
    catalog' = false,
    saved' = saved,
    playbackHistory' = playbackHistory,
  }

  action unsubscribeWithRetainedState = all {
    membership,
    (saved or playbackHistory),
    membership' = false,
    catalog' = true,
    saved' = saved,
    playbackHistory' = playbackHistory,
  }

  action idle = all {
    membership' = membership,
    catalog' = catalog,
    saved' = saved,
    playbackHistory' = playbackHistory,
  }

  action step = any {
    saveVideo,
    recordPlayback,
    removeSaved,
    unsubscribeWithoutRetainedState,
    unsubscribeWithRetainedState,
    idle,
  }

  val membershipRequiresCatalog =
    not(membership) or catalog

  val retainedStateRequiresCatalog =
    not(saved or playbackHistory) or catalog

  val catalogHasRetentionReason =
    not(catalog) or membership or saved or playbackHistory

  val safety =
    membershipRequiresCatalog and
    retainedStateRequiresCatalog and
    catalogHasRetentionReason
}
