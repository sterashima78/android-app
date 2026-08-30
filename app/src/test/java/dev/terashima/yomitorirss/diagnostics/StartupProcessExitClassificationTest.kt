package dev.terashima.yomitorirss.diagnostics

import android.app.ActivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupProcessExitClassificationTest {
  @Test
  fun `Android17 memory limiter reasonをメモリ関連終了として扱う`() {
    assertTrue(
      isMemoryRelatedProcessExit(
        reason = ANDROID_17_REASON_MEMORY_LIMITER,
        description = null,
      ),
    )
  }

  @Test
  fun `Android17 memory limiter reasonはcachedでも診断対象にする`() {
    assertTrue(
      shouldReportMemoryProcessExit(
        reason = ANDROID_17_REASON_MEMORY_LIMITER,
        description = null,
        importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
      ),
    )
  }

  @Test
  fun `process exit reasonを共有レポート向けの名前へ変換する`() {
    assertEquals("MEMORY_LIMITER", processExitReasonName(ANDROID_17_REASON_MEMORY_LIMITER))
    assertEquals("REASON_999", processExitReasonName(999))
  }

  @Test
  fun `service importanceを共有レポート向けの名前へ変換する`() {
    assertEquals(
      "SERVICE",
      processImportanceName(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE),
    )
  }
}
