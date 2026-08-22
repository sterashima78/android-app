package dev.terashima.yomitorirss.feature.mail.data

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.terashima.yomitorirss.feature.mail.MailAuthorizationRequiredException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class GmailAuthorizedAccount(
  val email: String,
  val displayName: String?,
  val accessToken: String,
)

sealed interface GmailAuthorizationOutcome {
  data class Authorized(val account: GmailAuthorizedAccount) : GmailAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : GmailAuthorizationOutcome
}

class GmailAuthorizationManager(context: Context) {
  private val client = Identity.getAuthorizationClient(context.applicationContext)
  private val profileClient = GmailAccountProfileClient()

  suspend fun requestAccount(): GmailAuthorizationOutcome = request(
    email = null,
    selectAccount = true,
  )

  suspend fun accessToken(email: String): String = when (
    val outcome = request(email = email, selectAccount = false)
  ) {
    is GmailAuthorizationOutcome.Authorized -> outcome.account.accessToken
    is GmailAuthorizationOutcome.RequiresResolution -> throw MailAuthorizationRequiredException(email)
  }

  suspend fun resultFromIntent(data: Intent): GmailAuthorizedAccount = account(
    client.getAuthorizationResultFromIntent(data),
    fallbackEmail = null,
  )

  private suspend fun request(
    email: String?,
    selectAccount: Boolean,
  ): GmailAuthorizationOutcome {
    val builder = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(GMAIL_MODIFY_SCOPE)))
    if (selectAccount) {
      builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
    } else if (!email.isNullOrBlank()) {
      builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
    }
    val result = suspendCoroutine<AuthorizationResult> { continuation ->
      client.authorize(builder.build())
        .addOnSuccessListener(continuation::resume)
        .addOnFailureListener(continuation::resumeWithException)
    }
    return if (result.hasResolution()) {
      GmailAuthorizationOutcome.RequiresResolution(
        requireNotNull(result.pendingIntent) { "Google authorization resolution is missing" },
      )
    } else {
      GmailAuthorizationOutcome.Authorized(account(result, email))
    }
  }

  private suspend fun account(
    result: AuthorizationResult,
    fallbackEmail: String?,
  ): GmailAuthorizedAccount {
    val accessToken = result.accessToken
    require(!accessToken.isNullOrBlank()) { "Gmail のアクセストークンを取得できませんでした" }
    val googleAccount = result.toGoogleSignInAccount()
    val email = googleAccount?.email
      ?.takeIf(String::isNotBlank)
      ?: fallbackEmail?.takeIf(String::isNotBlank)
      ?: profileClient.email(accessToken)
    return GmailAuthorizedAccount(
      email = email,
      displayName = googleAccount?.displayName,
      accessToken = accessToken,
    )
  }

  private companion object {
    const val GMAIL_MODIFY_SCOPE = "https://www.googleapis.com/auth/gmail.modify"
    const val GOOGLE_ACCOUNT_TYPE = "com.google"
  }
}
