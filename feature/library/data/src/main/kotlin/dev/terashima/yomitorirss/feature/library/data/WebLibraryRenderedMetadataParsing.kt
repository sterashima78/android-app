package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorExecution
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorStatus
import java.net.URI
import org.json.JSONObject
import org.json.JSONTokener

internal fun webLibraryMetadataTimeoutMillis(
  extractors: List<WebLibraryMetadataExtractor>,
  requestedUrl: String,
  fallbackTimeoutMillis: Long,
): Long = findMatchingWebLibraryMetadataExtractor(extractors, requestedUrl)
  ?.timeoutSeconds
  ?.times(1_000L)
  ?: fallbackTimeoutMillis

internal data class WebLibraryCustomMetadata(
  val title: String?,
  val thumbnailUrl: String?,
)

internal fun createWebLibraryMetadataExtractorExecution(
  extractor: WebLibraryMetadataExtractor,
  status: WebLibraryMetadataExtractorStatus,
  message: String? = null,
  metadata: WebLibraryCustomMetadata? = null,
): WebLibraryMetadataExtractorExecution = WebLibraryMetadataExtractorExecution(
  ruleId = extractor.id,
  urlPattern = extractor.urlPattern,
  status = status,
  message = message?.take(MAX_DIAGNOSTIC_MESSAGE_LENGTH),
  extractedTitle = metadata?.title,
  extractedThumbnailUrl = metadata?.thumbnailUrl,
)

internal data class WebLibraryCustomMetadataPromisePoll(
  val pending: Boolean,
  val metadata: WebLibraryCustomMetadata?,
  val status: WebLibraryMetadataExtractorStatus? = null,
  val message: String? = null,
)

internal fun customMetadataNativeWatchdogDelayMillis(): Long =
  CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS + CUSTOM_METADATA_NATIVE_WATCHDOG_GRACE_MILLIS

internal fun customMetadataStartScript(functionCode: String, stateKey: String): String {
  val expression = functionCode.trim().removeSuffix(";")
  val quotedExpression = JSONObject.quote(expression)
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      const source = $quotedExpression;
      const finish = (status, value = null, message = null) => {
        window[stateKey] = { pending: false, status, value, message };
      };
      const errorMessage = (error) => {
        const value = error && typeof error.message === 'string'
          ? error.message
          : String(error ?? '');
        return value.trim().slice(0, $MAX_DIAGNOSTIC_MESSAGE_LENGTH) || null;
      };
      window[stateKey] = { pending: true, status: null, value: null, message: null };
      setTimeout(() => {
        let extractor;
        try {
          extractor = eval('(' + source + ')');
        } catch (error) {
          finish('invalid_function', null, errorMessage(error));
          return;
        }
        if (typeof extractor !== 'function') {
          finish('invalid_function', null, '関数式として評価できませんでした');
          return;
        }
        try {
          const promise = extractor({ url: location.href });
          if (!promise || typeof promise.then !== 'function') {
            finish('non_promise_result', null, 'Promise を返していません');
            return;
          }
          Promise.resolve(promise)
            .then((value) => {
              if (!value || typeof value !== 'object') {
                finish('invalid_result', null, '戻り値が object ではありません');
                return;
              }
              const title = typeof value.title === 'string' ? value.title.trim() : null;
              const thumbnailUrl = typeof value.thumbnailUrl === 'string' ? value.thumbnailUrl.trim() : null;
              if (!title && !thumbnailUrl) {
                finish('empty_result', null, 'title と thumbnailUrl がどちらも空でした');
                return;
              }
              finish(
                'applied',
                JSON.stringify({
                  title: title || null,
                  thumbnailUrl: thumbnailUrl || null
                })
              );
            })
            .catch((error) => {
              finish('rejected', null, errorMessage(error));
            });
        } catch (error) {
          finish('threw', null, errorMessage(error));
        }
      }, 0);
      return null;
    })()
  """.trimIndent()
}

internal fun customMetadataPollScript(stateKey: String): String {
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      const state = window[stateKey];
      if (!state) {
        return JSON.stringify({
          pending: false,
          status: 'invalid_state',
          value: null,
          message: '取得ルールの実行状態が見つかりません'
        });
      }
      if (state.pending) {
        return JSON.stringify({ pending: true, status: null, value: null, message: null });
      }
      const value = typeof state.value === 'string' ? state.value : null;
      const status = typeof state.status === 'string' ? state.status : 'invalid_state';
      const message = typeof state.message === 'string' ? state.message : null;
      delete window[stateKey];
      return JSON.stringify({ pending: false, status, value, message });
    })()
  """.trimIndent()
}

internal fun customMetadataCleanupScript(stateKey: String): String {
  val quotedStateKey = JSONObject.quote(stateKey)
  return "delete window[$quotedStateKey]; null;"
}

internal fun parseCustomMetadataPromisePoll(
  finalUrl: String,
  rawResult: String?,
): WebLibraryCustomMetadataPromisePoll? = runCatching {
  val evaluationResult = rawResult ?: return null
  val decoded = JSONTokener(evaluationResult).nextValue()
  val poll = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> return null
  }
  val pending = poll.optBoolean("pending", false)
  if (pending) {
    WebLibraryCustomMetadataPromisePoll(pending = true, metadata = null)
  } else {
    val parsedStatus = parseExtractorStatus(poll.optionalString("status"))
      ?: WebLibraryMetadataExtractorStatus.INVALID_STATE
    val rawValue = poll.optionalString("value")
    val parsedMetadata = rawValue?.let { payload ->
      parseCustomRenderedWebLibraryMetadata(finalUrl, JSONObject.quote(payload))
    }
    val status = if (
      parsedStatus == WebLibraryMetadataExtractorStatus.APPLIED &&
      parsedMetadata == null
    ) {
      WebLibraryMetadataExtractorStatus.INVALID_RESULT
    } else {
      parsedStatus
    }
    val message = when {
      status == WebLibraryMetadataExtractorStatus.INVALID_RESULT &&
        parsedStatus == WebLibraryMetadataExtractorStatus.APPLIED ->
        "title または HTTPS の thumbnailUrl を取得できませんでした"
      else -> poll.optionalString("message")
    }
    WebLibraryCustomMetadataPromisePoll(
      pending = false,
      metadata = parsedMetadata,
      status = status,
      message = message,
    )
  }
}.getOrNull()

private fun parseExtractorStatus(value: String?): WebLibraryMetadataExtractorStatus? = when (value) {
  "applied" -> WebLibraryMetadataExtractorStatus.APPLIED
  "empty_result" -> WebLibraryMetadataExtractorStatus.EMPTY_RESULT
  "invalid_function" -> WebLibraryMetadataExtractorStatus.INVALID_FUNCTION
  "non_promise_result" -> WebLibraryMetadataExtractorStatus.NON_PROMISE_RESULT
  "rejected" -> WebLibraryMetadataExtractorStatus.REJECTED
  "threw" -> WebLibraryMetadataExtractorStatus.THREW
  "invalid_state" -> WebLibraryMetadataExtractorStatus.INVALID_STATE
  "invalid_result" -> WebLibraryMetadataExtractorStatus.INVALID_RESULT
  else -> null
}

internal fun parseCustomRenderedWebLibraryMetadata(
  finalUrl: String,
  rawResult: String?,
): WebLibraryCustomMetadata? = runCatching {
  val evaluationResult = rawResult ?: return null
  val decoded = JSONTokener(evaluationResult).nextValue()
  if (decoded == JSONObject.NULL) return null
  val metadata = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> return null
  }
  val title = metadata.optionalString("title")
  val thumbnailUrl = metadata.optionalString("thumbnailUrl")
    ?.let { resolveRenderedImageUrl(finalUrl, it) }
  if (title == null && thumbnailUrl == null) return null
  WebLibraryCustomMetadata(title = title, thumbnailUrl = thumbnailUrl)
}.getOrNull()

internal fun LibraryBook.applyCustomMetadata(customMetadata: WebLibraryCustomMetadata?): LibraryBook {
  if (customMetadata == null) return this
  return copy(
    title = customMetadata.title ?: title,
    thumbnailUrl = customMetadata.thumbnailUrl ?: thumbnailUrl,
  )
}

internal fun parseRenderedWebLibraryBook(
  requestedUrl: String,
  rawResult: String?,
  titleHint: String? = null,
): LibraryBook {
  val evaluationResult = requireNotNull(rawResult) { "WebView metadata の応答がありません" }
  val decoded = JSONTokener(evaluationResult).nextValue()
  val metadata = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> throw IllegalArgumentException("WebView metadata の応答形式が不正です")
  }
  val finalUrl = metadata.optionalString("url")
    ?.let(::normalizeWebUrl)
    ?.takeIf(::isSafeRenderedUrl)
    ?: normalizeWebUrl(requestedUrl)
  require(isSafeRenderedUrl(finalUrl)) { "WebView metadata の URL が安全ではありません" }

  val title = metadata.optionalString("title")
    ?: titleHint?.trim()?.takeIf(String::isNotEmpty)
    ?: URI(finalUrl).host.removePrefix("www.")
  val image = sequenceOf("image", "firstImage")
    .mapNotNull { key ->
      metadata.optionalString(key)
        ?.let { resolveRenderedImageUrl(finalUrl, it) }
    }
    .firstOrNull()
  val authors = metadata.optionalString("author")
    ?.split(',', '、')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

  return LibraryBook(
    source = LibrarySource.WEB,
    sourceId = finalUrl,
    title = title.trim(),
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = metadata.optionalString("description"),
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = image,
    infoUrl = finalUrl,
  )
}

private fun JSONObject.optionalString(key: String): String? =
  optString(key, "").trim().takeIf(String::isNotEmpty)

private fun resolveRenderedImageUrl(baseUrl: String, value: String): String? = runCatching {
  normalizeWebUrl(URI(baseUrl).resolve(value).toString())
}.getOrNull()?.takeIf(::isSafeRenderedUrl)

internal fun isSafeRenderedUrl(url: String): Boolean = runCatching {
  val uri = URI(url)
  uri.scheme.equals("https", ignoreCase = true) &&
    !uri.host.isNullOrBlank() &&
    (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

internal const val CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS = 10_000L
private const val CUSTOM_METADATA_NATIVE_WATCHDOG_GRACE_MILLIS = 1_000L
private const val MAX_DIAGNOSTIC_MESSAGE_LENGTH = 200
