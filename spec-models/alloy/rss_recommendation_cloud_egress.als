// Natural-language specifications:
// - docs/spec/15-privacy-security.md
//
// Covers:
// - RSS-RECOMMENDATION-CLOUD-EGRESS-001@sha256:9d643abace1ef31745f49c46988a140a4e7e7ab570f3bf6af7944545ec221c62 -> DefaultProviderIsLocal
// - RSS-RECOMMENDATION-CLOUD-EGRESS-001@sha256:9d643abace1ef31745f49c46988a140a4e7e7ab570f3bf6af7944545ec221c62 -> LocalProviderHasNoCloudEgress
// - RSS-RECOMMENDATION-CLOUD-EGRESS-001@sha256:9d643abace1ef31745f49c46988a140a4e7e7ab570f3bf6af7944545ec221c62 -> ForbiddenDataNeverLeavesForRecommendation
// - RSS-RECOMMENDATION-CLOUD-EGRESS-001@sha256:9d643abace1ef31745f49c46988a140a4e7e7ab570f3bf6af7944545ec221c62 -> ScoringCloudPayloadIsAllowlisted
// - RSS-RECOMMENDATION-CLOUD-EGRESS-001@sha256:9d643abace1ef31745f49c46988a140a4e7e7ab570f3bf6af7944545ec221c62 -> LearningCloudPayloadIsAllowlisted
//
// Scope:
// The model represents data categories crossing the cloud inference boundary,
// not concrete prompt strings. User-authored condition text is an allowed
// category even if its contents happen to mention a URL. Concrete prompt
// construction and provider adapter behavior remain implementation-test
// responsibilities.

module rss_recommendation_cloud_egress

abstract sig Provider {}
one sig Local, Cloud extends Provider {}

abstract sig Operation {}
one sig Scoring, Learning extends Operation {}

abstract sig DataCategory {}
one sig ManualCondition, LearnedCondition, LearnedPriorityCondition, ArticleTitle,
  FeedbackTitle, FeedbackKind, PreviousAssessment extends DataCategory {}
one sig ArticleUrl, FeedBody, LinkedPageBody, SavedSummary extends DataCategory {}

one sig DefaultRecommendationPolicy {
  provider: one Provider
}

sig RecommendationRequest {
  provider: one Provider,
  operation: one Operation,
  cloudEgress: set DataCategory
}

fun scoringAllowlist: set DataCategory {
  ManualCondition + LearnedCondition + LearnedPriorityCondition + ArticleTitle
}

fun learningAllowlist: set DataCategory {
  ManualCondition + LearnedCondition + LearnedPriorityCondition + FeedbackTitle + FeedbackKind + PreviousAssessment
}

fun forbiddenCategories: set DataCategory {
  ArticleUrl + FeedBody + LinkedPageBody + SavedSummary
}

fact RecommendationEgressPolicy {
  DefaultRecommendationPolicy.provider = Local

  all request: RecommendationRequest |
    request.provider = Local implies no request.cloudEgress

  all request: RecommendationRequest |
    request.provider = Cloud and request.operation = Scoring implies
      request.cloudEgress in scoringAllowlist

  all request: RecommendationRequest |
    request.provider = Cloud and request.operation = Learning implies
      request.cloudEgress in learningAllowlist
}

assert DefaultProviderIsLocal {
  DefaultRecommendationPolicy.provider = Local
}

assert LocalProviderHasNoCloudEgress {
  all request: RecommendationRequest |
    request.provider = Local implies no request.cloudEgress
}

assert ForbiddenDataNeverLeavesForRecommendation {
  no request: RecommendationRequest |
    some request.cloudEgress & forbiddenCategories
}

assert ScoringCloudPayloadIsAllowlisted {
  all request: RecommendationRequest |
    request.provider = Cloud and request.operation = Scoring implies
      request.cloudEgress in scoringAllowlist
}

assert LearningCloudPayloadIsAllowlisted {
  all request: RecommendationRequest |
    request.provider = Cloud and request.operation = Learning implies
      request.cloudEgress in learningAllowlist
}

check DefaultProviderIsLocal for 8 expect 0
check LocalProviderHasNoCloudEgress for 8 expect 0
check ForbiddenDataNeverLeavesForRecommendation for 8 expect 0
check ScoringCloudPayloadIsAllowlisted for 8 expect 0
check LearningCloudPayloadIsAllowlisted for 8 expect 0

run RepresentativeCloudScoring {
  some request: RecommendationRequest |
    request.provider = Cloud and
    request.operation = Scoring and
    ArticleTitle in request.cloudEgress and
    ManualCondition in request.cloudEgress
} for 8 expect 1

run RepresentativeCloudLearning {
  some request: RecommendationRequest |
    request.provider = Cloud and
    request.operation = Learning and
    FeedbackTitle in request.cloudEgress and
    PreviousAssessment in request.cloudEgress
} for 8 expect 1
