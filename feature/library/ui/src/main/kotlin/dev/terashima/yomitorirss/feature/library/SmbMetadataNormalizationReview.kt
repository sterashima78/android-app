@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
internal fun SmbMetadataNormalizationSettingsSection(
  enabled: Boolean,
  busy: Boolean,
  snapshot: SmbMetadataNormalizationBatchSnapshot?,
  onStart: () -> Unit,
  onApply: (String, String, SmbBookMetadataProposal) -> Unit,
  onDefer: (String) -> Unit,
  onReject: (String) -> Unit,
  onReopen: (String) -> Unit,
  onRetry: (String) -> Unit,
) {
  var reviewVisible by remember { mutableStateOf(false) }
  if (reviewVisible && snapshot != null) {
    SmbMetadataNormalizationReviewDialog(
      snapshot = snapshot,
      busy = busy,
      onApply = onApply,
      onDefer = onDefer,
      onReject = onReject,
      onReopen = onReopen,
      onRetry = onRetry,
      onDismiss = { reviewVisible = false },
    )
  }

  Column(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    HorizontalDivider()
    Text("書誌・ファイル名の正規化", style = MaterialTheme.typography.titleMedium)
    Text(
      "ファイルサーバ由来の未確定書籍について、現在のファイル名と取得済み表紙を端末内AIへ入力し、書誌情報と正規化ファイル名を提案します。ファイル名はレビューで反映するまで変更しません。反映済み・却下済みの書籍は次回の一括解析から除外されます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    snapshot?.let { batch ->
      Text(
        "全${batch.total} ・ 表紙待ち${batch.waitingForCover} ・ 解析待ち${batch.queued} ・ 解析中${batch.processing} ・ 未確認${batch.pendingReview} ・ 保留${batch.deferred} ・ 反映${batch.applied} ・ 却下${batch.rejected} ・ 失敗/対象外${batch.failed}",
        style = MaterialTheme.typography.bodySmall,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        enabled = enabled && !busy && snapshot?.hasActiveWork != true && snapshot?.hasUnresolvedReview != true,
        onClick = onStart,
      ) {
        Text("未確定書籍を一括解析")
      }
      if (snapshot?.items?.isNotEmpty() == true) {
        TextButton(
          enabled = !busy,
          onClick = { reviewVisible = true },
        ) { Text("候補をレビュー") }
      }
    }

    snapshot?.takeIf { it.hasUnresolvedReview }?.let {
      Text(
        "未確認・保留・失敗の候補を仕分けすると、次の一括解析を開始できます。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SmbMetadataNormalizationReviewDialog(
  snapshot: SmbMetadataNormalizationBatchSnapshot,
  busy: Boolean,
  onApply: (String, String, SmbBookMetadataProposal) -> Unit,
  onDefer: (String) -> Unit,
  onReject: (String) -> Unit,
  onReopen: (String) -> Unit,
  onRetry: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var filter by remember { mutableStateOf(SmbNormalizationReviewFilter.PENDING) }
  var editing by remember { mutableStateOf<SmbMetadataNormalizationItem?>(null) }

  editing?.let { item ->
    SmbMetadataCandidateEditDialog(
      item = item,
      onDismiss = { editing = null },
      onApply = { fileName, proposal ->
        onApply(item.sourceId, fileName, proposal)
        editing = null
      },
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        TopAppBar(
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.ArrowBack, contentDescription = "蔵書設定へ戻る")
            }
          },
          title = { Text("書誌正規化レビュー") },
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SmbNormalizationReviewFilter.entries.forEach { candidateFilter ->
            FilterChip(
              selected = filter == candidateFilter,
              onClick = { filter = candidateFilter },
              label = {
                Text(
                  "${candidateFilter.label} ${snapshot.items.count(candidateFilter::matches)}",
                  maxLines = 1,
                )
              },
            )
          }
        }

        val visibleItems = snapshot.items.filter(filter::matches)
        if (visibleItems.isEmpty()) {
          Text(
            "この分類の候補はありません。",
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(
              items = visibleItems,
              key = { "${it.batchId}:${it.sourceId}:${it.status}" },
            ) { item ->
              SmbMetadataNormalizationReviewCard(
                item = item,
                busy = busy,
                onApply = {
                  val fileName = item.proposedFileName
                  val proposal = item.proposal
                  if (fileName != null && proposal != null) onApply(item.sourceId, fileName, proposal)
                },
                onEdit = { editing = item },
                onDefer = { onDefer(item.sourceId) },
                onReject = { onReject(item.sourceId) },
                onReopen = { onReopen(item.sourceId) },
                onRetry = { onRetry(item.sourceId) },
                modifier = Modifier.padding(horizontal = 12.dp),
              )
            }
            item { Spacer(Modifier.height(20.dp)) }
          }
        }
      }
    }
  }
}

@Composable
private fun SmbMetadataNormalizationReviewCard(
  item: SmbMetadataNormalizationItem,
  busy: Boolean,
  onApply: () -> Unit,
  onEdit: () -> Unit,
  onDefer: () -> Unit,
  onReject: () -> Unit,
  onReopen: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        AsyncImage(
          model = item.coverUrl,
          contentDescription = "書籍表紙",
          modifier = Modifier.size(width = 82.dp, height = 116.dp),
          contentScale = ContentScale.Crop,
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(item.status.label, style = MaterialTheme.typography.labelMedium)
          Text("現在", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            item.originalFileName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          item.proposedFileName?.let { proposed ->
            Text("提案", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              proposed,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      item.proposal?.let { proposal ->
        MetadataLine("タイトル", proposal.title)
        MetadataLine("著者", proposal.authors.joinToString(" / ").ifBlank { null })
        MetadataLine("シリーズ", seriesLabel(proposal))
        MetadataLine("出版社", proposal.publisher)
        MetadataLine("発売日", proposal.publishedDate)
        MetadataLine("ISBN-13", proposal.isbn13)
        MetadataLine("ISBN-10", proposal.isbn10)
        proposal.confidence?.let { confidence ->
          MetadataLine("AI確信度", String.format(Locale.ROOT, "%.0f%%", confidence * 100f))
        }
        proposal.reason?.takeIf(String::isNotBlank)?.let { reason ->
          Text(
            reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      item.error?.takeIf(String::isNotBlank)?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }

      when (item.status) {
        SmbMetadataNormalizationStatus.PENDING_REVIEW -> {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              enabled = !busy && item.proposedFileName != null && item.proposal != null,
              onClick = onApply,
            ) { Text("反映") }
            TextButton(enabled = !busy && item.proposal != null, onClick = onEdit) { Text("編集して反映") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = !busy, onClick = onDefer) { Text("保留") }
            TextButton(enabled = !busy, onClick = onRetry) { Text("再解析") }
            TextButton(enabled = !busy, onClick = onReject) { Text("却下") }
          }
        }

        SmbMetadataNormalizationStatus.DEFERRED -> {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = !busy, onClick = onReopen) { Text("未確認へ戻す") }
            TextButton(enabled = !busy && item.proposal != null, onClick = onEdit) { Text("編集して反映") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = !busy, onClick = onRetry) { Text("再解析") }
            TextButton(enabled = !busy, onClick = onReject) { Text("却下") }
          }
        }

        SmbMetadataNormalizationStatus.FAILED,
        SmbMetadataNormalizationStatus.SKIPPED,
        -> {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = !busy, onClick = onRetry) { Text("再解析") }
            TextButton(enabled = !busy, onClick = onReject) { Text("却下して確定") }
          }
        }

        SmbMetadataNormalizationStatus.REJECTED -> {
          TextButton(enabled = !busy, onClick = onRetry) { Text("再解析") }
        }

        SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
        SmbMetadataNormalizationStatus.QUEUED,
        SmbMetadataNormalizationStatus.PROCESSING,
        SmbMetadataNormalizationStatus.APPLIED,
        -> Unit
      }
    }
  }
}

@Composable
private fun MetadataLine(label: String, value: String?) {
  value?.takeIf(String::isNotBlank)?.let {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        label,
        modifier = Modifier.size(width = 72.dp, height = 22.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun SmbMetadataCandidateEditDialog(
  item: SmbMetadataNormalizationItem,
  onDismiss: () -> Unit,
  onApply: (String, SmbBookMetadataProposal) -> Unit,
) {
  val source = item.proposal ?: return
  var fileName by remember(item) { mutableStateOf(item.proposedFileName.orEmpty()) }
  var title by remember(item) { mutableStateOf(source.title) }
  var authors by remember(item) { mutableStateOf(source.authors.joinToString("\n")) }
  var publisher by remember(item) { mutableStateOf(source.publisher.orEmpty()) }
  var publishedDate by remember(item) { mutableStateOf(source.publishedDate.orEmpty()) }
  var isbn13 by remember(item) { mutableStateOf(source.isbn13.orEmpty()) }
  var isbn10 by remember(item) { mutableStateOf(source.isbn10.orEmpty()) }
  var seriesName by remember(item) { mutableStateOf(source.seriesName.orEmpty()) }
  var seriesPosition by remember(item) { mutableStateOf(source.seriesPosition?.toString().orEmpty()) }
  val position = seriesPosition.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
  val valid = fileName.isNotBlank() && title.isNotBlank() &&
    (seriesPosition.isBlank() || position != null && position > 0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("候補を編集して反映") },
    text = {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          OutlinedTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("変更後ファイル名") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("タイトル") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = authors,
            onValueChange = { authors = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("著者（改行区切り）") },
            minLines = 2,
          )
        }
        item {
          OutlinedTextField(
            value = seriesName,
            onValueChange = { seriesName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("シリーズ") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = seriesPosition,
            onValueChange = { seriesPosition = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("巻数") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = publisher,
            onValueChange = { publisher = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("出版社") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = publishedDate,
            onValueChange = { publishedDate = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("発売日") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = isbn13,
            onValueChange = { isbn13 = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ISBN-13") },
            singleLine = true,
          )
        }
        item {
          OutlinedTextField(
            value = isbn10,
            onValueChange = { isbn10 = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ISBN-10") },
            singleLine = true,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = {
          onApply(
            fileName,
            source.copy(
              title = title.trim(),
              authors = authors.lines().map(String::trim).filter(String::isNotEmpty),
              publisher = publisher.trim().takeIf(String::isNotEmpty),
              publishedDate = publishedDate.trim().takeIf(String::isNotEmpty),
              isbn13 = isbn13.trim().takeIf(String::isNotEmpty),
              isbn10 = isbn10.trim().takeIf(String::isNotEmpty),
              seriesName = seriesName.trim().takeIf(String::isNotEmpty),
              seriesPosition = position,
            ),
          )
        },
      ) { Text("反映") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

private fun seriesLabel(proposal: SmbBookMetadataProposal): String? {
  val name = proposal.seriesName?.takeIf(String::isNotBlank)
  val position = proposal.seriesPosition
  return when {
    name != null && position != null -> "$name / 第${position}巻"
    name != null -> name
    position != null -> "第${position}巻"
    else -> null
  }
}

private enum class SmbNormalizationReviewFilter(val label: String) {
  PENDING("未確認"),
  DEFERRED("保留"),
  ACTIVE("解析中"),
  FAILED("失敗"),
  APPLIED("反映済み"),
  REJECTED("却下"),
  ALL("すべて"),
  ;

  fun matches(item: SmbMetadataNormalizationItem): Boolean = when (this) {
    PENDING -> item.status == SmbMetadataNormalizationStatus.PENDING_REVIEW
    DEFERRED -> item.status == SmbMetadataNormalizationStatus.DEFERRED
    ACTIVE -> item.status in setOf(
      SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
      SmbMetadataNormalizationStatus.QUEUED,
      SmbMetadataNormalizationStatus.PROCESSING,
    )
    FAILED -> item.status == SmbMetadataNormalizationStatus.FAILED ||
      item.status == SmbMetadataNormalizationStatus.SKIPPED
    APPLIED -> item.status == SmbMetadataNormalizationStatus.APPLIED
    REJECTED -> item.status == SmbMetadataNormalizationStatus.REJECTED
    ALL -> true
  }
}
