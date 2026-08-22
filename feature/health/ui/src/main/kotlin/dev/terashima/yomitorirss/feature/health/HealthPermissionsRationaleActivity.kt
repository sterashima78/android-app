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
            Text("このアプリは、歩数・活動消費カロリー・運動・心拍・睡眠・体重・体脂肪率・栄養を Health Connect から読み取り、ヘルスケア画面で表示するために利用します。")
            Text("活動消費カロリーや心拍数は Health Connect に記録された値を参照し、ワークアウト機能では推定・計算しません。")
            Text("栄養情報は摂取カロリー、たんぱく質、脂質、炭水化物を日ごとに集計し、一般的な摂取目安との比較表示に利用します。")
            Text("ヘルスケア画面では日・週・月を選択して表示中の期間を Health Connect から再取得できます。30日より前の他アプリ由来データを表示するため、対応端末では「過去のデータ」へのアクセス権限を利用します。")
            Text("このアプリ内で終了して保存したワークアウトは、運動セッションとして Health Connect へ書き込みます。Health Connect から読み取った運動を Workout へ取り込んだり、そのまま書き戻したりはしません。")
            Text("Health Connect から読み取ったデータは、この機能ではアプリのデータベースへ保存せず、バックアップ、外部サービス、AI 処理へ送信しません。バックグラウンドで健康データを読み取る権限も要求しません。")
            Text("権限は Android の Health Connect 設定からいつでも変更できます。")
            Button(onClick = ::finish) { Text("閉じる") }
          }
        }
      }
    }
  }
}
