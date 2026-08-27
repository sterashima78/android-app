package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.content.SharedPreferences
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import java.util.concurrent.atomic.AtomicInteger

/** Publishes persistence changes for the SharedPreferences keys included in database-snapshot backups. */
class BackupPreferenceChangeObserver(
  context: Context,
  private val persistenceChanges: PersistenceChangeNotifier = PersistenceChangeNotifier.shared,
) {
  private val appContext = context.applicationContext
  private val registrations = BackupPreferences.BACKUP_RULES.map { rule ->
    val preferences = appContext.getSharedPreferences(rule.name, Context.MODE_PRIVATE)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (
        !BackupPreferenceChangeSuppression.isActive &&
        (key == null || rule.allowedKeys == null || key in rule.allowedKeys)
      ) {
        persistenceChanges.notifyChanged()
      }
    }
    Registration(preferences, listener)
  }
  private var started = false

  fun start() {
    if (started) return
    registrations.forEach { it.preferences.registerOnSharedPreferenceChangeListener(it.listener) }
    started = true
  }

  fun stop() {
    if (!started) return
    registrations.forEach { it.preferences.unregisterOnSharedPreferenceChangeListener(it.listener) }
    started = false
  }

  private data class Registration(
    val preferences: SharedPreferences,
    val listener: SharedPreferences.OnSharedPreferenceChangeListener,
  )
}

internal object BackupPreferenceChangeSuppression {
  private val depth = AtomicInteger(0)

  val isActive: Boolean
    get() = depth.get() > 0

  fun <T> suppress(block: () -> T): T {
    depth.incrementAndGet()
    return try {
      block()
    } finally {
      depth.decrementAndGet()
    }
  }
}
