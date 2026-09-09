# Dia Log Lite — app Android

Cliente nativo do Dia Log Lite (controle de ponto / banco de horas) para Android.
Kotlin + Jetpack Compose, offline-first.

**Escopo da v1:** listar os dias, editar entrada/saída e observação, funcionar sem
rede e sincronizar quando a rede voltar. Estatísticas, gráfico e o eixo em R$
ficaram **de fora** de propósito.

---

## Como configurar o endereço do servidor

Na primeira abertura o app pede a **URL base**; depois ela fica na engrenagem da
barra de título. Aceita `host`, `host:porta` ou URL completa — sem esquema,
assume `http://`.

```
https://dialog.zippert.me      # via Caddy
100.70.38.0:8000               # direto no IP netbird do ymir
```

**Não há endereço embutido no binário, e não há credencial de espécie alguma.**
São duas instâncias distintas (uma no ymir, outra no attila), uma por pessoa,
cada uma com seu banco — e o mesmo APK atende as duas. O servidor não tem login:
quem controla o acesso é a **mesh netbird**. O APK é publicado como asset de
release num repositório público, então nada de segredo pode morar aqui.

Consequência prática: **fora da mesh o servidor fica inalcançável, e isso é
estado normal** — não é erro. As edições ficam na fila e sobem sozinhas.

---

## Arquitetura

```
  Compose UI ── DaysViewModel ──┐
                                ├── DayRepository ──┬── Room (cache + fila)
  WorkManager ── SyncWorker ────┘                   └── Retrofit/OkHttp
```

| Camada | O quê |
|---|---|
| `data/local` | Room. `days` = cache do que o servidor disse; `pending_edits` = fila de edições locais; `balance` = saldo consolidado. |
| `data/remote` | Retrofit + kotlinx.serialization. `BaseUrlInterceptor` reescreve host/porta/esquema a cada chamada (a base é runtime, não build-time). |
| `data/DayRepository` | Onde mora a sequência de sync e o tratamento das armadilhas do contrato. |
| `sync/` | `SyncWorker` (CoroutineWorker) + agendamento: periódico a cada 6 h, mais disparo na abertura do app, no pull-to-refresh e ao salvar uma edição. |
| `ui/` | `DaysScreen` (lista), `DayEditSheet` (edição rápida), `SetupScreen` (URL base). |

DI é manual (`AppContainer`): o grafo tem quatro objetos e um escopo só.
Hilt custaria processamento de anotação e tempo de build sem contrapartida.

### O saldo é do servidor, sempre

Saldo, horas esperadas e detecção de feriado (BR/SP) são calculados **no
servidor** e nada disso é replicado no app. Com edição pendente, o número na
tela é o de *antes* da edição — e a UI diz isso explicitamente ("não inclui
edições pendentes"), porque um saldo silenciosamente defasado seria pior que
nenhum saldo.

### A sequência de sync tem ordem obrigatória

1. `GET /api/history`
2. despejar a fila em `POST /day/bulk_update`
3. `GET /api/history` de novo

O passo 1 não é só leitura: é a única rota que chama `auto_populate_days()`, e
portanto a única que **cria** `DayRecord`. O `bulk_update` faz
`DayRecord.query.get(date)` e devolve `'dia não encontrado'` para data que não
existe. Sem o passo 1, ficar offline atravessando a meia-noite faz o lançamento
do dia novo falhar. O passo 3 traz saldo e deltas recalculados.

### Armadilhas do contrato (todas com teste)

| # | Armadilha | Como o app trata |
|---|---|---|
| 1 | `/day/bulk_update` devolve **HTTP 200 com `"status":"ok"` mesmo em falha**; o que vale é `errors[]` | Data que aparece em `errors[]` é falha: fica na fila com o erro registrado. O cliente web do projeto tem esse bug e engole o erro. |
| 2 | `bulk_update` **não cria dia** | A ordem acima. |
| 3 | Saldo/esperadas/feriado são do servidor | Nada é recalculado local; a UI marca o número como defasado. |
| 4 | **O servidor DEVOLVE `HH:MM:SS` mas só ACEITA `HH:MM`** | `core.update_day_periods` faz `strptime(..., '%H:%M')`. Reenviar cru o que veio do `/api/history` vira `erro nos horários` dentro de um 200. O cache normaliza para `HH:MM` já na entrada. |
| 5 | `entries: []` **APAGA** os períodos do dia | "Não editei isso" omite o campo do payload (`explicitNulls = false`), nunca manda lista vazia. Idem `notes`. |
| 6 | O servidor faz `zip(entries, exits)` e **trunca pelo menor** | As duas listas sempre saem com o mesmo tamanho; período em aberto vai com `exit` nulo. |
| 7 | Dia com **mais de 2 períodos** é recusado ("use edição avançada") | O app nem oferece o formulário nesse caso — enviar 2 períodos apagaria os demais. Mostra os períodos em leitura e manda usar a web. |

A #4, a #5 e a #6 não estavam no levantamento inicial; saíram da leitura de
`app/core.py` e `app/routes.py`.

### Fila de edições

Uma linha por data — `bulk_update` substitui os períodos do dia inteiro, então é
idempotente por dia e edições sucessivas do mesmo dia colapsam numa só. Replay é
seguro. Erro que não melhora sozinho (mais de 2 períodos, data inválida, horário
inválido) marca a pendência como **bloqueada**: sai da fila de envio e vira aviso
na UI. `'dia não encontrado'` **não** é tratado como permanente — costuma sumir
depois do `auto_populate_days()` —, e vale até 5 tentativas.

---

## Build

**Nada de JDK, SDK ou Gradle instalado no host.** Tudo roda em container efêmero,
com os caches em `android/.build-cache/` (dentro do repo, nunca em `~/.gradle` ou
`~/.android`).

```bash
./docker/build.sh                    # assembleRelease (padrão)
./docker/build.sh assembleDebug
./docker/build.sh testDebugUnitTest
./docker/build.sh lintRelease
./docker/build.sh clean assembleRelease --stacktrace
```

A imagem de build (`docker/Dockerfile`: Temurin 21 + Android SDK 36) é construída
na primeira execução e reaproveitada. O APK sai em
`app/build/outputs/apk/release/`.

Em CI headless basta ter Docker; ou, se preferir chamar o Gradle direto,
`./gradlew assembleRelease` com `ANDROID_SDK_ROOT` apontando para um SDK com
`platforms;android-36` e `build-tools;36.0.0`. O wrapper está commitado e a
distribuição do Gradle vem com `distributionSha256Sum` pinado.

### Assinatura

`assembleRelease` produz **`app-release-unsigned.apk`** quando não há keystore
configurada — isso é de propósito: o repositório é público e não há keystore
versionada. Para sair assinado, defina (secrets de CI, ou um
`keystore.properties` local não versionado):

```
DIALOGLITE_KEYSTORE           # caminho do .jks
DIALOGLITE_KEYSTORE_PASSWORD
DIALOGLITE_KEY_ALIAS
DIALOGLITE_KEY_PASSWORD
```

Sem isso o APK precisa ser assinado depois (`apksigner`) para ser instalável.
**A keystore tem que ser sempre a mesma** entre releases, senão a atualização
in-place quebra e o app precisa ser desinstalado a cada versão.

### Versões

Tudo pinado em `gradle/libs.versions.toml`. O teto é o **compileSdk 36**: a linha
atual do AndroidX (Compose 1.12 / BOM 2026.08, core-ktx 1.19, lifecycle 2.11) exige
compileSdk **37** e AGP 9.1+, e `platforms;android-37` ainda não existe no canal
estável do SDK (só `android-36` e `android-CANARY`). Quando o 37 sair, dá para
subir o conjunto todo junto.

OkHttp segue na 4.12 e Retrofit na 2.11 (as 5.x/3.0 são majors; a migração
mexeria no MockWebServer dos testes sem ganho para o app).

---

## Testes

```bash
./docker/build.sh testDebugUnitTest
```

19 testes JVM, sem emulador. Os de `SyncSequenceTest` usam MockWebServer + um DAO
em memória e existem para travar exatamente as armadilhas da tabela acima —
foram verificados **falhando** com o bug reintroduzido, não só passando.

## Fora de escopo na v1

Estatísticas (`/api/stats`), gráfico, conversão em R$, edição avançada
(mais de 2 períodos, feriado manual, `balance_override`, consolidação) e
`PUT /api/settings`. Tudo isso continua na web.
