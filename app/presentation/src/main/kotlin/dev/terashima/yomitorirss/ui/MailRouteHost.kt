package dev.terashima.yomitorirss.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.mail.MailRoute
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.platform.authorization.MailAuthorizationOutcome
import kotlinx.coroutines.launch

@Composable
internal fun MailRouteHost(
  modifier: Modifier,
  routeDependencies: AppRouteDependencies,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val mailViewModel: MailViewModel = viewModel(factory = routeDependencies.mailViewModelFactory)
  val authorization = routeDependencies.mailAuthorization
  val authorizationLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val data = result.data
    if (data == null) {
      Toast.makeText(context, "Gmail の認証結果を取得できませんでした", Toast.LENGTH_LONG).show()
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      runCatching { authorization.resultFromIntent(data) }
        .onSuccess { account ->
          mailViewModel.connectAuthorizedAccount(
            email = account.email,
            displayName = account.displayName,
            accessToken = account.accessToken,
          )
        }
        .onFailure { error ->
          Toast.makeText(
            context,
            error.message ?: "Gmail の認証に失敗しました",
            Toast.LENGTH_LONG,
          ).show()
        }
    }
  }

  val requestMailAccount: () -> Unit = {
    scope.launch {
      runCatching { authorization.requestAccount() }
        .onSuccess { outcome ->
          when (outcome) {
            is MailAuthorizationOutcome.Authorized -> {
              val account = outcome.account
              mailViewModel.connectAuthorizedAccount(
                email = account.email,
                displayName = account.displayName,
                accessToken = account.accessToken,
              )
            }

            is MailAuthorizationOutcome.RequiresResolution -> {
              authorizationLauncher.launch(
                IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
              )
            }
          }
        }
        .onFailure { error ->
          Toast.makeText(
            context,
            error.message ?: "Gmail の認証を開始できませんでした",
            Toast.LENGTH_LONG,
          ).show()
        }
    }
  }

  MailRoute(
    modifier = modifier,
    mailViewModel = mailViewModel,
    onAddAccount = requestMailAccount,
  )
}
