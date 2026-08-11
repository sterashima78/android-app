package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import org.json.JSONObject

internal class GoogleBooksApiClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun library(accessToken: String): List<LibraryBook> {
    val books = linkedMapOf<String, LibraryBook>()
    LIBRARY_SHELVES.forEach { shelf ->
      pageThroughShelf(shelf, accessToken) { book -> books[book.sourceId] = book }
    }
    return books.values.toList()
  }

  private suspend fun pageThroughShelf(
    shelf: Int,
    accessToken: String,
    onBook: (LibraryBook) -> Unit,
  ) {
    var startIndex = 0
    while (true) {
      val response = httpClient.execute(
        HttpRequest(
          url = "$BASE_URL/mylibrary/bookshelves/$shelf/volumes" +
            "?startIndex=$startIndex&maxResults=$PAGE_SIZE&projection=full&showPreorders=true",
          headers = mapOf(
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/json",
          ),
        ),
      )
      if (!response.isSuccessful) {
        throw IOException("Google Books API の取得に失敗しました (${response.statusCode})")
      }

      val root = JSONObject(response.body.toString(Charsets.UTF_8))
      val items = root.optJSONArray("items")
      val itemCount = items?.length() ?: 0
      for (index in 0 until itemCount) {
        parseBook(items.getJSONObject(index), shelf)?.let(onBook)
      }

      val totalItems = root.optInt("totalItems", itemCount)
      startIndex += itemCount
      if (itemCount == 0 || startIndex >= totalItems) break
    }
  }

  private fun parseBook(volume: JSONObject, shelf: Int): LibraryBook? {
    val id = volume.optString("id").takeIf(String::isNotBlank) ?: return null
    val info = volume.optJSONObject("volumeInfo") ?: return null
    val title = info.optString("title").takeIf(String::isNotBlank) ?: "タイトル不明"
    val authorsJson = info.optJSONArray("authors")
    val authors = buildList {
      if (authorsJson != null) {
        for (index in 0 until authorsJson.length()) {
          authorsJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
      }
    }
    val identifiers = info.optJSONArray("industryIdentifiers")
    var isbn10: String? = null
    var isbn13: String? = null
    if (identifiers != null) {
      for (index in 0 until identifiers.length()) {
        val identifier = identifiers.optJSONObject(index) ?: continue
        when (identifier.optString("type")) {
          "ISBN_10" -> isbn10 = identifier.stringOrNull("identifier")
          "ISBN_13" -> isbn13 = identifier.stringOrNull("identifier")
        }
      }
    }
    val imageLinks = info.optJSONObject("imageLinks")
    val thumbnail = imageLinks?.stringOrNull("thumbnail")
      ?.replace("http://", "https://")
    val accessInfo = volume.optJSONObject("accessInfo")
    val userInfo = volume.optJSONObject("userInfo")
    val isPurchased = when {
      userInfo?.has("isPurchased") == true -> userInfo.optBoolean("isPurchased", false)
      else -> shelf == PURCHASED_SHELF
    }

    return LibraryBook(
      source = LibrarySource.GOOGLE_PLAY_BOOKS,
      sourceId = id,
      title = title,
      authors = authors,
      publisher = info.stringOrNull("publisher"),
      publishedDate = info.stringOrNull("publishedDate"),
      description = info.stringOrNull("description"),
      isbn10 = isbn10,
      isbn13 = isbn13,
      thumbnailUrl = thumbnail,
      infoUrl = googleBooksReadingUrl(
        webReaderLink = accessInfo?.stringOrNull("webReaderLink"),
        infoLink = info.stringOrNull("infoLink"),
        isPurchased = isPurchased,
      ),
    )
  }

  private fun JSONObject.stringOrNull(name: String): String? = if (has(name) && !isNull(name)) {
    optString(name).takeIf(String::isNotBlank)
  } else {
    null
  }

  private companion object {
    const val BASE_URL = "https://www.googleapis.com/books/v1"
    const val PAGE_SIZE = 40
    const val PURCHASED_SHELF = 1
    const val MY_EBOOKS_SHELF = 7

    // My eBooks を先に処理し、Purchased の購入判定を同一 Volume の最終状態として残す。
    val LIBRARY_SHELVES = listOf(MY_EBOOKS_SHELF, PURCHASED_SHELF)
  }
}

@Suppress("UNUSED_PARAMETER")
internal fun googleBooksReadingUrl(
  webReaderLink: String?,
  infoLink: String?,
  isPurchased: Boolean = false,
): String? = webReaderLink?.takeIf(String::isNotBlank)
  ?: GOOGLE_PLAY_BOOKS_HOME_URL.takeIf { isPurchased }

internal const val GOOGLE_PLAY_BOOKS_HOME_URL = "https://play.google.com/books"
