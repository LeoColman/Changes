// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.db.CURRENT_SCHEMA_VERSION
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64

/**
 * Fixture versionada de export (ADR 0009, critério 7.8.4): `src/test/resources/backup/v<N>/`, em texto
 * (JSON formatado e mídias em base64), montada em zip pelo teste. Quando o schema subir para N+1, a
 * fixture de N continua aqui e passa a provar o import de versão anterior.
 *
 * Gerar de novo (só ao congelar uma versão): `./gradlew :core:test --tests '*BackupFixtureSpec' -Dchanges.writeFixtures=true`.
 */
class BackupFixtureSpec : FunSpec({
    test("the v1 export fixture imports into a clean install with every row and file") {
        runTest {
            val entries = fixtureEntries(1)
            val manifest = entries.manifest()
            val target = device()
            val summary = target.import(zipOf(entries), ImportMode.REPLACE)
            summary.tables.associate { it.table to it.inserted } shouldBe
                manifest.getValue("counts").jsonObject.mapValues { it.value.jsonPrimitive.int }
            target.mediaChecksums() shouldBe manifest.getValue("media").jsonArray.associate {
                it.jsonObject.getValue("path").jsonPrimitive.content to it.jsonObject.getValue("sha256").jsonPrimitive.content
            }
        }
    }

    test(
        "regenerate the fixture of the current version"
    ).config(enabled = System.getProperty("changes.writeFixtures") == "true") {
        runTest {
            val source = device().also { completeState().applyTo(it) }
            writeFixture(unzip(source.exportBytes()), File("src/test/resources/backup/v$CURRENT_SCHEMA_VERSION"))
        }
    }
})

private val pretty = Json { prettyPrint = true }

private fun prettyJson(bytes: ByteArray): ByteArray =
    (pretty.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(bytes.decodeToString())) + "\n").toByteArray()

private fun writeFixture(entries: Entries, dir: File) {
    dir.deleteRecursively()
    entries.replaceData(prettyJson(entries.getValue(BackupFormat.DATA_ENTRY)), recount = false)
    entries[BackupFormat.MANIFEST_ENTRY] = prettyJson(entries.getValue(BackupFormat.MANIFEST_ENTRY))
    for ((name, bytes) in entries) {
        val media = name.startsWith(BackupFormat.MEDIA_PREFIX)
        val file = File(dir, if (media) "$name.b64" else name).apply { parentFile.mkdirs() }
        if (media) file.writeText(Base64.getMimeEncoder().encodeToString(bytes) + "\n") else file.writeBytes(bytes)
    }
}

private fun fixtureEntries(version: Int): Entries {
    val url = checkNotNull(
        BackupFixtureSpec::class.java.getResource("/backup/v$version")
    ) { "Missing fixture v$version" }
    val root = File(url.toURI())
    val entries = Entries()
    entries[BackupFormat.DATA_ENTRY] = File(root, BackupFormat.DATA_ENTRY).readBytes()
    root.resolve("media").walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
        val name = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".b64")
        entries[name] = Base64.getMimeDecoder().decode(file.readText().trim())
    }
    entries[BackupFormat.MANIFEST_ENTRY] = File(root, BackupFormat.MANIFEST_ENTRY).readBytes()
    return entries
}
