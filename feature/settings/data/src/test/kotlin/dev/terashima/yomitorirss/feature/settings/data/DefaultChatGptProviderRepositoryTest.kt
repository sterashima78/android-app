package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultChatGptProviderRepositoryTest {
  @Test
  fun `summary picker includes only visible API models with web search`() {
    val models = listOf(
      model("eligible", visible = true, api = true, web = true),
      model("no-web", visible = true, api = true, web = false),
      model("hidden", visible = false, api = true, web = true),
      model("not-api", visible = true, api = false, web = true),
    )

    val selected = selectChatGptProviderModels(models)

    assertEquals(listOf("eligible"), selected.map { it.id })
  }

  private fun model(
    id: String,
    visible: Boolean,
    api: Boolean,
    web: Boolean,
  ) = ChatGptModelInfo(
    id = id,
    displayName = id,
    description = null,
    contextWindowTokens = null,
    maxContextWindowTokens = null,
    supportsWebSearch = web,
    supportedInApi = api,
    visibleInPicker = visible,
    priority = 0,
  )
}
