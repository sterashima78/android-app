package dev.terashima.yomitorirss

import android.app.Application
import android.content.Context
import android.os.Build
import java.time.Instant

object StartupCrashStore {
  private const val PREFERENCES_NAME = "startup_crash_diagnostics"
  private const val REPORT_KEY = "last_crash_report"

  @Volatile
  private var installed = false

  fun install(application: Application) {
    if (installed) return
    synchronized(this) {
      if (installed) return
      val previous = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        record(application, thread.name, throwable)
        previous?.uncaughtException(thread, throwable)
      }
      installed = true
    }
  }

  fun record(context: Context, threadName: String, throwable: Throwable) {
    runCatching {
      val report = buildString {
        appendLine("Mosaic crash report")
        appendLine("timestamp=${Instant.now()}")
        appendLine("version=${BuildConfig.VERSION_NAME}")
        appendLine("versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("commit=${BuildConfig.GIT_COMMIT_SHA}")
        appendLine("sdk=${Build.VERSION.SDK_INT}")
        appendLine("release=${Build.VERSION.RELEASE}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("thread=$threadName")
        appendLine()
        append(redactCrashDetails(throwable.stackTraceToString()))
      }
      preferences(context).edit().putString(REPORT_KEY, report).commit()
    }
  }

  fun peek(context: Context): String? =
    preferences(context).getString(REPORT_KEY, null)?.takeIf(String::isNotBlank)

  fun clear(context: Context) {
    preferences(context).edit().remove(REPORT_KEY).commit()
  }

  private fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun redactCrashDetails(value: String): String =
  SMB_BOOK_URI_PATTERN.replace(value, "yomitori://smb-book/open?[redacted]")

private val SMB_BOOK_URI_PATTERN = Regex("""yomitori://smb-book/open\?[^\s}]+""")
