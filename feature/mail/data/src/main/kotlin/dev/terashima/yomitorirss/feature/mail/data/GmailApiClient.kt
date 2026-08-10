package dev.terashima.yomitorirss.feature.mail.data

import android.text.Html
import android.util.Base64
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpMethod
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.mail.MailLabel
import dev.terashima.yomitorirss.feature.mail.MailMessage
import dev.terashima.yomitorirss.feature.mail.MailThread
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

internal data class GmailHistoryDelta(
  val threadIds: Set<String>,
  val historyId: String,
)

internal data class GmailThreadPage(
  val threadIds: List<String>,
  val nextPageToken: String?,
)

private data class GmailMessageBody(
  val plainText: String,
  val html: String?,
)

internal class GmailApiException(
  val statusCode: Int,
  message: String,
) : IllegalStateException(message)

internal class GmailHistoryExpiredException : IllegalStateException("Gmail の差分履歴が期限切れです")

internal class GmailApiClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun profileHistoryId(accessToken: String): String = request(
    accessToken = accessToken,
    path = "profile",
  ).getString("historyId")

  suspend fun listThreadIds(
    accessToken: String,
    query: String,
    maxResults: Int = 100,
  ): List<String> = listThreadPage(
    accessToken = accessToken,
    query = query,
    maxResults = maxResults,
    pageToken = null,
  ).threadIds

  suspend fun listThreadPage(
    accessToken: String,
    query: String = "",
    maxResults: Int = 100,
    pageToken: String? = null,
  ): GmailThreadPage {
    val response = request(
      accessToken = accessToken,
      path = threadsPath(query, maxResults.coerceIn(1, 500), pageToken),
    )
    return GmailThreadPage(
      threadIds = threadIds(response),
      nextPageToken = response.optString("nextPageToken").takeIf(String::isNotBlank),
    )
  }

  suspend fun listAllThreadIds(
    accessToken: String,
    query: String = "",
    pageSize: Int = 500,
  ): List<String> {
    val ids = linkedSetOf<String>()
    var pageToken: String? = null
    do {
      val page = listThreadPage(
        accessToken = accessToken,
        query = query,
        maxResults = pageSize,
        pageToken = pageToken,
      )
      ids += page.threadIds
      pageToken = page.nextPageToken
    } while (pageToken != null)
    return ids.toList()
  }

  suspend fun getThread(
    accessToken: String,
    accountId: String,
    threadId: String,
  ): MailThread {
    val json = request(
      accessToken = accessToken,
      path = "threads/${encodePath(threadId)}?format=full",
    )
    return parseThread(accessToken, accountId, json)
  }

  suspend fun listLabels(
    accessToken: String,
    accountId: String,
  ): List<MailLabel> {
    val labels = request(accessToken, "labels").optJSONArray("labels") ?: JSONArray()
    return buildList {
      for (index in 0 until labels.length()) {
        val label = labels.optJSONObject(index) ?: continue
        val id = label.optString("id")
        if (id.isBlank()) continue
        add(
          MailLabel(
            id = id,
            accountId = accountId,
            name = label.optString("name", id),
            type = label.optString("type", "user"),
          ),
        )
      }
    }.sortedWith(compareBy<MailLabel> { it.type != "system" }.thenBy { it.name.lowercase() })
  }

  suspend fun history(
    accessToken: String,
    startHistoryId: String,
  ): GmailHistoryDelta {
    val threadIds = linkedSetOf<String>()
    var pageToken: String? = null
    var latestHistoryId = startHistoryId
    do {
      val tokenPart = pageToken?.takeIf(String::isNotBlank)?.let { "&pageToken=${encode(it)}" }.orEmpty()
      val response = try {
        request(
          accessToken = accessToken,
          path = "history?startHistoryId=${encode(startHistoryId)}&maxResults=500$tokenPart",
        )
      } catch (error: GmailApiException) {
        if (error.statusCode == 404) throw GmailHistoryExpiredException()
        throw error
      }
      response.optString("historyId").takeIf(String::isNotBlank)?.let { latestHistoryId = it }
      val history = response.optJSONArray("history") ?: JSONArray()
      for (index in 0 until history.length()) {
        val item = history.optJSONObject(index) ?: continue
        collectThreadIds(item.optJSONArray("messagesAdded"), threadIds)
        collectThreadIds(item.optJSONArray("messagesDeleted"), threadIds)
        collectThreadIds(item.optJSONArray("labelsAdded"), threadIds)
        collectThreadIds(item.optJSONArray("labelsRemoved"), threadIds)
      }
      pageToken = response.optString("nextPageToken").takeIf(String::isNotBlank)
    } while (pageToken != null)
    return GmailHistoryDelta(threadIds = threadIds, historyId = latestHistoryId)
  }

  suspend fun modifyThread(
    accessToken: String,
    threadId: String,
    addLabelIds: Collection<String> = emptyList(),
    removeLabelIds: Collection<String> = emptyList(),
  ) {
    val body = JSONObject()
      .put("addLabelIds", JSONArray(addLabelIds.toList()))
      .put("removeLabelIds", JSONArray(removeLabelIds.toList()))
    request(
      accessToken = accessToken,
      path = "threads/${encodePath(threadId)}/modify",
      method = HttpMethod.POST,
      body = body,
    )
  }

  suspend fun trashThread(accessToken: String, threadId: String) {
    request(
      accessToken = accessToken,
      path = "threads/${encodePath(threadId)}/trash",
      method = HttpMethod.POST,
      body = JSONObject(),
    )
  }

  private suspend fun request(
    accessToken: String,
    path: String,
    method: HttpMethod = HttpMethod.GET,
    body: JSONObject? = null,
  ): JSONObject {
    val response = httpClient.execute(
      HttpRequest(
        url = "$BASE_URL/$path",
        headers = mapOf(
          "Authorization" to "Bearer $accessToken",
          "Accept" to "application/json",
        ),
        method = method,
        body = body?.toString()?.toByteArray(Charsets.UTF_8),
        contentType = body?.let { "application/json; charset=utf-8" },
      ),
    )
    response.requireSuccess()
    if (response.body.isEmpty()) return JSONObject()
    return JSONObject(response.body.toString(Charsets.UTF_8))
  }

  private fun HttpResponse.requireSuccess() {
    if (isSuccessful) return
    val details = body.toString(Charsets.UTF_8).take(1_000)
    throw GmailApiException(
      statusCode = statusCode,
      message = "Gmail API HTTP $statusCode: ${details.ifBlank { reasonPhrase }}",
    )
  }

  private suspend fun parseThread(
    accessToken: String,
    accountId: String,
    json: JSONObject,
  ): MailThread {
    val messagesJson = json.optJSONArray("messages") ?: JSONArray()
    val messages = buildList {
      for (index in 0 until messagesJson.length()) {
        messagesJson.optJSONObject(index)?.let { add(parseMessage(accessToken, accountId, it)) }
      }
    }.sortedBy { it.receivedAtEpochMillis }
    val latest = messages.lastOrNull()
    val subject = messages.firstNotNullOfOrNull { it.subject.takeIf(String::isNotBlank) }.orEmpty()
    return MailThread(
      id = json.optString("id"),
      accountId = accountId,
      subject = subject.ifBlank { "(件名なし)" },
      snippet = latest?.snippet.orEmpty(),
      lastMessageAtEpochMillis = messages.maxOfOrNull(MailMessage::receivedAtEpochMillis) ?: 0L,
      messageCount = messages.size,
      isInInbox = messages.any { "INBOX" in it.labelIds },
      isUnread = messages.any(MailMessage::isUnread),
      isStarred = messages.any(MailMessage::isStarred),
      messages = messages,
    )
  }

  private suspend fun parseMessage(
    accessToken: String,
    accountId: String,
    json: JSONObject,
  ): MailMessage {
    val payload = json.optJSONObject("payload") ?: JSONObject()
    val labels = json.optJSONArray("labelIds").toStringSet()
    val messageId = json.optString("id")
    val messageBody = body(accessToken, messageId, payload)
    return MailMessage(
      id = messageId,
      threadId = json.optString("threadId"),
      accountId = accountId,
      sender = header(payload, "From"),
      recipients = header(payload, "To"),
      subject = header(payload, "Subject"),
      snippet = json.optString("snippet"),
      body = messageBody.plainText,
      htmlBody = messageBody.html,
      receivedAtEpochMillis = json.optString("internalDate").toLongOrNull() ?: 0L,
      labelIds = labels,
      isUnread = "UNREAD" in labels,
      isStarred = "STARRED" in labels,
    )
  }

  private fun header(payload: JSONObject, name: String): String {
    val headers = payload.optJSONArray("headers") ?: return ""
    for (index in 0 until headers.length()) {
      val item = headers.optJSONObject(index) ?: continue
      if (item.optString("name").equals(name, ignoreCase = true)) return item.optString("value")
    }
    return ""
  }

  private suspend fun body(
    accessToken: String,
    messageId: String,
    payload: JSONObject,
  ): GmailMessageBody {
    val plainText = findBody(accessToken, messageId, payload, "text/plain")
      ?.trim()
      .orEmpty()
    val html = findBody(accessToken, messageId, payload, "text/html")
      ?.trim()
      ?.takeIf(String::isNotBlank)
    val fallbackText = if (plainText.isNotBlank()) {
      plainText
    } else {
      html?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }.orEmpty()
    }
    return GmailMessageBody(
      plainText = fallbackText,
      html = html,
    )
  }

  private suspend fun findBody(
    accessToken: String,
    messageId: String,
    payload: JSONObject,
    mimeType: String,
  ): String? {
    if (payload.optString("mimeType").equals(mimeType, ignoreCase = true)) {
      val body = payload.optJSONObject("body") ?: JSONObject()
      val inlineData = body.optString("data")
      if (inlineData.isNotBlank()) return decodeBody(inlineData)

      val attachmentId = body.optString("attachmentId")
      if (attachmentId.isNotBlank() && messageId.isNotBlank()) {
        val attachment = request(
          accessToken = accessToken,
          path = "messages/${encodePath(messageId)}/attachments/${encodePath(attachmentId)}",
        )
        val attachmentData = attachment.optString("data")
        if (attachmentData.isNotBlank()) return decodeBody(attachmentData)
      }
    }
    val parts = payload.optJSONArray("parts") ?: return null
    for (index in 0 until parts.length()) {
      val part = parts.optJSONObject(index) ?: continue
      findBody(accessToken, messageId, part, mimeType)?.let { return it }
    }
    return null
  }

  private fun decodeBody(data: String): String = runCatching {
    String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
  }.getOrDefault("")

  private fun threadIds(response: JSONObject): List<String> {
    val threads = response.optJSONArray("threads") ?: JSONArray()
    return buildList {
      for (index in 0 until threads.length()) {
        threads.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let(::add)
      }
    }
  }

  private fun threadsPath(
    query: String,
    maxResults: Int,
    pageToken: String?,
  ): String {
    val params = buildList {
      add("maxResults=$maxResults")
      query.takeIf(String::isNotBlank)?.let { add("q=${encode(it)}") }
      pageToken?.takeIf(String::isNotBlank)?.let { add("pageToken=${encode(it)}") }
    }
    return "threads?${params.joinToString("&")}"
  }

  private fun collectThreadIds(array: JSONArray?, target: MutableSet<String>) {
    if (array == null) return
    for (index in 0 until array.length()) {
      val message = array.optJSONObject(index)?.optJSONObject("message") ?: continue
      message.optString("threadId").takeIf(String::isNotBlank)?.let(target::add)
    }
  }

  private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
      for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
  }

  private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
  private fun encodePath(value: String): String = encode(value).replace("+", "%20")

  private companion object {
    const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"
  }
}
