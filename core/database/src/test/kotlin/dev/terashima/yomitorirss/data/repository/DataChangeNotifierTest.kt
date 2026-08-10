package dev.terashima.yomitorirss.core.database
import org.junit.Assert.assertEquals
import org.junit.Test

class DataChangeNotifierTest {
  @Test
  fun `通知ごとにバージョンが増える`() {
    val notifier = DataChangeNotifier()

    assertEquals(0L, notifier.version.value)

    notifier.notifyChanged()
    notifier.notifyChanged()

    assertEquals(2L, notifier.version.value)
  }
}
