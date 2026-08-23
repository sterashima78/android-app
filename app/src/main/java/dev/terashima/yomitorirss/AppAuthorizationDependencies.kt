package dev.terashima.yomitorirss

import android.app.PendingIntent
import android.content.Intent

/** Activity-result boundary for Gmail authorization. */
data class MailAuthorizationDependencies internal constructor(
  val requestAccount: suspend () -> MailAuthorizationOutcome,
  val resultFromIntent: suspend (Intent) -> MailAuthorizedAccount,
)

data class MailAuthorizedAccount internal constructor(
  val email: String,
  val displayName: String?,
  val accessToken: String,
)

sealed interface MailAuthorizationOutcome {
  data class Authorized(val account: MailAuthorizedAccount) : MailAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : MailAuthorizationOutcome
}

/** Activity-result boundary for Google Books authorization. */
data class LibraryAuthorizationDependencies internal constructor(
  val requestAccount: suspend () -> LibraryAuthorizationOutcome,
  val resultFromIntent: (Intent) -> LibraryAuthorizedAccount,
)

data class LibraryAuthorizedAccount internal constructor(
  val accessToken: String,
  val accountLabel: String?,
)

sealed interface LibraryAuthorizationOutcome {
  data class Authorized(val account: LibraryAuthorizedAccount) : LibraryAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : LibraryAuthorizationOutcome
}
