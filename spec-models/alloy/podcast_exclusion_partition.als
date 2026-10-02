// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> EveryCandidateClassifiedExactlyOnce
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> NoCandidateAppearsInBothOutcomes
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> FallbackIncludesEveryCandidate
//
// Scope:
// This model verifies the structural contract after news exclusion inference.
// It does not model whether a particular article semantically matches the
// user's exclusion condition. Each output-list occurrence is represented by a
// DecisionSlot so duplicate occurrences remain visible instead of collapsing
// into set membership. Output ordering and semantic decisions remain covered
// by implementation tests.

module podcast_exclusion_partition

abstract sig ExclusionOutcome {}
one sig ValidClassification, Fallback extends ExclusionOutcome {}

sig Candidate {}

abstract sig DecisionSlot {
  candidate: one Candidate
}
sig IncludedSlot, ExcludedSlot extends DecisionSlot {}

one sig ExclusionResult {
  outcome: one ExclusionOutcome
}

fact NonEmptyInput {
  some Candidate
}

fact FinalResultIsExactClassification {
  all candidate: Candidate |
    one slot: DecisionSlot | slot.candidate = candidate
}

fact FailureFallbackShape {
  ExclusionResult.outcome = Fallback implies (
    no ExcludedSlot and
    IncludedSlot.candidate = Candidate
  )
}

assert EveryCandidateClassifiedExactlyOnce {
  all candidate: Candidate |
    one slot: DecisionSlot | slot.candidate = candidate
}

assert NoCandidateAppearsInBothOutcomes {
  no IncludedSlot.candidate & ExcludedSlot.candidate
}

assert FallbackIncludesEveryCandidate {
  ExclusionResult.outcome = Fallback implies (
    no ExcludedSlot and
    IncludedSlot.candidate = Candidate and
    #IncludedSlot = #Candidate
  )
}

check EveryCandidateClassifiedExactlyOnce for 10 expect 0
check NoCandidateAppearsInBothOutcomes for 10 expect 0
check FallbackIncludesEveryCandidate for 10 expect 0

run RepresentativeValidExclusion {
  ExclusionResult.outcome = ValidClassification
  #Candidate = 3
  #IncludedSlot = 2
  #ExcludedSlot = 1
} for 10 expect 1

run RepresentativeFallback {
  ExclusionResult.outcome = Fallback
  #Candidate = 3
} for 10 expect 1
