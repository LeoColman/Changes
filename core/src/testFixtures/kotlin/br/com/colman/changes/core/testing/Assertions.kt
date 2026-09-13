// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

/**
 * Igualdade por `equals()`, que compara campos. O `shouldBe` do Kotest compara data classes por
 * reflexão sobre as propriedades, chamando os getters dos dois lados: um getter mutado pelo Pitest
 * devolve o mesmo valor errado para o esperado e para o obtido, e o mutante sobrevive. Use este
 * helper para comparar objetos do modelo inteiros (docs/core-guidelines.md, regra 13).
 */
public infix fun <T> T.shouldEqual(expected: T) {
    if (this != expected) throw AssertionError("expected:<$expected> but was:<$this>")
}
