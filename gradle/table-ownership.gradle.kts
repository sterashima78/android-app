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

  val violations = mutableListOf<String>()
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
            violations += foreignTableViolations(
              projectPath = project.path,
              repositoryPath = repositoryPath,
              sourceText = sourceFile.readText(),
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
        appendLine("Table ownership verification failed (${violations.size} violation(s)):")
        violations.sorted().forEach { appendLine("- $it") }
        append("See docs/adr/0106-domain-context-aggregate-and-persistence-ownership.md.")
      },
    )
  }

  root.logger.lifecycle(
    "Table ownership verification passed for ${tableOwners.size} owned table(s) with " +
      "${foreignTableAllowlist.size} explicit migration allowance(s).",
  )
}
