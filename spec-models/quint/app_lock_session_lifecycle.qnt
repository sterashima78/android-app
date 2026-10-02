// Natural-language specifications:
// - docs/spec/15-privacy-security.md
//
// Covers:
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> enableAfterAuthentication
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> unlockSuccess
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> normalBackgroundStop
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> lifecycleStopIgnored
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> customTabLaunchStarted
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> customTabLaunchFailed
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> customTabMarkerExpires
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> stopAfterFreshCustomTab
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> stopAfterStaleCustomTab
// - APP-LOCK-SESSION-001@sha256:44958760c899bfc3c0e21a343334bc3bdd3e308db3d90402599cb68b0c6ccf41 -> disabledIsUnlocked
//
// Abstraction:
// The model tracks the app-wide enabled/unlocked session state and the
// process-local Custom Tabs transition marker. Durable preference membership,
// biometric capability checks, prompt implementation, FLAG_SECURE, and exact
// elapsed time are covered by platform code/tests; FRESH means the marker is
// still within the 10-second
// one-shot window and STALE means it has expired.

module app_lock_session_lifecycle {
  val NO_MARKER = 0
  val FRESH_MARKER = 1
  val STALE_MARKER = 2

  var enabled: bool
  var unlocked: bool
  var customTabMarker: int

  action init = all {
    enabled' = false,
    unlocked' = true,
    customTabMarker' = NO_MARKER,
  }

  action enableAfterAuthentication = all {
    not(enabled),
    enabled' = true,
    unlocked' = true,
    customTabMarker' = customTabMarker,
  }

  action disableLock = all {
    enabled,
    enabled' = false,
    unlocked' = true,
    customTabMarker' = customTabMarker,
  }

  action unlockSuccess = all {
    enabled,
    not(unlocked),
    enabled' = true,
    unlocked' = true,
    customTabMarker' = customTabMarker,
  }

  action normalBackgroundStop = all {
    enabled,
    customTabMarker == NO_MARKER,
    enabled' = true,
    unlocked' = false,
    customTabMarker' = NO_MARKER,
  }

  action lifecycleStopIgnored = all {
    enabled,
    enabled' = true,
    unlocked' = unlocked,
    customTabMarker' = customTabMarker,
  }

  action customTabLaunchStarted = all {
    enabled' = enabled,
    unlocked' = unlocked,
    customTabMarker' = FRESH_MARKER,
  }

  action customTabLaunchFailed = all {
    customTabMarker == FRESH_MARKER,
    enabled' = enabled,
    unlocked' = unlocked,
    customTabMarker' = NO_MARKER,
  }

  action customTabMarkerExpires = all {
    customTabMarker == FRESH_MARKER,
    enabled' = enabled,
    unlocked' = unlocked,
    customTabMarker' = STALE_MARKER,
  }

  action stopAfterFreshCustomTab = all {
    enabled,
    customTabMarker == FRESH_MARKER,
    enabled' = true,
    unlocked' = unlocked,
    customTabMarker' = NO_MARKER,
  }

  action stopAfterStaleCustomTab = all {
    enabled,
    customTabMarker == STALE_MARKER,
    enabled' = true,
    unlocked' = false,
    customTabMarker' = NO_MARKER,
  }

  action idle = all {
    enabled' = enabled,
    unlocked' = unlocked,
    customTabMarker' = customTabMarker,
  }

  action step = any {
    enableAfterAuthentication,
    disableLock,
    unlockSuccess,
    normalBackgroundStop,
    lifecycleStopIgnored,
    customTabLaunchStarted,
    customTabLaunchFailed,
    customTabMarkerExpires,
    stopAfterFreshCustomTab,
    stopAfterStaleCustomTab,
    idle,
  }

  val knownMarker =
    customTabMarker == NO_MARKER or
    customTabMarker == FRESH_MARKER or
    customTabMarker == STALE_MARKER

  val disabledIsUnlocked =
    enabled or unlocked

  val safety =
    knownMarker and
    disabledIsUnlocked
}
