// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-CLUSTERING-PARTITION-001@sha256:f740ea44a36feb7478b8c8ede40b2cc9a9c6ae7495a380d1a182777f8386a72f -> EveryCandidateAppearsExactlyOnce
// - PODCAST-CLUSTERING-PARTITION-001@sha256:f740ea44a36feb7478b8c8ede40b2cc9a9c6ae7495a380d1a182777f8386a72f -> NoEmptyClusters
// - PODCAST-CLUSTERING-PARTITION-001@sha256:f740ea44a36feb7478b8c8ede40b2cc9a9c6ae7495a380d1a182777f8386a72f -> FallbackIsSingletonPartition
// - PODCAST-CLUSTERING-PARTITION-001@sha256:f740ea44a36feb7478b8c8ede40b2cc9a9c6ae7495a380d1a182777f8386a72f -> SkippedIsSingletonPartition
//
// Scope:
// This model verifies the structural contract after semantic clustering.
// Whether two headlines describe the same real-world event remains an AI /
// product-semantics concern covered by prompts and regression tests. The model
// checks only candidate coverage, disjointness, non-empty clusters, and the
// singleton fallback shape used when classification cannot be trusted.

module podcast_clustering_partition

abstract sig ClusteringStatus {}
one sig Success, FallbackInferenceError, FallbackInvalidOutput, Skipped extends ClusteringStatus {}

sig Candidate {}

sig NewsCluster {
  members: some Candidate
}

one sig ClusteringResult {
  candidates: set Candidate,
  clusters: set NewsCluster,
  status: one ClusteringStatus
}

fact NonEmptyInput {
  some Candidate
}

fact ResultOwnsModeledObjects {
  ClusteringResult.candidates = Candidate
  ClusteringResult.clusters = NewsCluster
}

fact FinalResultIsPartition {
  Candidate = NewsCluster.members
  all disj left, right: NewsCluster |
    no left.members & right.members
}

fact FailureFallbackShape {
  ClusteringResult.status in FallbackInferenceError + FallbackInvalidOutput implies
    all cluster: NewsCluster | one cluster.members
}

fact SkippedShape {
  ClusteringResult.status = Skipped implies
    all cluster: NewsCluster | one cluster.members
}

assert EveryCandidateAppearsExactlyOnce {
  all candidate: Candidate |
    one cluster: NewsCluster | candidate in cluster.members
}

assert NoEmptyClusters {
  all cluster: NewsCluster |
    some cluster.members
}

assert FallbackIsSingletonPartition {
  ClusteringResult.status in FallbackInferenceError + FallbackInvalidOutput implies (
    #NewsCluster = #Candidate and
    all cluster: NewsCluster | one cluster.members
  )
}

assert SkippedIsSingletonPartition {
  ClusteringResult.status = Skipped implies (
    #NewsCluster = #Candidate and
    all cluster: NewsCluster | one cluster.members
  )
}

check EveryCandidateAppearsExactlyOnce for 10 expect 0
check NoEmptyClusters for 10 expect 0
check FallbackIsSingletonPartition for 10 expect 0
check SkippedIsSingletonPartition for 10 expect 0

run RepresentativeSuccessfulMerge {
  ClusteringResult.status = Success
  #Candidate = 3
  #NewsCluster = 2
  some cluster: NewsCluster | #cluster.members = 2
} for 10 expect 1

run RepresentativeFallback {
  ClusteringResult.status = FallbackInvalidOutput
  #Candidate = 3
} for 10 expect 1
