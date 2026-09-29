package dev.terashima.yomitorirss.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `local AI text subprocessだけをPID ring logの相関対象にする`() {
    val packageName = "dev.terashima.yomitorirss"

    assertTrue(isLocalAiTextProcessName(packageName, "$packageName:local_ai_text"))
    assertFalse(isLocalAiTextProcessName(packageName, packageName))
    assertFalse(isLocalAiTextProcessName(packageName, "$packageName:local_ai_vision"))
    assertFalse(isLocalAiTextProcessName(packageName, null))
  }

  @Test
  fun `process exit reasonを共有レポート向けの名前へ変換する`() {
    assertEquals("MEMORY_LIMITER", processExitReasonName(ANDROID_17_REASON_MEMORY_LIMITER))
    assertEquals("REASON_999", processExitReasonName(999))
  }

  @Test
  fun `low memory と undelivered broadcast の併記を保持された system subreason として分類する`() {
    assertEquals(
      "LOW_MEMORY_WITH_RETAINED_SYSTEM_SUBREASON",
      processExitReasonContext(
        reason = ApplicationExitInfo.REASON_LOW_MEMORY,
        description = "[UNDELIVERED BROADCAST] Can't deliver broadcast",
      ),
    )
  }

  @Test
  fun `undelivered broadcast 単独では low memory の保持 subreason と分類しない`() {
    assertEquals(
      null,
      processExitReasonContext(
        reason = ApplicationExitInfo.REASON_OTHER,
        description = "[UNDELIVERED BROADCAST] Can't deliver broadcast",
      ),
    )
  }

  @Test
  fun `process exit report schema version を固定する`() {
    assertEquals(2, PROCESS_EXIT_REPORT_SCHEMA_VERSION)
  }

  @Test
  fun `service importanceを共有レポート向けの名前へ変換する`() {
    assertEquals(
      "SERVICE",
      processImportanceName(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE),
    )
  }
}
