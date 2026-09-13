# 0008. Lixeira, purga e remoção de mídia

**Status:** aceito (Fase 1)

## Contexto

- Seção 6: toda tabela de usuário tem soft delete (`deleted_at`), necessário para o merge de import.
- Seção 7.8: tombstones vão no export para que um merge não ressuscite dado apagado.
- Seção 9: toda ação destrutiva tem desfazer ou confirmação; exclusão vai para uma lixeira de 30 dias.
- Critério 7.3.2: excluir uma entrada exclui os arquivos de mídia órfãos, com limpeza em transação.

Apagar o arquivo da foto no momento da exclusão torna a lixeira inútil para fotos. Apagar a linha
depois de 30 dias devolve o problema da ressurreição no merge.

## Decisão

1. **Excluir** = `deleted_at = updated_at = agora`. Excluir uma entrada também marca as mídias dela,
   com o mesmo `deleted_at`. Os arquivos ficam onde estão (armazenamento interno privado).
2. **Restaurar** (lixeira, até 30 dias) = `deleted_at = NULL`, `updated_at = agora`. Restaurar uma
   entrada restaura as mídias que foram excluídas junto (mesmo `deleted_at`).
3. **Purgar** (automático ao passar de 30 dias, ou "excluir definitivamente" na lixeira):
   - numa transação, as linhas vencidas viram tombstone mínimo: colunas de texto livre ficam `NULL`
     (ou `''` quando `NOT NULL`), `updated_at = agora`; id, datas técnicas e FKs ficam;
   - as mídias dessas linhas têm o caminho coletado na mesma transação;
   - depois do commit, os arquivos coletados são apagados. Por fim, qualquer arquivo sob a raiz de
     mídia sem linha correspondente em `media_attachment` é apagado (varredura de órfãos).
   - `updated_at = agora` garante que o tombstone purgado vence no merge contra uma cópia antiga
     que ainda tem o texto.
   - a linha purgada fica com `deleted_at = 0`, um marcador de purga que dispensa coluna nova no
     schema: ela sai da janela de 30 dias da lixeira (que lista `deleted_at >= agora - 30 dias`) e a
     purga automática, que só pega `0 < deleted_at < agora - 30 dias`, nunca a purga de novo.
4. **Critério 7.3.2**, lido junto com a Seção 9: os arquivos de uma entrada excluída são removidos
   quando ela sai da lixeira (purga). O teste cobre exclusão + purga, e confirma que arquivos de
   entradas vivas nunca são tocados.

## Consequências

- Dado sensível excluído some do aparelho em até 30 dias, ou na hora, se a pessoa esvaziar a lixeira.
- O banco cresce com tombstones sem conteúdo. Custo desprezível.
