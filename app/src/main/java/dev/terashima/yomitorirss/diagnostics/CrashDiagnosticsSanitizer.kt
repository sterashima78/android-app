package dev.terashima.yomitorirss.diagnostics

internal object CrashDiagnosticsSanitizer {
  fun sanitize(value: String): String {
    var sanitized = value
    sanitized = SMB_BOOK_URI_PATTERN.replace(sanitized, "yomitori://smb-book/open?[redacted]")
    sanitized = WEB_URL_PATTERN.replace(sanitized) { match ->
      "${match.groupValues[1]}://[redacted]"
    }
    sanitized = GENERIC_URI_QUERY_PATTERN.replace(sanitized) { match ->
      "${match.groupValues[1]}?[redacted]"
    }
    sanitized = EMAIL_PATTERN.replace(sanitized, "[redacted-email]")
    sanitized = SENSITIVE_ASSIGNMENT_PATTERN.replace(sanitized) { match ->
      "${match.groupValues[1]}=[redacted]"
    }
    sanitized = BEARER_TOKEN_PATTERN.replace(sanitized) { match ->
      "${match.groupValues[1]}[redacted]"
    }
    sanitized = ANDROID_PRIVATE_PATH_PATTERN.replace(sanitized, "[redacted-path]")
    return sanitized
  }
}

internal fun sanitizeCrashDetails(value: String): String = CrashDiagnosticsSanitizer.sanitize(value)

private val SMB_BOOK_URI_PATTERN = Regex("""yomitori://smb-book/open\?[^\s}]+""")
private val WEB_URL_PATTERN = Regex("""(?i)\b(https?)://[^\s]+""")
private val GENERIC_URI_QUERY_PATTERN = Regex("""\b([A-Za-z][A-Za-z0-9+.-]*://[^\s?]+)\?[^\s}]+""")
private val EMAIL_PATTERN = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
private val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
  """(?i)\b(access_token|refresh_token|token|password|passwd|secret|api_key|apikey|authorization)\s*=\s*([^\s&]+)""",
)
private val BEARER_TOKEN_PATTERN = Regex("""(?i)\b(Bearer\s+)[A-Za-z0-9._~+/=-]+""")
private val ANDROID_PRIVATE_PATH_PATTERN = Regex(
  """(?:(?:/data/(?:user/\d+|data)|/storage/emulated/\d+|/sdcard)/[^\s]+)""",
)
