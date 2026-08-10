package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject

class GoogleDriveBackupStore(context: Context) {
  private val appContext = context.applicationContext
  private val resolver = appContext.contentResolver
  private val preferences = GoogleDriveBackupPreferences(appContext)

  fun write(json: String): String {
    validateBackup(json)

    val folderUri = preferences.status().folderUri
      ?.let(Uri::parse)
      ?: error("Google Driveのバックアップ先を設定してください")
    checkPersistedPermission(folderUri)

    val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
      folderUri,
      DocumentsContract.getTreeDocumentId(folderUri),
    )
    val fileName = autoBackupFileName()
    val documentUri = DocumentsContract.createDocument(
      resolver,
      parentDocumentUri,
      MIME_TYPE_JSON,
      fileName,
    ) ?: error("Google Driveにバックアップファイルを作成できませんでした")

    try {
      resolver.openOutputStream(documentUri, "w")
        ?.bufferedWriter(Charsets.UTF_8)
        ?.use {
          it.write(json)
          it.flush()
        }
        ?: error("Google Driveのバックアップファイルを開けませんでした")

      val saved = resolver.openInputStream(documentUri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: error("保存したバックアップを検証できませんでした")
      validateBackup(saved)
      pruneOldBackups(folderUri)
      return fileName
    } catch (error: Throwable) {
      runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
      throw error
    }
  }

  private fun checkPersistedPermission(folderUri: Uri) {
    val permission = resolver.persistedUriPermissions.firstOrNull { it.uri == folderUri }
    check(permission != null && permission.isReadPermission && permission.isWritePermission) {
      "Google Driveのフォルダ権限が失効しています。保存先を選び直してください"
    }
  }

  private fun pruneOldBackups(folderUri: Uri) {
    val documentId = DocumentsContract.getTreeDocumentId(folderUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
    val documentsByName = buildMap<String, Uri> {
      resolver.query(
        childrenUri,
        arrayOf(
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ),
        null,
        null,
        null,
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        while (cursor.moveToNext()) {
          val name = cursor.getString(nameIndex) ?: continue
          val childId = cursor.getString(idIndex) ?: continue
          put(name, DocumentsContract.buildDocumentUriUsingTree(folderUri, childId))
        }
      }
    }

    val obsolete = obsoleteAutoBackupNames(documentsByName.keys.toList())
    obsolete.forEach { name ->
      documentsByName[name]?.let { uri ->
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
      }
    }
  }

  private fun validateBackup(json: String) {
    val root = JSONObject(json)
    require(
      root.optString("format") == "yomitori-rss-backup" &&
        root.optInt("version") in SUPPORTED_BACKUP_VERSIONS,
    ) {
      "作成したバックアップの形式が正しくありません"
    }
  }

  companion object {
    private const val MIME_TYPE_JSON = "application/json"
    private val SUPPORTED_BACKUP_VERSIONS = 1..2
  }
}
