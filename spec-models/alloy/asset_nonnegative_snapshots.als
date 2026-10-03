// Natural-language specifications:
// - docs/spec/09-assets.md
//
// Covers:
// - ASSET-NONNEGATIVE-SNAPSHOT-001@sha256:dbf2c1db2cf714101cdef4a137ebfa30ba3e05f5bd35950e3ee8c61b86d54a35 -> NegativeImportNeverPersists
// - ASSET-NONNEGATIVE-SNAPSHOT-001@sha256:dbf2c1db2cf714101cdef4a137ebfa30ba3e05f5bd35950e3ee8c61b86d54a35 -> ImportedDateReplacesExistingRows
// - ASSET-NONNEGATIVE-SNAPSHOT-001@sha256:dbf2c1db2cf714101cdef4a137ebfa30ba3e05f5bd35950e3ee8c61b86d54a35 -> NegativeOnlyImportClearsOldSnapshot
// - ASSET-NONNEGATIVE-SNAPSHOT-001@sha256:dbf2c1db2cf714101cdef4a137ebfa30ba3e05f5bd35950e3ee8c61b86d54a35 -> ReadModelsExcludeNegativeRows
// - ASSET-NONNEGATIVE-SNAPSHOT-001@sha256:dbf2c1db2cf714101cdef4a137ebfa30ba3e05f5bd35950e3ee8c61b86d54a35 -> ZeroImportRemainsEligible
//
// Scope:
// The model represents one snapshot-replacement transaction. ExistingRow may
// include legacy negative values that remain physically stored when their date
// is not replaced. ImportRow represents the complete input snapshot. FinalRow
// represents physical rows after replacement. ReadModel abstracts overview,
// history, category composition, and category-setting projections.

module asset_nonnegative_snapshots

sig Date {}

abstract sig AmountSign {}
one sig Negative, Zero, Positive extends AmountSign {}

abstract sig SourceRow {
  date: one Date,
  sign: one AmountSign
}

sig ExistingRow extends SourceRow {}
sig ImportRow extends SourceRow {}

sig FinalRow {
  source: one SourceRow
}

abstract sig ReadModel {
  visible: set FinalRow
}

one sig OverviewRead, HistoryRead, CompositionRead, CategorySettingsRead extends ReadModel {}

fun importedDates: set Date {
  ImportRow.date
}

fact SnapshotReplacement {
  all imported: ImportRow |
    imported.sign = Negative implies
      no final: FinalRow | final.source = imported

  all imported: ImportRow |
    imported.sign != Negative implies
      one final: FinalRow | final.source = imported

  all existing: ExistingRow |
    existing.date in importedDates implies
      no final: FinalRow | final.source = existing

  all existing: ExistingRow |
    existing.date not in importedDates implies
      one final: FinalRow | final.source = existing

  all source: SourceRow |
    lone final: FinalRow | final.source = source
}

fact NonnegativeReadModels {
  all view: ReadModel |
    view.visible = { final: FinalRow | final.source.sign != Negative }
}

assert NegativeImportNeverPersists {
  no imported: ImportRow |
    imported.sign = Negative and
    some final: FinalRow | final.source = imported
}

assert ImportedDateReplacesExistingRows {
  no existing: ExistingRow |
    existing.date in importedDates and
    some final: FinalRow | final.source = existing
}

assert NegativeOnlyImportClearsOldSnapshot {
  all targetDate: importedDates |
    (
      all imported: ImportRow |
        imported.date = targetDate implies imported.sign = Negative
    ) implies
      no final: FinalRow | final.source.date = targetDate
}

assert ReadModelsExcludeNegativeRows {
  all view: ReadModel |
    no final: view.visible | final.source.sign = Negative
}

assert ZeroImportRemainsEligible {
  all imported: ImportRow |
    imported.sign = Zero implies
      one final: FinalRow | final.source = imported
}

check NegativeImportNeverPersists for 10 expect 0
check ImportedDateReplacesExistingRows for 10 expect 0
check NegativeOnlyImportClearsOldSnapshot for 10 expect 0
check ReadModelsExcludeNegativeRows for 10 expect 0
check ZeroImportRemainsEligible for 10 expect 0

run NegativeOnlyReplacementExample {
  some targetDate: Date |
    (some existing: ExistingRow |
      existing.date = targetDate and existing.sign = Positive) and
    (some imported: ImportRow |
      imported.date = targetDate and imported.sign = Negative) and
    (all imported: ImportRow |
      imported.date = targetDate implies imported.sign = Negative) and
    (no final: FinalRow |
      final.source.date = targetDate)
} for 10 expect 1

run LegacyNegativeOutsideReplacementExample {
  some legacy: ExistingRow |
    legacy.sign = Negative and
    legacy.date not in importedDates and
    (some final: FinalRow |
      final.source = legacy)
} for 10 expect 1
