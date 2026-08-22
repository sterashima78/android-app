package dev.terashima.yomitorirss

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

    val redacted = redactCrashDetails(source)

    assertTrue(redacted.contains("yomitori://smb-book/open?[redacted]"))
    assertFalse(redacted.contains("synthetic-source"))
    assertFalse(redacted.contains("synthetic-server"))
    assertFalse(redacted.contains("folder%5Cbook.pdf"))
  }

  @Test
  fun `SMB 書籍 URI を含まないクラッシュ情報は変更しない`() {
    val source = "java.lang.IllegalStateException: synthetic failure"

    assertEquals(source, redactCrashDetails(source))
  }
}
