package dev.terashima.yomitorirss.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType

@Composable
internal fun VideoProviderSettingsDialog(
  state: VideoUiState,
  onSaveProvider: (VideoProvider) -> Unit,
  onDeleteProvider: (String) -> Unit,
  onSubscribe: (String, String) -> Unit,
  onUnsubscribe: (String) -> Unit,
  onRefresh: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var sourceUrl by remember { mutableStateOf("") }
  var showCustomEditor by remember { mutableStateOf(false) }
  var customName by remember { mutableStateOf("") }
  var customFunctionCode by remember { mutableStateOf("") }
  val configuredTypes = state.providers.mapTo(HashSet()) { it.type }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("購読プロバイダ設定") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "Web動画は1件ずつ登録します。ここではチャンネルなどを購読し、新着動画を未読として取り込むプロバイダを管理します。",
        )

        VideoProviderType.entries
          .filter { it != VideoProviderType.CUSTOM && it !in configuredTypes }
          .forEach { type ->
            Button(
              onClick = {
                onSaveProvider(
                  VideoProvider(
                    id = "",
                    type = type,
                    name = type.displayName(),
                    enabled = true,
                  ),
                )
              },
              enabled = !state.busy,
            ) {
              Text("${type.displayName()} を追加")
            }
          }

        Button(
          onClick = { showCustomEditor = !showCustomEditor },
          enabled = !state.busy,
        ) {
          Text(if (showCustomEditor) "カスタム追加を閉じる" else "カスタムプロバイダを追加")
        }
        if (showCustomEditor) {
          CustomProviderEditor(
            name = customName,
            functionCode = customFunctionCode,
            enabled = !state.busy,
            onNameChange = { customName = it },
            onFunctionCodeChange = { customFunctionCode = it },
            actionLabel = "追加",
            onSave = {
              onSaveProvider(
                VideoProvider(
                  id = "",
                  type = VideoProviderType.CUSTOM,
                  name = customName,
                  enabled = true,
                  functionCode = customFunctionCode,
                ),
              )
              customName = ""
              customFunctionCode = ""
              showCustomEditor = false
            },
          )
        }

        state.providers.forEach { provider ->
          key(provider.id) {
            HorizontalDivider()
            ProviderEditor(
              provider = provider,
              sourceUrl = sourceUrl,
              busy = state.busy,
              subscriptions = state.subscriptions.filter { it.providerId == provider.id },
              onSourceUrlChange = { sourceUrl = it },
              onSaveProvider = onSaveProvider,
              onDeleteProvider = onDeleteProvider,
              onSubscribe = { providerId, value ->
                onSubscribe(providerId, value)
                sourceUrl = ""
              },
              onUnsubscribe = onUnsubscribe,
              onRefresh = onRefresh,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("閉じる")
      }
    },
  )
}

@Composable
private fun ProviderEditor(
  provider: VideoProvider,
  sourceUrl: String,
  busy: Boolean,
  subscriptions: List<dev.terashima.yomitorirss.feature.video.VideoSubscription>,
  onSourceUrlChange: (String) -> Unit,
  onSaveProvider: (VideoProvider) -> Unit,
  onDeleteProvider: (String) -> Unit,
  onSubscribe: (String, String) -> Unit,
  onUnsubscribe: (String) -> Unit,
  onRefresh: (String?) -> Unit,
) {
  var customName by remember(provider.id, provider.name) { mutableStateOf(provider.name) }
  var customFunctionCode by remember(provider.id, provider.functionCode) {
    mutableStateOf(provider.functionCode.orEmpty())
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(provider.name, fontWeight = FontWeight.SemiBold)
      Text(if (provider.enabled) "有効" else "無効")
    }
    TextButton(
      onClick = { onSaveProvider(provider.copy(enabled = !provider.enabled)) },
      enabled = !busy,
    ) {
      Text(if (provider.enabled) "無効化" else "有効化")
    }
    TextButton(
      onClick = { onDeleteProvider(provider.id) },
      enabled = !busy,
    ) {
      Text("削除")
    }
  }

  if (provider.type == VideoProviderType.CUSTOM) {
    CustomProviderEditor(
      name = customName,
      functionCode = customFunctionCode,
      enabled = !busy,
      onNameChange = { customName = it },
      onFunctionCodeChange = { customFunctionCode = it },
      actionLabel = "設定を保存",
      onSave = {
        onSaveProvider(
          provider.copy(
            name = customName,
            functionCode = customFunctionCode,
          ),
        )
      },
    )
  }

  OutlinedTextField(
    value = sourceUrl,
    onValueChange = onSourceUrlChange,
    modifier = Modifier.fillMaxWidth(),
    enabled = provider.enabled && !busy,
    singleLine = true,
    label = {
      Text(if (provider.type == VideoProviderType.CUSTOM) "購読入力" else "チャンネルURL")
    },
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = {
        val value = sourceUrl.trim()
        if (value.isNotEmpty()) onSubscribe(provider.id, value)
      },
      enabled = provider.enabled && sourceUrl.isNotBlank() && !busy,
    ) {
      Text("購読を追加")
    }
    TextButton(
      onClick = { onRefresh(provider.id) },
      enabled = provider.enabled && !busy,
    ) {
      Text("更新")
    }
  }

  if (subscriptions.isEmpty()) {
    Text("購読中のチャンネルはありません")
  } else {
    subscriptions.forEach { subscription ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(subscription.title)
          Text(subscription.sourceUrl)
        }
        TextButton(
          onClick = { onUnsubscribe(subscription.id) },
          enabled = !busy,
        ) {
          Text("解除")
        }
      }
    }
  }
}

@Composable
private fun CustomProviderEditor(
  name: String,
  functionCode: String,
  enabled: Boolean,
  onNameChange: (String) -> Unit,
  onFunctionCodeChange: (String) -> Unit,
  actionLabel: String,
  onSave: () -> Unit,
) {
  Text(
    "function は async (input, api) => ({ sourceId, title, sourceUrl, videos }) の形式で設定します。" +
      " 外部取得は api.fetch({ url, method, headers, body, contentType }) を使用します。",
  )
  OutlinedTextField(
    value = name,
    onValueChange = onNameChange,
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    singleLine = true,
    label = { Text("プロバイダ名") },
  )
  OutlinedTextField(
    value = functionCode,
    onValueChange = onFunctionCodeChange,
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    minLines = 8,
    label = { Text("JavaScript function") },
  )
  Button(
    onClick = onSave,
    enabled = enabled && name.isNotBlank() && functionCode.isNotBlank(),
  ) {
    Text(actionLabel)
  }
}

private fun VideoProviderType.displayName(): String = when (this) {
  VideoProviderType.YOUTUBE -> "YouTube"
  VideoProviderType.CUSTOM -> "カスタム"
}
