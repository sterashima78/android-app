// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - WORKOUT-REVIEW-UNIQUENESS-001@sha256:21c5e4c68d1159264b4979ed53d517b325de24c870ea8ea75273bf599839b0c2 -> AtMostOneSavedReviewPerDay
// - WORKOUT-REVIEW-UNIQUENESS-001@sha256:21c5e4c68d1159264b4979ed53d517b325de24c870ea8ea75273bf599839b0c2 -> LatestReviewIsSaved
//
// Scope:
// Review atoms represent conceptual generation attempts. The ordering is the
// generation order. The durable store exposes at most one review per day and,
// when attempts exist, points to the latest attempt for that day.

module workout_review_uniqueness

open util/ordering[Review]

sig Day {}

sig Review {
  day: one Day
}

one sig ReviewStore {
  saved: Day -> lone Review
}

fact SavedReviewMatchesDay {
  all targetDay: Day |
    targetDay.(ReviewStore.saved) in { review: Review | review.day = targetDay }
}

fact StoreLatestReviewPerDay {
  all targetDay: Day |
    let attempts = { review: Review | review.day = targetDay },
        selected = targetDay.(ReviewStore.saved) |
      (no attempts and no selected) or
      (
        some attempts and
        one selected and
        no (selected.^next & attempts)
      )
}

assert AtMostOneSavedReviewPerDay {
  all targetDay: Day |
    lone targetDay.(ReviewStore.saved)
}

assert LatestReviewIsSaved {
  all targetDay: Day |
    let attempts = { review: Review | review.day = targetDay },
        selected = targetDay.(ReviewStore.saved) |
      some attempts implies (
        one selected and
        no (selected.^next & attempts)
      )
}

check AtMostOneSavedReviewPerDay for 6 expect 0
check LatestReviewIsSaved for 6 expect 0

run MultipleDaysAndReplacement {
  #Day = 2
  #Review = 3
  some disj earlier, later: Review |
    earlier.day = later.day and
    later in earlier.^next
} for 6 expect 1
