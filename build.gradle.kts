import io.gitlab.arturbosch.detekt.Detekt

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

val detektSources = files(
    listOf("core", "app").flatMap { module ->
        listOf("main", "test", "testFixtures", "androidTest").map { set -> "$module/src/$set/kotlin" }
    },
).filter { it.exists() }

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    basePath = rootDir.absolutePath
    source.setFrom(detektSources)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

// Aplica as correções automáticas do ktlint (detekt-formatting). Nunca roda no `check`.
tasks.register<Detekt>("detektFormat") {
    description = "Runs detekt with auto-correct enabled."
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
    basePath = rootDir.absolutePath
    setSource(detektSources)
}

tasks.named("check") {
    dependsOn("detekt")
}

tasks.register("pitestAll") {
    group = "verification"
    description = "Runs mutation testing with gates on :core and on the JVM-only logic of :app."
    dependsOn(":core:pitest", ":app:appPitest")
}
