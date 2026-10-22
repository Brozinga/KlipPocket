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
| **RP2040** | Raspberry Pi Pico / RP2040 já em BOOTSEL (`2e8a:0003`) | **Testado** num Pico |
| **USB** | Placa rodando Klipper por USB. O app envia o pedido de bootloader a 1200 bps (como o `make flash` do Klipper) e grava pelo bootloader que aparecer: RP2040 BOOTSEL, STM32 DFU ou AVR109 (ATmega32U4 / Caterina) | **Testado** com o RP2040 e um `.uf2`; os outros bootloaders são **experimentais** |
| **DFU** | STM32 em modo DFU (`0483:df11`), protocolo DfuSe: apaga setores, grava, lê de volta, sai | **Experimental** |
| **UART** | Adaptador USB-serial ligado ao bootloader serial da placa: STM32 (ST AN3155, toda a flash é apagada) ou AVR (STK500v1 "arduino", STK500v2 "wiring") | **Experimental** |

> Os **modos experimentais** estão totalmente implementados, mas **não têm testes
> conclusivos em placas reais** (só testes unitários com bootloaders simulados).
> O app os marca na lista de modos e avisa de novo antes de gravar. **O uso é por
> sua conta e risco.**

**Não** cobre também: Pico 2 / RP2350, placas que carregam o firmware por
cartão SD (por exemplo a Neptune 3 Pro: copie o `.bin` para o cartão), placas
CAN e bootloaders diferentes dos acima (Katapult, HID-Flash, SAM-BA). O app
recusa arquivo ou dispositivo que não combina com o modo: um `.uf2` deve ser
feito para o RP2040, um `.bin` de RP2040 deve começar com um bloco boot2 válido
(assim um `.bin` de STM32 nunca é gravado num Pico), e DFU / UART precisam de
um `.bin` ou `.hex`.

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
| Nenhum bootloader suportado apareceu | A placa não entrou em RP2040 BOOTSEL, STM32 DFU nem AVR109 após o pedido de 1200 bps. Coloque-a no bootloader à mão (BOOTSEL / BOOT0) e use o modo *RP2040* ou *DFU*. |
| O dispositivo escolhido não corresponde ao modo | Escolha o dispositivo certo para o modo (*DFU* exige `0483:df11`, *RP2040* exige `2e8a:0003`). Nada foi gravado. |
| O bootloader não respondeu | UART: confira BOOT0 e reset (STM32) ou a linha de reset (AVR), a fiação e o baud rate. Nada foi gravado. |
| O dispositivo não é um adaptador USB-serial | O modo UART exige um adaptador CDC / CH34x / CP210x / FTDI. |
| Este tipo de arquivo não serve para o modo escolhido | RP2040: `.uf2` ou `.bin`; STM32 / AVR: `.bin` ou `.hex`. |
| O endereço inicial da flash não é válido | Arquivos `.bin` de STM32 vão entre `0x08000000` e `0x0FFFFFFF`. |
| A permissão USB não foi concedida | Tente de novo e aceite o pedido do Android. |
| Este UF2 não é para o RP2040 | Compile o firmware para o RP2040 (veja [build-firmware.md](build-firmware.md)). |
| A flash não confere com o arquivo | Não desconecte; execute de novo. Um RP2040 fica em BOOTSEL e sempre pode ser regravado. |

O Pico mantém sempre o seu carregador BOOTSEL, então uma gravação ruim se
corrige segurando BOOTSEL e gravando de novo.
