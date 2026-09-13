# 0001. Stack, versões e toolchain

**Status:** aceito (Fase 0)

## Contexto

O dossiê (Seção 3) fecha a stack, mas manda resolver as versões estáveis mais recentes no momento da
implementação. Algumas combinações têm atrito conhecido (AGP 9 com Kotlin embutido, Detekt 1.x com
Kotlin 2.4, Pitest com Kotest).

## Decisão

- Versões estáveis em 2026-09-12, fixadas em `gradle/libs.versions.toml`: AGP 9.4.0, Kotlin 2.4.20,
  Gradle 9.7.1, SQLDelight 2.3.2, Koin 4.2.2, Compose BOM 2026.09.00, Kotest 6.2.5, Detekt 1.23.8,
  Kover 0.9.9, gradle-pitest-plugin 1.19.0, Pitest 1.30.0, pitest-junit5-plugin 1.2.3, Konsist 0.17.3.
- `compileSdk = targetSdk = 37` (Android 17 é estável), `minSdk = 26`.
- AGP 9 com Kotlin embutido: o `:app` não aplica `org.jetbrains.kotlin.android`. A versão do KGP
  vem do plugin `org.jetbrains.kotlin.jvm` declarado com `apply false` na raiz.
- JDK: o daemon do Gradle roda em JDK 21 (`gradle/gradle-daemon-jvm.properties`); o bytecode é
  Java 17 nos dois módulos (`jvmToolchain(17)` no `:core`, `compileOptions` 17 no `:app`).
- Material Icons Extended não está mais no BOM do Compose; fica fixado em 1.7.8.
- `kotlin.uuid.ExperimentalUuidApi` e `kotlin.time.ExperimentalTime` ficam com opt-in global.
- Kotest 6 descobre a configuração de projeto pela propriedade `kotest.framework.config.fqn`,
  apontada para `KotestProjectConfig` (testFixtures do `:core`), que fixa a seed dos property tests.
- O `gradle-wrapper.jar` é o único binário versionado. É o artefato padrão do Gradle, verificado
  pelo F-Droid.

## Consequências

- Atualizar versões é trocar o catálogo. Nenhum número de versão vive fora dele, exceto o do Gradle
  no wrapper.
- A troca do daemon para JDK 21 evita que o compilador Kotlin 2.0.21 embutido no Detekt 1.23.8
  rode sobre JDK 25.

## Testes instrumentados no API 37

`androidx.test.espresso:espresso-core` fica fixado em 3.7.0 como dependência de `androidTest`. A versão
que chega pelo `ui-test-junit4` do BOM (3.5.0) chama `InputManager.getInstance()` por reflexão, e o
Android 17 (API 37) removeu esse método: toda regra de teste Compose falhava antes do primeiro passo.
