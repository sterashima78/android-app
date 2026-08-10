package dev.terashima.yomitorirss.feature.widget.data
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWidgetRepositoryContractTest {
  @Test
  fun `実装はWidgetRepository契約を満たす`() {
    assertTrue(WidgetRepository::class.java.isAssignableFrom(DefaultWidgetRepository::class.java))
  }
}
