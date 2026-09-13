# Regras de código do `:app`

Contrato para quem escreve uma feature.

## Estrutura de uma feature

Pacote `br.com.colman.changes.feature.<nome>`. Uma feature nunca importa outra (teste Konsist).
Navegação para outra feature acontece por callback que o grafo (`nav/`) liga.

Para cada tela `X`:

- `XUiState`: `@Immutable data class`, só com tipos imutáveis (`List`, `String`, `LocalDate`,
  modelos do `:core`). Nada de `MutableState` exposto.
- `XUiEvent`: `sealed interface` com as ações da tela.
- `XViewModel(repos...)`: expõe `val state: StateFlow<XUiState>` (via
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)`) e `fun onEvent(event: XUiEvent)`.
  Efeitos de uma vez (snackbar, navegar depois de salvar) saem por `Channel` exposto como `Flow`.
- `XScreen(state, onEvent, modifier)`: composable **sem estado próprio de negócio**. Não conhece
  ViewModel, Koin nem navegação.
- `XRoute(onNavigate..., viewModel = koinViewModel())`: wrapper fino que coleta o estado com
  `collectAsStateWithLifecycle()`, trata efeitos e chama `XScreen`.
- `<Nome>Module.kt`: `val <nome>Module: Module = module { viewModelOf(::XViewModel) }`. O módulo entra
  na lista em `di/AppModules.kt` e as rotas em `nav/`, numa mudança separada da feature.

## Dependências

- Repositórios do `:core` (`br.com.colman.changes.core.data`) por construtor. Nunca `SqlDriver`,
  queries geradas ou `ChangesDatabase`.
- Serviços de plataforma por interface/classe de `br.com.colman.changes.platform` (`SettingsStore`,
  `PhotoSanitizer`, `MediaStorage`). Features não importam `android.content.Context`,
  `AlarmManager`, `ExifInterface` nem `BiometricPrompt` (teste Konsist).
- Relógio e fuso: `kotlin.time.Clock` e `TimeZoneProvider` injetados. Nada de `Clock.System` direto.
- Nenhuma dependência nova e nenhuma permissão nova sem discutir antes numa issue.

## Componentes compartilhados (`ui/`)

`ChangesScreen` (barra superior, voltar, snackbar, FAB), `EmptyState`, `LoadingState`,
`SectionHeader`, `FormColumn`, `ConfirmDialog`, `TypedConfirmDialog`, `Formatters` (data, hora,
número por locale; `parseNumber` para campos numéricos). Use antes de criar componente novo.

## Texto

- Todo texto de UI em `res/values/strings_<feature>.xml` (pt-BR, idioma padrão) e
  `res/values-en/strings_<feature>.xml`, chaves com prefixo `<feature>_`. Nada de texto fixo em código.
- `res/values/strings_sensitive.xml` tem redação revisada à parte: telas de mudanças esperadas, condições,
  exames, saúde mental e vocabulário corporal usam **só** essas strings para frases de conteúdo.
  Não edite, não copie com outra redação.
- Frase com contagem é `<plurals>` e se usa com `pluralStringResource(id, count, args...)`. Em
  `strings_sensitive.xml` são plurais: `expected_chart_summary`, `labs_chart_summary`,
  `mood_heatmap_summary`, `mood_trend_summary`, `adherence_sentence`.
- Nomes de partes do corpo nunca aparecem em `strings.xml`: vêm do `BodyVocabularyResolver`.
- Tom: descritivo, sem julgamento. Proibido: "você deve", metas, streaks, "sobrepeso", "obesidade",
  "peso ideal", cores de alarme, emoji de celebração. Estado vazio explica o que a tela faz.
- Sem travessão longo (U+2014) em texto nenhum.

## Acessibilidade (Seção 9)

- `contentDescription` em todo ícone/imagem que não seja decorativo (decorativo: `null`).
- Alvo de toque >= 48dp (os componentes do Material 3 já garantem; não encolha).
- Texto quebra linha em vez de truncar; nada de `maxLines = 1` em conteúdo da pessoa.
- Gráfico desenhado com `Canvas` expõe resumo textual em `Modifier.semantics { contentDescription = ... }`.
- Títulos de seção com `SectionHeader` (anunciados como cabeçalho).

## Dados sensíveis

- Exclusão é soft delete com "Desfazer" no snackbar (chama `restore`).
- Foto: sempre `PhotoSanitizer` antes de `MediaRepository.attach`. Nunca grave foto em outro lugar.
- Nunca logue dado da pessoa. Log técnico só por `AppLogger`.

## Testes

- ViewModel: Kotest `FunSpec`, `MainDispatcherListener` (testFixtures), Turbine, repositórios reais
  sobre `testDatabase()` com `FixedClock` e `FixedTimeZoneProvider`. Um spec por ViewModel em
  `app/src/test/kotlin/br/com/colman/changes/feature/<nome>/`.
- Critério de aceite vira teste com o número do critério no nome.
- Detekt: zero findings. `./gradlew detektFormat` corrige formatação.
- Comando de verificação de toda feature:
  `./gradlew :app:testDebugUnitTest detekt :app:lintDebug`.
