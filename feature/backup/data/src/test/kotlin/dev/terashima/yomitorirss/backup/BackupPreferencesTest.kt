package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupPreferencesTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    (
      BackupPreferences.BACKED_UP_PREFERENCES + setOf(
        "google_drive_backup",
        "smb_library_credentials",
        "local_context_benchmarks",
      )
      ).forEach { name ->
      context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
  }

  @Test
  fun `allowlistのユーザー設定だけを復元する`() {
    context.getSharedPreferences("workout", Context.MODE_PRIVATE)
      .edit().putString("state_v1", "workout-history").commit()
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().putString("summary_prompt", "custom-prompt").commit()
    context.getSharedPreferences("smb_library_credentials", Context.MODE_PRIVATE)
      .edit().putString("server", "encrypted-secret").commit()
    context.getSharedPreferences("google_drive_backup", Context.MODE_PRIVATE)
      .edit().putString("folder_uri", "persisted-uri").commit()
    context.getSharedPreferences("local_context_benchmarks", Context.MODE_PRIVATE)
      .edit().putLong("device-memory", 123L).commit()

    val store = BackupPreferences(context)
    val bytes = store.encode()
    val encoded = bytes.toString(Charsets.UTF_8)

    assertFalse(encoded.contains("encrypted-secret"))
    assertFalse(encoded.contains("persisted-uri"))
    assertFalse(encoded.contains("device-memory"))

    context.getSharedPreferences("workout", Context.MODE_PRIVATE)
      .edit().putString("state_v1", "changed").commit()
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().clear().commit()

    store.restore(bytes)

    assertEquals(
      "workout-history",
      context.getSharedPreferences("workout", Context.MODE_PRIVATE).getString("state_v1", null),
    )
    assertEquals(
      "custom-prompt",
      context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE).getString("summary_prompt", null),
    )
    assertEquals(
      "encrypted-secret",
      context.getSharedPreferences("smb_library_credentials", Context.MODE_PRIVATE).getString("server", null),
    )
  }
}
