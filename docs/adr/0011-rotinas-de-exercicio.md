# 0011. Rotinas de exercício no calendário

**Status:** aceito

## Contexto

O dono do produto pediu, testando no aparelho, exercício contínuo ("toda quarta-feira", "uma vez por
semana", "diariamente") e que o resultado apareça no calendário. Uma sessão de exercício
(`exercise_session`) registra o que já aconteceu; uma rotina é um plano que se repete.

## Decisão

- Rotina de exercício é um evento manual do calendário com `category = EXERCISE` e `recurrence_rule` do
  subconjunto de RRULE que o calendário já expande: `FREQ=DAILY` ou `FREQ=WEEKLY;BYDAY=...`, com
  `INTERVAL`. Nenhuma tabela nova e nenhuma migração: `category` é texto sem `CHECK`.
- A agenda unificada, a tela Calendário e os lembretes (ADR 0010) já tratam eventos recorrentes, então a
  rotina aparece nos três sem código novo nessas partes.
- A tela de Exercício lista as rotinas por `CalendarRepository.observeByCategory(EXERCISE)` e cria,
  edita e exclui pelo mesmo repositório. Excluir manda para a lixeira como qualquer evento (ADR 0008).
- Registrar uma sessão continua separado: a rotina não cria sessão sozinha.

## Consequências

- Um backup com rotina importado numa versão anterior do app é recusado como dado inválido (categoria
  desconhecida), sem alterar nada, como acontece com qualquer valor novo de enum.
- "N vezes por semana sem dia fixo" não vira rotina: o calendário precisa de datas.
