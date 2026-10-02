// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> EveryCandidateClassifiedExactlyOnce
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> IncludedAndExcludedAreDisjoint
// - PODCAST-EXCLUSION-PARTITION-001@sha256:ea5ad06535cb5a535cde5cba86e2f3e0976c8a7f4bafa5989014fa9bbe8875c4 -> FallbackIncludesEveryCandidate
//
// Scope:
// This model verifies the structural contract after news exclusion inference.
// It does not model whether a particular article semantically matches the
// user's exclusion condition. That remains a prompt / inference behavior
// covered by implementation tests. The model checks that final classification
// never drops a candidate and that any failed attempt discards partial
// exclusion decisions.

module podcast_exclusion_partition

abstract sig ExclusionOutcome {}
one sig ValidClassification, Fallback extends ExclusionOutcome {}

sig Candidate {}

one sig ExclusionResult {
  included: set Candidate,
  excluded: set Candidate,
  outcome: one ExclusionOutcome
}

fact NonEmptyInput {
  some Candidate
}

fact FinalResultIsPartition {
  ExclusionResult.included + ExclusionResult.excluded = Candidate
  no ExclusionResult.included & ExclusionResult.excluded
}

fact FailureFallbackShape {
  ExclusionResult.outcome = Fallback implies (
    ExclusionResult.included = Candidate and
    no ExclusionResult.excluded
  )
}

assert EveryCandidateClassifiedExactlyOnce {
  all candidate: Candidate |
    (
      candidate in ExclusionResult.included and
      candidate not in ExclusionResult.excluded
    ) or (
      candidate in ExclusionResult.excluded and
      candidate not in ExclusionResult.included
    )
}

assert IncludedAndExcludedAreDisjoint {
  no ExclusionResult.included & ExclusionResult.excluded
}

assert FallbackIncludesEveryCandidate {
  ExclusionResult.outcome = Fallback implies (
    ExclusionResult.included = Candidate and
    no ExclusionResult.excluded
  )
}

check EveryCandidateClassifiedExactlyOnce for 8 expect 0
check IncludedAndExcludedAreDisjoint for 8 expect 0
check FallbackIncludesEveryCandidate for 8 expect 0

run RepresentativeValidExclusion {
  ExclusionResult.outcome = ValidClassification
  #Candidate = 3
  #ExclusionResult.included = 2
  #ExclusionResult.excluded = 1
} for 8 expect 1

run RepresentativeFallback {
  ExclusionResult.outcome = Fallback
  #Candidate = 3
} for 8 expect 1
