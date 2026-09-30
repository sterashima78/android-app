// Natural-language specifications:
// - docs/spec/12-backup-restore.md
//
// Covers:
// - BACKUP-ARCHIVE-001@sha256:3f78759f9b0390c7fc9cd90828f18b29194945278a2f72f5ace27a3f2945552d -> RequiredBackupMembersIncluded
// - BACKUP-ARCHIVE-001@sha256:3f78759f9b0390c7fc9cd90828f18b29194945278a2f72f5ace27a3f2945552d -> ExcludedDataNeverIncluded
//
// Scope:
// The model represents the backup-policy categories that matter to the
// specification. Concrete preference keys, archive paths, and cache file names
// are intentionally abstracted away.

module backup_archive

abstract sig BackupItem {}
abstract sig IncludedItem extends BackupItem {}
abstract sig ExcludedItem extends BackupItem {}

one sig Manifest extends IncludedItem {}
one sig DatabaseSnapshot extends IncludedItem {}
one sig WifiOnlyPreference extends IncludedItem {}
one sig BackupSchedulePreference extends IncludedItem {}

one sig Credential extends ExcludedItem {}
one sig Token extends ExcludedItem {}
one sig SmbPassword extends ExcludedItem {}
one sig StorageDestination extends ExcludedItem {}
one sig StorageDestinationDisplayName extends ExcludedItem {}
one sig ExecutionHistory extends ExcludedItem {}
one sig DeviceBenchmark extends ExcludedItem {}
one sig ModelCache extends ExcludedItem {}
one sig DerivedCache extends ExcludedItem {}

one sig BackupArchive {
  members: set BackupItem
}

fact ArchivePolicy {
  IncludedItem in BackupArchive.members
  no ExcludedItem & BackupArchive.members
}

assert RequiredBackupMembersIncluded {
  Manifest in BackupArchive.members
  DatabaseSnapshot in BackupArchive.members
  WifiOnlyPreference in BackupArchive.members
  BackupSchedulePreference in BackupArchive.members
}

assert ExcludedDataNeverIncluded {
  no ExcludedItem & BackupArchive.members
}

check RequiredBackupMembersIncluded for 15 expect 0
check ExcludedDataNeverIncluded for 15 expect 0

run ValidArchive {
  BackupArchive.members = IncludedItem
} for 15 expect 1
