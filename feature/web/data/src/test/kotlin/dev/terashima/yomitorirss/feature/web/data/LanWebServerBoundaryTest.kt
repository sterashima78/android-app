package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.web.LanWebContentGateway
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanWebServerBoundaryTest {
  @Test
  fun `LAN WebはWeb owned gatewayを受け取りcross feature Repositoryを受け取らない`() {
    val parameterTypes = LanWebServer::class.java.declaredConstructors
      .flatMap { it.parameterTypes.asIterable() }
      .toSet()

    assertTrue(LanWebContentGateway::class.java in parameterTypes)
    assertFalse(parameterTypes.any { it.simpleName.endsWith("Repository") })
    assertFalse(parameterTypes.any { it.name.contains("YomitoriDatabase") || it.name.contains("DatabaseConnection") })
  }
}
