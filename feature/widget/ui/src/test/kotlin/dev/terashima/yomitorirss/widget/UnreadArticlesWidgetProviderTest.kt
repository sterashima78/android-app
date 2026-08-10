package dev.terashima.yomitorirss.feature.widget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnreadArticlesWidgetProviderTest {
  @Test
  fun `記事操作ごとに異なるアクション識別子を使う`() {
    assertEquals("open", UnreadArticlesWidgetProvider.ITEM_ACTION_OPEN)
    assertEquals("mark_read", UnreadArticlesWidgetProvider.ITEM_ACTION_MARK_READ)
    assertEquals("read_later", UnreadArticlesWidgetProvider.ITEM_ACTION_READ_LATER)
    assertNotEquals(
      UnreadArticlesWidgetProvider.ITEM_ACTION_MARK_READ,
      UnreadArticlesWidgetProvider.ITEM_ACTION_READ_LATER,
    )
  }
}
