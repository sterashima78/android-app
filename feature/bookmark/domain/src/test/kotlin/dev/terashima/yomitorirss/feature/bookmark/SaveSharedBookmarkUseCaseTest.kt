package dev.terashima.yomitorirss.feature.bookmark

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveSharedBookmarkUseCaseTest {
  @Test
  fun `新規保存時だけ変更通知する`() = runBlocking {
    var notifications = 0
    val useCase = SaveSharedBookmarkUseCase(
      saver = FakeSharedBookmarkSaver(BookmarkSaveResult.ADDED),
      onBookmarkChanged = { notifications += 1 },
    )

    val result = useCase(
      url = "https://example.com/article",
      title = "Article",
      sourceTitle = "Shared",
    )

    assertEquals(BookmarkSaveResult.ADDED, result)
    assertEquals(1, notifications)
  }

  @Test
  fun `既存Bookmarkでは変更通知しない`() = runBlocking {
    var notifications = 0
    val useCase = SaveSharedBookmarkUseCase(
      saver = FakeSharedBookmarkSaver(BookmarkSaveResult.ALREADY_BOOKMARKED),
      onBookmarkChanged = { notifications += 1 },
    )

    val result = useCase(
      url = "https://example.com/article",
      title = "Article",
      sourceTitle = "Shared",
    )

    assertEquals(BookmarkSaveResult.ALREADY_BOOKMARKED, result)
    assertEquals(0, notifications)
  }

  private class FakeSharedBookmarkSaver(
    private val result: BookmarkSaveResult,
  ) : SharedBookmarkSaver {
    override suspend fun saveSharedArticle(
      url: String,
      title: String,
      sourceTitle: String,
    ): BookmarkSaveResult = result

    override suspend fun saveSharedArticleToFolder(
      url: String,
      title: String,
      sourceTitle: String,
      folderId: String,
    ): BookmarkSaveResult = result
  }
}
