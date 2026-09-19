import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kover)
    alias(libs.plugins.pitest)
    `java-test-fixtures`
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }
}

sqldelight {
    databases {
        create("ChangesDatabase") {
            // Todo código gerado vive em core.db.sql; código escrito à mão fica em core.db.
            packageName.set("br.com.colman.changes.core.db.sql")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(libs.sqldelight.runtime)
    api(project.dependencies.platform(libs.koin.bom))
    api(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.coroutines)

    testFixturesApi(libs.kotest.property)
    testFixturesApi(libs.kotest.assertions.core)
    testFixturesApi(libs.kotest.runner.junit5)
    testFixturesApi(libs.sqldelight.sqlite.driver)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.koin.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Plugin de teste do Pitest que executa specs inteiras do Kotest (ADR 0005).
    pitest(libs.kotest.extensions.pitest)
}

val kotestConfig = "br.com.colman.changes.core.KotestProjectConfig"

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperty("kotest.framework.config.fqn", kotestConfig)
    systemProperty("kotest.tags", "!Stress")
    // -D na linha de comando do Gradle não chega à JVM de teste sozinho.
    listOf("changes.roundtrip.iterations", "changes.writeFixtures").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}

// Critério 7.8.5: export de 10k registros e 500 fotos (125 MB) com heap de 128 MB. Passa só se o
// export for de fato em streaming.
val stressTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the backup stress test (criterion 7.8.5) with a capped heap."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    maxHeapSize = "128m"
    systemProperty("kotest.framework.config.fqn", kotestConfig)
    systemProperty("kotest.tags", "Stress")
    shouldRunAfter(tasks.test)
}

kover {
    reports {
        filters {
            excludes {
                packages("br.com.colman.changes.core.db.sql")
                classes("*\$\$serializer")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify", stressTest)
}

pitest {
    pitestVersion.set(libs.versions.pitest)
    // Rodada rápida durante o desenvolvimento: `-Ppitest.classes=br.com.colman.changes.core.model.*`.
    targetClasses.set(
        providers.gradleProperty("pitest.classes").map { it.split(",") }
            .orElse(listOf("br.com.colman.changes.core.*")),
    )
    targetTests.set(listOf("br.com.colman.changes.core.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
    exportLineCoverage.set(true)
    // Análise incremental (withHistory) não existe mais no Pitest OSS; ver ADR 0005.
    mutationThreshold.set(82)
    coverageThreshold.set(80)
    avoidCallsTo.set(listOf("kotlin.jvm.internal.Intrinsics", "kotlin.jvm.internal.Reflection"))
    excludedClasses.set(
        listOf(
            "br.com.colman.changes.core.db.sql.*",
            "*\$WhenMappings",
            "*\$DefaultImpls",
            "*\$\$serializer",
            "*Module*Kt",
        ),
    )
    excludedMethods.set(
        listOf("equals", "hashCode", "toString", "copy", "copy\$default", "component*", "<clinit>"),
    )
    mutators.set(
        listOf(
            "DEFAULTS",
            "CONDITIONALS_BOUNDARY",
            "INCREMENTS",
            "MATH",
            "NEGATE_CONDITIONALS",
            "INVERT_NEGS",
            "EMPTY_RETURNS",
            "FALSE_RETURNS",
            "TRUE_RETURNS",
            "NULL_RETURNS",
            "REMOVE_CONDITIONALS",
        ),
    )
    jvmArgs.set(listOf("-Dkotest.proptest.default.iteration.count=50", "-Dchanges.pitest=true", "-Dkotest.tags=!Stress"))
}

// ---------------------------------------------------------------------------------------------
// Gate por pacote (Seção 11.4). Lê mutations.xml, aplica pitest-suppressions.xml e verifica
// os pisos de cada pacote. Toda supressão precisa de justificativa e precisa casar com um mutante.
// ---------------------------------------------------------------------------------------------

val pitestPackageFloors = linkedMapOf(
    "br.com.colman.changes.core.clinical" to 90,
    "br.com.colman.changes.core.data" to 85,
    "br.com.colman.changes.core.model" to 85,
    "br.com.colman.changes.core.db" to 70,
)
val pitestModuleFloor = 82

data class PitMutant(val clazz: String, val method: String, val line: Int, val mutator: String, val detected: Boolean)

data class PitSuppression(val clazz: String, val method: String, val line: Int, val mutator: String, val why: String) {
    fun matches(m: PitMutant) =
        m.clazz == clazz && m.method == method && m.line == line && m.mutator.endsWith(mutator)
}

fun Element.child(tag: String): String = getElementsByTagName(tag).item(0)?.textContent.orEmpty().trim()

fun readPitMutants(file: File): List<PitMutant> {
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("mutation")
    return (0 until nodes.length).map { i ->
        val e = nodes.item(i) as Element
        PitMutant(
            clazz = e.child("mutatedClass"),
            method = e.child("mutatedMethod"),
            line = e.child("lineNumber").toInt(),
            mutator = e.child("mutator"),
            detected = e.getAttribute("detected") == "true",
        )
    }
}

fun readPitSuppressions(file: File): List<PitSuppression> {
    if (!file.exists()) return emptyList()
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("suppress")
    return (0 until nodes.length).map { i ->
        val e = nodes.item(i) as Element
        PitSuppression(
            clazz = e.getAttribute("class"),
            method = e.getAttribute("method"),
            line = e.getAttribute("line").toIntOrNull() ?: -1,
            mutator = e.getAttribute("mutator"),
            why = e.getAttribute("justification").trim(),
        )
    }
}

fun pitScore(mutants: List<PitMutant>): Double =
    if (mutants.isEmpty()) 100.0 else mutants.count { it.detected } * 100.0 / mutants.size

val pitestPackageGate by tasks.registering {
    group = "verification"
    description = "Verifies Pitest mutation score per package against the floors in ADR 0005."
    val mutationsFile = layout.buildDirectory.file("reports/pitest/mutations.xml")
    val suppressionsFile = layout.projectDirectory.file("pitest-suppressions.xml")
    val summaryFile = layout.buildDirectory.file("reports/pitest/package-scores.md")
    // Se o Pitest falhar antes de gerar o relatório, a falha dele é a que importa.
    // Num recorte (`-Ppitest.classes`) os pisos por pacote não valem: o gate só roda na rodada inteira.
    val scoped = providers.gradleProperty("pitest.classes").isPresent
    onlyIf { !scoped && mutationsFile.get().asFile.exists() }
    doLast {
        val all = readPitMutants(mutationsFile.get().asFile)
        val suppressions = readPitSuppressions(suppressionsFile.asFile)
        val problems = mutableListOf<String>()
        suppressions.filter { it.why.isEmpty() }.forEach { problems += "Suppression without justification: $it" }
        suppressions.filter { s -> all.none(s::matches) }.forEach { problems += "Stale suppression (matches no mutant): $it" }
        val effective = all.filterNot { m -> suppressions.any { it.matches(m) } }

        val lines = mutableListOf("| Package | Mutants | Detected | Score | Floor |", "|---|---:|---:|---:|---:|")
        val moduleScore = pitScore(effective)
        lines += "| (module) | ${effective.size} | ${effective.count { it.detected }} | ${"%.1f".format(moduleScore)}% | $pitestModuleFloor% |"
        if (moduleScore < pitestModuleFloor) problems += "Module score ${"%.1f".format(moduleScore)}% < $pitestModuleFloor%"
        pitestPackageFloors.forEach { (pkg, floor) ->
            val inPkg = effective.filter { it.clazz.startsWith("$pkg.") }
            val score = pitScore(inPkg)
            lines += "| $pkg | ${inPkg.size} | ${inPkg.count { it.detected }} | ${"%.1f".format(score)}% | $floor% |"
            if (inPkg.isNotEmpty() && score < floor) problems += "$pkg score ${"%.1f".format(score)}% < $floor%"
        }
        summaryFile.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n") + "\n")
        logger.lifecycle(lines.joinToString("\n"))
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
    }
}

tasks.named("pitest") {
    finalizedBy(pitestPackageGate)
}
