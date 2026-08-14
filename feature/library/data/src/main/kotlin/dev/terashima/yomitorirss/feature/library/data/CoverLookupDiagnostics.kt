package dev.terashima.yomitorirss.feature.library.data

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal enum class BookIdentifierRelation {
  EXACT_EDITION,
  SAME_WORK,
}

internal data class ResolvedBookIdentifier(
  val type: String,
  val value: String,
  val relation: BookIdentifierRelation,
  val source: String,
)

internal data class CoverLookupTraceStep(
  val provider: String,
  val status: CoverLookupStatus,
  val reason: String,
  val operation: String = "COVER_LOOKUP",
  val retryable: Boolean = false,
  val retryAfterSeconds: Long? = null,
  val httpStatus: Int? = null,
  val responseBytes: Int? = null,
  val candidateCount: Int? = null,
  val coverCandidateCount: Int? = null,
  val titleMatchCount: Int? = null,
  val authorMatchCount: Int? = null,
  val volumeMatchCount: Int? = null,
  val attributes: Map<String, String> = emptyMap(),
)

internal data class TracedCoverLookupResult(
  val lookup: CoverLookupResult,
  val step: CoverLookupTraceStep,
  val resolvedIdentifiers: List<ResolvedBookIdentifier> = emptyList(),
)

internal class CoverProviderIOException(
  message: String,
  val step: CoverLookupTraceStep,
  cause: Throwable? = null,
) : IOException(message, cause)

class KindleCoverEnrichmentException(
  message: String,
  val diagnosticTrace: String,
  cause: Throwable? = null,
) : IOException(message, cause)

internal fun List<CoverLookupTraceStep>.toDiagnosticTrace(
  resolvedIdentifiers: List<ResolvedBookIdentifier> = emptyList(),
  nextAttemptAtEpochMillis: Long? = null,
): String {
  val stepsJson = JSONArray()
  take(MAX_TRACE_STEPS).forEach { step ->
    val json = JSONObject()
      .put("provider", step.provider.take(MAX_DIAGNOSTIC_VALUE_CHARS))
      .put("operation", step.operation.take(MAX_DIAGNOSTIC_VALUE_CHARS))
      .put("status", step.status.name)
      .put("reason", step.reason.take(MAX_DIAGNOSTIC_VALUE_CHARS))
      .put("retryable", step.retryable)
    step.retryAfterSeconds?.let { json.put("retryAfterSeconds", it) }
    step.httpStatus?.let { json.put("httpStatus", it) }
    step.responseBytes?.let { json.put("responseBytes", it) }
    step.candidateCount?.let { json.put("candidateCount", it) }
    step.coverCandidateCount?.let { json.put("coverCandidateCount", it) }
    step.titleMatchCount?.let { json.put("titleMatchCount", it) }
    step.authorMatchCount?.let { json.put("authorMatchCount", it) }
    step.volumeMatchCount?.let { json.put("volumeMatchCount", it) }
    if (step.attributes.isNotEmpty()) {
      val safeAttributes = JSONObject()
      step.attributes.entries.take(MAX_TRACE_ATTRIBUTES).forEach { (key, value) ->
        safeAttributes.put(
          key.take(MAX_DIAGNOSTIC_KEY_CHARS),
          value.take(MAX_DIAGNOSTIC_VALUE_CHARS),
        )
      }
      json.put("attributes", safeAttributes)
    }
    stepsJson.put(json)
  }

  val root = JSONObject()
    .put("version", 2)
    .put("steps", stepsJson)

  if (resolvedIdentifiers.isNotEmpty()) {
    val identifiersJson = JSONArray()
    resolvedIdentifiers.distinctBy { "${it.type}:${it.value}:${it.relation}:${it.source}" }
      .take(MAX_RESOLVED_IDENTIFIERS)
      .forEach { identifier ->
        identifiersJson.put(
          JSONObject()
            .put("type", identifier.type.take(MAX_DIAGNOSTIC_VALUE_CHARS))
            .put("value", identifier.value.take(MAX_DIAGNOSTIC_VALUE_CHARS))
            .put("relation", identifier.relation.name)
            .put("source", identifier.source.take(MAX_DIAGNOSTIC_VALUE_CHARS)),
        )
      }
    root.put("resolvedIdentity", JSONObject().put("identifiers", identifiersJson))
  }
  nextAttemptAtEpochMillis?.let { root.put("nextAttemptAt", it) }
  return root.toString()
}

internal fun String.toResolvedIsbn(
  relation: BookIdentifierRelation,
  source: String,
): ResolvedBookIdentifier? {
  val cleaned = cleanBookIsbn() ?: return null
  return ResolvedBookIdentifier(
    type = if (cleaned.length == 13) "ISBN_13" else "ISBN_10",
    value = cleaned,
    relation = relation,
    source = source,
  )
}

internal const val DIAGNOSTIC_TRACE_COLUMN = "diagnostic_trace"
internal const val RETRY_COUNT_COLUMN = "retry_count"
internal const val NEXT_ATTEMPT_AT_COLUMN = "next_attempt_at"
private const val MAX_TRACE_STEPS = 8
private const val MAX_TRACE_ATTRIBUTES = 16
private const val MAX_RESOLVED_IDENTIFIERS = 8
private const val MAX_DIAGNOSTIC_KEY_CHARS = 64
private const val MAX_DIAGNOSTIC_VALUE_CHARS = 256
