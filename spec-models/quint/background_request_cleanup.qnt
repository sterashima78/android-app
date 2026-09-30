// Natural-language specifications:
// - docs/spec/13-background-execution.md
//
// Covers:
// - BACKGROUND-CLEANUP-001@sha256:c7089c5e33c583c62ccc7858548f8f627008d739a2828bda1b360351fdabc57e -> cleanupExpiredRequest
// - BACKGROUND-CLEANUP-001@sha256:c7089c5e33c583c62ccc7858548f8f627008d739a2828bda1b360351fdabc57e -> activeWorkRetainsRequest
//
// Abstraction:
// Work state is reduced to the states relevant to request cleanup. The model
// checks that expiry alone cannot remove request input while work is active.
// Terminal and orphaned work are represented as cleanup-eligible states.

module background_request_cleanup {
  var requestExists: bool
  var expired: bool
  var workState: int

  action init = all {
    requestExists' = true,
    expired' = false,
    workState' = 1,
  }

  action expire = all {
    requestExists' = requestExists,
    expired' = true,
    workState' = workState,
  }

  action enqueueToBlocked = all {
    requestExists,
    workState == 1,
    requestExists' = requestExists,
    expired' = expired,
    workState' = 2,
  }

  action blockedToRunning = all {
    requestExists,
    workState == 2,
    requestExists' = requestExists,
    expired' = expired,
    workState' = 3,
  }

  action runningToSucceeded = all {
    requestExists,
    workState == 3,
    requestExists' = requestExists,
    expired' = expired,
    workState' = 4,
  }

  action runningToFailed = all {
    requestExists,
    workState == 3,
    requestExists' = requestExists,
    expired' = expired,
    workState' = 5,
  }

  action activeToCancelled = all {
    requestExists,
    (workState == 1 or workState == 2 or workState == 3),
    requestExists' = requestExists,
    expired' = expired,
    workState' = 6,
  }

  action loseWorkRecord = all {
    requestExists,
    requestExists' = requestExists,
    expired' = expired,
    workState' = 0,
  }

  action cleanupExpiredRequest = all {
    requestExists,
    expired,
    (workState == 0 or workState == 4 or workState == 5 or workState == 6),
    requestExists' = false,
    expired' = expired,
    workState' = workState,
  }

  action remainCleaned = all {
    not(requestExists),
    requestExists' = false,
    expired' = expired,
    workState' = workState,
  }

  action step = any {
    expire,
    enqueueToBlocked,
    blockedToRunning,
    runningToSucceeded,
    runningToFailed,
    activeToCancelled,
    loseWorkRecord,
    cleanupExpiredRequest,
    remainCleaned,
  }

  val activeWorkRetainsRequest =
    not(workState == 1 or workState == 2 or workState == 3) or requestExists

  val safety = activeWorkRetainsRequest
}
