package dev.terashima.yomitorirss.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class Android17MemoryAnomalyProfilerTest {
  @Test
  fun `終了直前の安全なprofiling artifactだけを新しい順で返す`() {
    val directory = Files.createTempDirectory("profiling-artifacts").toFile()
    try {
      val recent = File(directory, "profile_trigger-type-8_recent.hprof").apply {
        writeText("recent")
        setLastModified(9_900L)
      }
      File(directory, "profile_trigger-type-8_older.hprof").apply {
        writeText("older")
        setLastModified(9_500L)
      }
      File(directory, "outside-window.hprof").apply {
        writeText("old")
        setLastModified(7_000L)
      }
      File(directory, "unsafe name.hprof").apply {
        writeText("unsafe")
        setLastModified(9_800L)
      }

      val artifacts = recentMemoryProfilingArtifactNames(
        profilingDirectory = directory,
        exitTimestampMillis = 10_000L,
        lookbackMillis = 1_000L,
      )

      assertEquals(
        listOf(recent.name, "profile_trigger-type-8_older.hprof"),
        artifacts,
      )
    } finally {
      directory.deleteRecursively()
    }
  }
}
