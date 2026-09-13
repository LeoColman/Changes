# 0004. Regras estáticas: o que fica no Detekt e o que vira teste Konsist

**Status:** aceito (Fase 0)

## Contexto

A Seção 10 pede Detekt com zero findings e sem baseline, `ForbiddenImport` para uma lista de
imports, e duas regras custom: Dispatchers literais só em `*Module.kt` e service locator do Koin só
em `app.di`. A Seção 4.2 pede que as fronteiras de pacote sejam testadas com Konsist.

Restrições encontradas:

- Detekt 1.23.8 com AGP 9 quebra a integração por variante (tarefas `detektMain`/`detektDebug`).
  Rodar sem type resolution é o caminho estável. `ForbiddenMethodCall` exige type resolution.
- `ForbiddenImport` é uma instância única de regra, com uma única lista de `excludes`. Não dá para
  excluir `src/test` para `runBlocking` e ao mesmo tempo liberar Koin só em `app.di`.
- Regra Detekt custom exige um módulo Gradle de regras. A Seção 4 limita o projeto a dois módulos.

## Decisão

- Uma única tarefa `detekt` na raiz analisa `core` e `app` (todos os source sets), sem type
  resolution. `detektFormat` aplica as correções do ktlint e nunca roda no `check`.
- `ForbiddenImport`: `android.util.Log`, `java.util.Date`, `java.text.SimpleDateFormat`,
  `GlobalScope`, `runBlocking`. Exclui `src/test`, `testFixtures`, `androidTest` e `AppLogger.kt`.
- Dispatchers: regra nativa `InjectDispatcher` (sem type resolution), com `Main` adicionado à lista
  e `**/*Module.kt` excluído. Um teste Konsist repete a regra pelo import de
  `kotlinx.coroutines.Dispatchers`, como rede de segurança.
- Koin: teste Konsist proíbe `KoinComponent`, `inject` e `get` de `org.koin.core.component` fora
  de `br.com.colman.changes.di`.
- Fronteiras da Seção 4.2, SPDX em `.kt` e `.sq`: testes Konsist/JVM em
  `app/src/test/.../architecture/ArchitectureSpec.kt`. Rodam no `check`.
- SQLDelight também é permitido em `app.platform`, porque a Seção 3.1 manda o driver Android (e a
  futura troca por SQLCipher) morar lá. Features continuam proibidas.
- `LongParameterList`: "6" lido como "até 6 parâmetros" (`functionThreshold`/`constructorThreshold`
  = 7, que é o número que dispara a regra). `LongMethod` 40 e `CyclomaticComplexMethod` 12 como
  escrito.
- Comprimento de linha (o dossiê não fixa): 120 colunas em código de produção (`style.MaxLineLength`,
  que exclui testes) e 150 em tudo, testes incluídos (`formatting.MaximumLineLength`, do ktlint).
  Asserts com literais longos leem melhor numa linha; o limite de produção não muda.
- Mapeadores de linha ficam em arquivos por assunto (`*Mappers.kt`), para respeitar
  `TooManyFunctions` sem mexer no limite.
- Arquivos `.sq` usam `--` no cabeçalho SPDX, porque `//` não é comentário SQL.
- Teste de manifesto: implementado como tarefa Gradle `verify<Variant>MergedManifest`, ligada ao
  `check`, que lê o artefato `MERGED_MANIFEST` do AGP. Um teste unitário não tem acesso estável ao
  manifesto mergeado.

## Consequências

Nenhuma regra da Seção 10 ficou sem verificação automática. Duas delas moraram no Konsist em vez
do Detekt; o efeito no build é o mesmo (o `check` falha).
