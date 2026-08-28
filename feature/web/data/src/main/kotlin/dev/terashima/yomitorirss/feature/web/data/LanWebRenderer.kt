package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.web.LanWebArticleItem
import dev.terashima.yomitorirss.feature.web.LanWebFeedItem

internal object LanWebRenderer {
  fun renderHome(page: LanWebHomePage): String {
    val body = when (val content = page.content) {
      is LanWebContent.Articles -> renderArticles(content.articles, content.emptyText)
      is LanWebContent.Feeds -> renderFeeds(content.feeds)
    }

    return """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<title>${escapeHtml(page.title)} - Mosaic</title>
<style>
:root{font-family:system-ui,-apple-system,sans-serif;color-scheme:dark;background:#111318;color:#e3e2e9}body{margin:0}header{position:sticky;top:0;background:#1b1b21;border-bottom:1px solid #45464f;padding:16px;z-index:1}h1{font-size:1.25rem;margin:0 0 12px}.nav{display:flex;gap:8px;overflow-x:auto}.nav a{white-space:nowrap;color:#c6c5d0;text-decoration:none;padding:8px 12px;border-radius:999px;background:#292930}.nav a.current{background:#d0bcff;color:#381e72}main{max-width:900px;margin:0 auto;padding:16px}.notice{font-size:.85rem;color:#c6c5d0;margin:0 0 16px}.card{display:block;color:inherit;text-decoration:none;background:#1b1b21;border:1px solid #45464f;border-radius:14px;padding:16px;margin-bottom:12px}.card:hover{border-color:#d0bcff}.title{font-size:1rem;font-weight:650;line-height:1.5;margin-bottom:8px}.meta{font-size:.8rem;color:#c6c5d0;line-height:1.5}.tags{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}.tag{background:#34313d;border-radius:999px;padding:3px 8px;font-size:.75rem}.empty{padding:40px 16px;text-align:center;color:#c6c5d0}.feed-url{overflow-wrap:anywhere}footer{text-align:center;color:#8f9099;font-size:.75rem;padding:20px}</style>
</head>
<body>
<header><h1>Mosaic</h1>${renderNavigation(page.view)}</header>
<main><p class="notice">同じネットワーク内の端末から、読み取り専用で表示しています。更新や編集はAndroidアプリで行ってください。</p><h2>${escapeHtml(page.title)}</h2>$body</main>
<footer>Mosaic LAN Web Server</footer>
</body>
</html>"""
  }

  fun renderError(title: String, message: String): String = """<!doctype html>
<html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>${escapeHtml(title)}</title></head>
<body style="font-family:system-ui;background:#111318;color:#e3e2e9;padding:32px"><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p></body></html>"""

  private fun renderNavigation(current: String): String = buildString {
    append("<nav class=\"nav\">")
    listOf(
      LanWebViews.UNREAD to "RSS未読",
      LanWebViews.REDDIT to "Reddit",
      LanWebViews.SAVED to "ブックマーク",
      LanWebViews.READ_LATER to "あとで読む",
      LanWebViews.FEEDS to "RSSフィード",
    ).forEach { (view, label) ->
      val css = if (view == current) " class=\"current\"" else ""
      append("<a$css href=\"/?view=${escapeHtml(view)}\">${escapeHtml(label)}</a>")
    }
    append("</nav>")
  }

  private fun renderArticles(articles: List<LanWebArticleItem>, emptyText: String): String {
    if (articles.isEmpty()) return "<div class=\"empty\">${escapeHtml(emptyText)}</div>"
    return articles.joinToString(separator = "") { article -> renderArticle(article) }
  }

  private fun renderArticle(article: LanWebArticleItem): String {
    val tags = if (article.tagNames.isEmpty()) "" else article.tagNames.joinToString(
      prefix = "<div class=\"tags\">",
      postfix = "</div>",
      separator = "",
    ) { tagName -> "<span class=\"tag\">${escapeHtml(tagName)}</span>" }
    return """<a class="card" href="${escapeAttribute(article.url)}" target="_blank" rel="noopener noreferrer">
<div class="title">${escapeHtml(article.title)}</div>
<div class="meta">${escapeHtml(article.sourceTitle)}<br>${escapeHtml(article.publishedAt)}</div>$tags
</a>"""
  }

  private fun renderFeeds(feeds: List<LanWebFeedItem>): String {
    if (feeds.isEmpty()) return "<div class=\"empty\">登録済みRSSフィードはありません。</div>"
    return feeds.joinToString(separator = "") { feed ->
      val href = feed.siteUrl ?: feed.feedUrl
      """<a class="card" href="${escapeAttribute(href)}" target="_blank" rel="noopener noreferrer">
<div class="title">${escapeHtml(feed.title)}</div>
<div class="meta feed-url">${escapeHtml(feed.feedUrl)}</div>
</a>"""
    }
  }
}

internal fun escapeHtml(value: String): String = buildString(value.length) {
  value.forEach { character ->
    append(
      when (character) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        '\'' -> "&#39;"
        else -> character
      },
    )
  }
}

private fun escapeAttribute(value: String): String = escapeHtml(value)
