// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.quality

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val FORBIDDEN_WEIGHT_TERMS = listOf(
    "sobrepeso",
    "obesidade",
    "obeso",
    "peso ideal",
    "meta de peso",
    "overweight",
    "obesity",
    "obese",
    "ideal weight",
    "weight goal",
    "target weight",
)

private val PRESCRIPTIVE_TERMS = listOf(
    "você deve",
    "você deveria",
    "o esperado para você",
    "você precisa tomar",
    "you should",
    "you must",
    "expected for you",
)

/** Termos de anatomia só saem do BodyVocabularyResolver, nunca de strings.xml (Seção 7.10). */
private val ANATOMY_TERMS = listOf(
    "clitóris",
    "clitoris",
    "clitoral",
    "vagina",
    "menstrua",
    "mamári",
    "seio",
    "peito",
    "tórax",
    "torax",
    "breast",
    "chest",
    "thorax",
    "pênis",
    "penis",
    "falo",
    "phallus",
    "genitália",
    "genital",
    "canal frontal",
    "front hole",
    "sangramento",
    "bleeding",
    "útero",
    "uterus",
)

/** Textos exibidos de um arquivo de recursos: conteúdo de `<string>` e `<item>`, sem comentários XML. */
private fun displayedTexts(file: File): List<String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    return listOf("string", "item").flatMap { tag ->
        val nodes = document.getElementsByTagName(tag)
        (0 until nodes.length).map { nodes.item(it).textContent.lowercase() }
    }
}

/** Varre todos os textos do app procurando termos proibidos (Seções 7.2, 7.4, 7.10 e 13). */
class StringsSpec : FunSpec({
    val stringFiles = File("src/main/res").walkTopDown()
        .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
        .toList()

    // Termo casa no início de palavra: "peito" não pode acusar "respeito".
    fun offenders(terms: List<String>) = stringFiles.flatMap { file ->
        val texts = displayedTexts(file)
        terms.filter { term ->
            val pattern = Regex("(?<![\\p{L}])" + Regex.escape(term))
            texts.any { pattern.containsMatchIn(it) }
        }.map { "${file.path}: $it" }
    }

    test("string resources exist, including the sensitive ones") {
        stringFiles.shouldNotBeEmpty()
        stringFiles.filter { it.name == "strings_sensitive.xml" }.shouldNotBeEmpty()
    }

    test("no weight classification or weight goals") {
        offenders(FORBIDDEN_WEIGHT_TERMS).shouldBeEmpty()
    }

    test("no prescriptive second person") {
        offenders(PRESCRIPTIVE_TERMS).shouldBeEmpty()
    }

    test("no anatomy terms outside the vocabulary resolver") {
        offenders(ANATOMY_TERMS).shouldBeEmpty()
    }

    test("no em-dash in any user-facing text") {
        offenders(listOf("—")).shouldBeEmpty()
    }

    test("every text exists in Portuguese and in English (Seção 9)") {
        fun keys(dir: String) = stringFiles.filter { it.parentFile.name == dir }.flatMap(::translatableNames).toSet()
        val portuguese = keys("values")
        val english = keys("values-en")
        (portuguese - english).shouldBeEmpty()
        (english - portuguese).shouldBeEmpty()
    }
})

/** Nomes dos textos traduzíveis de um arquivo de recursos (`translatable="false"` fica de fora). */
private fun translatableNames(file: File): List<String> {
    val children = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
    return (0 until children.length).map { children.item(it) }
        .filterIsInstance<Element>()
        .filter { it.getAttribute("translatable") != "false" }
        .map { it.getAttribute("name") }
}
