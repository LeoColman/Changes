# 0013. Gravação da voz e fotos do corpo censuradas

**Status:** aceito

## Contexto

Dois pedidos do dono do produto: as imagens do corpo ficam censuradas até a pessoa tocar no olho para
ver; no engrossamento da voz, gravar o áudio de uma frase para perceber a mudança ao longo do tempo.

## Decisão

- **Fotos.** Toda foto do corpo aparece censurada por padrão (pixelada e escurecida), com um botão de
  olho para mostrar. A revelação vale para aquela foto enquanto a tela está aberta; sair da tela
  censura de novo. A versão censurada é desenhada a partir de uma cópia reduzida a poucos pixels, que
  funciona em qualquer versão do Android (o `Modifier.blur` só existe a partir do Android 12).
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
