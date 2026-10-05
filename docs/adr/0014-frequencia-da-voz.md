# 0014. Frequência da voz estimada da gravação e gráfico

**Status:** aceito

## Contexto

O dono do produto pediu um gráfico na tela de Voz mostrando o quanto a voz da pessoa está ficando mais
grave com o tempo. O tipo `VOICE_DEEPENING` já tinha medida em Hz (frequência fundamental, Seção 8.1) e
cada entrada já podia ter a gravação de uma frase fixa (ADR 0013), mas quase ninguém sabe a própria
frequência para digitar.

## Decisão

- **Estimativa no aparelho.** Ao parar a gravação, o app estima a mediana da frequência fundamental da
  frase e preenche a medida em Hz, arredondada ao inteiro. Nada sai do aparelho e nenhuma dependência ou
  permissão nova entra.
- **Algoritmo.** `platform/VoicePitch.kt`, Kotlin puro, testado na JVM e no Pitest do `:app`. O PCM é
  reduzido a cerca de 11 kHz pela soma de blocos (filtro passa-baixa simples), cortado em quadros de 10 ms,
  e cada quadro com pelo menos 1% da energia do mais forte passa pelo YIN (de Cheveigné e Kawahara, 2002):
  limiar absoluto 0,15, descida até o mínimo local e interpolação parabólica. Busca entre 60 e 400 Hz;
  vale abaixo de 60 Hz deixa o quadro sem estimativa em vez de devolver um valor errado. Menos de 10
  quadros com voz (0,1 s) não dão estimativa. A mediana dos quadros ignora os poucos que erram a oitava.
- **Decodificação.** `AndroidVoicePitchAnalyzer` lê o AAC com `MediaExtractor` e `MediaCodec`, junta o
  PCM de 16 bits em mono e roda a estimativa no dispatcher de I/O. Um teste instrumentado codifica uma
  voz sintética com o codificador do aparelho e confere a fundamental de volta.
- **O valor é da pessoa.** A estimativa só preenche um campo vazio; um valor digitado nunca é trocado
  sozinho. "Estimar a frequência da gravação" estima de novo e troca o campo, e serve também para
  gravações feitas antes desta versão. Apagar a gravação apaga junto a medida que veio dela, se ninguém
  editou o valor. Salvar logo depois de parar espera a estimativa terminar. Gravação sem voz suficiente
  avisa e deixa o campo para digitar.
- **Gráfico.** Na tela de um tipo da categoria `VOICE` com medida em Hz, no topo: linha com a
  frequência de cada entrada no tempo (`LineChart`), primeiro e mais recente valor por extenso, e resumo
  textual para o TalkBack. Sem entradas com Hz, a seção explica como o gráfico aparece.
- **Faixa de referência.** Faixa sombreada de 90 a 155 Hz, cor neutra (`secondaryContainer`), com a
  frase "a variação individual é grande e estar fora da faixa não indica problema", no mesmo tom do aviso
  das mudanças esperadas. Fonte: Fitch e Holbrook, *Modal vocal fundamental frequency of young adults*,
  Archives of Otolaryngology 92(4):379-382, 1970 (100 homens adultos jovens), como citada em Baken e
  Orlikoff, *Clinical Measurement of Speech and Voice*, 2ª ed., 2000, p. 177. Texto e fonte ficam em
  `strings_sensitive.xml`.

## Consequências

- A estimativa é aproximada: microfone, ruído do ambiente e o jeito de ler a frase mudam o número. Por
  isso a frase é fixa, o valor é editável e o gráfico mostra a tendência, não um diagnóstico.
- A faixa foi conferida em fonte secundária (Baken e Orlikoff, via citações), não na tabela do artigo
  de 1970. Se a fonte primária divergir, vale ela, como na correção da ADR 0002.
- Sobrevivem 5 mutantes equivalentes em `VoicePitchKt` (ADR 0005): o sinal do filtro (a fundamental não
  muda com o sinal invertido), igualdade exata de `Double` no corte de energia, no limiar e na descida,
  e a guarda de faixa do laço de atrasos, sempre verdadeira.
