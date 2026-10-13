package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmbeddingModelArtifactTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `checksum validates exact bytes and rejects damaged artifacts`() {
    val artifact = temporaryFolder.newFile("artifact.litertlm")
    val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    artifact.writeText("hello")
    assertTrue(matchesSha256(artifact, helloSha))
    artifact.appendText("!")
    assertFalse(matchesSha256(artifact, helloSha))
    assertFalse(matchesSha256(temporaryFolder.root.resolve("absent"), helloSha))
  }
}
