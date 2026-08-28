import org.gradle.api.GradleException
import org.gradle.api.Project

private val FEATURE_TABLE_START = "<!-- feature-modules:start -->"
private val FEATURE_TABLE_END = "<!-- feature-modules:end -->"
private val FEATURE_LAYER_ORDER = listOf("domain", "data", "ui")

private val ADR_FILENAME = Regex("""^(\d{4})-([a-z0-9][a-z0-9-]*)\.md$""")
private val ADR_HEADING = Regex("""^# ADR-(\d{4}):\s+\S.*$""")
private val ADR_REFERENCE = Regex("""\bADR-(\d{4})\b""")
private val ADR_PATH_REFERENCE = Regex("""docs/adr/(\d{4}-[a-z0-9][a-z0-9-]*\.md)""")
private val MARKDOWN_LOCAL_ADR_LINK = Regex(
  """\]\((?:(?:\./)?|\.\./adr/)(\d{4}-[a-z0-9][a-z0-9-]*\.md)(?:#[^)]+)?\)""",
)

private val RETIRED_DOCUMENTATION_PATHS = mapOf(
  "docs/domain-context-map.md" to "docs/architecture/context-map.md",
)

private fun featureModulesFromProjectPaths(projectPaths: Iterable<String>): Map<String, List<String>> {
  val modules = linkedMapOf<String, MutableSet<String>>()
  projectPaths
    .filter { it.startsWith(":feature:") }
    .sorted()
    .forEach { projectPath ->
      val parts = projectPath.split(':')
      if (parts.size == 3 && parts[1] == "feature") return@forEach
      if (parts.size != 4 || parts[1] != "feature") {
        throw IllegalArgumentException("unsupported feature module path: $projectPath")
      }
      modules.getOrPut(parts[2]) { linkedSetOf() } += parts[3]
    }

  return modules
    .toSortedMap()
    .mapValues { (_, layers) ->
      FEATURE_LAYER_ORDER.filter(layers::contains) +
        layers.filterNot(FEATURE_LAYER_ORDER::contains).sorted()
    }
}

private fun featureModulesFromDocument(moduleMapText: String): Map<String, List<String>> {
  val startIndex = moduleMapText.indexOf(FEATURE_TABLE_START)
  val endIndex = moduleMapText.indexOf(FEATURE_TABLE_END)
  if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
    throw IllegalArgumentException("feature module table markers are missing")
  }

  val block = moduleMapText.substring(startIndex + FEATURE_TABLE_START.length, endIndex)
  val modules = linkedMapOf<String, List<String>>()
  Regex("""(?m)^\|\s*([a-z0-9][a-z0-9-]*)\s*\|\s*([^|]+?)\s*\|$""")
    .findAll(block)
    .forEach { match ->
      val feature = match.groupValues[1]
      val layers = match.groupValues[2]
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
      if (modules.put(feature, layers) != null) {
        throw IllegalArgumentException("duplicate feature row: $feature")
      }
    }
  return modules
}

private fun moduleMapVerificationErrors(
  projectPaths: Iterable<String>,
  moduleMapText: String,
): List<String> {
  val expected = featureModulesFromProjectPaths(projectPaths)
  val documented = featureModulesFromDocument(moduleMapText)
  val errors = mutableListOf<String>()

  (expected.keys - documented.keys).sorted().forEach { feature ->
    errors += "missing feature row: $feature -> ${expected.getValue(feature).joinToString(" / ")}"
  }
  (documented.keys - expected.keys).sorted().forEach { feature ->
    errors += "stale feature row: $feature -> ${documented.getValue(feature).joinToString(" / ")}"
  }
  (expected.keys intersect documented.keys).sorted().forEach { feature ->
    if (expected.getValue(feature) != documented.getValue(feature)) {
      errors +=
        "layer mismatch for $feature: settings=${expected.getValue(feature).joinToString(" / ")}, " +
          "docs=${documented.getValue(feature).joinToString(" / ")}"
    }
  }
  return errors
}

private data class ParsedAdr(
  val path: String,
  val filename: String,
  val number: String,
  val text: String,
)

private fun adrReferenceViolations(
  path: String,
  text: String,
  knownNumbers: Set<String>,
  knownFilenames: Set<String>,
): List<String> {
  val violations = mutableListOf<String>()
  ADR_REFERENCE.findAll(text).forEach { match ->
    val number = match.groupValues[1]
    if (number !in knownNumbers) {
      violations += "$path: ADR-$number does not exist"
    }
  }

  val explicitPaths = linkedSetOf<String>()
  ADR_PATH_REFERENCE.findAll(text).forEach { explicitPaths += it.groupValues[1] }
  MARKDOWN_LOCAL_ADR_LINK.findAll(text).forEach { explicitPaths += it.groupValues[1] }
  explicitPaths.sorted().forEach { filename ->
    if (filename !in knownFilenames) {
      violations += "$path: ADR link target does not exist: $filename"
    }
  }
  return violations
}

private fun adrIntegrityViolations(
  adrDocuments: Map<String, String>,
  currentDocuments: Map<String, String>,
): List<String> {
  val violations = mutableListOf<String>()
  val parsed = mutableListOf<ParsedAdr>()
  val pathsByNumber = linkedMapOf<String, MutableList<String>>()

  adrDocuments.toSortedMap().forEach { (path, text) ->
    val filename = path.substringAfterLast('/')
    if (filename == "README.md") return@forEach

    val filenameMatch = ADR_FILENAME.matchEntire(filename)
    if (filenameMatch == null) {
      violations += "$path: filename must match NNNN-lowercase-kebab-case.md"
      return@forEach
    }

    val number = filenameMatch.groupValues[1]
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val heading = ADR_HEADING.matchEntire(firstLine)
    if (heading == null) {
      violations += "$path: first line must be '# ADR-$number: <title>'"
    } else if (heading.groupValues[1] != number) {
      violations += "$path: heading ADR-${heading.groupValues[1]} does not match filename ADR-$number"
    }

    pathsByNumber.getOrPut(number) { mutableListOf() } += path
    parsed += ParsedAdr(path = path, filename = filename, number = number, text = text)
  }

  pathsByNumber.toSortedMap().forEach { (number, paths) ->
    if (paths.size > 1) {
      violations += "ADR-$number is duplicated: ${paths.sorted().joinToString(", ")}"
    }
  }

  val knownNumbers = pathsByNumber.keys
  val knownFilenames = parsed.mapTo(linkedSetOf()) { it.filename }
  parsed.forEach { adr ->
    violations += adrReferenceViolations(adr.path, adr.text, knownNumbers, knownFilenames)
  }
  currentDocuments.toSortedMap().forEach { (path, text) ->
    violations += adrReferenceViolations(path, text, knownNumbers, knownFilenames)
  }
  return violations.distinct().sorted()
}

private fun documentationCompatibilityViolations(
  existingPaths: Set<String>,
  markdownDocuments: Map<String, String>,
): List<String> {
  val violations = mutableListOf<String>()

  RETIRED_DOCUMENTATION_PATHS.toSortedMap().forEach { (retiredPath, currentPath) ->
    if (retiredPath in existingPaths) {
      violations += "$retiredPath: retired architecture compatibility entry must stay removed"
    }

    markdownDocuments.toSortedMap().forEach { (path, text) ->
      if (retiredPath in text) {
        violations += "$path: retired architecture path '$retiredPath' must link directly to '$currentPath'"
      }
    }
  }

  return violations.distinct().sorted()
}

private fun verifyArchitectureMetadataFixtures() {
  val modulePaths = listOf(
    ":app",
    ":feature:alpha",
    ":feature:alpha:ui",
    ":feature:alpha:domain",
    ":feature:alpha:data",
    ":feature:beta",
    ":feature:beta:ui",
  )
  check(
    featureModulesFromProjectPaths(modulePaths) == mapOf(
      "alpha" to listOf("domain", "data", "ui"),
      "beta" to listOf("ui"),
    ),
  ) { "Module map fixture failed to parse feature project layers" }

  val moduleMap = """
    | Ignore | Row |
    | --- | --- |
    <!-- feature-modules:start -->
    | Feature | Layers |
    | --- | --- |
    | alpha | domain / data / ui |
    | beta | ui |
    <!-- feature-modules:end -->
  """.trimIndent()
  check(
    featureModulesFromDocument(moduleMap) == mapOf(
      "alpha" to listOf("domain", "data", "ui"),
      "beta" to listOf("ui"),
    ),
  ) { "Module map fixture failed to parse marked documentation table" }

  val mismatchErrors = moduleMapVerificationErrors(
    listOf(":feature:alpha", ":feature:alpha:domain", ":feature:alpha:ui", ":feature:beta", ":feature:beta:ui"),
    """
      <!-- feature-modules:start -->
      | Feature | Layers |
      | --- | --- |
      | alpha | domain / data / ui |
      | gamma | ui |
      <!-- feature-modules:end -->
    """.trimIndent(),
  )
  check(
    mismatchErrors == listOf(
      "missing feature row: beta -> ui",
      "stale feature row: gamma -> ui",
      "layer mismatch for alpha: settings=domain / ui, docs=domain / data / ui",
    ),
  ) { "Module map mismatch fixture failed: $mismatchErrors" }

  val duplicateRowRejected = runCatching {
    featureModulesFromDocument(
      """
        <!-- feature-modules:start -->
        | Feature | Layers |
        | --- | --- |
        | alpha | domain |
        | alpha | ui |
        <!-- feature-modules:end -->
      """.trimIndent(),
    )
  }.exceptionOrNull()?.message == "duplicate feature row: alpha"
  check(duplicateRowRejected) { "Module map duplicate-row fixture failed" }

  val validAdrDocuments = mapOf(
    "docs/adr/README.md" to "# Architecture Decision Log\n",
    "docs/adr/0001-first.md" to "# ADR-0001: First\n\nSee ADR-0002.\n",
    "docs/adr/0002-second.md" to "# ADR-0002: Second\n\nSee [first](0001-first.md).\n",
  )
  val validCurrentDocuments = mapOf(
    "docs/architecture/principles.md" to "# Principles\n\nSee [ADR-0002](../adr/0002-second.md).\n",
  )
  check(adrIntegrityViolations(validAdrDocuments, validCurrentDocuments).isEmpty()) {
    "ADR integrity valid fixture was rejected"
  }

  val duplicateAdr = adrIntegrityViolations(
    mapOf(
      "docs/adr/0001-first.md" to "# ADR-0001: First\n",
      "docs/adr/0001-second.md" to "# ADR-0001: Second\n",
    ),
    emptyMap(),
  )
  check(duplicateAdr.any { "ADR-0001 is duplicated" in it }) {
    "ADR duplicate-number fixture failed: $duplicateAdr"
  }

  val malformedAdr = adrIntegrityViolations(
    mapOf("docs/adr/1-not-padded.md" to "# ADR-0001: First\n"),
    emptyMap(),
  )
  check(malformedAdr.any { "filename must match" in it }) {
    "ADR malformed-filename fixture failed: $malformedAdr"
  }

  val brokenReferences = adrIntegrityViolations(
    mapOf("docs/adr/0001-first.md" to "# ADR-0002: First\n\nSee ADR-9999 and [missing](9999-missing.md).\n"),
    mapOf("docs/spec.md" to "# Spec\n\nSee ADR-9998.\n"),
  )
  check(brokenReferences.any { "does not match filename ADR-0001" in it }) {
    "ADR heading mismatch fixture failed: $brokenReferences"
  }
  check(brokenReferences.any { "ADR-9999 does not exist" in it }) {
    "ADR numeric reference fixture failed: $brokenReferences"
  }
  check(brokenReferences.any { "ADR link target does not exist: 9999-missing.md" in it }) {
    "ADR explicit link fixture failed: $brokenReferences"
  }
  check(brokenReferences.any { "docs/spec.md: ADR-9998 does not exist" == it }) {
    "ADR current-document reference fixture failed: $brokenReferences"
  }

  val cleanDocumentation = mapOf(
    "docs/architecture/README.md" to "See docs/architecture/context-map.md",
  )
  check(
    documentationCompatibilityViolations(
      existingPaths = cleanDocumentation.keys,
      markdownDocuments = cleanDocumentation,
    ).isEmpty(),
  ) { "Current documentation compatibility fixture rejected the current path" }

  val restoredCompatibilityEntry = documentationCompatibilityViolations(
    existingPaths = setOf("docs/domain-context-map.md"),
    markdownDocuments = emptyMap(),
  )
  check(restoredCompatibilityEntry.any { "retired architecture compatibility entry" in it }) {
    "Retired documentation path fixture failed: $restoredCompatibilityEntry"
  }

  val staleDocumentationReference = documentationCompatibilityViolations(
    existingPaths = emptySet(),
    markdownDocuments = mapOf(
      "docs/README.md" to "See docs/domain-context-map.md",
    ),
  )
  check(staleDocumentationReference.any { "must link directly" in it }) {
    "Retired documentation reference fixture failed: $staleDocumentationReference"
  }
}

private fun currentArchitectureDocuments(root: Project): Map<String, String> {
  val files = mutableListOf<java.io.File>()
  root.file("docs/adr/README.md").takeIf(java.io.File::isFile)?.let(files::add)
  root.file("docs/architecture")
    .takeIf(java.io.File::isDirectory)
    ?.listFiles()
    ?.filter { it.isFile && it.extension == "md" }
    ?.sortedBy { it.name }
    ?.let(files::addAll)
  root.file("docs/spec.md").takeIf(java.io.File::isFile)?.let(files::add)
  return files.associate { file ->
    file.relativeTo(root.rootDir).invariantSeparatorsPath to file.readText()
  }
}

private fun repositoryMarkdownDocuments(root: Project): Map<String, String> {
  val docsRoot = root.file("docs")
  if (!docsRoot.isDirectory) return emptyMap()

  return docsRoot.walkTopDown()
    .filter { it.isFile && it.extension == "md" }
    .sortedBy { it.path }
    .associate { file ->
      file.relativeTo(root.rootDir).invariantSeparatorsPath to file.readText()
    }
}

gradle.projectsEvaluated {
  val root = gradle.rootProject
  verifyArchitectureMetadataFixtures()

  val violations = mutableListOf<String>()
  val moduleMapFile = root.file("docs/architecture/module-map.md")
  if (!moduleMapFile.isFile) {
    violations += "docs/architecture/module-map.md does not exist"
  } else {
    try {
      violations += moduleMapVerificationErrors(
        projectPaths = root.allprojects.map(Project::getPath),
        moduleMapText = moduleMapFile.readText(),
      )
    } catch (error: IllegalArgumentException) {
      violations += error.message ?: "module map verification failed"
    }
  }

  val adrDir = root.file("docs/adr")
  if (!adrDir.isDirectory) {
    violations += "ADR directory does not exist: ${adrDir.path}"
  } else {
    val adrDocuments = adrDir.listFiles()
      .orEmpty()
      .filter { it.isFile && it.extension == "md" }
      .associate { file ->
        file.relativeTo(root.rootDir).invariantSeparatorsPath to file.readText()
      }
    violations += adrIntegrityViolations(adrDocuments, currentArchitectureDocuments(root))
  }

  val markdownDocuments = repositoryMarkdownDocuments(root)
  val existingRetiredPaths = RETIRED_DOCUMENTATION_PATHS.keys
    .filterTo(linkedSetOf()) { root.file(it).exists() }
  violations += documentationCompatibilityViolations(
    existingPaths = existingRetiredPaths,
    markdownDocuments = markdownDocuments,
  )

  if (violations.isNotEmpty()) {
    throw GradleException(
      buildString {
        appendLine("Architecture metadata verification failed (${violations.size} violation(s)):")
        violations.distinct().sorted().forEach { appendLine("- $it") }
        append("Keep Gradle module declarations, architecture documentation, and ADR references consistent.")
      },
    )
  }

  val featureCount = featureModulesFromProjectPaths(root.allprojects.map(Project::getPath)).size
  val adrCount = root.file("docs/adr").listFiles().orEmpty().count {
    it.isFile && it.extension == "md" && it.name != "README.md"
  }
  root.logger.lifecycle(
    "Architecture metadata verification passed for $featureCount feature(s) and $adrCount ADR file(s).",
  )
}
