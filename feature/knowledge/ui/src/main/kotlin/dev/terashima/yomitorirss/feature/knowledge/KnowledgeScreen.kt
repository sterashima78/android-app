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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
  modifier: Modifier = Modifier,
) {
  val selected = state.selectedPage
  if (selected != null) {
    KnowledgePageDetail(
      page = selected,
      onBack = onClosePage,
      modifier = modifier,
    )
    return
  }

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
      IconButton(onClick = onRebuild, enabled = !state.building) {
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
      state.pages.isEmpty() -> EmptyKnowledge(onRebuild = onRebuild, building = state.building)
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
                text = "出典 ${page.sourceCount}件 · ${page.generatedAt}",
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
  building: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("まだWikiページがありません", style = MaterialTheme.typography.titleMedium)
    Text(
      text = "要約済みのブックマークを、タグ・フォルダ・提供元ごとに統合してナレッジページを生成します。",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onRebuild, enabled = !building) {
      Icon(Icons.Default.Refresh, contentDescription = null)
      Text("ナレッジを構築", modifier = Modifier.padding(start = 8.dp))
    }
  }
}

@Composable
private fun KnowledgePageDetail(
  page: KnowledgePage,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  LazyColumn(
    modifier = modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TextButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = null)
        Text("一覧へ")
      }
    }
    item {
      Text(page.title, style = MaterialTheme.typography.headlineSmall)
      Text(
        text = "出典 ${page.sourceCount}件 · ${page.generatedAt}",
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
    item { Spacer(Modifier.height(24.dp)) }
  }
}
