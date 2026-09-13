# 0003. Catálogo de medicações: concentrações conferidas nas bulas

**Status:** aceito (Fase 1)

## Contexto

A Seção 8.2 lista concentrações típicas para autocompletar. A concentração alimenta a conversão
mg/mL do app, então um valor errado no seed vira um registro de dose errado.

Conferência em 2026-09-12:

- **Deposteron** (EMS): bula registrada na ANVISA diz cipionato de testosterona **100 mg/mL**,
  ampola de 2 mL (200 mg por ampola). O dossiê dizia 200 mg/mL.
- **Axeron** (Eli Lilly): solução tópica **a 2%**, 30 mg por 1,5 mL (20 mg/mL), aplicação axilar.
  O dossiê juntava Axeron com Androgel (gel 1%, 10 mg/g).
- Durateston (250 mg/mL de ésteres), Nebido (1000 mg/4 mL = 250 mg/mL) e Androgel (gel 1% = 10 mg/g)
  conferem.

## Decisão

- Deposteron seedado com 100 mg/mL.
- Androgel e Axeron viram duas entradas: Androgel gel 10 mg/g; Axeron solução tópica 20 mg/mL.
- Hormus / enantato genérico, Finasterida e Minoxidil sem concentração (o dossiê não dá, e a
  concentração varia por apresentação).
- Nenhuma dose é seedada (Seção 8.2).

## Consequências

Dossiê de produto corrigido na Seção 8.2.
