// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:33d83599a90fbf02fd9dbd95a0dbcb5846fc2263e5b830ad43e8086900f69f17 -> unsubscribeWithoutRetainedState
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:33d83599a90fbf02fd9dbd95a0dbcb5846fc2263e5b830ad43e8086900f69f17 -> unsubscribeWithRetainedState
// - VIDEO-SUBSCRIPTION-RETENTION-001@sha256:33d83599a90fbf02fd9dbd95a0dbcb5846fc2263e5b830ad43e8086900f69f17 -> retainedStateRequiresCatalog
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
    unsubscribeWithoutRetainedState,
    unsubscribeWithRetainedState,
    idle,
  }

  val membershipRequiresCatalog =
    not(membership) or catalog

  val retainedStateRequiresCatalog =
    not(saved or playbackHistory) or catalog

  val safety =
    membershipRequiresCatalog and
    retainedStateRequiresCatalog
}
