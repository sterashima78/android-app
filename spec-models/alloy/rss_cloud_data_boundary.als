// Natural-language specifications:
// - docs/spec/15-privacy-security.md
//
// Covers:
// - RSS-CLOUD-DATA-BOUNDARY-001@sha256:5ed0dfde725a05b5c5fb73edde34d4980076dc69db7af8a270b57d04ee45ea29 -> LocalSelectionHasNoCloudRequest
// - RSS-CLOUD-DATA-BOUNDARY-001@sha256:5ed0dfde725a05b5c5fb73edde34d4980076dc69db7af8a270b57d04ee45ea29 -> CloudRequestRequiresCloudSelection
// - RSS-CLOUD-DATA-BOUNDARY-001@sha256:5ed0dfde725a05b5c5fb73edde34d4980076dc69db7af8a270b57d04ee45ea29 -> ScoringPayloadIsAllowlisted
// - RSS-CLOUD-DATA-BOUNDARY-001@sha256:5ed0dfde725a05b5c5fb73edde34d4980076dc69db7af8a270b57d04ee45ea29 -> LearningPayloadIsAllowlisted
// - RSS-CLOUD-DATA-BOUNDARY-001@sha256:5ed0dfde725a05b5c5fb73edde34d4980076dc69db7af8a270b57d04ee45ea29 -> SensitiveContentNeverLeavesApp
//
// Scope:
// The model represents data categories, not prompt formatting or concrete text.
// Local/cloud adapter routing and prompt construction remain implementation-test
// concerns; this model constrains which categories may cross the cloud boundary.

module rss_cloud_data_boundary

abstract sig Provider {}
one sig Local, Cloud extends Provider {}

abstract sig RequestKind {}
one sig Scoring, Learning extends RequestKind {}

abstract sig DataCategory {}
one sig ManualCondition,
        LearnedCondition,
        ArticleTitle,
        FeedbackTitle,
        PreviousAssessment,
        ArticleUrl,
        FeedBody,
        LinkedPageBody,
        SavedSummary extends DataCategory {}

sig CloudRequest {
  kind: one RequestKind,
  payload: set DataCategory
}

sig RecommendationInvocation {
  provider: one Provider,
  cloudRequest: lone CloudRequest
}

fact RequestOwnership {
  CloudRequest = RecommendationInvocation.cloudRequest

  all request: CloudRequest |
    one request.~cloudRequest
}

fact ProviderRouting {
  all invocation: RecommendationInvocation |
    invocation.provider = Local implies no invocation.cloudRequest

  all invocation: RecommendationInvocation |
    invocation.provider = Cloud implies one invocation.cloudRequest
}

fact PayloadAllowlist {
  all request: CloudRequest |
    request.kind = Scoring implies (
      request.payload in (
        ManualCondition +
        LearnedCondition +
        ArticleTitle
      ) and
      ArticleTitle in request.payload
    )

  all request: CloudRequest |
    request.kind = Learning implies (
      request.payload in (
        ManualCondition +
        LearnedCondition +
        FeedbackTitle +
        PreviousAssessment
      ) and
      FeedbackTitle in request.payload
    )
}

assert LocalSelectionHasNoCloudRequest {
  all invocation: RecommendationInvocation |
    invocation.provider = Local implies no invocation.cloudRequest
}

assert CloudRequestRequiresCloudSelection {
  all request: CloudRequest |
    all invocation: request.~cloudRequest |
      invocation.provider = Cloud
}

assert ScoringPayloadIsAllowlisted {
  all request: CloudRequest |
    request.kind = Scoring implies
      request.payload in (
        ManualCondition +
        LearnedCondition +
        ArticleTitle
      )
}

assert LearningPayloadIsAllowlisted {
  all request: CloudRequest |
    request.kind = Learning implies
      request.payload in (
        ManualCondition +
        LearnedCondition +
        FeedbackTitle +
        PreviousAssessment
      )
}

assert SensitiveContentNeverLeavesApp {
  no request: CloudRequest |
    some request.payload & (
      ArticleUrl +
      FeedBody +
      LinkedPageBody +
      SavedSummary
    )
}

check LocalSelectionHasNoCloudRequest for 10 expect 0
check CloudRequestRequiresCloudSelection for 10 expect 0
check ScoringPayloadIsAllowlisted for 10 expect 0
check LearningPayloadIsAllowlisted for 10 expect 0
check SensitiveContentNeverLeavesApp for 10 expect 0

run RepresentativeCloudRequests {
  some scoringInvocation: RecommendationInvocation |
    scoringInvocation.provider = Cloud and
    scoringInvocation.cloudRequest.kind = Scoring and
    ArticleTitle in scoringInvocation.cloudRequest.payload

  some learningInvocation: RecommendationInvocation |
    learningInvocation.provider = Cloud and
    learningInvocation.cloudRequest.kind = Learning and
    FeedbackTitle in learningInvocation.cloudRequest.payload

  some localInvocation: RecommendationInvocation |
    localInvocation.provider = Local and
    no localInvocation.cloudRequest
} for 10 expect 1
