// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-ENTRY-EXCLUSION-001@sha256:7c82561a322a69d0996c05a346ffdc8c5920359dcc6953e798656491dfeea9bb -> ExcludedEntryIsNotCandidate
// - PODCAST-ENTRY-CONSUMPTION-001@sha256:b0b6da350cdd6ecd6f8cff64648f7e7ca36cd794ae85d5ccf30399b957fc6abe -> ConsumedEntryIsNotCandidate
//
// Scope:
// The model represents one candidate-selection snapshot per program. It does not
// model feed parsing or clustering. Excluded and consumed identities are durable
// filters; candidate selection must not reintroduce either category.

module podcast_entry_eligibility

sig EntryIdentity {}

sig Program {
  consumed: set EntryIdentity,
  excluded: set EntryIdentity,
  candidates: set EntryIdentity
}

fact CandidateEligibility {
  all program: Program |
    no program.candidates & (program.consumed + program.excluded)
}

assert ExcludedEntryIsNotCandidate {
  all program: Program |
    no program.candidates & program.excluded
}

assert ConsumedEntryIsNotCandidate {
  all program: Program |
    no program.candidates & program.consumed
}

check ExcludedEntryIsNotCandidate for 5 expect 0
check ConsumedEntryIsNotCandidate for 5 expect 0

run MixedProgramState {
  some program: Program |
    some program.candidates and
    some program.consumed and
    some program.excluded
} for 5 expect 1
