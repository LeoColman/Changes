import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

// Versão: o workflow de release passa a tag (-PappVersionName=0.2.0). O versionCode sai do semver
// (MAJOR * 1_000_000 + MINOR * 1_000 + PATCH), então cresce sozinho a cada release.
val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.1.0")
val appVersionCode: Int = appVersionName.split(".").let { parts ->
    val numbers = parts.map { it.toIntOrNull() }
    require(numbers.size == 3 && numbers.all { it != null }) { "appVersionName must be MAJOR.MINOR.PATCH: $appVersionName" }
    numbers[0]!! * 1_000_000 + numbers[1]!! * 1_000 + numbers[2]!!
}

// Assinatura de release: só no workflow de release, com a chave guardada nos secrets do GitHub. Sem
// CHANGES_KEYSTORE_FILE, o assembleRelease sai sem assinatura, como no CI.
val releaseKeystore: String? = providers.environmentVariable("CHANGES_KEYSTORE_FILE").orNull

android {
    namespace = "br.com.colman.changes"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.colman.changes"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("CHANGES_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CHANGES_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CHANGES_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all { it.useJUnitPlatform() }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = false
        warningsAsErrors = false
        // Seção 10: acessibilidade e segurança são erro, não aviso.
        error += setOf(
            "ContentDescription",
            "ClickableViewAccessibility",
            "KeyboardInaccessibleWidget",
            "LabelFor",
            "GetContentDescriptionOverride",
            "AllowBackup",
            "DataExtractionRules",
            "HardcodedDebugMode",
            "ExportedContentProvider",
            "ExportedReceiver",
            "ExportedService",
            "GrantAllUris",
            "SecureRandom",
            "SetWorldReadable",
            "SetWorldWritable",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            "TrustAllX509TrustManager",
            "UnsafeProtectedBroadcastReceiver",
            "UnspecifiedImmutableFlag",
            "MutableImplicitPendingIntent",
            "PackagedPrivateKey",
        )
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }
}

composeCompiler {
    // Relatórios de estabilidade para o CI: ./gradlew :app:assembleRelease -PcomposeReports
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.android.driver)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.aboutlibraries.compose.m3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.koin.test)
    testImplementation(libs.konsist)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // O espresso-core 3.5.0 que vem pelo ui-test-junit4 quebra no API 37 (ADR 0001).
    androidTestImplementation(libs.androidx.test.espresso.core)
}

tasks.withType<Test>().configureEach {
    systemProperty("kotest.framework.config.fqn", "br.com.colman.changes.core.KotestProjectConfig")
    maxHeapSize = "2g"
}

// ---------------------------------------------------------------------------------------------
// Verificação do manifesto mergeado (Seções 3 e 11.3): falha se alguma dependência injetar
// INTERNET ou se o backup não estiver desligado.
// ---------------------------------------------------------------------------------------------

abstract class VerifyMergedManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val permissions = doc.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }
        val forbidden = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        val leaked = permissions.filter { it in forbidden }
        if (leaked.isNotEmpty()) throw GradleException("Merged manifest declares forbidden permissions: $leaked")
        val application = doc.getElementsByTagName("application").item(0) as Element
        if (application.getAttributeNS(androidNs, "allowBackup") != "false") {
            throw GradleException("Merged manifest must declare android:allowBackup=\"false\"")
        }
        if (application.getAttributeNS(androidNs, "dataExtractionRules").isBlank()) {
            throw GradleException("Merged manifest must declare android:dataExtractionRules")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyMergedManifestTask>("verify${name}MergedManifest") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.named("check") { dependsOn(verify) }
    }
}

// ---------------------------------------------------------------------------------------------
// Teste de mutação da lógica do :app que roda na JVM, sem Android (ADR 0005). O plugin do Pitest
// espera o source set `test` do plugin `java`, que o AGP não expõe, então a rodada chama a linha de
// comando do Pitest com o classpath do `testDebugUnitTest`. Só as classes da lista entram: o resto
// do módulo é Compose e plataforma, que a JVM não executa.
// ---------------------------------------------------------------------------------------------

val pitestCli: Configuration by configurations.creating

dependencies {
    pitestCli(libs.pitest.command.line)
    pitestCli(libs.kotest.extensions.pitest)
}

/** Piso do score de mutação do :app (ADR 0005). */
val appPitestFloor = 82

/** Rodada rápida durante o desenvolvimento: `-Ppitest.classes=br.com.colman.changes.ui.format.*`. */
val pitestClassesOverride: String? = providers.gradleProperty("pitest.classes").orNull

/** Classes do :app que não tocam em Android e têm spec de JVM. */
val pitestTargetClasses = listOf(
    "br.com.colman.changes.ui.format.*",
    "br.com.colman.changes.platform.reminders.PlannedUpcomingReminders*",
    "br.com.colman.changes.platform.reminders.SettingsUpcomingReminders*",
    "br.com.colman.changes.platform.reminders.ReminderResyncer*",
    "br.com.colman.changes.platform.reminders.ReminderSync*",
    "br.com.colman.changes.feature.medication.RegimenScheduleFormKt",
    "br.com.colman.changes.feature.medication.MedicationDisplayNames",
    "br.com.colman.changes.feature.settings.HeightInputKt",
    "br.com.colman.changes.feature.settings.VocabularyStateBuilderKt",
    "br.com.colman.changes.feature.body.PhotoBlurKt",
    "br.com.colman.changes.feature.body.BodyLabels",
    "br.com.colman.changes.feature.trash.TrashCategoryKt",
)

val appPitest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Mutation testing for the JVM-only logic of :app (ADR 0005)."
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    dependsOn(unitTest)
    val reportDir = layout.buildDirectory.dir("reports/pitest")
    outputs.dir(reportDir)
    classpath(pitestCli)
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
    argumentProviders.add(
        CommandLineArgumentProvider {
            val test = unitTest.get()
            val classpathFiles = test.classpath.files + pitestCli.files
            // O AGP entrega as classes do app num jar; é ele que o Pitest pode mutar.
            val mutableCode = test.classpath.files.filter { it.name == "classes.jar" }
            listOf(
                "--classPath", classpathFiles.joinToString(",") { it.path },
                "--mutableCodePaths", mutableCode.joinToString(",") { it.path },
                "--sourceDirs", file("src/main/kotlin").path,
                "--targetClasses", (pitestClassesOverride ?: pitestTargetClasses.joinToString(",")),
                "--targetTests", "br.com.colman.changes.*",
                "--reportDir", reportDir.get().asFile.path,
                "--outputFormats", "XML,HTML",
                "--timestampedReports", "false",
                "--threads", Runtime.getRuntime().availableProcessors().toString(),
                "--excludedMethods", "equals,hashCode,toString,copy,copy\$default,component*,<clinit>",
                "--jvmArgs",
                listOf(
                    "-Dkotest.framework.config.fqn=br.com.colman.changes.core.KotestProjectConfig",
                    "-Dchanges.pitest=true",
                ).joinToString(","),
            )
        },
    )
}


/**
 * Gate do :app (ADR 0005): o score do módulo não desce do piso. Numa rodada com `-Ppitest.classes`
 * o gate não roda: o recorte não representa o módulo.
 */
val appPitestGate by tasks.registering {
    group = "verification"
    description = "Verifies the :app mutation score against the floor in ADR 0005."
    val mutationsFile = layout.buildDirectory.file("reports/pitest/mutations.xml")
    val scoped = pitestClassesOverride != null
    onlyIf { !scoped && mutationsFile.get().asFile.exists() }
    doLast {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(mutationsFile.get().asFile)
            .getElementsByTagName("mutation")
        val total = nodes.length
        val detected = (0 until total).count { (nodes.item(it) as Element).getAttribute("detected") == "true" }
        val score = if (total == 0) 100.0 else detected * 100.0 / total
        val table = listOf(
            "| Module | Mutants | Detected | Score | Floor |",
            "|---|---:|---:|---:|---:|",
            "| :app (JVM-only) | $total | $detected | ${"%.1f".format(score)}% | $appPitestFloor% |",
        )
        layout.buildDirectory.file("reports/pitest/module-score.md").get().asFile
            .apply { parentFile.mkdirs() }
            .writeText(table.joinToString("\n") + "\n")
        logger.lifecycle(table.joinToString("\n"))
        if (score < appPitestFloor) {
            throw GradleException(":app mutation score ${"%.1f".format(score)}% < $appPitestFloor%")
        }
    }
}

appPitest { finalizedBy(appPitestGate) }
