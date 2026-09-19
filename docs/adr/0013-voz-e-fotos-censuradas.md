# 0013. Gravação da voz e fotos do corpo censuradas

**Status:** aceito

## Contexto

Dois pedidos do dono do produto: as imagens do corpo ficam censuradas até a pessoa tocar no olho para
ver; no engrossamento da voz, gravar o áudio de uma frase para perceber a mudança ao longo do tempo.

## Decisão

- **Fotos.** Toda foto do corpo aparece censurada por padrão, fortemente borrada, com um botão de olho
  para mostrar. A revelação vale para aquela foto enquanto a tela está aberta; sair da tela censura de
  novo. O borrão é feito numa cópia reduzida a 20 pixels de largura, com três passadas de box blur de
  raio 4 em cada direção (aproxima um blur gaussiano largo), ampliada com filtro bilinear: sobra só uma
  mancha de cor, sem contorno nem detalhe. Funciona em qualquer versão do Android (o `Modifier.blur`
  só existe a partir do Android 12). A primeira versão (0.2.0) pixelava a 12 pixels e escurecia; a
  silhueta continuava reconhecível, e o dono do produto pediu o borrão.
- **Tela cheia.** Tocar numa foto abre ela em tela cheia, com zoom por pinça e arraste. A tela cheia
  abre no mesmo estado da miniatura: foto censurada continua censurada, com o mesmo olho, e o zoom
  nunca revela o que está borrado. Fechar volta ao estado anterior.
- **Remover foto.** O "x" da foto pede confirmação antes de tirar a foto da entrada: é uma ação de
  dado sensível sem desfazer imediato, e a foto já salva só some de verdade ao salvar a entrada.
- **Voz.** A entrada de uma mudança da categoria `VOICE` (hoje `VOICE_DEEPENING`) pode ter a gravação
  de uma frase fixa, a mesma sempre, para que as gravações sejam comparáveis. AAC em MPEG-4
  (`audio/mp4`, `.m4a`), mono, até 60 segundos, gravada no cache e anexada como mídia da entrada ao
  salvar, do mesmo jeito que as fotos. `MediaPaths` aceita `.m4a`; `MediaAttachment.isAudio` separa
  gravação de foto.
- **Permissão.** `RECORD_AUDIO`, pedida só quando a pessoa toca em gravar. Negada, a tela diz que a
  gravação precisa do microfone e o resto funciona. O app continua sem internet.
- `VoiceRecorder` e `VoicePlayer` ficam em `platform/`; as features não tocam em `MediaRecorder` nem em
  `MediaPlayer`.
- Gravações entram no backup, na lixeira e na purga como qualquer mídia.

## Consequências

Um backup com gravação importado num app anterior é recusado (extensão de mídia desconhecida), sem
alterar nada.
