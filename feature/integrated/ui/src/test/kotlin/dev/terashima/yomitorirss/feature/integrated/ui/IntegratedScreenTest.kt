package dev.terashima.yomitorirss.feature.integrated.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class IntegratedScreenTest {
  @Test
  fun `統合ビューのスワイプ操作は各機能の一覧と一致する`() {
    val rss = item(IntegratedSource.RSS)
    val reddit = item(IntegratedSource.REDDIT)
    val youtube = item(IntegratedSource.YOUTUBE)
    val mail = item(IntegratedSource.MAIL, isDeferred = true, isStarred = true)

    assertEquals(listOf("既読", "ブックマーク", "あとで読む"), swipeLabels(rss, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "ブックマーク", "あとで読む"), swipeLabels(reddit, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "保存", "あとで見る"), swipeLabels(youtube, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "あとで読む解除", "アーカイブ"), swipeLabels(mail, IntegratedTab.UNREAD))

    assertEquals(listOf("ブックマーク解除", "未分類へ", null), swipeLabels(rss, IntegratedTab.READ_LATER))
    assertEquals(listOf("ブックマーク解除", "未分類へ", null), swipeLabels(reddit, IntegratedTab.READ_LATER))
    assertEquals(listOf("既読", "保存", "未読へ戻す"), swipeLabels(youtube, IntegratedTab.READ_LATER))
    assertEquals(listOf("あとで読む解除", "スター解除", "アーカイブ"), swipeLabels(mail, IntegratedTab.READ_LATER))

    assertEquals(listOf(false, false, false), swipeDismisses(youtube, IntegratedTab.READ_LATER))
    assertEquals(listOf(true, false, false), swipeDismisses(mail, IntegratedTab.READ_LATER))
  }

  @Test
  fun `メールの未読スワイプは保留状態に応じて切り替わる`() {
    val active = item(IntegratedSource.MAIL)
    val deferred = item(IntegratedSource.MAIL, isDeferred = true)

    assertEquals("あとで読む", integratedSwipeActions(active, IntegratedTab.UNREAD).right?.label)
    assertEquals(true, integratedSwipeActions(active, IntegratedTab.UNREAD).right?.dismissesItem)
    assertEquals("あとで読む解除", integratedSwipeActions(deferred, IntegratedTab.UNREAD).right?.label)
    assertEquals(false, integratedSwipeActions(deferred, IntegratedTab.UNREAD).right?.dismissesItem)
  }

  private fun swipeLabels(item: IntegratedItem, tab: IntegratedTab): List<String?> {
    val actions = integratedSwipeActions(item, tab)
    return listOf(actions.left?.label, actions.right?.label, actions.farRight?.label)
  }

  private fun swipeDismisses(item: IntegratedItem, tab: IntegratedTab): List<Boolean?> {
    val actions = integratedSwipeActions(item, tab)
    return listOf(actions.left?.dismissesItem, actions.right?.dismissesItem, actions.farRight?.dismissesItem)
  }

  private fun item(
    source: IntegratedSource,
    isDeferred: Boolean = false,
    isStarred: Boolean = false,
  ) = IntegratedItem(
    key = source.name.lowercase(),
    source = source,
    title = source.label,
    subtitle = "",
    timestamp = 1L,
    isDeferred = isDeferred,
    isStarred = isStarred,
  )
}
