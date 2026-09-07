package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSmbConnectionProfileRepository =
  staticCompositionLocalOf<SmbConnectionProfileRepository?> { null }

@Composable
fun ProvideSmbConnectionProfileRepository(
  repository: SmbConnectionProfileRepository,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalSmbConnectionProfileRepository provides repository, content = content)
}
