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
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.task.TaskRepository
import java.time.LocalDate

fun createAppResourceSkills(
  articleRepository: ArticleRepository,
  bookmarkRepository: BookmarkRepository,
  feedRepository: FeedRepository,
  redditRepository: RedditRepository,
  taskRepository: TaskRepository,
): List<AgentSkill> = listOf(
  rssSkill(articleRepository, feedRepository),
  redditSkill(articleRepository, redditRepository),
  bookmarkSkill(bookmarkRepository),
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
        description = "購読中のRSSフィードを一覧・検索する。Reddit購読は含まない。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val feeds = feedRepository.listFeeds()
        .filterNot { feed -> isRedditFeedUrl(feed.feedUrl) }
        .filter { feed -> query.isBlank() || listOf(feed.title, feed.feedUrl, feed.siteUrl.orEmpty()).any { it.contains(query, true) } }
      formatCollection(feeds, arguments.limit()) { feed ->
        "- id=${feed.id} | title=${feed.title} | feed_url=${feed.feedUrl} | site_url=${feed.siteUrl.orEmpty()} | last_fetched_at=${feed.lastFetchedAt.orEmpty()} | last_error=${feed.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_unread_articles",
        description = "RSSの未読記事をタイトル・配信元で検索する。Redditは含まない。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filterNot(Article::isRedditArticle),
        arguments.query(),
        arguments.limit(),
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
        description = "購読中のRedditコミュニティとスレッドを一覧・検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val subscriptions = redditRepository.listSubscriptions()
        .filter { subscription ->
          query.isBlank() || listOf(subscription.title, subscription.feedUrl, subscription.kind.name)
            .any { it.contains(query, true) }
        }
      formatCollection(subscriptions, arguments.limit()) { subscription ->
        "- id=${subscription.id} | title=${subscription.title} | kind=${subscription.kind.name.lowercase()} | feed_url=${subscription.feedUrl} | last_fetched_at=${subscription.lastFetchedAt.orEmpty()} | last_error=${subscription.lastError.orEmpty()}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_reddit_unread",
        description = "Redditの未読投稿・新着コメントをタイトル・配信元で検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(
        articleRepository.listUnreadArticles().filter(Article::isRedditArticle),
        arguments.query(),
        arguments.limit(),
      )
    },
  ),
)

private fun bookmarkSkill(
  bookmarkRepository: BookmarkRepository,
): AgentSkill = AgentSkill(
  name = "bookmark-reader",
  description = "保存済み・あとで読む記事、ブックマークフォルダ、タグを参照するSkill。",
  instructions = "ブックマーク、保存記事、あとで読む、フォルダ、タグについて質問されたときに利用する。読み取り専用で利用する。",
  tools = listOf(
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_saved_articles",
        description = "保存済みブックマークをタイトル・配信元・フォルダ・タグで検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticles(
        bookmarkRepository.listSavedArticles(tagId = null, folderId = null),
        arguments.query(),
        arguments.limit(),
      )
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "search_read_later_articles",
        description = "あとで読むフォルダの記事を検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatBookmarkedArticles(bookmarkRepository.listReadLaterArticles(), arguments.query(), arguments.limit())
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_folders",
        description = "ブックマークフォルダを一覧・検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val folders = bookmarkRepository.listFolders()
        .filter { query.isBlank() || it.name.contains(query, true) }
      formatCollection(folders, arguments.limit()) { folder ->
        "- id=${folder.id} | name=${folder.name} | system=${folder.isSystem}"
      }
    },
    LambdaAgentTool(
      definition = AgentToolDefinition(
        name = "list_bookmark_tags",
        description = "ブックマークタグを一覧・検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      val query = arguments.query()
      val tags = bookmarkRepository.listTags()
        .filter { query.isBlank() || it.name.contains(query, true) }
      formatCollection(tags, arguments.limit()) { tag -> "- id=${tag.id} | name=${tag.name}" }
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
        description = "既読履歴をタイトル・配信元で検索する。",
        arguments = commonSearchArguments(),
      ),
    ) { arguments ->
      formatArticles(articleRepository.listHistoryArticles(), arguments.query(), arguments.limit())
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
        description = "タスクをタイトル・説明と状態で検索する。",
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
      formatCollection(tasks, arguments.limit()) { task -> formatTask(task, today) }
    },
  ),
)

private class LambdaAgentTool(
  override val definition: AgentToolDefinition,
  private val block: suspend (Map<String, String>) -> String,
) : AgentTool {
  override suspend fun execute(arguments: Map<String, String>): String = block(arguments)
}

private fun commonSearchArguments(): List<AgentToolArgument> = listOf(
  AgentToolArgument(
    name = "query",
    description = "部分一致で絞り込む検索語。全件を見る場合は空文字または省略。",
  ),
  AgentToolArgument(
    name = "limit",
    description = "詳細を返す最大件数。1〜20。省略時は10。total はこの上限に関係なく検索結果全体の件数を返す。",
  ),
)

private fun Map<String, String>.query(): String = get("query")?.trim().orEmpty()

private fun Map<String, String>.limit(): Int =
  get("limit")?.toIntOrNull()?.coerceIn(1, MAX_TOOL_ITEMS) ?: DEFAULT_TOOL_ITEMS

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

private fun formatBookmarkedArticles(
  bookmarkedArticles: List<BookmarkedArticle>,
  query: String,
  limit: Int,
): String {
  val filtered = bookmarkedArticles.filter { bookmarked ->
    val article = bookmarked.article
    query.isBlank() || listOf(
      article.title,
      article.sourceTitle,
      bookmarked.folder?.name.orEmpty(),
      bookmarked.tags.joinToString(" ") { tag -> tag.name },
    ).any { value -> value.contains(query, true) }
  }
  return formatCollection(filtered, limit) { bookmarked ->
    val article = bookmarked.article
    "- id=${article.id} | title=${article.title} | source=${article.sourceTitle} | published_at=${article.publishedAt} | read_at=${article.readAt.orEmpty()} | folder=${bookmarked.folder?.name.orEmpty()} | tags=${bookmarked.tags.joinToString(",") { tag -> tag.name }} | url=${article.url}"
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
private const val MAX_TOOL_ITEMS = 20
