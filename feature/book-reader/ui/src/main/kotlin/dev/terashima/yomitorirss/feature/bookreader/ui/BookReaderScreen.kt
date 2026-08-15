package dev.terashima.yomitorirss.feature.bookreader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.bookreader.BookDocument
import dev.terashima.yomitorirss.feature.bookreader.BookPageImage
import dev.terashima.yomitorirss.feature.bookreader.BookPageSource
import dev.terashima.yomitorirss.feature.bookreader.ReaderMode
import dev.terashima.yomitorirss.feature.bookreader.ReadingDirection
import dev.terashima.yomitorirss.feature.bookreader.ReadingPosition
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
  document: BookDocument,
  source: BookPageSource,
  positionStore: ReadingPositionStore,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val initial = remember(document.id) { positionStore.load(document.id) }
  val pageCache = remember(source) { BookPageMemoryCache() }
  var mode by remember(document.id) { mutableStateOf(initial.mode) }
  var direction by remember(document.id) { mutableStateOf(initial.direction) }
  var currentPage by remember(document.id) {
    mutableIntStateOf(initial.pageIndex.coerceIn(0, (source.pageCount - 1).coerceAtLeast(0)))
  }
  var pageOffset by remember(document.id) { mutableIntStateOf(initial.pageOffset) }

  LaunchedEffect(currentPage, pageOffset, mode, direction) {
    positionStore.save(
      document.id,
      ReadingPosition(
        pageIndex = currentPage,
        pageOffset = pageOffset,
        mode = mode,
        direction = direction,
      ),
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "蔵書に戻る")
          }
        },
        title = {
          Text(
            document.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        actions = {
          TextButton(
            onClick = {
              mode = if (mode == ReaderMode.PAGED) ReaderMode.VERTICAL else ReaderMode.PAGED
              pageOffset = 0
            },
          ) {
            Text(if (mode == ReaderMode.PAGED) "左右" else "上下")
          }
          if (mode == ReaderMode.PAGED) {
            TextButton(
              onClick = {
                direction = if (direction == ReadingDirection.RIGHT_TO_LEFT) {
                  ReadingDirection.LEFT_TO_RIGHT
                } else {
                  ReadingDirection.RIGHT_TO_LEFT
                }
              },
            ) {
              Text(if (direction == ReadingDirection.RIGHT_TO_LEFT) "右→左" else "左→右")
            }
          }
        },
      )
    },
    bottomBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          "${currentPage + 1} / ${source.pageCount}",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      when (mode) {
        ReaderMode.PAGED -> PagedReader(
          source = source,
          pageCache = pageCache,
          initialPage = currentPage,
          direction = direction,
          onPageChanged = {
            currentPage = it
            pageOffset = 0
          },
        )

        ReaderMode.VERTICAL -> VerticalReader(
          source = source,
          pageCache = pageCache,
          initialPage = currentPage,
          initialOffset = pageOffset,
          onPositionChanged = { page, offset ->
            currentPage = page
            pageOffset = offset
          },
        )
      }
    }
  }
}

@Composable
private fun PagedReader(
  source: BookPageSource,
  pageCache: BookPageMemoryCache,
  initialPage: Int,
  direction: ReadingDirection,
  onPageChanged: (Int) -> Unit,
) {
  val pagerState = rememberPagerState(
    initialPage = initialPage.coerceIn(0, source.pageCount - 1),
    pageCount = { source.pageCount },
  )

  LaunchedEffect(initialPage) {
    if (pagerState.currentPage != initialPage) {
      pagerState.scrollToPage(initialPage.coerceIn(0, source.pageCount - 1))
    }
  }
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
  }

  HorizontalPager(
    state = pagerState,
    reverseLayout = direction == ReadingDirection.RIGHT_TO_LEFT,
    modifier = Modifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
  ) { page ->
    ReaderPage(
      source = source,
      pageCache = pageCache,
      page = page,
      vertical = false,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun VerticalReader(
  source: BookPageSource,
  pageCache: BookPageMemoryCache,
  initialPage: Int,
  initialOffset: Int,
  onPositionChanged: (Int, Int) -> Unit,
) {
  val listState = rememberLazyListState(
    initialFirstVisibleItemIndex = initialPage.coerceIn(0, source.pageCount - 1),
    initialFirstVisibleItemScrollOffset = initialOffset.coerceAtLeast(0),
  )
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .collect { (page, offset) -> onPositionChanged(page, offset) }
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
  ) {
    items((0 until source.pageCount).toList(), key = { it }) { page ->
      ReaderPage(
        source = source,
        pageCache = pageCache,
        page = page,
        vertical = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun ReaderPage(
  source: BookPageSource,
  pageCache: BookPageMemoryCache,
  page: Int,
  vertical: Boolean,
  modifier: Modifier = Modifier,
) {
  val cachedResult = remember(source, pageCache, page) {
    pageCache.get(page)?.let { Result.success(it) }
  }
  val result by produceState<Result<BookPageImage>?>(cachedResult, source, pageCache, page) {
    if (value == null) {
      value = runCatching { source.loadPage(page, PAGE_RENDER_WIDTH) }
        .onSuccess { pageCache.put(page, it) }
    }
  }
  val pageModifier = if (vertical) {
    modifier.aspectRatio(pageCache.aspectRatio(page))
  } else {
    modifier
  }

  Box(
    modifier = pageModifier,
    contentAlignment = Alignment.Center,
  ) {
    when (val current = result) {
      null -> CircularProgressIndicator(Modifier.padding(32.dp))
      else -> current.fold(
        onSuccess = { image ->
          ZoomablePageImage(
            image = image,
            modifier = Modifier.fillMaxSize(),
          )
        },
        onFailure = { error ->
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("ページを表示できませんでした")
            Text(
              error.message.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        },
      )
    }
  }
}

@Composable
private fun ZoomablePageImage(
  image: BookPageImage,
  modifier: Modifier,
) {
  val bitmap = remember(image.bytes) {
    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap()
  }
  if (bitmap == null) {
    Text("画像を読み込めませんでした")
    return
  }

  var scale by remember(image.bytes) { mutableFloatStateOf(1f) }
  var offset by remember(image.bytes) { mutableStateOf(Offset.Zero) }

  Image(
    bitmap = bitmap,
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = offset.x
        translationY = offset.y
      }
      .pointerInput(image.bytes) {
        awaitEachGesture {
          do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2 || (scale > 1f && pressed.isNotEmpty())) {
              val nextScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
              val pan = event.calculatePan()
              scale = nextScale
              offset = if (nextScale <= 1f) Offset.Zero else offset + pan
              event.changes.forEach { it.consume() }
            }
          } while (event.changes.any { it.pressed })
        }
      },
  )
}

private const val PAGE_RENDER_WIDTH = 1600
private const val MAX_ZOOM = 4f
