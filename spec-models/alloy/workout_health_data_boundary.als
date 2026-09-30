// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - WORKOUT-HEALTH-BOUNDARY-001@sha256:dcef8ee89badc30a1792ab4b6cdf2c8a2b9026dd06b6603dcbbb67eeb4f7d630 -> HealthReadNeverFeedsWorkoutOrAi
// - WORKOUT-HEALTH-BOUNDARY-001@sha256:dcef8ee89badc30a1792ab4b6cdf2c8a2b9026dd06b6603dcbbb67eeb4f7d630 -> OnlyCompletedWorkoutMayWriteHealthData
// - WORKOUT-HEALTH-BOUNDARY-001@sha256:dcef8ee89badc30a1792ab4b6cdf2c8a2b9026dd06b6603dcbbb67eeb4f7d630 -> NoDerivedWorkoutMetricWrite
// - HEALTH-READ-OWNERSHIP-001@sha256:0c8cc86386d388c6a1427640a1059d59ef1fdf819777dfc5a804456f5277b672 -> HealthReadStaysInReadModel
// - HEALTH-READ-OWNERSHIP-001@sha256:0c8cc86386d388c6a1427640a1059d59ef1fdf819777dfc5a804456f5277b672 -> HealthReadNeverPersistsInAppDatabase
// - HEALTH-READ-OWNERSHIP-001@sha256:0c8cc86386d388c6a1427640a1059d59ef1fdf819777dfc5a804456f5277b672 -> OnlyCompletedWorkoutMayWriteHealthData
//
// Scope:
// This model represents ownership and allowed data-flow relations, not platform
// permission checks or record mapping. Health read values are abstract atoms;
// the only modeled outbound write source is a completed app-owned Workout.
// Runtime availability, permission gating, and concrete record conversion remain
// covered by implementation tests.

module workout_health_data_boundary

abstract sig Destination {}
one sig HealthReadModel, AppDatabase, WorkoutState, AiInput extends Destination {}

sig HealthReadDatum {
  flowsTo: set Destination
}

abstract sig HealthWriteSource {}
one sig CompletedWorkout, DerivedWorkoutMetric, ImportedHealthRead extends HealthWriteSource {}

sig HealthWrite {
  source: one HealthWriteSource
}

fact HealthReadBoundary {
  all datum: HealthReadDatum |
    datum.flowsTo = HealthReadModel
}

fact WorkoutWriteBoundary {
  all write: HealthWrite |
    write.source = CompletedWorkout
}

assert HealthReadStaysInReadModel {
  all datum: HealthReadDatum |
    datum.flowsTo = HealthReadModel
}

assert HealthReadNeverPersistsInAppDatabase {
  no datum: HealthReadDatum |
    AppDatabase in datum.flowsTo
}

assert HealthReadNeverFeedsWorkoutOrAi {
  no datum: HealthReadDatum |
    some datum.flowsTo & (WorkoutState + AiInput)
}

assert OnlyCompletedWorkoutMayWriteHealthData {
  all write: HealthWrite |
    write.source = CompletedWorkout
}

assert NoDerivedWorkoutMetricWrite {
  no write: HealthWrite |
    write.source = DerivedWorkoutMetric
}

check HealthReadStaysInReadModel for 8 expect 0
check HealthReadNeverPersistsInAppDatabase for 8 expect 0
check HealthReadNeverFeedsWorkoutOrAi for 8 expect 0
check OnlyCompletedWorkoutMayWriteHealthData for 8 expect 0
check NoDerivedWorkoutMetricWrite for 8 expect 0

run RepresentativeBoundary {
  some HealthReadDatum
  some HealthWrite
} for 8 expect 1
