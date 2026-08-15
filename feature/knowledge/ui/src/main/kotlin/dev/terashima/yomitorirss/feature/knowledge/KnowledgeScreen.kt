package dev.terashima.yomitorirss.feature.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.MarkdownText

@Composable
fun KnowledgeScreen(
  state: KnowledgeUiState,
  onQueryChange: (String) -> Unit,
  onOpenPage: (String) -> Unit,
  onClosePage: () -> Unit,
  onRebuild: () -> Unit,
  onStartCreate: () -> Unit,
  onStartRelatedCreate: (String) -> Unit,
  onComposerRequestChange: (String) -> Unit,
  onCreatePage: () -> Unit,
  onCancelCreate: () -> Unit,
  onEditInstructionChange: (String) -> Unit,
  onEditPage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selected = state.selectedPage
  if (selected != null) {
    KnowledgePageDetail(
      page = selected,
      editInstruction = state.editInstruction,
      working = state.working,
      message = state.message,
      onBack = onClosePage,
      onStartRelatedCreate = { onStartRelatedCreate(selected.id) },
      onEditInstructionChange = onEditInstructionChange,
      onEditPage = onEditPage,
      modifier = modifier,
    )
  } else {
    KnowledgePageList(
      state = state,
      onQueryChange = onQueryChange,
      onOpenPage = onOpenPage,
      onRebuild = onRebuild,
      onStartCreate = onStartCreate,
      modifier = modifier,
    )
  }

  if (state.composerOpen) {
    KnowledgeCreateDialog(
      request = state.composerRequest,
      related = state.composerSourcePageId != null,
      working = state.working,
      message = state.message,
      onRequestChange = onComposerRequestChange,
      onCreate = onCreatePage,
      onCancel = onCancelCreate,
    )
  }
}

@Composable
private fun KnowledgePageList(
  state: KnowledgeUiState,
  onQueryChange: (String) -> Unit,
  onOpenPage: (String) -> Unit,
  onRebuild: () -> Unit,
  onStartCreate: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        label = { Text("Wikiを検索") },
      )
      IconButton(onClick = onStartCreate, enabled = !state.building && !state.working) {
        Icon(Icons.Default.Add, contentDescription = "Wiki記事を作成")
      }
      IconButton(onClick = onRebuild, enabled = !state.building && !state.working) {
        Icon(Icons.Default.Refresh, contentDescription = "ナレッジを再構築")
      }
    }

    if (state.building) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      Text(
        text = "保存済み要約からWikiを更新しています",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    state.message?.let { message ->
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    state.lastBuild?.let { result ->
      Text(
        text = buildString {
          append("生成 ${result.generated}件 / 再利用 ${result.reused}件")
          if (result.pending > 0) append(" / 次回 ${result.pending}件")
          if (result.skippedWithoutSummary > 0) append(" / 要約待ち ${result.skippedWithoutSummary}件")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    when {
      !state.initialized -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
      state.pages.isEmpty() -> EmptyKnowledge(
        onRebuild = onRebuild,
        onCreate = onStartCreate,
        busy = state.building || state.working,
      )
      else -> LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(state.pages, key = KnowledgePageSummary::id) { page ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenPage(page.id) },
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(page.title, style = MaterialTheme.typography.titleMedium)
              Text(
                text = buildString {
                  if (page.editorManaged) append("LLM編集 · ")
                  append("出典 ${page.sourceCount}件 · ${page.generatedAt}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        item { Spacer(Modifier.height(16.dp)) }
      }
    }
  }
}

@Composable
private fun EmptyKnowledge(
  onRebuild: () -> Unit,
  onCreate: () -> Unit,
  busy: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("まだWikiページがありません", style = MaterialTheme.typography.titleMedium)
    Text(
      text = "作りたい記事をLLM編集者へ依頼するか、要約済みブックマークから自動Wikiを構築できます。",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onCreate, enabled = !busy) {
      Icon(Icons.Default.AutoAwesome, contentDescription = null)
      Text("記事を作成", modifier = Modifier.padding(start = 8.dp))
    }
    TextButton(onClick = onRebuild, enabled = !busy) {
      Icon(Icons.Default.Refresh, contentDescription = null)
      Text("自動Wikiを構築", modifier = Modifier.padding(start = 8.dp))
    }
  }
}

@Composable
private fun KnowledgePageDetail(
  page: KnowledgePage,
  editInstruction: String,
  working: Boolean,
  message: String?,
  onBack: () -> Unit,
  onStartRelatedCreate: () -> Unit,
  onEditInstructionChange: (String) -> Unit,
  onEditPage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  LazyColumn(
    modifier = modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onBack, enabled = !working) {
          Icon(Icons.Default.ArrowBack, contentDescription = null)
          Text("一覧へ")
        }
        TextButton(onClick = onStartRelatedCreate, enabled = !working) {
          Icon(Icons.Default.Add, contentDescription = null)
          Text("この記事から新規")
        }
      }
    }
    item {
      Text(page.title, style = MaterialTheme.typography.headlineSmall)
      Text(
        text = buildString {
          if (page.editorManaged) append("LLM編集管理 · ")
          append("出典 ${page.sourceCount}件 · ${page.generatedAt}")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    item { MarkdownText(content = page.bodyMarkdown) }
    item {
      HorizontalDivider()
      Text(
        text = "出典",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
      )
    }
    items(page.sources, key = KnowledgeSource::articleId) { source ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(enabled = source.url.isNotBlank()) { uriHandler.openUri(source.url) },
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Text("[${source.citationNumber}]", style = MaterialTheme.typography.labelLarge)
          Column(modifier = Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.bodyMedium)
            Text(
              source.sourceTitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (source.url.isNotBlank()) {
            Icon(Icons.Default.OpenInNew, contentDescription = "出典を開く")
          }
        }
      }
    }
    item {
      HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
      Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Text("LLM Editor", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            "記事を直接編集せず、変更したい内容をLLMへ指示します。必要に応じて関連する要約済み資料も再検索します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = editInstruction,
            onValueChange = onEditInstructionChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !working,
            minLines = 3,
            maxLines = 8,
            label = { Text("編集内容を指示") },
            placeholder = { Text("例: E4Bとの比較を追加して。結論を短くして。") },
          )
          if (working) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Text("LLMが記事を編集中です", style = MaterialTheme.typography.bodySmall)
            }
          }
          message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
          Button(
            onClick = onEditPage,
            enabled = !working && editInstruction.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
          ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Text("編集を依頼", modifier = Modifier.padding(start = 8.dp))
          }
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun KnowledgeCreateDialog(
  request: String,
  related: Boolean,
  working: Boolean,
  message: String?,
  onRequestChange: (String) -> Unit,
  onCreate: () -> Unit,
  onCancel: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!working) onCancel() },
    title = {
      Text(if (related) "この記事を元に新しい記事" else "新しいWiki記事")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          if (related) {
            "現在の記事の出典を優先しつつ、依頼内容に関連する資料を追加検索して別の記事を作成します。"
          } else {
            "作りたい記事を自然言語で指示してください。要約済みブックマークから関連資料を選び、出典付きの記事を作成します。"
          },
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = request,
          onValueChange = onRequestChange,
          modifier = Modifier.fillMaxWidth(),
          enabled = !working,
          minLines = 4,
          maxLines = 10,
          label = { Text("どのような記事を作りますか？") },
          placeholder = { Text("例: Pixel 9でGemma 4を使う実用性を、RAM・速度・モデルサイズの観点からまとめて") },
        )
        if (working) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("資料を選び、記事を生成しています", style = MaterialTheme.typography.bodySmall)
          }
        }
        message?.let {
          Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onCreate, enabled = !working && request.isNotBlank()) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Text("作成", modifier = Modifier.padding(start = 6.dp))
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel, enabled = !working) {
        Text("キャンセル")
      }
    },
  )
}
