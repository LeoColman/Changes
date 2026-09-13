# 0010. Lembretes: alarmes, notificações e reagendamento

**Status:** aceito (Fase 7; infraestrutura adiantada)

## Contexto

Seção 7.9: `AlarmManager` com `setExactAndAllowWhileIdle`, canal próprio, reagendamento em
`BOOT_COMPLETED` e depois de restore, pedido de `SCHEDULE_EXACT_ALARM`/`POST_NOTIFICATIONS` com
degradação graciosa, texto discreto e configurável, e preview oculto na tela bloqueada. Critério 7.9.3:
alarmes pendentes voltam depois de reiniciar o aparelho.

## Decisão

- **Uma regra de reconciliação** (`ReminderSync`): calcula os próximos lembretes, cancela os alarmes
  que deixaram de existir, agenda os atuais e guarda os códigos agendados. É idempotente e roda ao abrir
  o app, depois de cada alarme, depois de reinício, mudança de hora ou fuso, atualização do app e de todo
  import. A regra é testada em JVM com portas falsas (`AlarmGateway`, `ScheduledRegistry`).
- **Janela de 64 alarmes**, os mais próximos. O sistema limita alarmes por app; cada disparo reagenda a
  janela seguinte.
- **Exato quando permitido.** Com `canScheduleExactAlarms()` falso (Android 12+ sem a permissão
  especial), usa `setAndAllowWhileIdle`: o lembrete chega com alguns minutos de atraso em vez de não
  chegar. `ReminderPermissions` expõe o estado e o intent da tela do sistema para a feature de Ajustes.
- **Discrição.** Canal `reminders` com `lockscreenVisibility = VISIBILITY_SECRET`; notificação também
  `VISIBILITY_SECRET`; título = texto definido pela pessoa ou "Lembrete"; sem corpo; ícone neutro. Nenhum
  texto menciona medicação, dose ou transição. Sem `POST_NOTIFICATIONS`, nada é postado e o app segue
  funcionando.
- **Receivers não exportados.** Broadcasts do sistema (`BOOT_COMPLETED`, `TIME_SET`,
  `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`) chegam a receivers com `exported="false"`. Nenhum outro app
  consegue disparar reagendamento.
- **Injeção em receivers.** O sistema instancia `BroadcastReceiver` sem construtor injetável. A única
  ponte `KoinComponent` fora da Application é `app.di.ReceiverDependencies`, dentro do pacote onde a
  regra de DI permite.
- **Fontes.** Check-in diário (`SettingsUpcomingReminders`, três dias à frente); doses de regimes
  ativos com hora marcada (14 dias, só com o lembrete de dose ligado) e eventos com lembrete (60 dias)
  por `PlannedUpcomingReminders`, que usa o `ReminderPlanner` do `:core`: datas locais convertidas em
  instante com o fuso atual (mesma regra do `DoseSchedule`). A chave de cada lembrete é estável
  (`dose:<regime>:<dia>`, `event:<evento>:<dia>`), então reagendar não duplica.
- **Telas não chamam a sincronização.** `ReminderResyncer` observa regimes ativos, eventos com lembrete
  e os ajustes, e roda `ReminderSync` depois de 500 ms sem mudanças. Salvar um regime, um evento, um
  ajuste ou importar um backup reagenda sem que a feature saiba de alarmes.

## Consequências

Lembrete de dose com regime sem hora marcada não existe (a dose é de dia inteiro, ADR 0007). Mudar o
fuso reagenda tudo pela hora local do novo fuso.
