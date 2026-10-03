// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> ExactIdentityCanCollapse
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> SameKnownOriginNonExactStayDistinct
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> CrossOriginDuplicateNeedsEvidence
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> EveryInputHasExactlyOneKeptRepresentative
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> KeptSessionsAreOriginalInputs
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> KeptSessionsArePairwiseDistinctRealWorldExercises
//
// Scope:
// The model checks the structural result of exercise-session deduplication.
// ExactKey abstracts exercise type + start/end time. strongOverlap and
// segmentMatch abstract the implementation's numeric temporal thresholds.
// Representative preference (segments, notes, title, duration) remains an
// implementation-test concern rather than being duplicated here.

module health_exercise_deduplication

abstract sig Origin {}
one sig UnknownOrigin extends Origin {}
sig KnownOrigin extends Origin {}

sig ExactKey {}

sig Session {
  origin: one Origin,
  exactKey: one ExactKey,
  strongOverlap: set Session,
  segmentMatch: set Session
}

one sig DedupResult {
  kept: set Session,
  representative: Session -> lone Session
}

fact StrongOverlapIsSymmetric {
  all left, right: Session |
    right in left.strongOverlap iff left in right.strongOverlap
}

fact NoSelfEvidence {
  no session: Session | session in session.strongOverlap
  no session: Session | session in session.segmentMatch
}

pred exactIdentity[left, right: Session] {
  left.exactKey = right.exactKey
}

pred sameKnownOrigin[left, right: Session] {
  left.origin = right.origin
  left.origin != UnknownOrigin
}

pred crossOriginDuplicateEvidence[left, right: Session] {
  not sameKnownOrigin[left, right]
  (
    right in left.strongOverlap or
    right in left.segmentMatch or
    left in right.segmentMatch
  )
}

pred sameRealWorldExercise[left, right: Session] {
  exactIdentity[left, right] or
  crossOriginDuplicateEvidence[left, right]
}

fact DedupProjection {
  DedupResult.kept in Session

  all session: Session | {
    one DedupResult.representative[session]
    DedupResult.representative[session] in DedupResult.kept
  }

  all kept: DedupResult.kept |
    DedupResult.representative[kept] = kept

  all session: Session |
    let kept = DedupResult.representative[session] |
      session = kept or sameRealWorldExercise[session, kept]

  no disj left, right: DedupResult.kept |
    sameRealWorldExercise[left, right]

  all disj left, right: Session |
    sameKnownOrigin[left, right] and not exactIdentity[left, right] implies
      DedupResult.representative[left] != DedupResult.representative[right]
}

assert ExactIdentityCanCollapse {
  all disj left, right: Session |
    exactIdentity[left, right] implies
      sameRealWorldExercise[left, right]
}

assert SameKnownOriginNonExactStayDistinct {
  all disj left, right: Session |
    sameKnownOrigin[left, right] and not exactIdentity[left, right] implies
      DedupResult.representative[left] != DedupResult.representative[right]
}

assert CrossOriginDuplicateNeedsEvidence {
  all disj session, kept: Session |
    DedupResult.representative[session] = kept and not exactIdentity[session, kept] implies
      crossOriginDuplicateEvidence[session, kept]
}

assert EveryInputHasExactlyOneKeptRepresentative {
  all session: Session |
    one kept: DedupResult.kept |
      DedupResult.representative[session] = kept
}

assert KeptSessionsAreOriginalInputs {
  DedupResult.kept in Session
}

assert KeptSessionsArePairwiseDistinctRealWorldExercises {
  no disj left, right: DedupResult.kept |
    sameRealWorldExercise[left, right]
}

check ExactIdentityCanCollapse for 8 expect 0
check SameKnownOriginNonExactStayDistinct for 8 expect 0
check CrossOriginDuplicateNeedsEvidence for 8 expect 0
check EveryInputHasExactlyOneKeptRepresentative for 8 expect 0
check KeptSessionsAreOriginalInputs for 8 expect 0
check KeptSessionsArePairwiseDistinctRealWorldExercises for 8 expect 0

run RepresentativeCrossOriginDedup {
  #Session = 3
  #DedupResult.kept = 2
  some disj duplicate, representative, separate: Session | {
    duplicate.origin != representative.origin
    representative in duplicate.strongOverlap
    DedupResult.representative[duplicate] = representative
    representative in DedupResult.kept
    separate in DedupResult.kept
  }
} for 8 expect 1
