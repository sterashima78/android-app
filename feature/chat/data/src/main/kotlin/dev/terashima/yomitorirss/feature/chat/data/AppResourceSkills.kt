package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.chat.AgentSkill
import dev.terashima.yomitorirss.feature.chat.AgentTool
import dev.terashima.yomitorirss.feature.chat.AgentToolArgument
import dev.terashima.yomitorirss.feature.chat.AgentToolDefinition
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.task.TaskRepository
import java.time.LocalDate

fun createAppResourceSkills(
  articleRepository: ArticleRepository,
  bookmarkRepository: BookmarkRepository,
  feedRepository: FeedRepository,
  redditRepository: RedditRepository,
  summaryRepository: SummaryRepository,
  taskRepository: TaskRepository,
): List<AgentSkill> = listOf(
  rssSkill(articleRepository, feedRepository),
  redditSkill(articleRepository, redditRepository),
  bookmarkSkill(bookmarkRepository, summaryRepository),
  historySkill(articleRepository),
  taskSkill(taskRepository),
)

private fun rssSkill(
  articleRepository: ArticleRepository,
  feedRepository: FeedRepository,
): AgentSkill = AgentSkill(
  name = "rss-reader",
  description = "購読中のRSSフィードとRSSの未読記事を参照するSkill。Redditは含まない。",
  instructions = "RSS、購読フィード、RSSの未読記事について質問されたときに利用する。Redditはreddit-readerを利用する。データの変更は行わない。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_feeds",
        description = "購読中のRSSフィードを一覧・検索する。Reddit購読は含まない。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val feeds = rankByQuery(
        items = feedRepository.listFeeds().filter { feed -> RedditSourceBoundary.isNonRedditFeed(feed.feedUrl) },
        query = arguments.query(),
      ) { feed ->
        listOf(
          RetrievalField(feed.title, weight = 4),
          RetrievalField(feed.siteUrl.orEmpty(), weight = 2),
          RetrievalField(feed.feedUrl),
        )
      }
      formatCollection(feeds, DEFAULT_TOOL_ITEMS) { feed ->
        "- id=${feed.id} | title=${feed.title} | feed_url=${feed.feedUrl} | site_url=${feed.siteUrl.orEmpty()} | last_fetched_at=${feed.lastFetchedAt.orEmpty()} | last_error=${feed.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_unread_articles",
        description = "RSSの未読記事をタイトル・配信元で語彙検索する。Redditは含まない。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filter(RedditSourceBoundary::isNonRedditArticle),
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
  ),
)

private fun redditSkill(
  articleRepository: ArticleRepository,
  redditRepository: RedditRepository,
): AgentSkill = AgentSkill(
  name = "reddit-reader",
  description = "購読中のRedditコミュニティ・スレッドとRedditの未読を参照するSkill。",
  instructions = "Reddit、subreddit、コミュニティ、購読中スレッド、Redditの未読について質問されたときに利用する。読み取り専用で利用する。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_reddit_subscriptions",
        description = "購読中のRedditコミュニティとスレッドを一覧・語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val subscriptions = rankByQuery(
        items = redditRepository.listSubscriptions(),
        query = arguments.query(),
      ) { subscription ->
        listOf(
          RetrievalField(subscription.title, weight = 4),
          RetrievalField(subscription.kind.name, weight = 2),
          RetrievalField(subscription.feedUrl),
        )
      }
      formatCollection(subscriptions, DEFAULT_TOOL_ITEMS) { subscription ->
        "- id=${subscription.id} | title=${subscription.title} | kind=${subscription.kind.name.lowercase()} | feed_url=${subscription.feedUrl} | last_fetched_at=${subscription.lastFetchedAt.orEmpty()} | last_error=${subscription.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_reddit_unread",
        description = "Redditの未読投稿・新着コメントをタイトル・配信元で語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filter(RedditSourceBoundary::isRedditArticle),
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
  ),
)

private fun bookmarkSkill(
  bookmarkRepository: BookmarkRepository,
  summaryRepository: SummaryRepository,
): AgentSkill = AgentSkill(
  name = "bookmark-reader",
  description = "保存済み・あとで読む記事、ブックマークフォルダ、タグ、AI要約を参照するSkill。",
  instructions = "ブックマーク、保存記事、あとで読む、フォルダ、タグ、保存記事の内容について質問されたときに利用する。候補検索ではタイトル・タグ・保存済みAI要約等を使い、回答根拠として本文相当の要約が必要な候補だけget_saved_article_detailで詳細取得する。読み取り専用で利用する。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_recent_saved_articles",
        description = "最近ブックマークした保存記事を保存日時の新しい順に最大10件の候補として返す。「最近」「最新」「直近」のブックマークを求められた場合に使う。引数は不要。",
      ),
    ) {
      formatBookmarkedArticleCandidates(
        recentBookmarks(bookmarkRepository.listSavedArticles(tagId = null, folderId = null)),
        summaryRepository,
        query = "",
        limit = DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_saved_articles",
        description = "保存済みブックマークをタイトル・配信元・フォルダ・タグ・AI要約から語彙検索し、関連度順に最大10件の候補を返す。単に最近・最新の記事を求める場合はlist_recent_saved_articlesを使う。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticleCandidates(
        recentBookmarks(bookmarkRepository.listSavedArticles(tagId = null, folderId = null)),
        summaryRepository,
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_read_later_articles",
        description = "あとで読むフォルダの記事をタイトル・配信元・タグ・AI要約から語彙検索し、関連度順に最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticleCandidates(
        bookmarkRepository.listReadLaterArticles(),
        summaryRepository,
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "get_saved_article_detail",
        description = "候補検索で得た保存記事1件について、回答根拠に必要な場合だけメタデータと保存済みAI要約全文を取得する。候補に含まれるarticle idを指定する。",
        arguments = listOf(
          AgentToolArgument(
            name = "article_id",
            description = "候補検索結果に含まれる保存記事のid。",
            required = true,
          ),
        ),
      ),
    ) { arguments ->
      val articleId = arguments["article_id"]?.trim().orEmpty()
      val bookmarked = findSavedArticle(bookmarkRepository, articleId)
      formatBookmarkedArticleDetail(bookmarked, summaryRepository)
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_folders",
        description = "ブックマークフォルダを一覧・語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val folders = rankByQuery(bookmarkRepository.listFolders(), arguments.query()) { folder ->
        listOf(RetrievalField(folder.name, weight = 4))
      }
      formatCollection(folders, DEFAULT_TOOL_ITEMS) { folder ->
        "- id=${folder.id} | name=${folder.name} | system=${folder.isSystem}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_tags",
        description = "ブックマークタグを一覧・語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val tags = rankByQuery(bookmarkRepository.listTags(), arguments.query()) { tag ->
        listOf(RetrievalField(tag.name, weight = 4))
      }
      formatCollection(tags, DEFAULT_TOOL_ITEMS) { tag -> "- id=${tag.id} | name=${tag.name}" }
    },
  ),
)

private fun historySkill(articleRepository: ArticleRepository): AgentSkill = AgentSkill(
  name = "reading-history",
  description = "既読履歴から過去に読んだ記事を参照するSkill。",
  instructions = "以前読んだ記事や閲覧履歴について質問されたときに利用する。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_read_history",
        description = "既読履歴をタイトル・配信元で語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(articleRepository.listHistoryArticles(), arguments.query(), DEFAULT_TOOL_ITEMS)
    },
  ),
)

private fun taskSkill(taskRepository: TaskRepository): AgentSkill = AgentSkill(
  name = "task-reader",
  description = "アプリ内のタスク、説明、期限、完了状態を参照するSkill。",
  instructions = "TODO、タスク、期限、未完了・完了・期日超過について質問されたときに利用する。タスクは変更しない。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_tasks",
        description = "タスクをタイトル・説明と状態で語彙検索する。最大10件の候補を返す。",
        arguments = commonSearchArguments() + AgentToolArgument(
          name = "status",
          description = "all, open, completed, overdue のいずれか。省略時は all。",
        ),
      ),
    ) { arguments ->
      val today = LocalDate.now()
      val status = arguments["status"]?.trim()?.lowercase().orEmpty().ifBlank { "all" }
      val tasks = rankByQuery(
        items = taskRepository.listTasks().filter { task -> task.matchesStatus(status, today) },
        query = arguments.query(),
      ) { task ->
        listOf(
          RetrievalField(task.title, weight = 4),
          RetrievalField(task.description, weight = 2),
        )
      }
      formatCollection(tasks, DEFAULT_TOOL_ITEMS) { task -> formatTask(task, today) }
    },
  ),
)

private class LambdaAgentTool(
  override val definition: AgentToolDefinition,
  private val block: suspend (Map<String, String>) -> String,
) : AgentTool {
  override suspend fun execute(arguments: Map<String, String>): String = block(arguments)
}

internal fun commonSearchArguments(): List<AgentToolArgument> = listOf(
  AgentToolArgument(
    name = "query",
    description = "語彙検索に使う具体的な検索語。空白区切りの複数語は異なる項目に一致してよい。自然文をそのまま入れず、対象を表す短い語を使う。全件を見る場合は省略する。「最近」「最新」「直近」「新しい順」は検索語ではないのでqueryへ入れない。",
  ),
)

private fun Map<String, String>.query(): String = get("query")?.trim().orEmpty()

internal fun recentBookmarks(bookmarks: List<BookmarkedArticle>): List<BookmarkedArticle> =
  bookmarks.sortedByDescending(BookmarkedArticle::savedAt)

private fun formatArticles(
  articles: List<Article>,
  query: String,
  limit: Int,
): String {
  val ranked = rankByQuery(articles, query) { article ->
    listOf(
      RetrievalField(article.title, weight = 4),
      RetrievalField(article.sourceTitle, weight = 2),
    )
  }
  return formatCollection(ranked, limit) { article ->
    "- id=${article.id} | title=${article.title} | source=${article.sourceTitle} | published_at=${article.publishedAt} | read_at=${article.readAt.orEmpty()} | url=${article.url}"
  }
}

private suspend fun formatBookmarkedArticleCandidates(
  bookmarkedArticles: List<BookmarkedArticle>,
  summaryRepository: SummaryRepository,
  query: String,
  limit: Int,
): String {
  val enriched = bookmarkedArticles.map { bookmarked ->
    bookmarked to summaryRepository.findSummary(bookmarked.article.id)
  }
  val ranked = rankByQuery(enriched, query) { (bookmarked, summary) ->
    val article = bookmarked.article
    listOf(
      RetrievalField(article.title, weight = 5),
      RetrievalField(bookmarked.tags.joinToString(" ") { tag -> tag.name }, weight = 4),
      RetrievalField(summary.orEmpty(), weight = 3),
      RetrievalField(bookmarked.folder?.name.orEmpty(), weight = 2),
      RetrievalField(article.sourceTitle, weight = 2),
    )
  }
  return formatCollection(ranked, limit) { (bookmarked, summary) ->
    val article = bookmarked.article
    val summaryExcerpt = compactExcerpt(summary.orEmpty(), SUMMARY_EXCERPT_CHARS)
    "- id=${article.id} | title=${article.title} | source=${article.sourceTitle} | saved_at=${bookmarked.savedAt} | published_at=${article.publishedAt} | folder=${bookmarked.folder?.name.orEmpty()} | tags=${bookmarked.tags.joinToString(",") { tag -> tag.name }} | summary_excerpt=$summaryExcerpt"
  }
}

private suspend fun findSavedArticle(
  bookmarkRepository: BookmarkRepository,
  articleId: String,
): BookmarkedArticle? {
  if (articleId.isBlank()) return null
  return (
    bookmarkRepository.listSavedArticles(tagId = null, folderId = null) +
      bookmarkRepository.listReadLaterArticles()
    ).distinctBy { it.article.id }
    .firstOrNull { bookmarked -> bookmarked.article.id == articleId }
}

private suspend fun formatBookmarkedArticleDetail(
  bookmarked: BookmarkedArticle?,
  summaryRepository: SummaryRepository,
): String {
  if (bookmarked == null) return "該当する保存記事はありません。"
  val article = bookmarked.article
  val summary = summaryRepository.findSummary(article.id).orEmpty().replace(Regex("\\s+"), " ").trim()
  return "id=${article.id} | title=${article.title} | source=${article.sourceTitle} | saved_at=${bookmarked.savedAt} | published_at=${article.publishedAt} | read_at=${article.readAt.orEmpty()} | folder=${bookmarked.folder?.name.orEmpty()} | tags=${bookmarked.tags.joinToString(",") { tag -> tag.name }} | summary=$summary | url=${article.url}"
}

private fun <T> formatCollection(
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

private fun TaskItem.matchesStatus(status: String, today: LocalDate): Boolean = when (status) {
  "all" -> true
  "open" -> !completed
  "completed" -> completed
  "overdue" -> !completed && dueDate?.isBefore(today) == true
  else -> true
}

private fun formatTask(task: TaskItem, today: LocalDate): String {
  val status = when {
    task.completed -> "completed"
    task.dueDate?.isBefore(today) == true -> "overdue"
    else -> "open"
  }
  return "- id=${task.id} | title=${task.title} | description=${task.description} | status=$status | due_date=${task.dueDate?.toString().orEmpty()} | parent_id=${task.parentId.orEmpty()}"
}

private const val DEFAULT_TOOL_ITEMS = 10
private const val SUMMARY_EXCERPT_CHARS = 240
