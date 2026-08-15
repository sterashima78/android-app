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
import dev.terashima.yomitorirss.feature.reddit.isRedditArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
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
        description = "購読中のRSSフィードを一覧・検索する。Reddit購読は含まない。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val feeds = feedRepository.listFeeds()
        .filterNot { feed -> isRedditFeedUrl(feed.feedUrl) }
        .filter { feed -> query.isBlank() || listOf(feed.title, feed.feedUrl, feed.siteUrl.orEmpty()).any { it.contains(query, true) } }
      formatCollection(feeds, DEFAULT_TOOL_ITEMS) { feed ->
        "- id=${feed.id} | title=${feed.title} | feed_url=${feed.feedUrl} | site_url=${feed.siteUrl.orEmpty()} | last_fetched_at=${feed.lastFetchedAt.orEmpty()} | last_error=${feed.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_unread_articles",
        description = "RSSの未読記事をタイトル・配信元で検索する。Redditは含まない。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filterNot(Article::isRedditArticle),
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
        description = "購読中のRedditコミュニティとスレッドを一覧・検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val subscriptions = redditRepository.listSubscriptions()
        .filter { subscription ->
          query.isBlank() || listOf(subscription.title, subscription.feedUrl, subscription.kind.name)
            .any { it.contains(query, true) }
        }
      formatCollection(subscriptions, DEFAULT_TOOL_ITEMS) { subscription ->
        "- id=${subscription.id} | title=${subscription.title} | kind=${subscription.kind.name.lowercase()} | feed_url=${subscription.feedUrl} | last_fetched_at=${subscription.lastFetchedAt.orEmpty()} | last_error=${subscription.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_reddit_unread",
        description = "Redditの未読投稿・新着コメントをタイトル・配信元で検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filter(Article::isRedditArticle),
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
  instructions = "ブックマーク、保存記事、あとで読む、フォルダ、タグ、保存記事の内容について質問されたときに利用する。検索と回答にはタグと保存済みAI要約を優先して利用する。読み取り専用で利用する。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_recent_saved_articles",
        description = "最近ブックマークした保存記事を保存日時の新しい順に最大10件返す。「最近」「最新」「直近」のブックマークを求められた場合に使う。引数は不要。",
      ),
    ) {
      formatBookmarkedArticles(
        recentBookmarks(bookmarkRepository.listSavedArticles(tagId = null, folderId = null)),
        summaryRepository,
        query = "",
        limit = DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_saved_articles",
        description = "保存済みブックマークをキーワードでタイトル・配信元・フォルダ・タグ・AI要約から検索し、保存日時の新しい順に最大10件返す。単に最近・最新の記事を求める場合はlist_recent_saved_articlesを使う。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticles(
        recentBookmarks(bookmarkRepository.listSavedArticles(tagId = null, folderId = null)),
        summaryRepository,
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_read_later_articles",
        description = "あとで読むフォルダの記事をタイトル・配信元・タグ・AI要約で検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticles(
        bookmarkRepository.listReadLaterArticles(),
        summaryRepository,
        arguments.query(),
        DEFAULT_TOOL_ITEMS,
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_folders",
        description = "ブックマークフォルダを一覧・検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val folders = bookmarkRepository.listFolders()
        .filter { query.isBlank() || it.name.contains(query, true) }
      formatCollection(folders, DEFAULT_TOOL_ITEMS) { folder ->
        "- id=${folder.id} | name=${folder.name} | system=${folder.isSystem}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_tags",
        description = "ブックマークタグを一覧・検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val tags = bookmarkRepository.listTags()
        .filter { query.isBlank() || it.name.contains(query, true) }
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
        description = "既読履歴をタイトル・配信元で検索する。最大10件の詳細を返す。",
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
        description = "タスクをタイトル・説明と状態で検索する。最大10件の詳細を返す。",
        arguments = commonSearchArguments() + AgentToolArgument(
          name = "status",
          description = "all, open, completed, overdue のいずれか。省略時は all。",
        ),
      ),
    ) { arguments ->
      val query = arguments.query()
      val today = LocalDate.now()
      val status = arguments["status"]?.trim()?.lowercase().orEmpty().ifBlank { "all" }
      val tasks = taskRepository.listTasks()
        .filter { task -> query.isBlank() || listOf(task.title, task.description).any { it.contains(query, true) } }
        .filter { task -> task.matchesStatus(status, today) }
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
    description = "部分一致で絞り込む実際の検索語。全件を見る場合は省略する。「最近」「最新」「直近」「新しい順」は検索語ではないのでqueryへ入れない。",
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
  val filtered = articles.filter { article ->
    query.isBlank() || listOf(article.title, article.sourceTitle).any { it.contains(query, true) }
  }
  return formatCollection(filtered, limit) { article ->
    "- id=${article.id} | title=${article.title} | source=${article.sourceTitle} | published_at=${article.publishedAt} | read_at=${article.readAt.orEmpty()} | url=${article.url}"
  }
}

private suspend fun formatBookmarkedArticles(
  bookmarkedArticles: List<BookmarkedArticle>,
  summaryRepository: SummaryRepository,
  query: String,
  limit: Int,
): String {
  val enriched = bookmarkedArticles.map { bookmarked ->
    bookmarked to summaryRepository.findSummary(bookmarked.article.id)
  }
  val filtered = enriched.filter { (bookmarked, summary) ->
    val article = bookmarked.article
    query.isBlank() || listOf(
      article.title,
      article.sourceTitle,
      bookmarked.folder?.name.orEmpty(),
      bookmarked.tags.joinToString(" ") { tag -> tag.name },
      summary.orEmpty(),
    ).any { value -> value.contains(query, true) }
  }
  return formatCollection(filtered, limit) { (bookmarked, summary) ->
    val article = bookmarked.article
    val compactSummary = summary.orEmpty().replace(Regex("\\s+"), " ").trim()
    "- id=${article.id} | title=${article.title} | source=${article.sourceTitle} | saved_at=${bookmarked.savedAt} | published_at=${article.publishedAt} | read_at=${article.readAt.orEmpty()} | folder=${bookmarked.folder?.name.orEmpty()} | tags=${bookmarked.tags.joinToString(",") { tag -> tag.name }} | summary=$compactSummary | url=${article.url}"
  }
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
