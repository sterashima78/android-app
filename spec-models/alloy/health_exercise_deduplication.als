// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - HEALTH-EXERCISE-DEDUP-001@sha256:1cc91f87f217331462f6a3a8b5bb54a6d50325bbc540ba670e362499cbaf82bf -> ExactIdentityCollapses
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
  segmentMatch: set Session,
  representative: lone Session
}

one sig DedupResult {
  input: set Session,
  kept: set Session
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
  some DedupResult.input
  DedupResult.kept in DedupResult.input

  all session: DedupResult.input | {
    one session.representative
    session.representative in DedupResult.kept
  }

  all session: Session - DedupResult.input |
    no session.representative

  all kept: DedupResult.kept |
    kept.representative = kept

  all session: DedupResult.input |
    let kept = session.representative |
      session = kept or sameRealWorldExercise[session, kept]

  all disj left, right: DedupResult.input |
    exactIdentity[left, right] implies
      left.representative = right.representative

  no disj left, right: DedupResult.kept |
    sameRealWorldExercise[left, right]

  all disj left, right: DedupResult.input |
    sameKnownOrigin[left, right] and not exactIdentity[left, right] implies
      left.representative != right.representative
}

assert ExactIdentityCollapses {
  all disj left, right: DedupResult.input |
    exactIdentity[left, right] implies
      left.representative = right.representative
}

assert SameKnownOriginNonExactStayDistinct {
  all disj left, right: DedupResult.input |
    sameKnownOrigin[left, right] and not exactIdentity[left, right] implies
      left.representative != right.representative
}

assert CrossOriginDuplicateNeedsEvidence {
  all disj session, kept: DedupResult.input |
    session.representative = kept and not exactIdentity[session, kept] implies
      crossOriginDuplicateEvidence[session, kept]
}

assert EveryInputHasExactlyOneKeptRepresentative {
  all session: DedupResult.input |
    one kept: DedupResult.kept |
      session.representative = kept
}

assert KeptSessionsAreOriginalInputs {
  DedupResult.kept in DedupResult.input
}

assert KeptSessionsArePairwiseDistinctRealWorldExercises {
  no disj left, right: DedupResult.kept |
    sameRealWorldExercise[left, right]
}

check ExactIdentityCollapses for 6 expect 0
check SameKnownOriginNonExactStayDistinct for 6 expect 0
check CrossOriginDuplicateNeedsEvidence for 6 expect 0
check EveryInputHasExactlyOneKeptRepresentative for 6 expect 0
check KeptSessionsAreOriginalInputs for 6 expect 0
check KeptSessionsArePairwiseDistinctRealWorldExercises for 6 expect 0

run RepresentativeCrossOriginDedup {
  #Session = 4
  #DedupResult.input = 3
  #DedupResult.kept = 2
  some disj duplicate, keptSession, separate: DedupResult.input | {
    duplicate.origin != keptSession.origin
    keptSession in duplicate.strongOverlap
    duplicate.representative = keptSession
    keptSession in DedupResult.kept
    separate in DedupResult.kept
  }
} for 6 expect 1
