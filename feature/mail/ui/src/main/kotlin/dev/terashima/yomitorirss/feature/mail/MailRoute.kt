package dev.terashima.yomitorirss.feature.mail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MailRoute(
  modifier: Modifier,
  mailViewModel: MailViewModel,
  onAddAccount: () -> Unit,
) {
  val state by mailViewModel.state.collectAsState()

  if (!state.initialized) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  MailScreen(
    modifier = modifier,
    state = state,
    onAddAccount = onAddAccount,
    onRemoveSelectedAccount = mailViewModel::removeSelectedAccount,
    onSelectAccount = mailViewModel::selectAccount,
    onSelectMailbox = mailViewModel::selectMailbox,
    onUpdateQuery = mailViewModel::updateQuery,
    onSearch = mailViewModel::search,
    onRefresh = mailViewModel::refresh,
    onOpenThread = mailViewModel::openThread,
    onCloseThread = mailViewModel::closeThread,
    onToggleRead = mailViewModel::toggleRead,
    onToggleStarred = mailViewModel::toggleStarred,
    onToggleReadLater = mailViewModel::toggleReadLater,
    onArchive = mailViewModel::archive,
    onTrash = mailViewModel::trash,
    onApplyLabel = mailViewModel::applyLabel,
    onDismissMessage = mailViewModel::dismissMessage,
  )
}
