package dev.terashima.yomitorirss.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ChatRoute(
  modifier: Modifier,
  chatViewModel: ChatViewModel,
) {
  val state by chatViewModel.state.collectAsState()

  if (!state.initialized) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  AiChatScreen(
    modifier = modifier,
    state = state,
    onSelectSession = chatViewModel::selectSession,
    onStartNewSession = chatViewModel::startNewSession,
    onSendMessage = chatViewModel::sendMessage,
  )
}
