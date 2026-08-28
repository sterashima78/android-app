import org.gradle.api.GradleException
import org.gradle.api.Project

fun readTsv(root: Project, path: String, minimumColumns: Int): List<List<String>> =
  root.file(path)
    .readLines()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .mapIndexed { index, line ->
      line.split('\t').also { columns ->
        if (columns.size < minimumColumns) {
          throw GradleException("Invalid TSV row at $path:${index + 1}: $line")
        }
      }
    }

fun referencedDatabaseTables(sourceText: String): Set<String> {
  val tables = linkedSetOf<String>()
  val sqlTablePattern = Regex(
    """\b(?:FROM|JOIN|INTO|UPDATE|DELETE\s+FROM)\s+[`\"]?([A-Za-z_][A-Za-z0-9_]*)""",
    RegexOption.IGNORE_CASE,
  )
  sqlTablePattern.findAll(sourceText).forEach { match ->
    tables += match.groupValues[1].lowercase()
  }

  val sqliteApiPattern = Regex(
    """\b(?:insert|insertOrThrow|insertWithOnConflict|replace|update|delete|tableExists)\s*\(\s*\"([A-Za-z_][A-Za-z0-9_]*)\"""",
  )
  sqliteApiPattern.findAll(sourceText).forEach { match ->
    tables += match.groupValues[1].lowercase()
  }
  return tables
}

fun createdDatabaseTables(sourceText: String): Set<String> {
  val constants = Regex(
    """(?m)^\s*(?:(?:private|internal|public)\s+)?const\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*\"([A-Za-z_][A-Za-z0-9_]*)\"""",
  ).findAll(sourceText).associate { match -> match.groupValues[1] to match.groupValues[2].lowercase() }

  val tables = linkedSetOf<String>()
  val createPattern = Regex(
    """CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+(?:\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?|[`\"]?([A-Za-z_][A-Za-z0-9_]*))""",
    RegexOption.IGNORE_CASE,
  )
  createPattern.findAll(sourceText).forEach { match ->
    val constantName = match.groupValues[1]
    val literalName = match.groupValues[2]
    when {
      constantName.isNotBlank() -> constants[constantName]?.let(tables::add)
      literalName.isNotBlank() -> tables += literalName.lowercase()
    }
  }
  return tables
}

fun appUiDependencyViolations(repositoryPath: String, sourceText: String): List<String> {
  val violations = mutableListOf<String>()
  val concreteFeatureDataImport = Regex(
    """(?m)^\s*import\s+dev\.terashima\.yomitorirss\.feature\.[A-Za-z0-9_.]+\.data\.""",
  )
  if (concreteFeatureDataImport.containsMatchIn(sourceText)) {
    violations += "app presentation composition must not import concrete feature data: $repositoryPath"
  }

  val infrastructureImport = Regex(
    """(?m)^\s*import\s+(?:dev\.terashima\.yomitorirss\.core\.database\.(?:DatabaseConnection|YomitoriDatabase)\b|androidx\.work\.)""",
  )
  if (infrastructureImport.containsMatchIn(sourceText)) {
    violations += "app presentation composition must not import database or WorkManager infrastructure: $repositoryPath"
  }

  val concreteConstruction = Regex(
    """\b(?:DatabaseConnection|YomitoriDatabase|Default[A-Za-z0-9_]*Repository|WorkManager[A-Za-z0-9_]*(?:Scheduler|Controller))\s*\(""",
  )
  concreteConstruction.find(sourceText)?.let { match ->
    violations += "app presentation composition must not construct concrete data/background dependencies: $repositoryPath (${match.value.trim()})"
  }
  return violations
}

fun androidPlatformBaselineViolations(repositoryPath: String, buildText: String): List<String> {
  val isAndroidModule = buildText.contains("id(\"com.android.application\")") ||
    buildText.contains("id(\"com.android.library\")")
  if (!isAndroidModule) return emptyList()

  val minSdk = Regex("""\bminSdk\s*=\s*(\d+)""")
    .find(buildText)
    ?.groupValues
    ?.get(1)
    ?.toIntOrNull()
    ?: return listOf("Android module must declare minSdk = 34 or newer: $repositoryPath")
  return if (minSdk < 34) {
    listOf("Android module minSdk must be API 34 or newer, found $minSdk: $repositoryPath")
  } else {
    emptyList()
  }
}

gradle.projectsEvaluated {
  val root = gradle.rootProject
  val tableOwners: Map<String, String> =
    readTsv(root, "config/architecture/table-ownership.tsv", 2)
      .associate { columns -> columns[0].lowercase() to columns[1] }
  val foreignTableAllowlist: Map<Pair<String, String>, String> =
    readTsv(root, "config/architecture/foreign-table-access-allowlist.tsv", 3)
      .associate { columns ->
        (columns[0].replace('\\', '/') to columns[1].lowercase()) to columns.drop(2).joinToString("\t")
      }

  fun foreignTableViolations(
    projectPath: String,
    repositoryPath: String,
    sourceText: String,
  ): List<String> {
    val normalizedPath = repositoryPath.replace('\\', '/')
    return referencedDatabaseTables(sourceText).mapNotNull { table ->
      val owner = tableOwners[table] ?: return@mapNotNull null
      if (owner == projectPath || foreignTableAllowlist.containsKey(normalizedPath to table)) {
        null
      } else {
        "feature data must not access foreign table '$table' owned by $owner: $normalizedPath"
      }
    }
  }

  fun createdTableViolations(
    projectPath: String,
    repositoryPath: String,
    sourceText: String,
  ): List<String> = createdDatabaseTables(sourceText).mapNotNull { table ->
    when (val owner = tableOwners[table]) {
      null -> "durable table '$table' must be registered in table-ownership.tsv: $repositoryPath"
      projectPath -> null
      else -> "durable table '$table' is created by $projectPath but registered to $owner: $repositoryPath"
    }
  }

  val fixtureViolation = foreignTableViolations(
    projectPath = ":feature:article:data",
    repositoryPath = "feature/article/data/src/main/kotlin/example/Fixture.kt",
    sourceText = "database.rawQuery(\"SELECT * FROM summary_tasks\", null)",
  )
  if (fixtureViolation.none { "summary_tasks" in it }) {
    throw GradleException("Table ownership rule fixture failed to detect foreign summary_tasks access")
  }

  val ownerFixture = foreignTableViolations(
    projectPath = ":feature:summary:data",
    repositoryPath = "feature/summary/data/src/main/kotlin/example/Fixture.kt",
    sourceText = "database.rawQuery(\"SELECT * FROM summary_tasks\", null)",
  )
  if (ownerFixture.isNotEmpty()) {
    throw GradleException("Table ownership rule fixture rejected owner access: $ownerFixture")
  }

  val createFixture = createdDatabaseTables(
    """
      private const val TABLE = "fixture_table"
      db.execSQL("CREATE TABLE IF NOT EXISTS ${'$'}TABLE(id TEXT PRIMARY KEY)")
      db.execSQL("CREATE TABLE IF NOT EXISTS literal_table(id TEXT PRIMARY KEY)")
    """.trimIndent(),
  )
  if (createFixture != setOf("fixture_table", "literal_table")) {
    throw GradleException("Table ownership create-table fixture failed: $createFixture")
  }

  val appUiFixture = appUiDependencyViolations(
    repositoryPath = "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/MailRouteHost.kt",
    sourceText = "import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationOutcome",
  )
  if (appUiFixture.none { "concrete feature data" in it }) {
    throw GradleException("App presentation ownership fixture failed to detect Host concrete data import")
  }

  val platformFixture = androidPlatformBaselineViolations(
    repositoryPath = "feature/example/ui/build.gradle.kts",
    buildText = "id(\"com.android.library\")\nandroid { defaultConfig { minSdk = 29 } }",
  )
  if (platformFixture.none { "API 34" in it }) {
    throw GradleException("Android platform baseline fixture failed to detect minSdk 29")
  }

  val violations = mutableListOf<String>()
  root.subprojects.forEach { project ->
    val buildFile = project.buildFile
    if (buildFile.isFile) {
      val repositoryPath = buildFile.relativeTo(root.rootDir).path.replace('\\', '/')
      violations += androidPlatformBaselineViolations(repositoryPath, buildFile.readText())
    }
  }

  val appUiRoot = root.file("app/presentation/src/main")
  if (appUiRoot.isDirectory) {
    root.fileTree(appUiRoot) { include("**/ui/**/*.kt") }
      .files
      .sortedBy { it.path }
      .forEach { sourceFile ->
        val repositoryPath = sourceFile.relativeTo(root.rootDir).path.replace('\\', '/')
        violations += appUiDependencyViolations(repositoryPath, sourceFile.readText())
      }
  }

  root.subprojects
    .filter { it.path.startsWith(":feature:") && it.path.endsWith(":data") }
    .forEach { project ->
      listOf("src/main/java", "src/main/kotlin").forEach { sourceRootPath ->
        val sourceRoot = project.file(sourceRootPath)
        if (!sourceRoot.isDirectory) return@forEach
        project.fileTree(sourceRoot) { include("**/*.kt") }
          .files
          .sortedBy { it.path }
          .forEach { sourceFile ->
            val repositoryPath = sourceFile.relativeTo(root.rootDir).path.replace('\\', '/')
            val sourceText = sourceFile.readText()
            violations += foreignTableViolations(
              projectPath = project.path,
              repositoryPath = repositoryPath,
              sourceText = sourceText,
            )
            violations += createdTableViolations(
              projectPath = project.path,
              repositoryPath = repositoryPath,
              sourceText = sourceText,
            )
          }
      }
    }

  foreignTableAllowlist.forEach { (key, reason) ->
    val (repositoryPath, table) = key
    if (reason.isBlank()) {
      violations += "foreign table allowlist entry requires a reason: $repositoryPath -> $table"
      return@forEach
    }
    if (table !in tableOwners) {
      violations += "foreign table allowlist references unknown table '$table': $repositoryPath"
      return@forEach
    }
    val sourceFile = root.file(repositoryPath)
    if (!sourceFile.isFile) {
      violations += "foreign table allowlist references missing file: $repositoryPath -> $table"
      return@forEach
    }
    if (table !in referencedDatabaseTables(sourceFile.readText())) {
      violations += "stale foreign table allowlist entry: $repositoryPath -> $table"
    }
  }

  if (violations.isNotEmpty()) {
    throw GradleException(
      buildString {
        appendLine("Architecture ownership verification failed (${violations.size} violation(s)):")
        violations.sorted().forEach { appendLine("- $it") }
        append("See docs/architecture/principles.md and docs/architecture/persistence.md.")
      },
    )
  }

  root.logger.lifecycle(
    "Architecture ownership verification passed for ${tableOwners.size} owned table(s) with " +
      "${foreignTableAllowlist.size} explicit migration allowance(s).",
  )
}
