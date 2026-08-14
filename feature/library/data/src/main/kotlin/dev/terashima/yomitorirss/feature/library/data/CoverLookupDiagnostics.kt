package dev.terashima.yomitorirss.feature.library.data

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal data class CoverLookupTraceStep(
  val provider: String,
  val status: CoverLookupStatus,
  val reason: String,
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

internal fun List<CoverLookupTraceStep>.toDiagnosticTrace(): String {
  val stepsJson = JSONArray()
  take(MAX_TRACE_STEPS).forEach { step ->
    val json = JSONObject()
      .put("provider", step.provider.take(MAX_DIAGNOSTIC_VALUE_CHARS))
      .put("status", step.status.name)
      .put("reason", step.reason.take(MAX_DIAGNOSTIC_VALUE_CHARS))
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
  return JSONObject()
    .put("version", 1)
    .put("steps", stepsJson)
    .toString()
}

internal const val DIAGNOSTIC_TRACE_COLUMN = "diagnostic_trace"
private const val MAX_TRACE_STEPS = 4
private const val MAX_TRACE_ATTRIBUTES = 16
private const val MAX_DIAGNOSTIC_KEY_CHARS = 64
private const val MAX_DIAGNOSTIC_VALUE_CHARS = 256
