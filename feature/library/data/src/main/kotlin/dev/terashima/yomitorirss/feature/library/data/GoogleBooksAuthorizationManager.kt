package dev.terashima.yomitorirss.feature.library.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class GoogleBooksAuthorizedAccount(
  val accessToken: String,
  val accountLabel: String?,
)

sealed interface GoogleBooksAuthorizationOutcome {
  data class Authorized(val account: GoogleBooksAuthorizedAccount) : GoogleBooksAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : GoogleBooksAuthorizationOutcome
}

class GoogleBooksAuthorizationManager(context: Context) {
  private val client = Identity.getAuthorizationClient(context.applicationContext)

  suspend fun requestAccount(): GoogleBooksAuthorizationOutcome {
    val request = baseRequestBuilder()
      .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
      .build()
    return outcome(authorize(request))
  }

  fun resultFromIntent(data: Intent): GoogleBooksAuthorizedAccount = account(
    client.getAuthorizationResultFromIntent(data),
  )

  private suspend fun authorize(request: AuthorizationRequest): AuthorizationResult =
    suspendCoroutine { continuation ->
      client.authorize(request)
        .addOnSuccessListener(continuation::resume)
        .addOnFailureListener(continuation::resumeWithException)
    }

  private fun baseRequestBuilder(): AuthorizationRequest.Builder = AuthorizationRequest.builder()
    .setRequestedScopes(listOf(Scope(BOOKS_SCOPE)))

  private fun outcome(result: AuthorizationResult): GoogleBooksAuthorizationOutcome = if (result.hasResolution()) {
    GoogleBooksAuthorizationOutcome.RequiresResolution(
      requireNotNull(result.pendingIntent) { "Google Books の認証画面を開けませんでした" },
    )
  } else {
    GoogleBooksAuthorizationOutcome.Authorized(account(result))
  }

  private fun account(result: AuthorizationResult): GoogleBooksAuthorizedAccount {
    val accessToken = result.accessToken
    require(!accessToken.isNullOrBlank()) { "Google Books のアクセストークンを取得できませんでした" }
    val googleAccount = result.toGoogleSignInAccount()
    return GoogleBooksAuthorizedAccount(
      accessToken = accessToken,
      accountLabel = googleAccount?.email?.takeIf(String::isNotBlank)
        ?: googleAccount?.displayName?.takeIf(String::isNotBlank),
    )
  }

  private companion object {
    const val BOOKS_SCOPE = "https://www.googleapis.com/auth/books"
  }
}
