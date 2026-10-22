# Aba Firmware — baixar e instalar o firmware do MCU pelo app

**Idiomas: [English](../firmware-tab.md) · [Português (BR)](firmware-tab.md) · [简体中文](../zh-Hans/firmware-tab.md)**

A aba **Firmware** (o ícone de chip na barra superior) permite obter um arquivo
de firmware e gravá-lo numa placa ligada ao celular/tablet, sem PC. É o
equivalente, no aparelho, da opção **6 Flash Firmware** do
[menu do PC](build-firmware.md#gravando-a-placa): o firmware continua sendo
**compilado num PC** (o Android não compila); aqui você só o baixa e grava.

![A aba Firmware](../images/firmware-tab/tab-home.png)

## Modos: o que funciona e o que é experimental

| Modo | Placa | Situação |
|---|---|---|
| **RP2040 / RP2350** | Raspberry Pi Pico / Pico 2 já em BOOTSEL (`2e8a:0003` / `2e8a:000f`) | **Testado** num Pico (RP2040). O **RP2350 é experimental** (sem Pico 2 para testar) |
| **USB** | Placa rodando Klipper por USB. O app envia o pedido de bootloader a 1200 bps (como o `make flash` do Klipper) e grava pelo bootloader que aparecer: RP2040 BOOTSEL, STM32 DFU ou AVR109 (ATmega32U4 / Caterina) | **Testado** com o RP2040 e um `.uf2`; os outros bootloaders (e o RP2350) são **experimentais** |
| **DFU** | STM32 em modo DFU (`0483:df11`), protocolo DfuSe: apaga setores, grava, lê de volta, sai | **Experimental** |
| **UART** | Adaptador USB-serial ligado ao bootloader serial da placa: STM32 (ST AN3155, toda a flash é apagada) ou AVR (STK500v1 "arduino", STK500v2 "wiring") | **Experimental** |
| **Katapult** | Bootloader [Katapult](https://github.com/Arksine/katapult) do Klipper (`1d50:6177`) por USB, ou por um adaptador USB-serial ligado à UART dele (250000 baud). Uma placa Klipper por USB é mandada antes para o Katapult (1200 bps). `.bin` puro | **Experimental** |
| **HID-Flash** | STM32F1/F4 com o bootloader HID (`1209:beba`), o protocolo do `hid-flash` do Klipper. Uma placa Klipper por USB é mandada antes para ele (1200 bps). `.bin` puro | **Experimental** |
| **SAM-BA** | Atmel SAM3 / SAM4 / SAME70 (Arduino Due, placas SAM4E…) no bootloader da ROM (`03eb:6124`): apaga toda a flash, grava, lê de volta, liga a boot pela flash e reinicia, como o `bossac`. Uma placa Klipper por USB é mandada antes para ele (1200 bps). `.bin` puro | **Experimental** |
| **CAN** | Katapult por um barramento CAN, através de um adaptador gs_usb / candlelight ou da ponte USB-CAN do Klipper (`1d50:606f`). Você informa o **UUID** do nó (`canbus_query.py`) e a velocidade do barramento; o app manda o nó para o Katapult, dá a ele um id e grava, como o `flash_can.py`. `.bin` puro | **Experimental** |

> Os **modos experimentais** estão totalmente implementados, mas **não têm testes
> conclusivos em placas reais** (só testes unitários com bootloaders simulados).
> O app os marca na lista de modos e avisa de novo antes de gravar. **O uso é por
> sua conta e risco.**

**Não** cobre também: placas que carregam o firmware por
cartão SD (por exemplo a Neptune 3 Pro: copie o `.bin` para o cartão), as
placas SAMD / SAME5x da família SAM-BA (outro controlador de flash) e
bootloaders diferentes dos acima. O app
recusa arquivo ou dispositivo que não combina com o modo: um `.uf2` deve ser
feito para o chip, um `.bin` para Pico deve começar com um bloco boot2 de RP2040
válido (ou um bloco IMAGE_DEF de RP2350), assim um `.bin` de STM32 nunca é gravado
num Pico, DFU / UART precisam de um `.bin` ou `.hex`, e Katapult / HID-Flash / SAM-BA / CAN precisam de um `.bin`. O Klipper grava o mesmo
ID de família UF2 para o RP2040 e o RP2350, então o app os distingue olhando
dentro da imagem; um arquivo do outro chip é recusado antes de gravar qualquer coisa.

> **Gravar firmware pode deixar a placa inutilizável.** Grave só um arquivo
> feito para aquela placa, mantenha o cabo conectado e a tela aberta, e lembre
> que o Klipper perde a conexão com a placa durante a gravação.

## Os três botões

- **Listar dispositivos USB** — a mesma lista de *Configurações → USB → Listar dispositivos USB*.

  ![Dispositivos USB](../images/firmware-tab/list-usb.png)

- **Baixar firmware** — cole um link e o app salva o arquivo.
- **Instalar firmware** — o assistente passo a passo abaixo.

Abaixo dos botões, **Arquivos de firmware** lista o que está guardado no app
(nome, tipo, tamanho) com **Excluir**, e **Importar do aparelho** copia um
arquivo que você já tem (por exemplo um compilado no PC) pelo seletor de
arquivos do sistema. Aceitos: `.bin`, `.hex`, `.uf2` e `.zip` (os firmwares
dentro dele são extraídos).

### Excluir arquivos

Cada arquivo tem um botão **Excluir** (também no passo 1 do assistente), e
**Excluir todos** remove todos os firmwares guardados. Os dois pedem confirmação.

![Botões de excluir](../images/firmware-tab/delete-files.png)

## Baixar um firmware

Toque em **Baixar firmware**, cole um link **https** e toque em **Baixar**, por
exemplo `https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin`.
Limite: 16 MB.

| | |
|---|---|
| ![Diálogo de download](../images/firmware-tab/download-dialog.png) | ![Arquivo salvo](../images/firmware-tab/download-saved.png) |

## Instalar um firmware

Toque em **Instalar firmware**. Cada passo tem **Próximo** / **Voltar**; o botão
voltar do aparelho também volta um passo.

**1 — Escolha o arquivo.** Selecione um dos arquivos guardados.

![Passo 1](../images/firmware-tab/step1-file.png)

**2 — Escolha o dispositivo.** Todo dispositivo USB que o aparelho enxerga
aparece na lista; **Atualizar** recarrega (ela também atualiza sozinha). Uma
placa em BOOTSEL aparece como *RP2 Boot · 2e8a:0003*, um Pico rodando Klipper
como *rp2040 · 1d50:614e*. O Android pede a permissão USB quando necessário.

![Passo 2](../images/firmware-tab/step2-device.png)

**3 — Escolha o modo de gravação.** O app sugere um conforme o dispositivo
(*USB* para uma placa com Klipper, *RP2040* para BOOTSEL, *DFU* para
`0483:df11`). Modos sem testes conclusivos mostram o aviso **Experimental**.

![Passo 3](../images/firmware-tab/step3-mode.png)

Para um **`.bin`** puro gravado num **STM32** (modo DFU, UART ou USB) aparece o
campo **Endereço inicial da flash**: onde o arquivo vai (`0x08000000`, a menos
que haja um bootloader seu abaixo; o *Bootloader offset* do Klipper é esse
tamanho). Arquivos `.hex` e `.uf2` já trazem os endereços.

![Endereço inicial](../images/firmware-tab/start-address.png)

No modo **CAN** aparecem dois campos no lugar: o **UUID CAN** do nó (12 dígitos
hexadecimais, como o `canbus_query.py` mostra) e a **velocidade do barramento**
em bit/s (1000000 por padrão, o mesmo `canbus_frequency` da configuração do
Klipper). No passo 2 escolha o próprio adaptador (o dispositivo gs_usb), não a
placa que está atrás dele.

**4 — Confirme.** Um alerta mostra arquivo, dispositivo e modo e, nos modos
experimentais, um segundo aviso de que você segue por sua conta e risco.
**Cancelar/Voltar** não altera nada; **Confirmar** inicia.

![Passo 4](../images/firmware-tab/step4-warning.png)

**Progresso.** A tela fica acesa. Uma barra cobre a gravação inteira (etapa por
etapa, nunca para trás) e enche suavemente, com um brilho percorrendo-a
enquanto o app trabalha; abaixo dela, a etapa atual e a porcentagem total:
preparando, colocando a placa em BOOTSEL, aguardando, permissão USB, conversando
com o bootloader, apagando, gravando, verificando (lê de volta e compara),
reiniciando e aguardando a placa. Se algo falhar, a barra some e o erro é
mostrado.

![Progresso](../images/firmware-tab/progress.png)

**Resultado.** Em caso de sucesso (a barra fica cheia) a placa foi gravada, verificada e (quando usa
USB) voltou ao barramento como dispositivo Klipper. Se os dados foram
verificados mas a placa não reapareceu no USB, o app avisa. Em caso de falha,
ele explica o motivo no seu idioma.

| | |
|---|---|
| ![Sucesso](../images/firmware-tab/success.png) | ![Falha: o bootloader não respondeu](../images/firmware-tab/failure.png) |
| ![Dispositivo errado para o modo](../images/firmware-tab/failure-wrong-device.png) | |

**Ver log** abre a aba Logs na nova fonte **Gravação de firmware**.

## O log

Toda gravação é registrada (arquivo, sha256, dispositivo, modo, cada etapa, resultado):

![Log da gravação](../images/firmware-tab/logs.png)

## Tema e idioma

A aba segue o tema e o idioma do app (English, Português, 简体中文, 繁體中文, Русский).

| | |
|---|---|
| ![Tema claro](../images/firmware-tab/light-tab.png) | ![Tema claro, alerta](../images/firmware-tab/light-warning.png) |
| ![Português](../images/firmware-tab/pt-br-tab.png) | ![Português, falha](../images/firmware-tab/pt-br-failure.png) |

## Se algo der errado

| Mensagem | O que fazer |
|---|---|
| Nenhum bootloader suportado apareceu | A placa não entrou em RP2040 BOOTSEL, STM32 DFU, Katapult, HID, SAM-BA nem AVR109 após o pedido de 1200 bps. Coloque-a no bootloader à mão (BOOTSEL / BOOT0 / botão do bootloader) e use o modo desse bootloader. |
| O dispositivo escolhido não corresponde ao modo | Escolha o dispositivo certo para o modo (*DFU* exige `0483:df11`, *RP2040* exige `2e8a:0003`). Nada foi gravado. |
| O bootloader não respondeu | UART: confira BOOT0 e reset (STM32) ou a linha de reset (AVR), a fiação e o baud rate. Nada foi gravado. |
| O dispositivo não é um adaptador USB-serial | O modo UART exige um adaptador CDC / CH34x / CP210x / FTDI. |
| Este tipo de arquivo não serve para o modo escolhido | RP2040: `.uf2` ou `.bin`; STM32 / AVR: `.bin` ou `.hex`; Katapult, HID-Flash, SAM-BA e CAN: `.bin`. |
| O UUID CAN falta ou não é válido | 12 dígitos hexadecimais, exatamente como o `canbus_query.py` mostra. |
| O dispositivo não é um adaptador CAN | No modo CAN escolha, no passo 2, o adaptador gs_usb / candlelight (ou a ponte USB-CAN do Klipper). |
| O endereço inicial da flash não é válido | Arquivos `.bin` de STM32 vão entre `0x08000000` e `0x0FFFFFFF`. |
| A permissão USB não foi concedida | Tente de novo e aceite o pedido do Android. |
| O arquivo não é para este chip | O RP2040 e o RP2350 precisam cada um do seu firmware: compile para o chip que você tem (veja [build-firmware.md](build-firmware.md)). |
| A flash não confere com o arquivo | Não desconecte; execute de novo. Um RP2040 fica em BOOTSEL e sempre pode ser regravado. |

## Como os modos experimentais foram testados sem placa

O autor só tem um Raspberry Pi Pico, então todos os outros modos foram conferidos
contra **placas simuladas que, por sua vez, são conferidas contra as ferramentas reais**:

- Os simuladores estão em [`app/src/test/python/fake_bootloaders.py`](../../app/src/test/python/fake_bootloaders.py):
  um nó Katapult (USB/UART e barramento CAN), um bootloader HID de STM32 (páginas de
  1 KiB e 2 KiB) e um SAM3X8E / SAM4E8E na ROM SAM-BA com o controlador de flash EEFC.
- O `test_bootloader_fakes.py` roda as ferramentas **do próprio Klipper** contra eles:
  `flash_can.py` (caminhos serial e CAN), `hid-flash` e `bossac` (compilados das fontes
  da árvore). Se a ferramenta real termina com a flash correta, o simulador fala como a placa.
- Os testes em Kotlin (`OtherBootloadersTest`) rodam então o código do app contra os
  mesmos simuladores, incluindo respostas erradas, silêncio, gravação ruim e falha
  na leitura de volta. Uma checagem de mutação (pular o apagamento do SAM-BA) os faz falhar, como deve ser.
- O que isso **não** prova: temporização USB, a parte elétrica, uma versão de bootloader
  diferente da de referência e o enquadramento `gs_usb` de um adaptador CAN real
  (segue o driver `gs_usb` do Linux, mas nunca viu um). Por isso esses modos
  continuam marcados como **experimentais**.

O Pico mantém sempre o seu carregador BOOTSEL, então uma gravação ruim se
corrige segurando BOOTSEL e gravando de novo.
