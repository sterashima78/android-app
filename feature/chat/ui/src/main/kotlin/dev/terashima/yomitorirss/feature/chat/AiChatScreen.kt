package dev.terashima.yomitorirss.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun AiChatScreen(
  modifier: Modifier = Modifier,
  state: ChatUiState,
  onSelectSession: (String) -> Unit,
  onStartNewSession: () -> Unit,
  onSendMessage: (String) -> Unit,
) {
  val listState = rememberLazyListState()
  var input by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(state.messages.size, state.sending, state.responseStarted, state.streamingReply.length) {
    val transientItems = if (state.sending) {
      1 + if (state.responseStarted && state.streamingReply.isNotBlank()) 1 else 0
    } else {
      0
    }
    val itemCount = state.messages.size + transientItems
    if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
  }

  fun sendMessage() {
    val text = input.trim()
    if (text.isBlank() || state.sending || state.selectedModel == null) return
    input = ""
    onSendMessage(text)
  }

  Column(
    modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = state.selectedModel?.let { "使用モデル: ${it.name}" } ?: "AIモデルが選択されていません",
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = if (state.selectedModel == null) {
            "設定のローカルAIモデルからモデルをダウンロードして選択してください。"
          } else {
            "要約と同じローカルモデルを使用します。会話は端末内に保存され、最新5セッションだけを保持します。"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("セッション", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      TextButton(
        onClick = {
          input = ""
          onStartNewSession()
        },
        enabled = !state.sending,
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text("新規")
      }
    }

    if (state.sessions.isNotEmpty()) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.sessions, key = { it.id }) { session ->
          FilterChip(
            selected = session.id == state.activeSessionId,
            onClick = { onSelectSession(session.id) },
            enabled = !state.sending,
            label = { Text(session.title, maxLines = 1) },
          )
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxWidth().weight(1f),
      state = listState,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.messages.isEmpty() && !state.sending) {
        item {
          Text(
            text = "質問を入力してチャットを開始します。将来、ここにRSS・ブックマークなどの参照コンテキストを接続できる構成です。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
          )
        }
      }
      items(state.messages, key = { it.id }) { message ->
        MessageBubble(message)
      }
      if (state.sending && state.responseStarted && state.streamingReply.isNotBlank()) {
        item(key = "streaming-reply") {
          MessageBubble(isUser = false, content = state.streamingReply)
        }
      }
      if (state.sending) {
        item(key = "chat-progress") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Text(
              text = state.progress?.let { chatProgressLabel(it.stage, it.modelName) } ?: "応答を生成しています…",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    state.errorText?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        enabled = !state.sending,
        placeholder = { Text("メッセージ") },
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
      )
      IconButton(
        onClick = ::sendMessage,
        enabled = input.isNotBlank() && !state.sending && state.selectedModel != null,
      ) {
        Icon(Icons.Default.Send, contentDescription = "送信")
      }
    }
  }
}

@Composable
private fun MessageBubble(message: StoredChatMessage) {
  MessageBubble(
    isUser = message.role == ChatRole.USER,
    content = message.content,
  )
}

@Composable
private fun MessageBubble(isUser: Boolean, content: String) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Surface(
      modifier = Modifier
        .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
        .widthIn(max = 360.dp),
      shape = RoundedCornerShape(14.dp),
      color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
    ) {
      Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
          text = if (isUser) "あなた" else "AI",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isUser) {
          Text(content, style = MaterialTheme.typography.bodyMedium)
        } else {
          MarkdownMessage(content)
        }
      }
    }
  }
}

private fun chatProgressLabel(stage: String, modelName: String?): String = when (stage) {
  "preparing_model" -> modelName?.let { "$it を読み込んでいます…" } ?: "モデルを読み込んでいます…"
  "generating_reply" -> modelName?.let { "$it で応答を生成しています…" } ?: "応答を生成しています…"
  else -> "応答を生成しています…"
}
