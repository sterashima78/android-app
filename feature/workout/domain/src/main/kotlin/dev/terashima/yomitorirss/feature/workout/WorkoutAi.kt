package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate

enum class WorkoutAiProvider {
  LOCAL,
  CHATGPT,
}

enum class WorkoutAiRequestType {
  MENU_SUGGESTION,
  POST_WORKOUT_REVIEW,
}

data class WorkoutAiSettings(
  val provider: WorkoutAiProvider = WorkoutAiProvider.LOCAL,
  val workoutPolicy: String = "",
)

data class WorkoutAiReview(
  val date: String,
  val generatedAt: String,
  val provider: WorkoutAiProvider,
  val content: String,
)

interface WorkoutAiSettingsRepository {
  suspend fun loadSettings(): WorkoutAiSettings
  suspend fun saveSettings(settings: WorkoutAiSettings)
  suspend fun loadMemo(date: String): String
  suspend fun saveMemo(date: String, memo: String)
  suspend fun loadMemos(dates: Set<String>): Map<String, String>
}

interface WorkoutAiReviewRepository {
  suspend fun save(review: WorkoutAiReview)
  suspend fun loadAll(): List<WorkoutAiReview>
}

interface WorkoutAiAdvisor {
  suspend fun generate(provider: WorkoutAiProvider, prompt: String): String
}

enum class WorkoutAiTaskState {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
}

data class WorkoutAiTaskSnapshot(
  val state: WorkoutAiTaskState,
  val response: String? = null,
  val error: String? = null,
)

data class WorkoutAiTaskReference(
  val requestId: String,
  val type: WorkoutAiRequestType,
)

interface WorkoutAiTaskController {
  suspend fun enqueue(type: WorkoutAiRequestType): String
  suspend fun snapshot(requestId: String): WorkoutAiTaskSnapshot
  suspend fun recoverableTask(): WorkoutAiTaskReference?
  suspend fun dismiss(requestId: String)
}

object WorkoutAiPromptBuilder {
  private const val HISTORY_DAYS = 14L

  fun build(
    type: WorkoutAiRequestType,
    snapshot: WorkoutSnapshot,
    settings: WorkoutAiSettings,
    memos: Map<String, String>,
    reviews: List<WorkoutAiReview> = emptyList(),
    today: LocalDate = LocalDate.now(),
  ): String {
    val since = today.minusDays(HISTORY_DAYS - 1)
    val recentHistory = snapshot.history
      .filter { history -> history.date.toLocalDateOrNull()?.let { !it.isBefore(since) && !it.isAfter(today) } == true }
      .sortedBy { it.date }
    val todayDate = today.toString()
    val todayHistory = recentHistory.filter { it.date == todayDate }
    val pastHistory = recentHistory.filterNot { it.date == todayDate }
    val todaySets = buildList {
      todayHistory.forEach { addAll(it.sets) }
      if (snapshot.today.date == todayDate) addAll(snapshot.today.sets)
    }.distinctBy { it.id }
    val todayMenus = buildList {
      todayHistory.mapNotNullTo(this) { it.menu }
      if (snapshot.today.date == todayDate) snapshot.today.menu?.let(::add)
    }.distinctBy { it.id to it.items }
    val exercisesById = snapshot.exercises.associateBy { it.id }
    val recentReviews = reviews
      .filter { review ->
        review.date.toLocalDateOrNull()?.let { !it.isBefore(since) && !it.isAfter(today) } == true
      }
      .sortedByDescending { it.date }

    return buildString {
      appendLine("あなたは筋力トレーニングの記録を読み、実行可能な提案を返すアシスタントです。")
      appendLine("医療診断は行わず、痛み・強い不調・異常が記載されている場合は無理な運動を勧めないでください。")
      appendLine("入力にない重量・回数・体調・RPE・休憩時間などを事実として補完しないでください。")
      appendLine("登録済み種目やプリセットは候補です。今日の予定として扱ってよいのは「今日の予定メニュー」に示された内容だけです。")
      appendLine("ワークアウトメモ、RPE、フォーム所感は主観的な記録です。実測の重量や全体負荷と同一視しないでください。")
      appendLine("運動強度や回復状況は確認できる記録の範囲で評価し、負荷条件が異なるセットの回数は単純比較しないでください。")
      appendLine("休憩秒数は利用者が記録した場合だけ事実として扱ってください。")
      appendLine()
      appendLine("## ワークアウト方針")
      appendLine(settings.workoutPolicy.ifBlank { "未設定" })
      appendLine()
      appendLine("## 登録済み種目")
      snapshot.exercises.forEach { exercise ->
        appendLine("- id=${exercise.id} / ${exercise.name} / unit=${exercise.unit.name.lowercase()} / type=${exercise.type.name.lowercase()}")
      }
      appendLine()
      appendLine("## プリセットメニュー（候補）")
      if (snapshot.menus.isEmpty()) {
        appendLine("未設定")
      } else {
        snapshot.menus.forEach { menu ->
          appendLine("### ${menu.name}")
          appendMenu(menu, exercisesById)
        }
      }
      appendLine()
      appendLine("## 直近14日間の過去記録")
      if (pastHistory.isEmpty()) appendLine("履歴なし") else pastHistory.forEach { appendHistory(it, memos[it.date], exercisesById) }
      if (type == WorkoutAiRequestType.MENU_SUGGESTION) {
        appendLine()
        appendLine("## 過去のAIレビュー（参考情報）")
        appendLine("以下は過去のAIが生成した二次情報です。現在の実績・メモ・方針と矛盾する場合は現在の一次情報を優先してください。")
        if (recentReviews.isEmpty()) {
          appendLine("なし")
        } else {
          recentReviews.forEach { review ->
            appendLine("### ${review.date}")
            appendLine(review.content)
          }
        }
      }
      appendLine()
      appendLine("## 今日 $today")
      appendLine("ワークアウトメモ: ${memos[todayDate].orEmpty().ifBlank { "なし" }}")
      appendLine("今日の予定メニュー:")
      if (todayMenus.isEmpty()) {
        appendLine("不明")
      } else {
        todayMenus.forEach { menu ->
          appendLine("### ${menu.name}")
          appendMenu(menu, exercisesById)
        }
      }
      if (todaySets.isEmpty()) appendLine("記録済みセット: なし") else {
        appendLine("記録済みセット:")
        todaySets.forEach { appendLine("- ${formatSetForAi(it)}") }
      }
      appendLine()
      when (type) {
        WorkoutAiRequestType.MENU_SUGGESTION -> {
          appendLine("## 依頼")
          appendLine("今日行うメニューを、次のJSONだけで返してください。Markdownコードフェンスや説明文は付けないでください。")
          appendLine("種目は登録済み種目を優先し、必要なら新しい種目も提案できます。今日のメモ、直近実績、過去レビューを踏まえてセット数や各セットの目標値を調整してください。")
          appendLine("""{"version":1,"name":"今日のメニュー","exercises":[{"id":"既存なら種目id","name":"種目名","unit":"reps","type":"reps","sets":[10,10,8]}]}""")
          appendLine("unit は reps または seconds、type は reps / timed / plank / step_up のいずれかを指定してください。")
          appendLine("sets は各セットの回数または秒数の配列です。配列の長さがセット数になります。")
        }
        WorkoutAiRequestType.POST_WORKOUT_REVIEW -> {
          appendLine("## 依頼")
          appendLine("今日のワークアウトをレビューしてください。")
          appendLine("「今日の予定メニュー」と「記録済みセット」を比較し、予定との差分は予定メニューに存在する項目だけについて述べてください。登録済み種目や他のプリセットにあるだけの種目を未実施扱いしないでください。")
          appendLine("負荷の評価は、記録されたセット数・回数・時間と直近14日間の同種目実績から確認できる範囲に限定してください。重量、RPE、フォーム、休憩時間等が未記録の場合は全体負荷を断定しないでください。")
          appendLine("メモの疲労感や筋肉への負荷感は主観的所感として扱い、客観的な運動強度と同一視しないでください。")
          appendLine("出力は「実績」「予定との差分」「最近の実績との比較」「所感の読み取り」「次回の調整案」「判断できない点」の順で簡潔にまとめてください。")
          appendLine("今日の記録が不足している場合は、不足していることを明示し、断定的な評価を避けてください。")
        }
      }
    }.trim()
  }

  fun recentDates(snapshot: WorkoutSnapshot, today: LocalDate = LocalDate.now()): Set<String> {
    val since = today.minusDays(HISTORY_DAYS - 1)
    return buildSet {
      add(today.toString())
      snapshot.history.forEach { history ->
        val date = history.date.toLocalDateOrNull() ?: return@forEach
        if (!date.isBefore(since) && !date.isAfter(today)) add(history.date)
      }
    }
  }

  private fun StringBuilder.appendHistory(
    history: WorkoutHistory,
    memo: String?,
    exercisesById: Map<String, WorkoutExercise>,
  ) {
    appendLine("### ${history.date}")
    appendLine("メモ: ${memo.orEmpty().ifBlank { "なし" }}")
    history.menu?.let { menu ->
      appendLine("実施時メニュー: ${menu.name}")
      appendMenu(menu, exercisesById)
    }
    history.sets.forEach { appendLine("- ${formatSetForAi(it)}") }
  }

  private fun StringBuilder.appendMenu(
    menu: WorkoutMenu,
    exercisesById: Map<String, WorkoutExercise>,
  ) {
    menu.items.forEach { item ->
      val exercise = exercisesById[item.exerciseId] ?: return@forEach
      val targetText = if (item.targets.isEmpty()) {
        "${item.targetSets}セット"
      } else {
        item.targets.joinToString(prefix = "[", postfix = "]")
      }
      appendLine("- ${exercise.name}: $targetText ${exercise.unit.label}")
    }
  }

  private fun formatSetForAi(set: WorkoutSet): String = buildString {
    append(set.exerciseName)
    append(": ${set.amount}${set.unit.label}")
    set.steps?.let { append(" / ${it}段") }
    formatWorkoutSetDetails(set).takeIf(String::isNotBlank)?.let { append(" / $it") }
    if (set.memo.isNotBlank()) append(" / セットメモ: ${set.memo}")
  }

  private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
}
