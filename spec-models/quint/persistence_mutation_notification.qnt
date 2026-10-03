// Natural-language specifications:
// - docs/spec/11-persistence.md
//
// Covers:
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> beginDurable
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> beginLocal
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> durableScopeNoChange
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> nestedDurableChange
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> nestedDurableOtherWrapper
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> commit
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> rollback
// - PERSISTENCE-MUTATION-NOTIFICATION-001@sha256:a8df1efce71a7ddf7afb49dee24f01ab419f30d3ac6b8fa572a01e2e38c9a4ce -> notificationOnlyAfterCommittedChange
//
// Abstraction:
// One outer SQLite transaction is modeled. "publish" represents the shared
// transaction scope flag used by DatabaseConnection. Local and durable changes
// are not distinguished in total_changes(); once a durable scope is nested,
// any actual change in that outer transaction causes one persistence-change
// notification after successful commit. Schema/migration raw-writable usage is
// outside this runtime mutation model.

module persistence_mutation_notification {
  val IDLE = 0
  val ACTIVE = 1
  val COMMITTED = 2
  val ROLLED_BACK = 3

  var phase: int
  var changed: bool
  var publish: bool
  var notifications: int

  action init = all {
    phase' = IDLE,
    changed' = false,
    publish' = false,
    notifications' = 0,
  }

  action beginDurable = all {
    phase == IDLE,
    phase' = ACTIVE,
    changed' = false,
    publish' = true,
    notifications' = 0,
  }

  action beginLocal = all {
    phase == IDLE,
    phase' = ACTIVE,
    changed' = false,
    publish' = false,
    notifications' = 0,
  }

  action localChange = all {
    phase == ACTIVE,
    phase' = ACTIVE,
    changed' = true,
    publish' = publish,
    notifications' = 0,
  }

  action durableScopeNoChange = all {
    phase == ACTIVE,
    phase' = ACTIVE,
    changed' = changed,
    publish' = true,
    notifications' = 0,
  }

  action nestedDurableChange = all {
    phase == ACTIVE,
    phase' = ACTIVE,
    changed' = true,
    publish' = true,
    notifications' = 0,
  }

  action nestedDurableOtherWrapper = all {
    phase == ACTIVE,
    phase' = ACTIVE,
    changed' = true,
    publish' = true,
    notifications' = 0,
  }

  action commit = all {
    phase == ACTIVE,
    phase' = COMMITTED,
    changed' = changed,
    publish' = publish,
    notifications' = if (changed and publish) 1 else 0,
  }

  action rollback = all {
    phase == ACTIVE,
    phase' = ROLLED_BACK,
    changed' = false,
    publish' = publish,
    notifications' = 0,
  }

  action terminalIdle = all {
    phase == COMMITTED or phase == ROLLED_BACK,
    phase' = phase,
    changed' = changed,
    publish' = publish,
    notifications' = notifications,
  }

  action step = any {
    beginDurable,
    beginLocal,
    localChange,
    durableScopeNoChange,
    nestedDurableChange,
    nestedDurableOtherWrapper,
    commit,
    rollback,
    terminalIdle,
  }

  val knownPhase =
    phase == IDLE or phase == ACTIVE or phase == COMMITTED or phase == ROLLED_BACK

  val notificationAtMostOnce =
    notifications == 0 or notifications == 1

  val activeNeverNotified =
    not(phase == ACTIVE) or notifications == 0

  val rollbackNeverNotified =
    not(phase == ROLLED_BACK) or notifications == 0

  val notificationOnlyAfterCommittedChange =
    notifications == 0 or (phase == COMMITTED and changed and publish)

  val committedNotificationMatchesScope =
    not(phase == COMMITTED) or
      ((changed and publish and notifications == 1) or
       (not(changed and publish) and notifications == 0))

  val safety =
    knownPhase and
    notificationAtMostOnce and
    activeNeverNotified and
    rollbackNeverNotified and
    notificationOnlyAfterCommittedChange and
    committedNotificationMatchesScope
}
