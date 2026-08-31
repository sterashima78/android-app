package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.chat.AgentSkill
import dev.terashima.yomitorirss.feature.chat.AgentTool
import dev.terashima.yomitorirss.feature.chat.AgentToolArgument
import dev.terashima.yomitorirss.feature.chat.AgentToolDefinition
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeReader
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryReader

fun createKnowledgeLibraryResourceSkills(
  knowledgeReader: KnowledgeReader,
  libraryReader: LibraryReader,
): List<AgentSkill> = listOf(
  knowledgeSkill(knowledgeReader),
  librarySkill(libraryReader),
)

private fun knowledgeSkill(knowledgeReader: KnowledgeReader): AgentSkill = AgentSkill(
  name = "knowledge-reader",
  description = "Knowledge Wiki のページを検索し、蓄積済みの知識を参照するSkill。",
  instructions = "Knowledge、Wiki、蓄積済み知識、過去に整理した内容について質問されたときに利用する。まずsearch_knowledge_pagesで候補を検索し、回答根拠として必要なページだけget_knowledge_page_detailで本文を取得する。読み取り専用で利用する。",
  tools = listOf(
    ReadOnlyAgentTool(
      definition = AgentToolDefinition(
        name = "search_knowledge_pages",
        description = "Knowledgeページをタイトルと本文から検索し、最大10件の候補を返す。検索語を省略した場合はKnowledge側の既定順で一覧する。",
        arguments = listOf(
          AgentToolArgument(
            name = "query",
            description = "タイトルまたは本文に含まれる具体的な短い検索語。自然文をそのまま入れず、対象を識別しやすい語を使う。全件を見る場合は省略する。",
          ),
        ),
      ),
    ) { arguments ->
      val pages = knowledgeReader.listPages(arguments.query())
      formatResourceCollection(pages, DEFAULT_RESOURCE_ITEMS) { page ->
        "- id=${page.id} | title=${page.title} | source_count=${page.sourceCount} | generated_at=${page.generatedAt} | editor_managed=${page.editorManaged}"
      }
    },
    ReadOnlyAgentTool(
      definition = AgentToolDefinition(
        name = "get_knowledge_page_detail",
        description = "候補検索で得たKnowledgeページ1件の本文と出典を取得する。回答根拠として必要な候補だけに使う。",
        arguments = listOf(
          AgentToolArgument(
            name = "page_id",
            description = "search_knowledge_pagesの候補に含まれるKnowledge page id。",
            required = true,
          ),
        ),
      ),
    ) { arguments ->
      formatKnowledgePage(knowledgeReader.findPage(arguments["page_id"]?.trim().orEmpty()))
    },
  ),
)

private fun librarySkill(libraryReader: LibraryReader): AgentSkill = AgentSkill(
  name = "library-reader",
  description = "Kindle、Audible、Google Play Books、SMB、Webを含む蔵書を検索・参照するSkill。",
  instructions = "蔵書、所有している本、著者、シリーズ、出版社、説明、ISBN、ナレーターについて質問されたときに利用する。まずsearch_library_booksで候補を検索し、回答根拠として詳細が必要な本だけget_library_book_detailで取得する。非表示の本は通常の検索対象に含めない。読み取り専用で利用する。",
  tools = listOf(
    ReadOnlyAgentTool(
      definition = AgentToolDefinition(
        name = "search_library_books",
        description = "表示中の蔵書をタイトル・著者・シリーズ・説明・出版社・ナレーター等から語彙検索し、関連度順に最大10件の候補を返す。query省略時はLibrary側の既定順を維持する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val books = rankLibraryBooks(libraryReader.snapshot().books, arguments.query())
      formatResourceCollection(books, DEFAULT_RESOURCE_ITEMS) { book -> formatLibraryBookCandidate(book) }
    },
    ReadOnlyAgentTool(
      definition = AgentToolDefinition(
        name = "get_library_book_detail",
        description = "候補検索で得た蔵書1件の書誌詳細を取得する。回答根拠として必要な候補だけに使う。",
        arguments = listOf(
          AgentToolArgument(
            name = "source",
            description = "候補に含まれるsource。kindle, audible, google_play_books, smb, web のいずれか。",
            required = true,
          ),
          AgentToolArgument(
            name = "source_id",
            description = "候補に含まれるsource_id。",
            required = true,
          ),
        ),
      ),
    ) { arguments ->
      val source = arguments["source"]?.trim().orEmpty()
      val sourceId = arguments["source_id"]?.trim().orEmpty()
      val book = libraryReader.snapshot().books.firstOrNull { candidate ->
        candidate.source.name.equals(source, ignoreCase = true) && candidate.sourceId == sourceId
      }
      formatLibraryBookDetail(book)
    },
  ),
)

private fun rankLibraryBooks(books: List<LibraryBook>, query: String): List<LibraryBook> =
  rankByQuery(books, query) { book ->
    listOf(
      RetrievalField(book.title, weight = 5),
      RetrievalField(book.authors.joinToString(" "), weight = 4),
      RetrievalField(book.series?.name.orEmpty(), weight = 4),
      RetrievalField(book.description.orEmpty(), weight = 3),
      RetrievalField(book.publisher.orEmpty(), weight = 2),
      RetrievalField(book.narrators.joinToString(" "), weight = 2),
      RetrievalField(book.source.label, weight = 2),
      RetrievalField(book.isbn10.orEmpty()),
      RetrievalField(book.isbn13.orEmpty()),
    )
  }

private fun formatLibraryBookCandidate(book: LibraryBook): String {
  val descriptionExcerpt = compactExcerpt(book.description.orEmpty(), LIBRARY_DESCRIPTION_EXCERPT_CHARS)
  return "- source=${book.source.name.lowercase()} | source_id=${book.sourceId} | title=${book.title} | authors=${book.authors.joinToString(",")} | series=${book.series?.name.orEmpty()} | publisher=${book.publisher.orEmpty()} | description_excerpt=$descriptionExcerpt"
}

private fun formatLibraryBookDetail(book: LibraryBook?): String {
  if (book == null) return "該当する蔵書はありません。"
  val description = book.description.orEmpty().replace(Regex("\\s+"), " ").trim()
  return "source=${book.source.name.lowercase()} | source_id=${book.sourceId} | title=${book.title} | authors=${book.authors.joinToString(",")} | publisher=${book.publisher.orEmpty()} | published_date=${book.publishedDate.orEmpty()} | series=${book.series?.name.orEmpty()} | series_position=${book.series?.position?.toString().orEmpty()} | narrators=${book.narrators.joinToString(",")} | duration=${book.duration.orEmpty()} | isbn10=${book.isbn10.orEmpty()} | isbn13=${book.isbn13.orEmpty()} | description=$description | info_url=${book.infoUrl.orEmpty()}"
}

private fun formatKnowledgePage(page: KnowledgePage?): String = buildString {
  if (page == null) {
    append("該当するKnowledgeページはありません。")
    return@buildString
  }
  append("id=${page.id} | title=${page.title} | source_count=${page.sourceCount} | generated_at=${page.generatedAt} | editor_managed=${page.editorManaged}")
  append("\nbody_markdown:\n")
  append(page.bodyMarkdown.trim())
  if (page.sources.isNotEmpty()) {
    append("\nsources:")
    page.sources.forEach { source ->
      append("\n- citation=${source.citationNumber} | article_id=${source.articleId} | title=${source.title} | source=${source.sourceTitle} | saved_at=${source.savedAt} | url=${source.url}")
    }
  }
}

private class ReadOnlyAgentTool(
  override val definition: AgentToolDefinition,
  private val block: suspend (Map<String, String>) -> String,
) : AgentTool {
  override suspend fun execute(arguments: Map<String, String>): String = block(arguments)
}

private fun Map<String, String>.query(): String = get("query")?.trim().orEmpty()

private fun <T> formatResourceCollection(
  items: List<T>,
  limit: Int,
  formatItem: (T) -> String,
): String = buildString {
  append("total=${items.size}")
  if (items.isEmpty()) {
    append("\n該当するデータはありません。")
  } else {
    items.take(limit).forEach { item ->
      append('\n')
      append(formatItem(item))
    }
  }
}

private const val DEFAULT_RESOURCE_ITEMS = 10
private const val LIBRARY_DESCRIPTION_EXCERPT_CHARS = 240
