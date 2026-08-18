package dev.terashima.yomitorirss.feature.task.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.task.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class TaskStore(
  private val database: DatabaseConnection,
) {
  fun listTasks(): List<TaskItem> = listTasks(database.readable)

  fun createTask(title: String, description: String, parentId: String?, dueDate: LocalDate?): TaskItem = transaction { db ->
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "タスク名を入力してください" }
    if (parentId != null) require(taskExists(db, parentId)) { "親タスクが見つかりません" }

    val now = Instant.now()
    val task = TaskItem(
      id = UUID.randomUUID().toString(),
      title = normalizedTitle,
      description = description.trim(),
      parentId = parentId,
      dueDate = dueDate,
      completedAt = null,
      createdAt = now,
      sortOrder = nextSortOrder(db, parentId),
    )
    db.insertOrThrow("tasks", null, task.values())
    if (parentId != null) syncAncestors(db, parentId)
    task
  }

  fun updateTask(id: String, title: String, description: String, dueDate: LocalDate?) {
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "タスク名を入力してください" }
    database.writable.update(
      "tasks",
      ContentValues().apply {
        put("title", normalizedTitle)
        put("description", description.trim())
        if (dueDate == null) putNull("due_date") else put("due_date", dueDate.toString())
      },
      "id=?",
      arrayOf(id),
    )
  }

  fun deleteTask(id: String) = transaction { db ->
    val parentId = parentId(db, id)
    db.delete("tasks", "id=?", arrayOf(id))
    if (parentId != null) syncAncestors(db, parentId)
  }

  fun setCompleted(id: String, completed: Boolean) = transaction { db ->
    val tasks = listTasks(db)
    val byId = tasks.associateBy { it.id }
    require(byId.containsKey(id)) { "タスクが見つかりません" }
    val children = tasks.groupBy { it.parentId }
    val subtreeIds = buildList {
      fun collect(taskId: String) {
        add(taskId)
        children[taskId].orEmpty().forEach { collect(it.id) }
      }
      collect(id)
    }
    val completedAt = if (completed) Instant.now().toString() else null
    subtreeIds.forEach { taskId ->
      db.update(
        "tasks",
        ContentValues().apply {
          if (completedAt == null) putNull("completed_at") else put("completed_at", completedAt)
        },
        "id=?",
        arrayOf(taskId),
      )
    }
    byId[id]?.parentId?.let { syncAncestors(db, it) }
  }

  private fun syncAncestors(db: SQLiteDatabase, startId: String) {
    var currentId: String? = startId
    val now = Instant.now().toString()
    while (currentId != null) {
      val id = currentId
      val counts = db.rawQuery(
        "SELECT COUNT(*), SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) FROM tasks WHERE parent_id=?",
        arrayOf(id),
      ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0) to cursor.getInt(1)
      }
      if (counts.first > 0) {
        val allChildrenCompleted = counts.first == counts.second
        db.update(
          "tasks",
          ContentValues().apply {
            if (allChildrenCompleted) put("completed_at", now) else putNull("completed_at")
          },
          "id=?",
          arrayOf(id),
        )
      }
      currentId = parentId(db, id)
    }
  }

  private fun listTasks(db: SQLiteDatabase): List<TaskItem> = db.rawQuery(
    "SELECT id,title,description,parent_id,due_date,completed_at,created_at,sort_order FROM tasks ORDER BY sort_order,created_at",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          TaskItem(
            id = cursor.getString(0),
            title = cursor.getString(1),
            description = cursor.getString(2),
            parentId = cursor.getStringOrNull(3),
            dueDate = cursor.getStringOrNull(4)?.let(LocalDate::parse),
            completedAt = cursor.getStringOrNull(5)?.let(Instant::parse),
            createdAt = Instant.parse(cursor.getString(6)),
            sortOrder = cursor.getLong(7),
          ),
        )
      }
    }
  }

  private fun nextSortOrder(db: SQLiteDatabase, parentId: String?): Long {
    val (sql, args) = if (parentId == null) {
      "SELECT COALESCE(MAX(sort_order),-1)+1 FROM tasks WHERE parent_id IS NULL" to null
    } else {
      "SELECT COALESCE(MAX(sort_order),-1)+1 FROM tasks WHERE parent_id=?" to arrayOf(parentId)
    }
    return db.rawQuery(sql, args).use { cursor ->
      cursor.moveToFirst()
      cursor.getLong(0)
    }
  }

  private fun parentId(db: SQLiteDatabase, id: String): String? = db.rawQuery(
    "SELECT parent_id FROM tasks WHERE id=?",
    arrayOf(id),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getStringOrNull(0) else null }

  private fun taskExists(db: SQLiteDatabase, id: String): Boolean = db.rawQuery(
    "SELECT 1 FROM tasks WHERE id=? LIMIT 1",
    arrayOf(id),
  ).use { it.moveToFirst() }

  private fun TaskItem.values() = ContentValues().apply {
    put("id", id)
    put("title", title)
    put("description", description)
    if (parentId == null) putNull("parent_id") else put("parent_id", parentId)
    if (dueDate == null) putNull("due_date") else put("due_date", dueDate.toString())
    if (completedAt == null) putNull("completed_at") else put("completed_at", completedAt.toString())
    put("created_at", createdAt.toString())
    put("sort_order", sortOrder)
  }

  private fun <T> transaction(block: (SQLiteDatabase) -> T): T = database.transaction {
    block(this)
  }

  private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)
}
