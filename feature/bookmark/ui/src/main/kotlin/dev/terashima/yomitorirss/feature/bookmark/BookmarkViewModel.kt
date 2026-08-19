package dev.terashima.yomitorirss.feature.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BookmarkUiState(
  val initialized: Boolean = false,
  val saved: List<BookmarkedArticle> = emptyList(),
  val bookmarkDetails: Map<String, BookmarkedArticle> = emptyMap(),
  val folders: List<BookmarkFolder> = emptyList(),
  val tags: List<Tag> = emptyList(),
  val selectedFolderId: String? = null,
  val selectedTagId: String? = null,
  val hiddenArticleIds: Set<String> = emptySet(),
  val message: String? = null,
  val importCompleted: Boolean = false,
)

class BookmarkViewModel(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val imports: BookmarkImportRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(BookmarkUiState())
  val state: StateFlow<BookmarkUiState> = _state.asStateFlow()
  private val reloadMutex = Mutex()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      articleRepository.changes.collect { reload() }
    }
    viewModelScope.launch(Dispatchers.IO) {
      bookmarkRepository.changes.collect { reload() }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) { reload() }
  }

  fun selectTag(tagId: String?) {
    _state.update { it.copy(selectedTagId = tagId) }
    viewModelScope.launch(Dispatchers.IO) { reload() }
  }

  fun selectFolder(folderId: String?) {
    _state.update { it.copy(selectedFolderId = folderId) }
    viewModelScope.launch(Dispatchers.IO) { reload() }
  }

  fun unsave(article: Article) = performArticleAction(article) {
    bookmarkRepository.unsaveArticle(article.id)
  }

  fun setArticleContentType(article: Article, contentType: ContentType?) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { articleRepository.setArticleContentType(article.id, contentType) }
        .onSuccess {
          reload()
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "コンテンツ種別を変更しました") }
        }
        .onFailure(::showError)
    }
  }

  fun createFolder(name: String) = mutateBookmark(successMessage = "フォルダを作成しました") {
    bookmarkRepository.createFolder(name)
  }

  fun renameFolder(folder: BookmarkFolder, name: String) = mutateBookmark {
    bookmarkRepository.renameFolder(folder.id, name)
  }

  fun deleteFolder(folder: BookmarkFolder) = mutateBookmark(
    successMessage = "フォルダを削除し、中の記事を未分類へ移動しました",
  ) {
    bookmarkRepository.deleteFolder(folder.id)
  }

  fun moveArticleToFolder(article: Article, folderId: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { bookmarkRepository.moveArticleToFolder(article.id, folderId) }
        .onSuccess {
          reload()
          backupChangeScheduler.scheduleAfterChange()
          val folderName = when (folderId) {
            null, UNCATEGORIZED_FOLDER_ID -> "未分類"
            else -> _state.value.folders.firstOrNull { it.id == folderId }?.name ?: "フォルダ"
          }
          _state.update { it.copy(message = "$folderName へ移動しました") }
        }
        .onFailure(::showError)
    }
  }

  fun createTag(name: String) = mutateBookmark(successMessage = "タグを作成しました") {
    bookmarkRepository.createTag(name)
  }

  fun renameTag(tag: Tag, name: String) = mutateBookmark {
    bookmarkRepository.renameTag(tag.id, name)
  }

  fun deleteTag(tag: Tag) = mutateBookmark {
    bookmarkRepository.deleteTag(tag.id)
  }

  fun deleteUnusedTags() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { bookmarkRepository.deleteUnusedTags() }
        .onSuccess { deletedCount ->
          reload()
          if (deletedCount > 0) backupChangeScheduler.scheduleAfterChange()
          val message = if (deletedCount > 0) {
            "未使用のタグを${deletedCount}件削除しました"
          } else {
            "削除対象のタグはありません"
          }
          _state.update { it.copy(message = message) }
        }
        .onFailure(::showError)
    }
  }

  fun replaceArticleTags(article: Article, tagIds: Set<String>) = mutateBookmark {
    bookmarkRepository.replaceArticleTags(article.id, tagIds)
  }

  fun importCsv(documentUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { imports.importBookmarkCsv(documentUri) }
        .onSuccess { result -> completeImport(result.added, result.duplicates, result.skipped) }
        .onFailure { error ->
          _state.update { it.copy(message = "ブックマークをインポートできませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun importHtml(documentUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { imports.importBookmarkHtml(documentUri) }
        .onSuccess { result -> completeImport(result.added, result.duplicates, result.skipped) }
        .onFailure { error ->
          _state.update { it.copy(message = "ブックマークをインポートできませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun consumeImportCompleted() {
    _state.update { it.copy(importCompleted = false) }
  }

  private suspend fun completeImport(added: Int, duplicates: Int, skipped: Int) {
    if (added > 0) backupChangeScheduler.scheduleAfterChange()
    _state.update { it.copy(selectedTagId = null, selectedFolderId = null) }
    reload()
    _state.update {
      it.copy(
        message = "${added}件をインポートしました（重複 ${duplicates}件、スキップ ${skipped}件）",
        importCompleted = true,
      )
    }
  }

  private fun performArticleAction(article: Article, action: suspend () -> Unit) {
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + article.id) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }
        .onSuccess {
          reload()
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds - article.id) }
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              hiddenArticleIds = it.hiddenArticleIds - article.id,
              message = "操作を反映できなかったため元に戻しました: ${error.userMessage()}",
            )
          }
        }
    }
  }

  private fun mutateBookmark(successMessage: String? = null, action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }
        .onSuccess {
          reload()
          backupChangeScheduler.scheduleAfterChange()
          if (successMessage != null) _state.update { it.copy(message = successMessage) }
        }
        .onFailure(::showError)
    }
  }

  private suspend fun reload() {
    reloadMutex.withLock {
      val selectedTag = _state.value.selectedTagId
      val selectedFolder = _state.value.selectedFolderId
      val tags = bookmarkRepository.listTags()
      val folders = bookmarkRepository.listFolders()
      val validSelectedTag = selectedTag?.takeIf { id -> tags.any { it.id == id } }
      val validSelectedFolder = when {
        selectedFolder == null -> null
        selectedFolder == UNCATEGORIZED_FOLDER_ID -> selectedFolder
        folders.any { it.id == selectedFolder } -> selectedFolder
        else -> null
      }
      // listSavedArticles is intentionally capped for the article list. Tag counts/details need the full set.
      val bookmarkDetails = bookmarkRepository.listAllSavedArticles()
      val saved = bookmarkRepository.listSavedArticles(validSelectedTag, validSelectedFolder)
      _state.update {
        it.copy(
          initialized = true,
          saved = saved,
          bookmarkDetails = bookmarkDetails.associateBy { bookmarked -> bookmarked.article.id },
          folders = folders,
          tags = tags,
          selectedFolderId = validSelectedFolder,
          selectedTagId = validSelectedTag,
        )
      }
    }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(message = error.userMessage()) }
  }

  class Factory(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val imports: BookmarkImportRepository,
    private val backupChangeScheduler: BackupChangeScheduler,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(BookmarkViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return BookmarkViewModel(articleRepository, bookmarkRepository, imports, backupChangeScheduler) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
