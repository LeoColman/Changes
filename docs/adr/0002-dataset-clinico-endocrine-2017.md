# 0002. Faixas temporais corrigidas contra a Endocrine Society 2017

**Status:** aceito (Fase 1)

## Contexto

A Seção 8.4 manda verificar cada faixa da tabela 8.1 contra a fonte antes de seedar, e diz que,
havendo divergência, prevalece a fonte e o documento é corrigido.

Verificação feita em 2026-09-12:

- **ENDO_2017**: Hembree WC et al., J Clin Endocrinol Metab 2017;102(11):3869-3903, **Tabela 12**
  ("Masculinizing Effects in Transgender Males", p. 3889). Lida no PDF do repositório institucional
  da Amsterdam UMC (cópia do artigo publicado).
- **UCSF_2016**: "Overview of masculinizing hormone therapy", Deutsch MB, 17/06/2016. A página
  **não tem** tabela de início/efeito máximo. Afirma, em texto, que engrossamento da voz e
  crescimento da barba são irreversíveis.
- **WPATH_SOC8**: Coleman E et al., Int J Transgend Health 2022 (PMC9553112). Não tem tabela de
  faixas. No capítulo 8 (pessoas não binárias), diz que, suspensa a testosterona, amenorreia e
  desenvolvimento de pelos corporais revertem, e que calvície de padrão masculino, crescimento
  genital e crescimento de pelos faciais são permanentes (citando Hembree 2017). No capítulo de
  voz, fala do impacto permanente da testosterona na voz.

A tabela 8.1 do dossiê reproduz os valores de Hembree 2009 / WPATH SOC7, não os de 2017. Diverge
em 7 das 9 linhas.

## Decisão

Seed com os valores da Tabela 12 de 2017, todos com `source_key = ENDO_2017`:

| code | Início | Efeito máximo |
|---|---|---|
| SKIN_OILINESS_ACNE | 1-6 meses | 1-2 anos |
| FACIAL_BODY_HAIR | 6-12 meses | 4-5 anos |
| SCALP_HAIR_LOSS | 6-12 meses | não informado (nota a da tabela) |
| MUSCLE_MASS_STRENGTH | 6-12 meses | 2-5 anos |
| FAT_REDISTRIBUTION | 1-6 meses | 2-5 anos |
| MENSES_CESSATION | 1-6 meses | não informado (nota b da tabela) |
| CLITORAL_ENLARGEMENT | 1-6 meses | 1-2 anos |
| VAGINAL_ATROPHY | 1-6 meses | 1-2 anos |
| VOICE_DEEPENING | 6-12 meses | 1-2 anos |

A Tabela 12 não tem coluna de permanência. Em vez do booleano `is_permanent`, `expected_change`
guarda `permanence` (`PERMANENT`, `PARTIALLY_PERMANENT`, `NOT_PERMANENT`, `NOT_STATED`) e
`permanence_source_key`, só com o que as fontes afirmam:

| code | Permanência | Fonte |
|---|---|---|
| FACIAL_BODY_HAIR | PARTIALLY_PERMANENT (facial permanente, corporal regride) | WPATH_SOC8 |
| SCALP_HAIR_LOSS | PERMANENT | WPATH_SOC8 |
| MENSES_CESSATION | NOT_PERMANENT | WPATH_SOC8 |
| CLITORAL_ENLARGEMENT | PERMANENT | WPATH_SOC8 |
| VOICE_DEEPENING | PERMANENT | WPATH_SOC8 |
| demais | NOT_STATED | nenhuma fonte afirma |

`UCSF_2016` continua na lista de referências (a UI exibe a lista completa), mas nenhuma faixa cita
UCSF, porque a página não publica faixas.

Meses a partir de anos: 1 ano = 12 meses. A regra de estado usa meses médios (365,2425 / 12 dias).

## Consequências

- A tabela 8.1 do dossiê de produto foi corrigida, com referência a este ADR.
- Quatro linhas do dossiê que diziam "Não" para permanência (acne, massa muscular, gordura, atrofia)
  viram "a fonte não informa". A UI mostra isso como ausência de afirmação, não como "reversível".
- `body_change_type.is_reversible` é derivado da mesma fonte no seed: `PERMANENT` vira 0,
  `NOT_PERMANENT` vira 1, o resto fica NULL.
