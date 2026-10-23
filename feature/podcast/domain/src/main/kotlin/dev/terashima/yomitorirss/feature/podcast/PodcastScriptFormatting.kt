package dev.terashima.yomitorirss.feature.podcast

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun buildPodcastChapterPrompt(
  programName: String,
  article: PodcastEpisodeArticle,
  chapterNumber: Int,
  totalChapters: Int,
): String = buildPodcastChapterPrompt(programName, listOf(article), chapterNumber, totalChapters)

fun buildPodcastChapterPrompt(
  programName: String,
  articles: List<PodcastEpisodeArticle>,
  chapterNumber: Int,
  totalChapters: Int,
  mode: PodcastClusteringMode = PodcastClusteringMode.EVENT,
): String {
  require(articles.isNotEmpty()) { "articles must not be empty" }
  require(chapterNumber in 1..totalChapters) { "chapterNumber must be within totalChapters" }
  val targetChars = if (mode == PodcastClusteringMode.TOPIC) {
    (articles.size * 140).coerceIn(300, 1_800)
  } else (PODCAST_TARGET_SCRIPT_CHARS / totalChapters).coerceIn(60, 500)
  val clusteringInstruction = if (mode == PodcastClusteringMode.TOPIC) {
    "以下は関連するトピックを扱う記事です。同じ出来事とは限りません。異なる出来事・発表・日付・意見は混同せず、各記事の主要な事実を必ず個別に取り上げてください。共通部分だけ重複を省き、関連性をつないで説明してください。"
  } else {
    "以下は同じ具体的なニュースを報じた記事です。入力記事だけを根拠に、重複内容を繰り返さず1つの日本語音声チャプターへ統合してください。"
  }
  val articleText = articles.mapIndexed { index, article ->
    buildString {
      appendLine("記事${index + 1}:")
      appendLine("タイトル: ${article.title}")
      article.sourceTitle?.takeIf(String::isNotBlank)?.let { appendLine("情報源: $it") }
      appendLine("本文:")
      append(article.feedContent.trim())
    }
  }.joinToString("\n---\n")

  return """
    あなたはニュース音声番組「$programName」の原稿編集者です。
    $clusteringInstruction

    出力形式:
    - 1行目は必ず [[TITLE:日本語の見出し]] とする。
    - 2行目以降に読み上げ本文だけを書く。
    - 2行目以降では見出しを繰り返さず、「タイトル」「見出し」などのラベルも付けない。
    - 見出しは元記事のタイトルをそのまま複製せず、入力内容だけを根拠に音声ニュースとして自然な日本語へ翻訳・言い換える。
    - 見出しは40文字程度までを目安に簡潔にし、入力にない事実を追加しない。

    必須条件:
    - 複数記事に共通する内容は一度だけ説明する。
    - 各記事にしかない情報は、互いに矛盾しない範囲で統合する。
    - 記事間で内容が食い違う場合は断定せず、一致している事実を優先する。
    - 外部ページ、リンク先、一般知識から情報を補わない。
    - 入力が外国語なら内容を日本語で自然に要約する。
    - 何が起きたか、なぜ重要かを簡潔に説明する。
    - 推測と入力に明記された事実を混同しない。
    - TITLEマーカー以外のMarkdown見出し、箇条書き、URL、チャプターマーカーは出力しない。
    - 番組全体の冒頭挨拶や締めの挨拶は出力しない。
    - 記号、略語、数字の羅列は、意味を変えない範囲で音声合成が自然に読める日本語表現へ言い換える。
    - ${targetChars}文字程度を目安に、重要な内容を優先して簡潔にまとめる。

    チャプター: $chapterNumber / $totalChapters
    入力記事:
    ---
    $articleText
  """.trimIndent()
}

fun buildPodcastEpisodeScript(programName: String, articles: List<PodcastEpisodeArticle>): String =
  buildPodcastEpisodeScriptFromChapters(programName, articles.map { listOf(it) })

internal fun buildPodcastEpisodeScriptFromChapters(
  programName: String,
  chapters: List<List<PodcastEpisodeArticle>>,
): String {
  require(chapters.isNotEmpty()) { "chapters must not be empty" }
  require(chapters.all { chapter ->
    chapter.isNotEmpty() && chapter.all {
      it.chapterStatus == PodcastChapterGenerationStatus.READY && !it.chapterScript.isNullOrBlank()
    }
  }) { "all podcast chapters must be ready" }

  return chapters.mapIndexed { index, chapter ->
    val script = requireNotNull(chapter.first().chapterScript).trim()
    require(chapter.all { it.chapterScript?.trim() == script }) { "chapter articles must share the same generated script" }
    val speech = buildString {
      if (index == 0) append("${programName}です。今回のニュースをお伝えします。 ")
      append(script)
      if (index == chapters.lastIndex) append(" 以上、今回のニュースでした。")
    }
    "[[CHAPTER:${index + 1}]]\n$speech"
  }.joinToString("\n\n")
}

internal fun stripRepeatedSpokenTitle(speech: String, spokenTitle: String?): String {
  val title = spokenTitle?.trim()?.takeIf(String::isNotBlank) ?: return speech
  val repeatedTitle = Regex("""^\s*(?:タイトル|見出し)\s*[、,:：]\s*${Regex.escape(title)}\s*(?:[。.!！?？]\s*)?""")
  return speech.replaceFirst(repeatedTitle, "").trimStart()
}


fun buildEpisodeTitle(programName: String, createdAtEpochMillis: Long): String =
  "$programName / ${EPISODE_TITLE_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis).atZone(ZoneId.systemDefault()))}"

private val EPISODE_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
private const val PODCAST_TARGET_SCRIPT_CHARS = 3_000
