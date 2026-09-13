# 0006. Decisões de schema onde o dossiê é omisso

**Status:** aceito (Fase 1)

## Contexto

A Seção 6 lista tabelas e colunas, mas deixa aberto como representar alguns casos. Erro no schema
corrompe dado de saúde, então cada decisão fica registrada aqui.

## Decisão

1. **Compatibilidade com SQLite 3.18.** `minSdk = 26` traz SQLite 3.18. Nada de UPSERT
   (`ON CONFLICT DO UPDATE`), `RETURNING`, window functions ou `RENAME COLUMN`. O dialeto padrão do
   SQLDelight (3.18) valida os `.sq`. `INSERT OR REPLACE` é proibido porque apaga a linha e dispara
   efeitos de FK.
2. **Instantes.** Todo instante mostrado à pessoa tem coluna irmã `*_offset_seconds`, inclusive
   `end_at`, `completed_at` e `captured_at`. `created_at`, `updated_at` e `deleted_at` são carimbos
   técnicos, sem offset.
3. **Datas sem hora** (`hrt_start_date`, `start_date`, `end_date`, `diagnosed_at`, `resolved_at`,
   `entry_date`) são epoch day local. Não dependem de fuso.
4. **Colunas de auditoria** (`created_at`, `updated_at`, `deleted_at`) também nos catálogos e no
   perfil, porque itens customizados e o estado `is_hidden` precisam de merge.
5. **Builtin com id fixo.** Itens de catálogo builtin têm UUID fixo definido em
   `clinical/catalog.json`. Com id aleatório por instalação, importar backup de outro aparelho
   duplicaria o catálogo.
6. **Rótulos.** `body_change_type` e `lab_analyte` ganham `custom_label` para itens criados pela
   pessoa; itens builtin usam `label_key`, resolvido em runtime (rótulos de anatomia passam pelo
   `BodyVocabularyResolver`). Um CHECK garante que exatamente um dos dois existe. Código de tipo
   customizado é `CUSTOM_<id>`, o que torna colisão de `code` impossível sem adulteração.
7. **Permanência.** `expected_change.is_permanent` virou `permanence` (texto) +
   `permanence_source_key`. Ver ADR 0002.
8. **`is_reversible`** em `body_change_type` é anulável: NULL significa "a fonte não informa".
9. **`expected_change` e `app_meta` não são exportados.** São dados da versão do app, não da pessoa;
   são reseedados a cada abertura. O export cobre todas as outras tabelas.
10. **FKs.** `dose_log.regimen_id` é `ON DELETE SET NULL` (critério 7.1.4 para remoção definitiva).
    Exclusão normal é soft delete e mantém a FK. Mídia tem dono polimórfico, sem FK. As FKs são
    ligadas por conexão (`PRAGMA foreign_keys = ON`) pelos drivers.
11. **`mood_log.entry_date` UNIQUE vale para tombstones.** Registrar de novo numa data apagada
    reaproveita a linha (mesmo id, `deleted_at` limpo). No merge, `entry_date` é chave natural.
12. **`profile.show_bmi` nasce 0.** IMC é opt-in: o onboarding liga se a pessoa informar altura e
    quiser vê-lo.
13. **Medidas** são gravadas na unidade em que foram digitadas (`value` + `unit`), sem conversão na
    escrita. A UI converte para o sistema de unidades do perfil.
14. **Ajustes do aparelho** (tema, bloqueio biométrico, texto de notificação, lembretes, onboarding
    concluído) ficam em preferências locais do `:app`, fora do banco e do backup: são escolhas do
    aparelho, não dados de saúde.

## Consequências

Schema v1 = `ChangesDatabase.Schema.version` 1. A partir da v2, toda mudança vem com `.sqm`, dump da
versão anterior em `src/test/resources/schema/` e teste de migração.
