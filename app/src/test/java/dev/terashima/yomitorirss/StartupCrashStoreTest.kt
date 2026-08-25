package dev.terashima.yomitorirss

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCrashStoreTest {
  @Test
  fun `SMB 書籍 URI の query をクラッシュ情報から除去する`() {
    val source = """
      java.lang.IllegalArgumentException: Can't open yomitori://smb-book/open?sourceId=synthetic-source&serverId=synthetic-server&path=folder%5Cbook.pdf&size=123&modified=456&format=PDF.
      Caused by: android.content.ActivityNotFoundException: No Activity found to handle Intent { dat=yomitori://smb-book/open?sourceId=synthetic-source&serverId=synthetic-server&path=folder%5Cbook.pdf }
    """.trimIndent()

    val redacted = sanitizeCrashDetails(source)

    assertTrue(redacted.contains("yomitori://smb-book/open?[redacted]"))
    assertFalse(redacted.contains("synthetic-source"))
    assertFalse(redacted.contains("synthetic-server"))
    assertFalse(redacted.contains("folder%5Cbook.pdf"))
  }

  @Test
  fun `共有可能なクラッシュ情報からURL認証値メールとprivate pathを除去する`() {
    val source = """
      java.lang.IllegalStateException: failed at https://example.invalid/private/book?id=abc&token=query-secret
      account=reader@example.invalid
      access_token=synthetic-token
      Authorization=synthetic-authorization
      header=Bearer synthetic-bearer
      file=/storage/emulated/0/Documents/private-book.pdf
      cache=/data/user/0/dev.terashima.yomitorirss/files/local-summary-models/private-model.litertlm
    """.trimIndent()

    val sanitized = sanitizeCrashDetails(source)

    assertTrue(sanitized.contains("https://[redacted]"))
    assertTrue(sanitized.contains("[redacted-email]"))
    assertTrue(sanitized.contains("access_token=[redacted]"))
    assertTrue(sanitized.contains("Authorization=[redacted]"))
    assertTrue(sanitized.contains("Bearer [redacted]"))
    assertTrue(sanitized.contains("[redacted-path]"))
    assertFalse(sanitized.contains("example.invalid"))
    assertFalse(sanitized.contains("private/book"))
    assertFalse(sanitized.contains("reader@example.invalid"))
    assertFalse(sanitized.contains("synthetic-token"))
    assertFalse(sanitized.contains("synthetic-authorization"))
    assertFalse(sanitized.contains("synthetic-bearer"))
    assertFalse(sanitized.contains("private-book.pdf"))
    assertFalse(sanitized.contains("private-model.litertlm"))
  }

  @Test
  fun `機密情報を含まないクラッシュ情報は変更しない`() {
    val source = "java.lang.IllegalStateException: synthetic failure"

    assertEquals(source, sanitizeCrashDetails(source))
  }

  @Test
  fun `main processをアプリ所有として扱う`() {
    assertTrue(
      isAppOwnedProcessName(
        packageName = "dev.terashima.yomitorirss",
        processName = "dev.terashima.yomitorirss",
      ),
    )
  }

  @Test
  fun `アプリの明示的なsubprocessをアプリ所有として扱う`() {
    assertTrue(
      isAppOwnedProcessName(
        packageName = "dev.terashima.yomitorirss",
        processName = "dev.terashima.yomitorirss:local_ai_vision",
      ),
    )
  }

  @Test
  fun `WebView sandbox processをアプリ所有として扱わない`() {
    assertFalse(
      isAppOwnedProcessName(
        packageName = "dev.terashima.yomitorirss",
        processName = "com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
      ),
    )
  }

  @Test
  fun `process名がない終了をアプリ所有として扱わない`() {
    assertFalse(
      isAppOwnedProcessName(
        packageName = "dev.terashima.yomitorirss",
        processName = null,
      ),
    )
  }

  @Test
  fun `MemoryLimiter による終了をメモリ関連として扱う`() {
    assertTrue(
      isMemoryRelatedProcessExit(
        reason = ApplicationExitInfo.REASON_OTHER,
        description = "MemoryLimiter:AnonSwap",
      ),
    )
  }

  @Test
  fun `low memory 終了は description がなくてもメモリ関連として扱う`() {
    assertTrue(
      isMemoryRelatedProcessExit(
        reason = ApplicationExitInfo.REASON_LOW_MEMORY,
        description = null,
      ),
    )
  }

  @Test
  fun `通常の終了理由をメモリ関連として扱わない`() {
    assertFalse(
      isMemoryRelatedProcessExit(
        reason = ApplicationExitInfo.REASON_USER_REQUESTED,
        description = "user requested",
      ),
    )
  }
}
