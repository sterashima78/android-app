// Natural-language specifications:
// - docs/spec/12-backup-restore.md
// - docs/spec/13-background-execution.md
//
// Covers:
// - BACKUP-SCHEDULE-001@sha256:0d16ab7971e74f9b8f8fd71a6fbe3913562def892a03e026621655cb37ac5de6 -> scheduleMatchesArmedWork
// - BACKUP-SCHEDULE-001@sha256:0d16ab7971e74f9b8f8fd71a6fbe3913562def892a03e026621655cb37ac5de6 -> noAutomaticWorkWithoutConfiguredTime
// - BACKUP-SCHEDULE-001@sha256:0d16ab7971e74f9b8f8fd71a6fbe3913562def892a03e026621655cb37ac5de6 -> manualBackup
// - BACKUP-SCHEDULE-002@sha256:d1a0e0c976b0d8b93d60b3802f6506ebf81c06f639b5ddd658b58c24b5b94983 -> fireMorning
// - BACKUP-SCHEDULE-002@sha256:d1a0e0c976b0d8b93d60b3802f6506ebf81c06f639b5ddd658b58c24b5b94983 -> fireEvening
//
// Abstraction:
// Two independent slots represent arbitrary distinct configured local times.
// The model verifies per-slot scheduling semantics, not the number of times a
// user may configure in production. Cardinality/uniqueness across configured
// times is modeled separately in Alloy.

module backup_schedule {
  var configuredMorning: bool
  var configuredEvening: bool
  var armedMorning: bool
  var armedEvening: bool

  action init = all {
    configuredMorning' = false,
    configuredEvening' = false,
    armedMorning' = false,
    armedEvening' = false,
  }

  action addMorning = all {
    configuredMorning' = true,
    configuredEvening' = configuredEvening,
    armedMorning' = true,
    armedEvening' = armedEvening,
  }

  action addEvening = all {
    configuredMorning' = configuredMorning,
    configuredEvening' = true,
    armedMorning' = armedMorning,
    armedEvening' = true,
  }

  action removeMorning = all {
    configuredMorning' = false,
    configuredEvening' = configuredEvening,
    armedMorning' = false,
    armedEvening' = armedEvening,
  }

  action removeEvening = all {
    configuredMorning' = configuredMorning,
    configuredEvening' = false,
    armedMorning' = armedMorning,
    armedEvening' = false,
  }

  // A scheduled trigger can fire only while the corresponding time is armed.
  // Completion re-arms the same logical slot for its next local occurrence.
  action fireMorning = all {
    armedMorning,
    configuredMorning' = configuredMorning,
    configuredEvening' = configuredEvening,
    armedMorning' = true,
    armedEvening' = armedEvening,
  }

  action fireEvening = all {
    armedEvening,
    configuredMorning' = configuredMorning,
    configuredEvening' = configuredEvening,
    armedMorning' = armedMorning,
    armedEvening' = true,
  }

  // Manual backup remains available independently of automatic scheduling.
  action manualBackup = all {
    configuredMorning' = configuredMorning,
    configuredEvening' = configuredEvening,
    armedMorning' = armedMorning,
    armedEvening' = armedEvening,
  }

  action step = any {
    addMorning,
    addEvening,
    removeMorning,
    removeEvening,
    fireMorning,
    fireEvening,
    manualBackup,
  }

  val scheduleMatchesArmedWork =
    configuredMorning == armedMorning and configuredEvening == armedEvening

  val noAutomaticWorkWithoutConfiguredTime =
    (configuredMorning or configuredEvening) or
    (not(armedMorning) and not(armedEvening))

  // Repository convention: CI verifies the definition named safety.
  val safety = scheduleMatchesArmedWork and noAutomaticWorkWithoutConfiguredTime
}
