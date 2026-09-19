# 0005. Gate de mutação: integração, supressões e pisos por pacote

**Status:** aceito (Fase 0)

## Contexto

A Seção 11.4 pede Pitest só no `:core`, com `pitest-junit5-plugin`, validação na Fase 0 de que um
mutante é morto por teste Kotest, `withHistory = true`, `pitest-suppressions.xml` com justificativa
e pisos por pacote verificados por script.

## Decisão

- **Integração: pitest-junit5-plugin falhou, trocado por `kotest-extensions-pitest`.** Na Fase 0 o
  pitest-junit5-plugin 1.2.3 matou mutantes com `FunSpec` plano (`ResultSpec`). Na Fase 1 ficou
  claro o problema: o plugin atribui cobertura a testes-folha (`[spec:X]/[test:Y]`) e reexecuta cada
  folha por unique id. O Kotest não executa isoladamente uma folha aninhada (`Given`/`Then`,
  `context`, `withData`), então essas execuções passavam sem rodar nada e mutantes óbvios
  "sobreviviam" (ex.: `DoseSchedule.occurrences` devolvendo lista vazia, com 32 execuções vazias).
  Como o dossiê exige `BehaviorSpec`, o plugin do Pitest passa a ser o do próprio Kotest
  (`io.kotest:kotest-extensions-pitest`, Apache-2.0, mesma versão do Kotest), que executa a spec
  inteira. Granularidade menor, resultado correto.
- **Fixtures sem cache.** O Pitest reaproveita a JVM entre mutantes; dataset e rótulos em `lazy`
  nos testFixtures escondiam mutações no código de leitura. Agora são lidos a cada acesso.
- **Sem `withHistory`.** No Pitest 1.30 a análise incremental foi retirada da distribuição aberta
  (o run falha em `ErroringHistoryFactory`). O `:core` roda completo em todo PR que o toca; o custo
  é baixo porque o módulo é pequeno e o Pitest usa todos os núcleos.
- **Supressões.** O Pitest não tem arquivo de supressão nativo. A tarefa `:core:pitestPackageGate`
  (finalizer de `:core:pitest`) lê `build/reports/pitest/mutations.xml`, remove os mutantes listados
  em `core/pitest-suppressions.xml` e falha se: alguma entrada não tiver justificativa; alguma entrada
  não casar com mutante real (supressão órfã); o score do módulo ficar abaixo de 82%; algum pacote
  ficar abaixo do piso (clinical 90, data 85, model 85, db 70). Publica a tabela em
  `build/reports/pitest/package-scores.md`, que o CI anexa ao run.
- O `mutationThreshold = 82` do plugin continua configurado e é aplicado sobre o número bruto, antes
  das supressões.
- **Ruído de Kotlin.** Além dos filtros da Seção 11.4, o código do `:core` evita as duas fontes de
  mutante equivalente encontradas na prática: funções `inline` (o corpo é copiado no chamador) e
  `when` exaustivo sobre tipo selado (o último `is` é checagem redundante). Regras em
  `docs/core-guidelines.md`.
- Código gerado pelo SQLDelight vive em `br.com.colman.changes.core.db.sql` e é excluído inteiro,
  tanto do Pitest quanto do Kover. O que sobra em `core.db` é código escrito à mão.
- Property tests rodam com 50 iterações dentro do Pitest (`-Dkotest.proptest.default.iteration.count`)
  e `-Dchanges.pitest=true` permite reduzir iterações explícitas pesadas. No `check` e no CI valem
  as iterações completas. As chamadas com número fixo passam por `propertyIterations(n)` (testFixtures),
  que devolve no máximo 40 numa rodada de mutação; o round-trip de backup já caía para 20.
- **`:app` também tem rodada de mutação**, só na lógica que roda na JVM: formatação, mapeamento de
  agenda do regime, altura, vocabulário, rótulos do corpo, categorias da lixeira, borrão da foto e os
  planejadores de lembrete. O plugin do Pitest depende do source set `test` do plugin `java`, que o AGP
  não expõe, então `:app:appPitest` chama a linha de comando do Pitest com o classpath do
  `testDebugUnitTest` e muta o jar de classes do app. Compose, Activity, Route e tudo que precisa de
  aparelho ficam de fora: não é código que a JVM executa.
- Piso do `:app`: 60% no módulo, verificado por `:app:appPitestGate`. É o nível medido quando a rodada
  entrou; subir o piso é o trabalho seguinte, não um ajuste de configuração.
- **Recorte para desenvolver.** `-Ppitest.classes=<globs>` troca o alvo dos dois módulos
  (ex.: `./gradlew :core:pitest -Ppitest.classes=br.com.colman.changes.core.model.*`, segundos em vez de
  minutos). Numa rodada recortada os gates não rodam: o recorte não representa o módulo.

## Consequências

`./gradlew pitestAll` roda os dois gates (`:core:pitest` e `:app:appPitest`). A rodada inteira do
`:core` leva cerca de 5 minutos em 24 núcleos (2900 mutantes, cada um reexecutando as specs que o
cobrem) e a do `:app`, cerca de 20 segundos. Baixar piso ou suprimir mutante exige ADR.
