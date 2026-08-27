package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    context.getSharedPreferences("workout_ai", Context.MODE_PRIVATE)
      .edit()
      .putString("provider", "CHATGPT")
      .putString("workout_policy", "keep-going")
      .putString("memo:2026-08-27", "synthetic-workout-memo")
      .commit()
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().putString("summary_prompt", "custom-prompt").commit()
    context.getSharedPreferences("library_ai_preferences", Context.MODE_PRIVATE)
      .edit().putString("smb_metadata_normalization_prompt", "custom-library-prompt").commit()
    context.getSharedPreferences("smb_library_credentials", Context.MODE_PRIVATE)
      .edit().putString("server", "encrypted-secret").commit()
    context.getSharedPreferences("google_drive_backup", Context.MODE_PRIVATE)
      .edit().putString("folder_uri", "persisted-uri").commit()
    context.getSharedPreferences("local_context_benchmarks", Context.MODE_PRIVATE)
      .edit().putLong("device-memory", 123L).commit()

    val localModels = context.getSharedPreferences("local_summary_models", Context.MODE_PRIVATE)
    localModels.edit()
      .putString("selected_model_id", "model-a")
      .putString("inference_backend", "CPU")
      .putString("model_revision.model-a", "source-revision")
      .putLong("preparing_model.model-a.duration_millis", 123L)
      .commit()

    val store = BackupPreferences(context)
    val bytes = store.encode()
    val encoded = bytes.toString(Charsets.UTF_8)

    assertFalse(encoded.contains("encrypted-secret"))
    assertFalse(encoded.contains("persisted-uri"))
    assertFalse(encoded.contains("device-memory"))
    assertFalse(encoded.contains("source-revision"))
    assertFalse(encoded.contains("duration_millis"))
    assertTrue(encoded.contains("selected_model_id"))
    assertTrue(encoded.contains("custom-library-prompt"))
    assertTrue(encoded.contains("synthetic-workout-memo"))

    context.getSharedPreferences("workout", Context.MODE_PRIVATE)
      .edit().putString("state_v1", "changed").commit()
    context.getSharedPreferences("workout_ai", Context.MODE_PRIVATE)
      .edit().clear().putString("provider", "LOCAL").commit()
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().clear().commit()
    context.getSharedPreferences("library_ai_preferences", Context.MODE_PRIVATE)
      .edit().putString("smb_metadata_normalization_prompt", "changed-library-prompt").commit()
    localModels.edit()
      .putString("selected_model_id", "model-b")
      .putString("model_revision.model-a", "destination-revision")
      .putLong("preparing_model.model-a.duration_millis", 999L)
      .commit()

    store.restore(bytes)

    assertEquals(
      "workout-history",
      context.getSharedPreferences("workout", Context.MODE_PRIVATE).getString("state_v1", null),
    )
    val workoutAi = context.getSharedPreferences("workout_ai", Context.MODE_PRIVATE)
    assertEquals("CHATGPT", workoutAi.getString("provider", null))
    assertEquals("keep-going", workoutAi.getString("workout_policy", null))
    assertEquals("synthetic-workout-memo", workoutAi.getString("memo:2026-08-27", null))
    assertEquals(
      "custom-prompt",
      context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE).getString("summary_prompt", null),
    )
    assertEquals(
      "custom-library-prompt",
      context.getSharedPreferences("library_ai_preferences", Context.MODE_PRIVATE)
        .getString("smb_metadata_normalization_prompt", null),
    )
    assertEquals(
      "encrypted-secret",
      context.getSharedPreferences("smb_library_credentials", Context.MODE_PRIVATE).getString("server", null),
    )
    assertEquals("model-a", localModels.getString("selected_model_id", null))
    assertEquals("destination-revision", localModels.getString("model_revision.model-a", null))
    assertEquals(999L, localModels.getLong("preparing_model.model-a.duration_millis", 0L))
  }
}
