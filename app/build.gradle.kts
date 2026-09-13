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
