package dev.terashima.yomitorirss.security

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppLockCoordinator(
  private val activity: ComponentActivity,
  private val session: AppLockSessionViewModel,
  private val preferences: AppLockPreferences = AppLockPreferences(activity),
  private val onUnlocked: () -> Unit,
) {
  var enabled by mutableStateOf(false)
    private set
  var unlocked by mutableStateOf(true)
    private set
  private var promptShowing = false

  fun initialize() {
    enabled = preferences.enabled
    unlocked = !enabled || session.unlocked
    if (enabled) protectWindowContent()
  }

  fun onStart() {
    if (enabled && !unlocked) requestUnlock()
  }

  fun onResume() {
    if (!enabled || unlocked) exposeWindowContent()
  }

  fun onPause() {
    if (enabled && !activity.isChangingConfigurations && !promptShowing) {
      protectWindowContent()
    }
  }

  fun onStop() {
    if (
      enabled &&
      !activity.isChangingConfigurations &&
      !promptShowing &&
      appLockExternalTransitionTracker.shouldLockOnStop()
    ) {
      session.lock()
      unlocked = false
    }
  }

  fun updateEnabled(value: Boolean) {
    if (!value) {
      preferences.enabled = false
      session.lock()
      enabled = false
      unlocked = true
      exposeWindowContent()
      return
    }

    val biometricManager = activity.getSystemService(BiometricManager::class.java)
    if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
      BiometricManager.BIOMETRIC_SUCCESS
    ) {
      Toast.makeText(
        activity,
        "端末に生体認証を登録してから有効にしてください",
        Toast.LENGTH_LONG,
      ).show()
      return
    }
    requestAuthentication(enableAfterSuccess = true)
  }

  fun requestUnlock() {
    if (!enabled || unlocked) return
    requestAuthentication(enableAfterSuccess = false)
  }

  private fun requestAuthentication(enableAfterSuccess: Boolean) {
    if (promptShowing) return
    promptShowing = true

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val prompt = BiometricPrompt.Builder(activity)
      .setTitle(if (enableAfterSuccess) "生体認証ロックを有効にする" else "アプリのロックを解除")
      .setSubtitle("生体認証または端末の画面ロックで認証")
      .setAllowedAuthenticators(authenticators)
      .build()

    prompt.authenticate(
      CancellationSignal(),
      activity.mainExecutor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          promptShowing = false
          session.unlock()
          if (enableAfterSuccess) {
            preferences.enabled = true
            enabled = true
            unlocked = true
            exposeWindowContent()
            Toast.makeText(activity, "生体認証ロックを有効にしました", Toast.LENGTH_SHORT).show()
          } else {
            unlocked = true
            exposeWindowContent()
            onUnlocked()
          }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          promptShowing = false
          if (enableAfterSuccess && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
            Toast.makeText(activity, errString, Toast.LENGTH_LONG).show()
          }
        }
      },
    )
  }

  private fun protectWindowContent() {
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }

  private fun exposeWindowContent() {
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }
}
