// Natural-language specifications:
// - docs/spec/10-web-x-widget-games.md
//
// Covers:
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> startServer
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> bootstrapSuccess
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> bootstrapQueryRejectedAfterConsumption
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> sessionAuthenticated
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> rotateAddress
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> stopServer
// - LAN-WEB-AUTH-LIFECYCLE-001@sha256:12c9aa0d6230d69a7f83b8693fb0bf7341d3036aaaeab9fd2ce4996308d87b8a -> credentialExclusive
//
// Abstraction:
// Token bytes are not modeled. Instead, the model tracks which credential class
// is currently valid for one server generation. Replacing the bootstrap token
// starts a new generation and invalidates the previous bootstrap/session state.
// URL query stripping and Cookie attributes remain implementation-level tests.

module lan_web_auth_lifecycle {
  val STOPPED = 0
  val BOOTSTRAP_READY = 1
  val SESSION_READY = 2

  var phase: int
  var generation: int
  var bootstrapValid: bool
  var sessionValid: bool

  action init = all {
    phase' = STOPPED,
    generation' = 0,
    bootstrapValid' = false,
    sessionValid' = false,
  }

  action startServer = all {
    phase == STOPPED,
    phase' = BOOTSTRAP_READY,
    generation' = generation + 1,
    bootstrapValid' = true,
    sessionValid' = false,
  }

  action bootstrapSuccess = all {
    phase == BOOTSTRAP_READY,
    bootstrapValid,
    phase' = SESSION_READY,
    generation' = generation,
    bootstrapValid' = false,
    sessionValid' = true,
  }

  action bootstrapQueryRejectedAfterConsumption = all {
    phase == SESSION_READY,
    not(bootstrapValid),
    phase' = SESSION_READY,
    generation' = generation,
    bootstrapValid' = false,
    sessionValid' = sessionValid,
  }

  action sessionAuthenticated = all {
    phase == SESSION_READY,
    sessionValid,
    phase' = SESSION_READY,
    generation' = generation,
    bootstrapValid' = false,
    sessionValid' = true,
  }

  action rotateAddress = all {
    not(phase == STOPPED),
    phase' = BOOTSTRAP_READY,
    generation' = generation + 1,
    bootstrapValid' = true,
    sessionValid' = false,
  }

  action stopServer = all {
    not(phase == STOPPED),
    phase' = STOPPED,
    generation' = generation,
    bootstrapValid' = false,
    sessionValid' = false,
  }

  action idle = all {
    phase' = phase,
    generation' = generation,
    bootstrapValid' = bootstrapValid,
    sessionValid' = sessionValid,
  }

  action step = any {
    startServer,
    bootstrapSuccess,
    bootstrapQueryRejectedAfterConsumption,
    sessionAuthenticated,
    rotateAddress,
    stopServer,
    idle,
  }

  val knownPhase =
    phase == STOPPED or
    phase == BOOTSTRAP_READY or
    phase == SESSION_READY

  val credentialExclusive =
    not(bootstrapValid and sessionValid)

  val stoppedHasNoCredential =
    not(phase == STOPPED) or (not(bootstrapValid) and not(sessionValid))

  val bootstrapPhaseHasOnlyBootstrap =
    not(phase == BOOTSTRAP_READY) or (bootstrapValid and not(sessionValid))

  val sessionPhaseHasOnlySession =
    not(phase == SESSION_READY) or (not(bootstrapValid) and sessionValid)

  val nonNegativeGeneration =
    generation >= 0

  val safety =
    knownPhase and
    credentialExclusive and
    stoppedHasNoCredential and
    bootstrapPhaseHasOnlyBootstrap and
    sessionPhaseHasOnlySession and
    nonNegativeGeneration
}
