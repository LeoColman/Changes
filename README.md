# Changes

App Android offline, livre e de código aberto para acompanhar terapia hormonal masculinizante:
doses, mudanças corporais com fotos opcionais, faixas temporais descritas na literatura, peso e
medidas, exercício, condições de saúde, exames, check-in diário de saúde mental, calendário e
backup completo.

O app **não dá conselho médico**: não sugere dose, não ajusta dose, não diagnostica. Ele registra o
que a pessoa e quem acompanha o tratamento dela decidiram, e mostra literatura descritiva com fonte.
Não substitui acompanhamento profissional.

## Instalação

Baixe o APK mais recente em [Releases](https://github.com/LeoColman/Changes/releases) e instale num
Android 8.0 ou mais novo. Cada release traz o SHA-256 do arquivo. Todas as versões são assinadas com o
mesmo certificado, de SHA-256
`2C:46:52:4C:AA:BF:43:67:0D:EE:14:62:E5:6E:3B:A9:03:24:DC:69:F2:4C:E4:12:6E:93:E4:64:D3:4F:02:12`.

## Privacidade

- Sem permissão de internet. Nada sai do aparelho, exceto por export feito pela própria pessoa.
- Sem analytics, sem crash reporting de terceiros.
- Backup do Android e transferência entre aparelhos desligados; fotos só no armazenamento interno
  privado do app; `FLAG_SECURE` em todas as telas; bloqueio biométrico opcional.
- O banco não é criptografado (decisão de produto). Num aparelho com root ou
  desbloqueado por terceiros, os dados ficam legíveis.

## Arquitetura

Dois módulos Gradle:

- `:core`: Kotlin/JVM puro. Modelo de domínio, schema SQLDelight, repositórios, dataset clínico,
  export/import. Nenhuma dependência de Android. Testado em JVM e com teste de mutação.
- `:app`: Android. Compose + Material 3, ViewModels (MVVM, fluxo unidirecional), Koin, mídia,
  alarmes, driver SQLite.

Fronteiras de pacote verificadas por testes Konsist. Decisões registradas em [`docs/adr/`](docs/adr/);
regras para quem contribui em [`docs/app-guidelines.md`](docs/app-guidelines.md) e
[`docs/core-guidelines.md`](docs/core-guidelines.md).

## Requisitos

- JDK 17 e JDK 21 instalados (o daemon do Gradle usa 21; o bytecode é Java 17).
- Android SDK com a plataforma 37.

## Comandos

```bash
./gradlew check          # compilação, Detekt, lint, testes JVM, teste de estresse do backup com heap
                         # de 128 MB, Kover (80% no :core), verificação do manifesto
./gradlew pitestAll      # teste de mutação com pisos: :core inteiro e a lógica de JVM do :app
./gradlew :core:pitest   # só o :core, com pisos por pacote (cerca de 5 min, fora do check)
./gradlew :app:appPitest # só a lógica de JVM do :app (cerca de 20 s)
./gradlew :core:pitest -Ppitest.classes='br.com.colman.changes.core.model.*'   # recorte rápido, sem gate
./gradlew :core:test -Dchanges.roundtrip.iterations=1000   # property tests de backup com N iterações
./gradlew :core:test --tests '*BackupFixtureSpec' -Dchanges.writeFixtures=true   # só ao congelar uma versão do schema
./gradlew detektFormat   # aplica correções automáticas do ktlint
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest   # testes instrumentados (precisa de aparelho/emulador)
```

## Publicar uma versão

```bash
git tag v0.2.0 && git push origin v0.2.0
```

O workflow `Release` roda o `check`, gera o APK com R8, assina com a chave guardada nos secrets do
repositório (`CHANGES_KEYSTORE_BASE64`, `CHANGES_KEYSTORE_PASSWORD`, `CHANGES_KEY_ALIAS`,
`CHANGES_KEY_PASSWORD`) e cria a release com o APK e o SHA-256. O `versionCode` sai da tag.

## Licença

Copyright (C) 2026 Leonardo Colman Lopes.

Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob os termos da
GNU Affero General Public License, publicada pela Free Software Foundation, na versão 3 da
licença ou (a seu critério) qualquer versão posterior. Ver [`LICENSE`](LICENSE).

Este programa é distribuído na esperança de que seja útil, mas **SEM NENHUMA GARANTIA**, nem mesmo a
garantia implícita de COMERCIABILIDADE ou ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO.

**Sobre a AGPL e rede.** O §13 da AGPL só se aplica quando o software é oferecido a usuários por
uma rede. O Changes não tem permissão de internet e não fala com servidor nenhum; na prática, a
versão atual se comporta como GPL-3.0-or-later. A AGPL é um seguro para o futuro: se um dia existir
sincronização em nuvem, quem hospedar uma versão modificada terá de publicar as modificações. A
escolha da licença não indica que o app se comunica com algum servidor.

O dataset clínico em `core/src/main/resources/clinical/` está sob a mesma licença.
