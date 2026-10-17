package dev.terashima.yomitorirss.feature.rss

import kotlinx.coroutines.CancellationException

/**
 * Saves first, then registers a positive RSS reference.
 *
 * Priority feedback is best-effort: an already saved article must not be reported as a failed
 * bookmark operation if reference registration fails. Both RSS UI and the RSS widget use this flow.
 */
suspend fun markReadLaterWithPriorityFeedback(
  saveReadLater: suspend () -> Boolean,
  recordPriority: (suspend () -> Unit)?,
  scheduleLearning: () -> Unit,
  onPriorityError: (Throwable) -> Unit,
) {
  if (!saveReadLater()) return
  val record = recordPriority ?: return
  try {
    record()
  } catch (error: CancellationException) {
    throw error
  } catch (error: Throwable) {
    onPriorityError(error)
    return
  }
  runCatching { scheduleLearning() }
}
