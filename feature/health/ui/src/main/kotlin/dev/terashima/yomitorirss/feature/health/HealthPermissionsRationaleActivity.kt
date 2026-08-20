package dev.terashima.yomitorirss.feature.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthPermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text("Health Connect データの利用について", style = MaterialTheme.typography.headlineSmall)
            Text("このアプリは、歩数・運動・心拍・睡眠・体重・体脂肪率を Health Connect から読み取り、アプリ内で表示するために利用します。")
            Text("このアプリ内で終了して保存したワークアウトは、運動セッションとして Health Connect へ書き込みます。Health Connect から読み取った運動を Workout へ取り込んだり、そのまま書き戻したりはしません。")
            Text("Health Connect から読み取ったデータは、この機能ではアプリのデータベースへ保存せず、バックアップ、外部サービス、AI 処理へ送信しません。")
            Text("権限は Android の Health Connect 設定からいつでも変更できます。")
            Button(onClick = ::finish) { Text("閉じる") }
          }
        }
      }
    }
  }
}
