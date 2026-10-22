# Aba Firmware — baixar e instalar o firmware do MCU pelo app

**Idiomas: [English](../firmware-tab.md) · [Português (BR)](firmware-tab.md) · [简体中文](../zh-Hans/firmware-tab.md)**

A aba **Firmware** (o ícone de chip na barra superior) permite obter um arquivo
de firmware e gravá-lo numa placa ligada ao celular/tablet, sem PC. É o
equivalente, no aparelho, da opção **6 Flash Firmware** do
[menu do PC](build-firmware.md#gravando-a-placa): o firmware continua sendo
**compilado num PC** (o Android não compila); aqui você só o baixa e grava.

![A aba Firmware](../images/firmware-tab/tab-home.png)

## O que funciona e o que não funciona

| Modo | Placa | Situação |
|---|---|---|
| **RP2040** | Raspberry Pi Pico / RP2040 já em BOOTSEL (`2e8a:0003`) | **Funciona** (testado num Pico) |
| **USB** | Raspberry Pi Pico / RP2040 rodando Klipper por USB: o app a coloca em BOOTSEL sozinho (pedido de 1200 bps) e grava | **Funciona** (testado num Pico) |
| **DFU** | STM32 em modo DFU (`0483:df11`) | Ainda indisponível: executa e informa uma falha, **nada é gravado** |
| **UART** | Adaptador USB-serial ligado a um bootloader serial | Ainda indisponível: igual ao DFU |

**Não** cobre também: Pico 2 / RP2350, placas que carregam o firmware por
cartão SD (por exemplo a Neptune 3 Pro: copie o `.bin` para o cartão) e placas
CAN. O arquivo deve ser um `.uf2` feito para o RP2040 (o mais seguro) ou um
`.bin` puro; o app recusa um `.uf2` de outro chip.

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
(*USB* para uma placa com Klipper, *RP2040* para BOOTSEL).

![Passo 3](../images/firmware-tab/step3-mode.png)

**4 — Confirme.** Um alerta mostra arquivo, dispositivo e modo. **Cancelar/Voltar**
não altera nada; **Confirmar** inicia.

![Passo 4](../images/firmware-tab/step4-warning.png)

**Progresso.** A tela fica acesa e mostra a etapa: preparando, colocando a
placa em BOOTSEL, aguardando, permissão USB, apagando, gravando, verificando
(lê de volta e compara), reiniciando e aguardando a placa.

![Progresso](../images/firmware-tab/progress.png)

**Resultado.** Em caso de sucesso a placa foi gravada, verificada e (quando usa
USB) voltou ao barramento como dispositivo Klipper. Se os dados foram
verificados mas a placa não reapareceu no USB, o app avisa. Em caso de falha,
ele explica o motivo no seu idioma.

| | |
|---|---|
| ![Sucesso](../images/firmware-tab/success.png) | ![Falha](../images/firmware-tab/failure.png) |

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
| A placa não entrou em modo BOOTSEL | Segure **BOOTSEL** ao ligar a placa e use o modo *RP2040*. |
| A permissão USB não foi concedida | Tente de novo e aceite o pedido do Android. |
| Este UF2 não é para o RP2040 | Compile o firmware para o RP2040 (veja [build-firmware.md](build-firmware.md)). |
| A flash não confere com o arquivo | Não desconecte; execute de novo. A placa fica em BOOTSEL e sempre pode ser regravada. |
| Este modo de gravação ainda não está disponível | DFU e UART não estão implementados; nada foi gravado. |

O Pico mantém sempre o seu carregador BOOTSEL, então uma gravação ruim se
corrige segurando BOOTSEL e gravando de novo.
