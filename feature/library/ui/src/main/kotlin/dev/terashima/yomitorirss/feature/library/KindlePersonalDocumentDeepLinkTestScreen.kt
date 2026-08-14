package dev.terashima.yomitorirss.feature.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

internal data class KindlePersonalDocumentDeepLinkCandidate(
  val label: String,
  val uri: String,
)

internal fun kindlePersonalDocumentDeepLinkCandidates(rawId: String): List<KindlePersonalDocumentDeepLinkCandidate> {
  val id = rawId.trim().uppercase(Locale.ROOT)
  if (!KINDLE_PERSONAL_DOCUMENT_ID.matches(id)) return emptyList()
  val contentIdentifier = "$id:KindlePDoc"
  return listOf(
    KindlePersonalDocumentDeepLinkCandidate(
      label = "購入本と同じ asin 形式",
      uri = "kindle://book/?action=open&asin=$id",
    ),
    KindlePersonalDocumentDeepLinkCandidate(
      label = "asin に KindlePDoc を付加",
      uri = "kindle://book/?action=open&asin=$contentIdentifier",
    ),
    KindlePersonalDocumentDeepLinkCandidate(
      label = "contentIdentifier パラメータ",
      uri = "kindle://book/?action=open&contentIdentifier=$contentIdentifier",
    ),
    KindlePersonalDocumentDeepLinkCandidate(
      label = "contentId パラメータ",
      uri = "kindle://book/?action=open&contentId=$contentIdentifier",
    ),
  )
}

@Composable
internal fun KindlePersonalDocumentDeepLinkTestScreen(
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(Modifier.fillMaxSize()) {
      KindlePersonalDocumentDeepLinkTestContent(onDismiss = onDismiss)
    }
  }
}

@Composable
private fun KindlePersonalDocumentDeepLinkTestContent(
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var personalDocumentId by remember { mutableStateOf("") }
  var packageStatus by remember { mutableStateOf(readKindlePackageStatus(context)) }
  var launchResult by remember { mutableStateOf<String?>(null) }
  var resultsByUri by remember { mutableStateOf(emptyMap<String, String>()) }
  val candidates = remember(personalDocumentId) {
    kindlePersonalDocumentDeepLinkCandidates(personalDocumentId)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("Kindle Personal Document リンク検証", style = MaterialTheme.typography.titleLarge)
      TextButton(onClick = onDismiss) { Text("閉じる") }
    }

    Text(
      "Personal Document の32文字IDを入力し、Kindleアプリが各 deep link 候補を解決・起動できるか実機で確認します。入力値や結果は保存・送信しません。",
      style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedTextField(
      value = personalDocumentId,
      onValueChange = { personalDocumentId = it.trim().uppercase(Locale.ROOT).take(64) },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Personal Document ID") },
      supportingText = {
        Text(
          if (personalDocumentId.isEmpty() || KINDLE_PERSONAL_DOCUMENT_ID.matches(personalDocumentId)) {
            "エクスポートJSONの books[].id を入力"
          } else {
            "32文字の英大文字・数字を入力してください"
          },
        )
      },
      singleLine = true,
    )

    HorizontalDivider()
    Text("Kindleアプリ", style = MaterialTheme.typography.titleMedium)
    Text(packageStatus.summary, style = MaterialTheme.typography.bodyMedium)
    if (packageStatus.exportedActivities.isNotEmpty()) {
      Text("公開Activity候補", style = MaterialTheme.typography.labelLarge)
      packageStatus.exportedActivities.forEach { activity ->
        Text(activity, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { packageStatus = readKindlePackageStatus(context) }) {
        Text("再確認")
      }
      Button(
        onClick = { launchResult = launchKindleHome(context) },
        enabled = packageStatus.installed,
      ) {
        Text("Kindleを起動")
      }
    }
    launchResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

    HorizontalDivider()
    Text("deep link 候補", style = MaterialTheme.typography.titleMedium)
    if (candidates.isEmpty()) {
      Text("有効なIDを入力すると候補を表示します。", style = MaterialTheme.typography.bodySmall)
    } else {
      candidates.forEach { candidate ->
        Card(Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(candidate.label, style = MaterialTheme.typography.titleSmall)
            Text(
              candidate.uri,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  resultsByUri = resultsByUri + (
                    candidate.uri to describeKindleDeepLinkResolution(context, candidate.uri)
                    )
                },
              ) {
                Text("解決確認")
              }
              Button(
                onClick = {
                  resultsByUri = resultsByUri + (
                    candidate.uri to launchKindleDeepLink(context, candidate.uri)
                    )
                },
              ) {
                Text("開く")
              }
            }
            resultsByUri[candidate.uri]?.let { result ->
              Text(result, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }

    Text(
      "判定方法: 「解決確認」は com.amazon.kindle に限定して ACTION_VIEW を受ける公開Activityを列挙します。「開く」は同じIntentを実際に送信します。Kindleが開いても対象ドキュメント以外が表示された場合は、その候補は直接オープン不可として扱います。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private data class KindlePackageStatus(
  val installed: Boolean,
  val summary: String,
  val exportedActivities: List<String>,
)

@Suppress("DEPRECATION")
private fun readKindlePackageStatus(context: Context): KindlePackageStatus {
  val packageManager = context.packageManager
  return try {
    val packageInfo = packageManager.getPackageInfo(KINDLE_PACKAGE, PackageManager.GET_ACTIVITIES)
    val exportedActivities = packageInfo.activities
      .orEmpty()
      .asSequence()
      .filter { it.exported }
      .map { it.name }
      .filter { name ->
        val normalized = name.lowercase(Locale.ROOT)
        listOf("book", "kindle", "reader", "reading", "home").any(normalized::contains)
      }
      .distinct()
      .sorted()
      .take(MAX_EXPORTED_ACTIVITY_DISPLAY)
      .toList()
    KindlePackageStatus(
      installed = true,
      summary = "インストール済み: ${packageInfo.versionName ?: "versionName不明"} (${packageInfo.longVersionCode})",
      exportedActivities = exportedActivities,
    )
  } catch (_: PackageManager.NameNotFoundException) {
    KindlePackageStatus(
      installed = false,
      summary = "Kindleアプリ ($KINDLE_PACKAGE) を確認できませんでした。",
      exportedActivities = emptyList(),
    )
  }
}

@Suppress("DEPRECATION")
private fun describeKindleDeepLinkResolution(
  context: Context,
  uri: String,
): String {
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(KINDLE_PACKAGE)
  val activities = context.packageManager.queryIntentActivities(
    intent,
    PackageManager.MATCH_DEFAULT_ONLY,
  ).mapNotNull { it.activityInfo?.name }
    .distinct()
  return if (activities.isEmpty()) {
    "解決できるActivityなし"
  } else {
    "解決: ${activities.joinToString()}"
  }
}

private fun launchKindleDeepLink(
  context: Context,
  uri: String,
): String = try {
  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(KINDLE_PACKAGE))
  "起動要求を送信しました。Kindle側で開いた内容を確認してください。"
} catch (_: ActivityNotFoundException) {
  "起動失敗: 対応Activityがありません"
} catch (_: SecurityException) {
  "起動失敗: Activityへのアクセスが拒否されました"
}

private fun launchKindleHome(context: Context): String {
  val intent = context.packageManager.getLaunchIntentForPackage(KINDLE_PACKAGE)
    ?: return "Kindleの起動Intentを取得できませんでした"
  return try {
    context.startActivity(intent)
    "Kindleを起動しました"
  } catch (_: ActivityNotFoundException) {
    "Kindleを起動できませんでした"
  } catch (_: SecurityException) {
    "Kindleの起動が拒否されました"
  }
}

private const val KINDLE_PACKAGE = "com.amazon.kindle"
private const val MAX_EXPORTED_ACTIVITY_DISPLAY = 12
private val KINDLE_PERSONAL_DOCUMENT_ID = Regex("^[A-Z0-9]{32}$")
