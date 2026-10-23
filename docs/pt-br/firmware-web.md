# Página web de firmware — grave firmware pelo navegador (porta 4400)

**Languages: [English](../firmware-web.md) · [Português (BR)](firmware-web.md) · [简体中文](../zh-Hans/firmware-web.md)**

A aba **Firmware** do [KlipPocket Dashboard](dashboard.md) — a página web que o app serve na **porta 4400** — faz o que a
[aba Firmware](firmware-tab.md) faz, mas pelo navegador de um PC ou de outro
celular na mesma rede: enviar um arquivo de firmware, colar um link de download,
ver os dispositivos USB ligados ao celular e gravar uma placa. A página só conversa
com o app: o envio, o download e a gravação são feitos pelo mesmo código da aba,
então os dois sempre concordam.

A página fica **sempre disponível com o app aberto** (não precisa de impressora
rodando) e uma notificação discreta a mantém acessível com o app em segundo plano.

## Abrir a página

No app, role a **tela inicial** até o fim, em **Dashboard**: ela mostra o endereço
(`http://<IP do celular>:4400`) e um **PIN** de seis dígitos.

![Endereço e PIN no app](../images/firmware-web/app-card.png)

Abra o endereço em qualquer navegador da mesma rede e digite o PIN. **Novo PIN** no
app o substitui e fecha todas as páginas abertas.

![Tela do PIN](../images/firmware-web/login.jpg)

> **Por que um PIN?** Quem alcança a página pode gravar uma placa ligada ao
> celular. Tudo, exceto os arquivos da própria página, exige o PIN; depois de cinco
> erros o endereço fica bloqueado por um minuto. A página usa HTTP simples, como as
> outras páginas do app: use em uma rede de confiança.

## A tela inicial

![Início](../images/firmware-web/home.jpg)

- **Enviar arquivo** — escolha um arquivo ou solte-o na área tracejada. `.bin`,
  `.hex` e `.uf2`, até 16 MB. Ele é salvo na pasta de firmware do app (a mesma lista
  da aba). Uma tela de carregamento mostra o progresso.

  ![Envio](../images/firmware-web/upload.jpg)

- **Colar link** — cole o link de um arquivo de firmware (só https), por exemplo
  `https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin`,
  e **Baixar**. Quem baixa é o próprio app, então o celular precisa de internet.

  ![Colar link](../images/firmware-web/paste-link.jpg)

- **Arquivos de firmware** — o que está guardado, com **Excluir** e **Excluir todos**.
- **Dispositivos USB** — todo dispositivo que o celular enxerga, com nome, `VID/PID`,
  caminho no barramento e, para placas seriais, a porta usada no `printer.cfg`: a
  mesma lista de *Listar dispositivos USB* do app. **Atualizar** lê de novo depois de
  conectar ou desconectar uma placa (também atualiza sozinha).
- **Gravar firmware** — o assistente abaixo.

## Gravar um firmware

**Gravar firmware** primeiro avisa que a função é **experimental** e pode causar
problemas; **Continuar** inicia os quatro passos.

![Aviso de função experimental](../images/firmware-web/experimental.jpg)

| 1 — Arquivo | 2 — Dispositivo |
|---|---|
| ![Passo 1](../images/firmware-web/step1-file.jpg) | ![Passo 2](../images/firmware-web/step2-device.jpg) |

**1** escolha o arquivo; **2** escolha o dispositivo (com **Atualizar**); **3** escolha o modo.

![Passo 3](../images/firmware-web/step3-mode.jpg)

Os modos são **USB**, **UART**, **DFU** e **RP2040 / RP2350**, com as mesmas regras,
observações e marcas de *experimental* da [aba](firmware-tab.md#modos-o-que-funciona-e-o-que-é-experimental).
Para um `.bin` puro em STM32 aparece um campo de **endereço inicial**. (Os modos
Katapult, HID-Flash, SAM-BA e CAN do app ainda não estão na página: use a aba.)

**4** um alerta de que o processo pode danificar a placa: **Confirmar** inicia,
**Cancelar** não muda nada.

![Passo 4](../images/firmware-web/step4-warning.jpg)

Depois uma tela de carregamento mostra a etapa e a porcentagem (o Android pode
pedir permissão USB **no celular**: aceite lá). No fim a página diz se deu certo e,
se não, **por quê**:

| | |
|---|---|
| ![Progresso](../images/firmware-web/progress.jpg) | ![Sucesso](../images/firmware-web/success.jpg) |
| ![Falha com o motivo](../images/firmware-web/failure.jpg) | |

**Registro** mostra o fim do registro *Gravação de firmware* do app; **Concluir** fecha o resultado.

### Se você atualizar ou fechar a página

A gravação roda dentro do app, não na página. Atualize a página, feche a aba, abra em
outro aparelho: ela volta direto para a tela de progresso (ou para o resultado, se a
gravação terminou e ninguém o fechou). O passo em que você estava (arquivo,
dispositivo, modo) fica guardado no navegador até você confirmar.

## Idioma e tema

A página oferece os cinco idiomas do app e, na primeira vez, segue o idioma e o tema
claro/escuro do app; a escolha feita na página fica guardada no navegador.

![Chinês, tema claro](../images/firmware-web/home-zh-light.jpg)

## Se algo der errado

| O que aparece | O que fazer |
|---|---|
| A página não abre | Celular e computador devem estar no mesmo Wi-Fi, sem *isolamento de clientes*; use o endereço mostrado no app. Se a porta 4400 estiver em uso por outro app, o cartão na tela inicial avisa que a página não está rodando. |
| *Muitas tentativas* | Espere os segundos indicados ou use **Novo PIN** no app. |
| *Conexão com o app perdida* | O app foi fechado ou o celular dormiu: abra o app de novo. Uma gravação em andamento não se perde enquanto o app estiver vivo. |
| Uma falha de gravação | As mensagens são as da [aba](firmware-tab.md#se-algo-der-errado). |

## Para desenvolvedores

Tudo em `/api` exige o cookie de sessão do login com PIN e, quando algo muda, o
cabeçalho `X-KlipPocket: 1`. Rotas principais: `GET /api/state`,
`PUT /api/upload?name=…`, `POST /api/download`, `GET /api/devices`,
`POST /api/flash`, `GET /api/flash`, `POST /api/flash/reset`. O código está em
`app/src/main/java/ru/ytkab0bp/beamklipper/firmwareweb/` e a página em
`app/src/main/assets/firmware_web/`; os textos são gerados das strings do app com
`scripts/gen_firmware_web_i18n.py`.
