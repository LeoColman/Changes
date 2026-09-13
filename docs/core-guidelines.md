# Regras de código do `:core`

Valem para quem escreve no `:core`. Existem para que os gates (Detekt,
Kover 80%, Pitest por pacote) meçam o que importa.

## Estrutura

- `model/`: tipos de domínio puros, sem SQLDelight, sem JSON, sem I/O.
- `db/`: código escrito à mão sobre o banco (fábrica do banco, seed, histórico de schema).
  `db/sql/` é **só** código gerado pelo SQLDelight (os `.sq` ficam em
  `src/main/sqldelight/br/com/colman/changes/core/db/sql/`).
- `data/`: repositórios. Leitura devolve `Flow`, escrita é `suspend` e devolve `Result`.
- `clinical/`: dataset clínico e regras puras de faixa temporal.
- `media/`: interface de armazenamento de mídia (a implementação fica no `:app`).
- `di/`: `coreModule`.

## Regras

1. `explicitApi()` está ligado: tudo que é público declara `public` e tipo de retorno.
2. Sem `inline` (exceto quando `reified` for indispensável): o Pitest não mata mutantes em corpo inline.
3. Sem `when` exaustivo sobre tipo selado com checagem `is` no último ramo. Use `if (x is A) ... else (x as B)`
   para dois casos, ou `else ->` no último ramo. O último `is` vira checagem redundante e gera
   mutante equivalente.
4. Erros esperados são `Result.Failure(DomainError)`, nunca exceção. Exceção só para bug.
5. Nenhum `Dispatchers.*` literal: receba `CoroutineDispatcher` no construtor. O `coreModule` o amarra
   com `named("io")`.
6. Nenhum relógio implícito: receba `kotlin.time.Clock` e, quando precisar de fuso, `TimeZoneProvider`.
7. Todo instante mostrado à pessoa é gravado como `*_at` (epoch millis UTC) + `*_at_offset_seconds`.
   Datas sem hora (data de início da TH, datas de regime, data do check-in) são epoch day local.
8. IDs são `Uuid` v4 como texto. Itens builtin de catálogo têm id fixo, definido no JSON de seed.
9. Nenhuma query sem `deleted_at IS NULL` alimenta a UI, salvo as de lixeira e as de merge.
10. Todo arquivo `.kt` começa com as duas linhas SPDX; todo `.sq`/`.sqm` com o equivalente em `--`.
11. Teste com Kotest (`FunSpec` para unidade, `BehaviorSpec` para regra de domínio). Banco em teste:
    `JdbcSqliteDriver` em memória via `testDatabase()` dos testFixtures.
12. Casos-limite obrigatórios em regras com data: sem `hrt_start_date`, regime sem fim, meses de 28/31
    dias, 29 de fevereiro, horário de verão.
13. Para comparar objetos inteiros do modelo em teste, use `shouldEqual` (testFixtures), não `shouldBe`.
    O `shouldBe` do Kotest compara data classes chamando os getters dos dois lados e esconde mutantes
    de getter. Para campos individuais, `shouldBe` é ok.
14. Nada de cache estático em testFixtures (`lazy`, `object` com estado): o Pitest reaproveita a JVM.
15. Evite retorno antecipado que devolve o mesmo valor que o caminho normal devolveria (ex.:
    `return emptyList()` antes de um laço que já daria vazio) e checagens cujo efeito outra checagem
    já cobre: são mutantes equivalentes. Prefira um único ponto de retorno.
16. Evite funções inline da biblioteca com lógica própria (`compareBy`, `sequence {}`, `use {}`) no
    caminho crítico: o código delas é copiado para a sua classe e mutado. Laço simples resolve.
