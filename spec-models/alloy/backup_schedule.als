// Natural-language specifications:
// - docs/spec/12-backup-restore.md
// - docs/spec/13-background-execution.md
//
// Scope:
// This model focuses on structural constraints: configured local times map
// one-to-one to scheduled work, automatic jobs always originate from such
// work, and manual jobs remain valid without an automatic schedule.

module backup_schedule

sig LocalTime {}

one sig Preferences {
  configured: set LocalTime
}

sig ScheduledWork {
  at: one LocalTime
}

abstract sig BackupJob {}
sig AutomaticJob extends BackupJob {
  source: one ScheduledWork
}
sig ManualJob extends BackupJob {}

fact ScheduledWorkMatchesPreferences {
  all work: ScheduledWork |
    work.at in Preferences.configured

  all time: Preferences.configured |
    some work: ScheduledWork | work.at = time

  all disj left, right: ScheduledWork |
    left.at != right.at
}

assert NoConfiguredTimeMeansNoScheduledWork {
  no Preferences.configured implies no ScheduledWork
}

assert ExactlyOneScheduledWorkPerConfiguredTime {
  all time: Preferences.configured |
    one work: ScheduledWork | work.at = time
}

assert AutomaticJobsUseConfiguredTimes {
  all job: AutomaticJob |
    job.source.at in Preferences.configured
}

check NoConfiguredTimeMeansNoScheduledWork for 5 expect 0
check ExactlyOneScheduledWorkPerConfiguredTime for 5 expect 0
check AutomaticJobsUseConfiguredTimes for 5 expect 0

run TwoConfiguredTimes {
  #Preferences.configured = 2
  some AutomaticJob
} for 5 expect 1

run ManualBackupWithoutAutomaticSchedule {
  no Preferences.configured
  some ManualJob
  no AutomaticJob
} for 3 expect 1
