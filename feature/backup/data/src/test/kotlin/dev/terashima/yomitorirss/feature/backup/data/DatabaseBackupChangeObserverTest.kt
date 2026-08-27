package dev.terashima.yomitorirss.feature.backup.data

import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseBackupChangeObserverTest {
  @Test
  fun `永続データ変更ごとにバックアップをスケジュールする`() {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var scheduled = 0
    val observer = DatabaseBackupChangeObserver(
      dataChanges = changes,
      scheduler = BackupChangeScheduler { scheduled += 1 },
      scope = scope,
    )

    observer.start()
    assertEquals(0, scheduled)

    changes.tryEmit(Unit)
    assertEquals(1, scheduled)

    observer.start()
    changes.tryEmit(Unit)
    assertEquals(2, scheduled)

    observer.stop()
    changes.tryEmit(Unit)
    assertEquals(2, scheduled)
  }
}
