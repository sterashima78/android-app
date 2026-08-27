package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupPreferenceChangeObserverTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    BackupPreferences.BACKED_UP_PREFERENCES.forEach { name ->
      context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun `backup対象preferencesの変更を永続化変更として通知する`() {
    val notifier = PersistenceChangeNotifier()
    val observer = BackupPreferenceChangeObserver(context, notifier)
    observer.start()

    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit()
      .putString("summary_prompt", "custom")
      .commit()

    assertEquals(1L, notifier.version.value)
    observer.stop()
  }

  @Test
  fun `key allowlist外の変更は通知しない`() {
    val notifier = PersistenceChangeNotifier()
    val observer = BackupPreferenceChangeObserver(context, notifier)
    observer.start()
    val preferences = context.getSharedPreferences("local_summary_models", Context.MODE_PRIVATE)

    preferences.edit().putString("model_revision.model-a", "revision").commit()
    assertEquals(0L, notifier.version.value)

    preferences.edit().putString("selected_model_id", "model-a").commit()
    assertEquals(1L, notifier.version.value)
    observer.stop()
  }

  @Test
  fun `backup restore中のbackground preferences変更は遅延callbackも個別通知しない`() {
    val store = BackupPreferences(context)
    val preferences = context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
    preferences.edit().putString("summary_prompt", "backup-value").commit()
    val backup = store.encode()
    preferences.edit().putString("summary_prompt", "current-value").commit()

    val notifier = PersistenceChangeNotifier()
    val observer = BackupPreferenceChangeObserver(context, notifier)
    observer.start()
    val completed = CountDownLatch(1)
    thread(name = "backup-restore-test") {
      try {
        store.restore(backup)
      } finally {
        completed.countDown()
      }
    }
    completed.await()

    // Background SharedPreferences commits enqueue listener callbacks onto the main looper.
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(0L, notifier.version.value)
    assertEquals("backup-value", preferences.getString("summary_prompt", null))
    observer.stop()
  }
}
