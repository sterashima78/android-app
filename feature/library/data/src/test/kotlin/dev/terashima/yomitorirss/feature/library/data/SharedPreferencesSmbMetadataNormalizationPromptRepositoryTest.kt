package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.library.DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesSmbMetadataNormalizationPromptRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("library_ai_preferences", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `未設定時は既定promptを返す`() {
    val repository = SharedPreferencesSmbMetadataNormalizationPromptRepository(context)

    assertEquals(DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT, repository.prompt())
  }

  @Test
  fun `保存したpromptを新しいrepositoryから読める`() {
    val repository = SharedPreferencesSmbMetadataNormalizationPromptRepository(context)
    repository.update("表紙の副題も確認してください。 {{fileName}}")

    val restored = SharedPreferencesSmbMetadataNormalizationPromptRepository(context)

    assertEquals("表紙の副題も確認してください。 {{fileName}}", restored.prompt())
  }

  @Test
  fun `不正な型で保存されていても既定promptへfallbackする`() {
    context.getSharedPreferences("library_ai_preferences", Context.MODE_PRIVATE)
      .edit()
      .putInt("smb_metadata_normalization_prompt", 1)
      .commit()

    val repository = SharedPreferencesSmbMetadataNormalizationPromptRepository(context)

    assertEquals(DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT, repository.prompt())
  }

  @Test
  fun `resetすると既定promptへ戻る`() {
    val repository = SharedPreferencesSmbMetadataNormalizationPromptRepository(context)
    repository.update("カスタム指示")
    repository.reset()

    assertEquals(DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT, repository.prompt())
  }
}
