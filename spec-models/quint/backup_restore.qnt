// Natural-language specifications:
// - docs/spec/12-backup-restore.md
//
// Covers:
// - BACKUP-RESTORE-001@sha256:de724867aadbcc11ed99d4bf7543fa4243788a1ef7b1cca054a82b859a27872a -> restoreRequiresValidatedMatchingArchive
// - BACKUP-RESTORE-001@sha256:de724867aadbcc11ed99d4bf7543fa4243788a1ef7b1cca054a82b859a27872a -> rejectedArchiveIsNeverRestored
//
// Abstraction:
// A candidate archive is reduced to the two validation facts that determine
// restore eligibility: archive integrity/identity validation and schema match.
// The model intentionally omits the concrete checksum and database APIs.

module backup_restore {
  var candidateLoaded: bool
  var archiveValid: bool
  var schemaMatches: bool
  var restored: bool
  var rejected: bool

  action init = all {
    candidateLoaded' = false,
    archiveValid' = false,
    schemaMatches' = false,
    restored' = false,
    rejected' = false,
  }

  action loadValidMatchingArchive = all {
    not(candidateLoaded),
    candidateLoaded' = true,
    archiveValid' = true,
    schemaMatches' = true,
    restored' = false,
    rejected' = false,
  }

  action loadInvalidArchive = all {
    not(candidateLoaded),
    candidateLoaded' = true,
    archiveValid' = false,
    schemaMatches' = false,
    restored' = false,
    rejected' = false,
  }

  action loadSchemaMismatch = all {
    not(candidateLoaded),
    candidateLoaded' = true,
    archiveValid' = true,
    schemaMatches' = false,
    restored' = false,
    rejected' = false,
  }

  action restore = all {
    candidateLoaded,
    archiveValid,
    schemaMatches,
    not(rejected),
    candidateLoaded' = candidateLoaded,
    archiveValid' = archiveValid,
    schemaMatches' = schemaMatches,
    restored' = true,
    rejected' = rejected,
  }

  action rejectInvalidArchive = all {
    candidateLoaded,
    not(archiveValid),
    not(restored),
    candidateLoaded' = candidateLoaded,
    archiveValid' = archiveValid,
    schemaMatches' = schemaMatches,
    restored' = restored,
    rejected' = true,
  }

  action rejectSchemaMismatch = all {
    candidateLoaded,
    archiveValid,
    not(schemaMatches),
    not(restored),
    candidateLoaded' = candidateLoaded,
    archiveValid' = archiveValid,
    schemaMatches' = schemaMatches,
    restored' = restored,
    rejected' = true,
  }

  action step = any {
    loadValidMatchingArchive,
    loadInvalidArchive,
    loadSchemaMismatch,
    restore,
    rejectInvalidArchive,
    rejectSchemaMismatch,
  }

  val restoreRequiresValidatedMatchingArchive =
    not(restored) or
    (candidateLoaded and archiveValid and schemaMatches and not(rejected))

  val rejectedArchiveIsNeverRestored =
    not(rejected) or not(restored)

  val safety =
    restoreRequiresValidatedMatchingArchive and
    rejectedArchiveIsNeverRestored
}
