# 0009. Backup: formato, validação, migração e merge

**Status:** aceito (Fase 8; decidido antes para orientar a Fase 1)

## Contexto

Seção 7.8: `.ttbackup.zip` com `manifest.json`, `data.json` e `media/`; tombstones incluídos;
export por SAF; import em dois modos (Mesclar e Substituir) com prévia antes de executar; validação
antes de qualquer escrita; transação única com rollback total; migração de schema antigo no import
usando as mesmas migrações do banco; round-trip byte-equivalente; export em streaming. É o ponto
onde perda silenciosa de dados acontece, então cada decisão fica registrada aqui.

## Decisão

### Formato

- Entradas do zip: `data.json`, depois `media/<arquivo>` na ordem de `relative_path`, e por último
  `manifest.json`. O manifesto vai no fim porque só se conhece o SHA-256 de `data.json` e das mídias
  depois de escrevê-las; assim o export faz uma única passada, em streaming. O import abre o zip com
  acesso aleatório (`ZipFile`, a partir de uma cópia no cache privado), então a ordem não importa
  para ler.
- `manifest.json`: `format` (`"changes-backup"`), `formatVersion`, `schemaVersion`, `appVersion`,
  `exportedAt`, `counts` (linhas por tabela), `dataSha256` e a lista de mídias com `path`, `sha256` e
  `size`.
- `data.json`: um objeto com uma chave por tabela e um array de objetos por tabela; cada objeto tem
  exatamente as colunas daquela versão do schema, com os nomes das colunas, valores como estão no
  banco (inteiros, reais, texto, `null`). Linhas ordenadas por `id`.
- Tabelas exportadas (ordem de inserção compatível com as FKs): `profile`, `medication`, `regimen`,
  `dose_log`, `body_change_type`, `body_change_entry`, `media_attachment`, `measurement`,
  `exercise_session`, `health_condition`, `lab_analyte`, `lab_result`, `mood_log`, `calendar_event`.
  Ficam de fora `app_meta` e `expected_change` (dados do app, reseedados). Um teste falha se aparecer
  tabela nova no schema sem estar em uma das duas listas.
- Mídia exportada: todo arquivo referenciado por `media_attachment`, inclusive de itens na lixeira
  (a pessoa pode restaurá-los no outro aparelho). Arquivos já purgados não existem mais.

### Export

Linhas lidas por cursor e escritas uma a uma (`PRAGMA table_info` dá nomes e tipos declarados;
`SELECT` explícito das colunas). Mídia copiada em blocos, com hash calculado durante a cópia.
Memória constante em relação ao tamanho da base (critério 7.8.5, testado com heap limitado).

### Import: planejar, mostrar, aplicar

1. **Cópia** do documento SAF para um arquivo temporário no cache privado.
2. **Validação sem escrita** (`plan`):
   - manifesto legível e `format` correto; `schemaVersion` entre 1 e a atual (maior: rejeitado com
     mensagem clara, critério 7.8.4);
   - SHA-256 de `data.json` confere (critério 7.8.3); SHA-256 e tamanho de cada mídia conferem;
   - manifesto lido ignorando campos desconhecidos, para que um arquivo de versão futura seja recusado
     como "versão mais nova" e não como "não é um backup"; tetos de 16 MiB para o manifesto e 64 MiB
     para `data.json` contra arquivo hostil;
   - `data.json` carregado num **banco temporário em memória** criado na versão do backup, sempre a
     partir do DDL congelado em `schema/v<N>.sql` (existe também para a versão atual, e um teste exige
     que seja idêntico ao `Schema.create`). CHECK, NOT NULL e UNIQUE valem na carga; as FKs ficam
     desligadas durante a carga (a ordem das tabelas deixa de importar) e `PRAGMA foreign_key_check`
     confere o banco inteiro logo depois. Depois, `Schema.migrate(temp, N, atual)`: as mesmas migrações
     do banco (Seção 7.8). A tabela `android_metadata`, que o Android cria em todo banco, não conta;
   - toda linha do banco temporário passa pelos mesmos mapeadores que o app usa para ler (enum,
     UUID, offset, JSON). Se o app não conseguiria ler, o backup é rejeitado antes de tocar em nada;
   - toda mídia referenciada por anexo não excluído existe no zip, e o checksum do anexo é igual ao
     do arquivo; caminhos passam por `MediaPaths.isValid` (sem `..`, sem diretório). Exceção: anexo
     vivo cujo arquivo já não existia no aparelho de origem é declarado em `missingMedia` no manifesto;
     o import aceita e a prévia mostra quantos são;
   - resultado: um plano com contagens por tabela (inseridas, atualizadas, iguais, mantidas) para
     a tela de prévia.
3. **Aplicação** (`apply`), numa única transação do banco principal com
   `PRAGMA defer_foreign_keys = ON`:
   - **Mesclar**: upsert por `id`. Vence a linha com maior `updated_at`; empate é decidido pela
     comparação canônica da linha inteira (coluna a coluna, `null` < inteiro < real < texto). Essa
     ordem total torna o merge comutativo (`merge(a, b) == merge(b, a)`) e idempotente.
     `mood_log.entry_date` é chave natural: se outra linha ocupa a data, a vencedora pela mesma regra
     fica e a outra sai. Colisão de `code` em catálogo com ids diferentes só acontece com arquivo
     adulterado e rejeita o import.
   - **Substituir**: apaga todas as tabelas exportáveis e insere o backup. Exige confirmação digitada.
   - mídias do backup são gravadas numa pasta de staging e movidas para o nome final antes do
     commit; se qualquer passo falhar, rollback do banco e remoção das mídias novas (critério 7.8.3).
     Arquivos que já existiam não são tocados.
   - `PRAGMA foreign_key_check` antes do commit; violação aborta.
   - depois do commit: `Seeder.seed()` (catálogos e dataset do app atual) e, no modo Substituir,
     varredura de mídias órfãs.
4. O app reagenda lembretes depois de qualquer import (Seção 7.9).
5. **Recusas** chegam como `DomainError.BackupRejected(reason)`: `UNREADABLE`, `NOT_A_BACKUP`,
   `NEWER_VERSION`, `UNSUPPORTED_VERSION`, `CHECKSUM_MISMATCH`, `MISSING_MEDIA`, `INVALID_DATA`, e as
   de armazenamento `EXPORT_FAILED` e `IMPORT_FAILED` (disco cheio durante a aplicação: staging
   descartado, nada gravado). `apply` valida o arquivo de novo, porque ele pode ter mudado desde a
   prévia. Se o processo morrer depois do commit e antes de mover as mídias, a abertura seguinte do
   app completa ou descarta o staging (`recoverInterruptedImports`).

### Testes (Seção 11.2)

- Round-trip por property test (`Arb<DatabaseState>` com tombstones, mídias, catálogos customizados,
  unicode e strings vazias): exportar, importar em banco limpo no modo Substituir, e comparar o dump
  ordenado de todas as tabelas e os checksums das mídias. 1000 iterações no CI.
- Mesclar duas vezes o mesmo arquivo: nada muda na segunda vez.
- Comutatividade: dois exports independentes com ids em comum mesclados nas duas ordens dão o mesmo
  dump.
- Adulteração de `data.json`, mídia faltando, checksum de mídia errado, versão maior, enum
  desconhecido, offset fora de ±18h, caminho com `..`: todos rejeitados, banco e mídias intactos.
- Fixture versionada de export da v1 (em texto, montada em zip pelo teste): quando existir v2, ela
  vira o teste de "import de versão anterior" do critério 7.8.4. Fica em
  `core/src/test/resources/backup/v1/` (JSON formatado, mídias em base64, `-text` no `.gitattributes`).
- Critério 7.8.5: task `stressTest` (tag Kotest `Stress`, parte do `check`) exporta 10k registros e
  500 fotos (125 MB) com 128 MB de heap e valida o arquivo gerado.

## Consequências

- O formato não depende do código gerado pelo SQLDelight: é um espelho das colunas. Uma migração de
  schema não quebra backups antigos, porque eles passam pelas migrações no banco temporário.
- O import custa memória proporcional a `data.json` (dezenas de MB para bases muito grandes), não às
  mídias, que são sempre copiadas em streaming.
