# 0012. Sentimentos do check-in e schema v2

**Status:** aceito

## Contexto

O dono do produto pediu que os sentimentos do check-in diário sigam os sentimentos comuns no início da
testosterona: alívio e bem-estar, irritabilidade e impaciência, intensidade emocional e ansiedade. O
check-in já tinha humor, energia, ansiedade e disforia (escalas 1 a 5) e horas de sono.

## Decisão

- Três colunas novas em `mood_log`, opcionais, com o mesmo `CHECK` 1..5 das outras escalas: `relief`
  (alívio e bem-estar), `irritability` (irritabilidade e impaciência) e `emotional_intensity`
  (intensidade emocional). `anxiety` já existia. Humor, energia, disforia e sono continuam.
- Primeira migração do projeto: `migrations/1.sqm` leva o schema de 1 para 2 com `ALTER TABLE ADD
  COLUMN`. As colunas ficam no fim também no `CREATE TABLE` de `Mood.sq`, para que banco novo e banco
  migrado tenham as mesmas colunas na mesma ordem; um teste compara as duas rotas coluna a coluna.
- `schema/v2.sql` congelado. `schema/v1.sql` continua: um backup v1 entra pelo banco temporário v1 e
  pela mesma migração (ADR 0009). A fixture de export v1 continua no teste e ganha a irmã v2.
- A descrição curta de cada sentimento fica em `strings_sensitive.xml`. Ela descreve, não interpreta:
  nada no app tira conclusão das escalas (critério 7.7.2).

## Consequências

Um backup feito com schema 2 e importado num app de schema 1 é recusado como "versão mais nova", sem
alterar nada.
