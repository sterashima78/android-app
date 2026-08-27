package dev.terashima.yomitorirss.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AppLockContent(onUnlock: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
  ) {
    Text("アプリはロックされています", style = MaterialTheme.typography.headlineSmall)
    Text(
      "生体認証または端末の画面ロックで認証してください。",
      modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
    )
    Button(onClick = onUnlock) {
      Text("ロックを解除")
    }
  }
}
