# 0007. Modelo de agenda de doses (pergunta em aberto 1)

**Status:** aceito provisoriamente; pergunta 1 da Seção 15 continua com o dono do produto

## Contexto

A pergunta 1 da Seção 15 é se dose fracionada semanal (ex.: 0,25 mL duas vezes por semana,
subcutâneo) precisa de modelagem própria ou se `INTERVAL_DAYS(3.5)` / `WEEKLY BYDAY` resolvem. A
Fase 2 precisa de uma resposta para existir.

## Decisão

- `INTERVAL_DAYS` só aceita número inteiro de dias (1..366). Intervalo de 3,5 dias não existe como
  data local: metade das doses cairia em horário diferente, o que quebra o critério 7.1.1 (intervalo
  constante em dias locais).
- "N vezes por semana" é `WEEKLY` com os dias escolhidos (`daysOfWeek`) e `everyWeeks` (1..52). Cada
  dia gera uma dose com a dose do regime. É o caso do exemplo: segunda e quinta, 0,25 mL cada.
- `CUSTOM_CRON` guarda uma regra do mesmo subconjunto de RRULE do calendário (`FREQ`, `INTERVAL`,
  `BYDAY`, `UNTIL`, `COUNT`), para casos como "a cada 3 meses" (Nebido).
- `AS_NEEDED` não gera doses previstas.
- `STEPPED` (pedido do dono do produto, testando no aparelho): intervalos que mudam de uma dose para a
  outra, como dose de ataque e depois manutenção ("primeira dose em 45 dias, a segunda 90 dias depois
  e daí uso contínuo a cada 90 dias"). `steps` são os dias desde a dose anterior (1 a 12 passos; o
  primeiro conta da data de início e aceita 0 = dose no próprio início; os demais de 1 a 366);
  `thenEvery` (1 a 366, opcional) repete depois do último passo. A série conta das datas previstas,
  não da dose registrada, como os outros tipos. `schedule_config` é validado já na leitura: um
  `thenEvery` zero vindo de backup adulterado faria a expansão da série andar para sempre.
- A tela de regime oferece, além de "a cada N dias", "semanal" e "quando necessário": "diariamente"
  (`INTERVAL_DAYS(1)`), "mensal" e "trimestral" (`CUSTOM_CRON` com `FREQ=MONTHLY;INTERVAL=N`, N = 3 no
  trimestral) e "intervalos variáveis" (`STEPPED`). São atalhos de tela sobre os tipos acima, sem tipo
  novo no banco além de `STEPPED`.
- `schedule_config` (JSON): `{"days":14}`, `{"daysOfWeek":["MONDAY","THURSDAY"],"everyWeeks":1}`,
  `{}`, `{"rrule":"FREQ=MONTHLY;INTERVAL=3"}`, `{"steps":[45,90],"thenEvery":90}`.
- A série é calculada em datas locais e só vira instante na hora de agendar lembrete, com o fuso
  atual do aparelho. Sem `time_of_day`, a dose prevista é de dia inteiro e não gera lembrete.

## Consequências

Se o dono do produto quiser doses diferentes por dia da semana (ex.: 0,25 mL na segunda e 0,5 mL na
quinta), a resposta é dois regimes, ou uma v2 do schema com dose por dia. Registrado como pendência
no resumo de entrega.

Um backup com regime `STEPPED` importado numa versão anterior do app é recusado como dado inválido
(tipo de agenda desconhecido), sem alterar nada, como acontece com qualquer valor novo de enum.
