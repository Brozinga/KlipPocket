# Compilando o firmware do MCU

**Idiomas: [English](../build-firmware.md) · [Português (BR)](build-firmware.md) · [简体中文](../zh-Hans/build-firmware.md)**

O KlipPocket roda o host Klipper/Kalico no Android. A placa da impressora ainda
precisa do firmware do MCU, compilado e gravado uma vez num PC (o Android não
compila).

**O jeito recomendado é o menu interativo** descrito nesta página. Ele pergunta
se você quer Klipper ou Kalico, mostra a tela *Firmware Configuration* do próprio
motor, compila no Docker, valida o resultado, grava a placa e confere se o
firmware novo é mesmo o que está rodando. As outras formas (imagens
pré-compiladas, os scripts Docker/Local) ficam no
[fim da página](#outras-formas-de-obter-o-firmware).

- [Requisitos](#requisitos)
- [Início rápido](#início-rápido)
- [Passo a passo: um exemplo completo](#passo-a-passo-um-exemplo-completo)
- [Gravando a placa](#gravando-a-placa)
- [O que a validação confere](#o-que-a-validação-confere)
- [Outras formas de obter o firmware](#outras-formas-de-obter-o-firmware)

---

## Requisitos

| O quê | Para quê |
|---|---|
| **O repositório**, baixado ou clonado (`git clone https://github.com/Brozinga/KlipPocket.git`, ou *Code → Download ZIP* no GitHub) | O menu, o Dockerfile e todos os scripts auxiliares ficam em `scripts/` e `firmware/`. Não é preciso baixar mais nada à mão. |
| **Docker** — Docker Desktop no Windows e no macOS, Docker Engine no Linux | Tudo roda dentro dele: os compiladores (ARM, AVR), o código do Klipper e do Kalico e as ferramentas de gravação. |
| **Um shell para abrir o menu** — **Bash** no Linux e no macOS; **PowerShell** (5.1 ou 7) ou **Git Bash** no Windows | `scripts/firmware_menu.sh` (Bash / Git Bash) ou `scripts/firmware_menu.ps1` (PowerShell). |
| **Internet, na primeira vez** | O Docker cria a imagem uma vez (cerca de 400 MB: compiladores e o código dos dois motores); depois ela fica em cache. |

## Início rápido

Na pasta do repositório:

```bash
./scripts/firmware_menu.sh        # Linux, macOS, WSL, Git Bash
```
```powershell
.\scripts\firmware_menu.ps1       # Windows PowerShell / Windows Terminal
```

![Menu principal](../images/firmware-menu/menu-main.png)

O topo mostra o motor e o perfil em uso e os **requisitos** (Docker instalado e
rodando, download completo). As opções:

| Opção | O que faz |
|---|---|
| **1 Select engine** | Klipper (v0.13.0) ou Kalico (v2026.10.00). |
| **2 Select or create a profile** | Um perfil é uma configuração salva: `firmware/configs/<perfil>.config`. |
| **3 Firmware Configuration** | O `make menuconfig` real do motor. |
| **4 Make Firmware** | Compila no Docker e valida o resultado. |
| **5 Validate Firmware** | Confere a saída de novo; uma checagem profunda opcional recompila do zero e compara os hashes. |
| **6 Flash Firmware** | Lista o que está conectado e grava a placa (modo USB, UART, DFU ou RP2040). |
| **7 Verify Flashed Firmware** | Conecta na placa e confere se ela roda exatamente o firmware que você compilou. |
| **8 Show summary** | As opções principais do perfil e os arquivos do último build. |
| **9 Docker image** | Cria a imagem, ou troca as versões do Klipper/Kalico dentro dela. |
| **c** | Confere os requisitos de novo (depois de instalar ou iniciar o Docker). |

Os resultados vão para `firmware/output/` como `<perfil>-<motor>.*`: a imagem
(`.bin`, `.hex` no AVR, `.uf2` no RP2040), o `.dict` que o host lê, a configuração
realmente usada (`.config.used`) e um `.manifest.json` com tamanhos e hashes SHA-256.

---

## Passo a passo: um exemplo completo

Este exemplo gera o firmware de uma placa STM32F401 com bootloader de 32 KiB que
conversa com o host pela porta serial (UART) — o tipo de placa que a maioria das
impressoras de fábrica usa.

### Onde acho as configurações da minha impressora?

As respostas certas do `make menuconfig` (chip, offset do bootloader, como a placa
fala com o host) dependem da sua placa-mãe. O projeto Klipper mantém um
`printer-*.cfg` para muitas impressoras aqui:

**<https://github.com/Klipper3d/klipper/tree/master/config>**

Abra o da sua impressora: **os comentários no topo dizem como gerar o firmware
dela.** Para a placa deste exemplo, ele diz:

```ini
# To use this config, during "make menuconfig" select the STM32F401 with a
# "32KiB bootloader" and serial (on USART1 PA10/PA9) communication.

# Note that the "make flash" command does not work with this board.
# After running "make", rename the out/klipper.bin file to out/<file name>.bin
# Copy the file out/<file name>.bin to an SD card formatted to FAT32
# and then restart the printer with the SD card inserted.
```

Isso diz três coisas: **o que escolher no menu** (STM32F401, bootloader de 32 KiB,
serial em USART1 PA10/PA9), **como o firmware chega na placa** (aqui: cartão SD,
não USB) e **qual nome o arquivo precisa ter** para esse bootloader. Deixe essas
notas à mão para os passos abaixo.

> Não sabe qual chip a sua placa tem? Veja [Identifique o chip da placa](#1-identifique-o-chip-da-placa).

### 1. Abra o menu

Rode o script como no [início rápido](#início-rápido). Se o topo mostra o Docker
instalado e rodando e o pacote completo, está tudo pronto.

### 2. Escolha o motor (opção 1)

![Escolhendo o motor](../images/firmware-menu/menu-engine.png)

Digite `1` para Klipper ou `2` para Kalico. O Kalico compila exatamente como o
Klipper e mostra as mesmas telas de configuração.

### 3. Crie um perfil (opção 2)

![Criando um perfil](../images/firmware-menu/menu-profile.png)

Escolha `n` e dê um nome ao perfil (letras, dígitos, `-` e `_`). Ele é salvo como
`firmware/configs/<nome>.config`, então você pode guardar um perfil por placa e
voltar a ele depois.

### 4. Firmware Configuration (opção 3)

Abre a tela de configuração do próprio motor, dentro do Docker. Mova-se com as
**setas**, use **Espaço/Enter** para marcar ou abrir um item, **Esc** para voltar
e **Q** para sair (ele pergunta se quer salvar: aperte **Y**).

![menuconfig, primeira tela](../images/firmware-menu/menuconfig-1-start.png)

A primeira tela só mostra os padrões de um Atmega2560. Marque **Enable extra
low-level configuration options** (Espaço): aparecem as opções do seu chip.

![Ativando as opções de baixo nível](../images/firmware-menu/menuconfig-2-low-level.png)

**Micro-controller Architecture** — escolha a família do chip, aqui
*STMicroelectronics STM32*:

![Lista de arquiteturas](../images/firmware-menu/menuconfig-3-architecture.png)

**Processor model** — o chip exato, aqui *STM32F401*:

![Lista de processadores](../images/firmware-menu/menuconfig-4-processor.png)

**Bootloader offset** — quanto da flash o bootloader da própria placa usa e não
pode ser sobrescrito, aqui *32KiB bootloader*. É a configuração mais importante
para acertar:

![Offset do bootloader](../images/firmware-menu/menuconfig-5-bootloader.png)

**Clock Reference** fica no padrão (*8 MHz crystal*). **Communication interface**
é como a placa fala com o host, aqui *Serial (on USART1 PA10/PA9)*; o baud rate
fica em 250000, o único que o KlipPocket suporta:

![Interface de comunicação](../images/firmware-menu/menuconfig-6-communication.png)

A tela final:

![A configuração pronta](../images/firmware-menu/menuconfig-7-summary.png)

Aperte **Q** e depois **Y** para salvar o perfil:

![Salvando a configuração](../images/firmware-menu/menuconfig-8-save.png)

Placas com USB no próprio MCU (por exemplo muitas STM32 e RP2040) usam *USB* como
interface de comunicação no lugar da serial; as outras telas funcionam igual.

### 5. Make Firmware (opção 4)

O menu compila dentro do Docker e valida o que saiu:

![Make Firmware](../images/firmware-menu/menu-make.png)

`All checks passed` significa que a imagem está coerente: chip certo, clock certo,
tabela de vetores no offset do bootloader, baud rate 250000 (veja
[o que a validação confere](#o-que-a-validação-confere)). A opção **8** lista as
opções principais e os arquivos:

![Resumo](../images/firmware-menu/menu-summary.png)

A imagem é `firmware/output/<perfil>-<motor>.bin`.

### 6. Coloque na placa

**A placa deste exemplo carrega o firmware de um cartão SD**, então ela não é
gravada pelo menu: renomeie o `.bin` para o nome que o cabeçalho pediu, copie para
um cartão SD FAT32, insira o cartão e religue a placa (detalhes em
[Gravando a placa](#gravando-a-placa)).

Placas que podem ser gravadas por USB usam a opção **6 Flash Firmware**, mostrada
a seguir com um Raspberry Pi Pico.

---

## Gravando a placa

*Flash Firmware* (opção **6**) tem um menu próprio. A primeira metade **lista o que
está conectado** sem gravar nada, para você conferir que a placa é reconhecida
antes de gravar; a segunda metade grava. As duas têm quatro modos:

![Menu de gravação](../images/firmware-menu/flash-menu.png)

| Modo | Para | Lista |
|---|---|---|
| **USB** | Placa rodando Klipper (ou Katapult) por USB, ou placa USB estilo Arduino | portas `/dev/serial/by-id/usb-…` (CDC/ACM) |
| **UART** | Adaptador USB-serial (CH340, FTDI, CP210x) ligado ao bootloader UART da placa; STM32 usa `stm32flash`, AVR usa `avrdude` | adaptadores USB-serial |
| **USB (DFU mode)** | Um STM32 em modo DFU (BOOT0 e reset: `0483:df11`) | dispositivos DFU |
| **USB RP2040 mode** | Um Raspberry Pi Pico / RP2040 com BOOTSEL pressionado (`2e8a:0003`) | dispositivos BOOTSEL |

### Exemplo: um Raspberry Pi Pico

Crie o perfil do Pico como acima (arquitetura *Raspberry Pi RP2040/RP235x*,
comunicação *USB*). Depois segure o **BOOTSEL** enquanto liga o Pico.

**1. Liste primeiro.** As opções 1–4 só olham; não gravam nada. Com o Pico em
BOOTSEL, *USB RP2040 mode* o encontra, enquanto *USB* ainda não tem nada a mostrar
(a dica diz como é uma placa desse tipo):

![Nada encontrado no modo USB](../images/firmware-menu/flash-list-none.png)
![O Pico encontrado no modo RP2040](../images/firmware-menu/flash-list-rp2040.png)

**2. Grave.** A opção 8 (*Flash via USB RP2040 mode*) mostra os dispositivos desse
tipo, pergunta qual e pede confirmação:

![Escolhendo o dispositivo](../images/firmware-menu/flash-pick-device.png)

Antes de gravar, o menu valida o build de novo, recompila e confere que é
**idêntico byte a byte** à imagem validada, e então roda a regra de gravação do
Klipper. No sucesso ele avisa e oferece conferir o que a placa está rodando:

![Flash OK](../images/firmware-menu/flash-ok.png)

**3. Verifique.** *Verify Flashed Firmware* (oferecido logo após a gravação, ou
opção **7** do menu principal) abre a porta serial da placa, pede o dicionário de
dados como o Klipper faz ao conectar e compara com o seu build:

![Verificação](../images/firmware-menu/flash-verify.png)

`the board runs exactly the firmware that was built` é a prova real; versão ou chip
diferente aparece como falha, o que também pega uma gravação que não teve efeito.
O Pico agora roda Klipper por USB, então *USB* o lista:

![O Pico encontrado no modo USB](../images/firmware-menu/flash-list-usb.png)

(No modo USB dá para gravar de novo sem mexer no BOOTSEL: o menu pede ao firmware
em execução que reinicie no bootloader.)

### Quando algo dá errado

Se a gravação falha, o menu avisa e mostra o erro, e espera Enter. Por exemplo,
um firmware de RP2040 oferecido a uma placa em modo DFU de STM32 é recusado antes
de gravar qualquer coisa:

![Uma gravação recusada](../images/firmware-menu/flash-error.png)

Conferências antes de gravar qualquer coisa:

1. O build é validado de novo (hashes, formato, configuração).
2. O modo precisa combinar com o firmware: DFU exige build STM32, o modo RP2040
   exige build RP2040/RP2350 (uma imagem errada pode inutilizar a placa).
3. O firmware é recompilado e precisa ser **idêntico byte a byte** à imagem validada.
4. O dispositivo precisa existir; a gravação tem limite de quatro minutos em vez de travar.

As ferramentas de gravação verificam enquanto escrevem quando possível (`avrdude`
e `stm32flash -v` fazem releitura). O menu usa a regra de gravação do próprio
Klipper (`make flash`) com as ferramentas já na imagem (`dfu-util`, `stm32flash`,
`avrdude` e os gravadores de RP2040 e SAM).

### Limites

- No **Linux** funciona direto (o container é iniciado com acesso a `/dev`). No
  **Windows**, anexe a placa ao WSL2 com
  [usbipd-win](https://learn.microsoft.com/windows/wsl/connect-usb); o Docker
  Desktop no **macOS** não acessa USB, então grave `firmware/output/` com as
  ferramentas do seu sistema.
- Feche o app ou qualquer perfil que esteja usando a placa.
- Placas cujo bootloader lê o firmware de um **cartão SD** não são gravadas aqui:
  copie a imagem de `firmware/output/` para o cartão (renomeada como as notas da
  placa pedem). O *Verify Flashed Firmware* ainda funciona depois.
- Testado num Raspberry Pi Pico (modo BOOTSEL e modo Klipper por USB). Os outros
  modos usam as mesmas regras de gravação, mas ainda não rodaram em hardware.

---

## O que a validação confere

Rodada pelo *Make Firmware* e pela opção **5**:

- Os arquivos existem, não estão vazios e batem com os hashes gravados na compilação.
- O formato da imagem: checksums do Intel HEX (AVR), blocos UF2 (RP2040) e, em
  `.bin` ARM, uma tabela de vetores coerente ligada no offset do bootloader configurado.
- A imagem foi feita para o MCU e o clock da configuração, e a versão é a do motor.
- O baud rate é 250000 (o único que o KlipPocket suporta) ou a placa usa USB.
- O offset do bootloader deixa espaço e a imagem cabe na área da aplicação.
- O perfil salvo não foi alterado depois da compilação.

A imagem traz os compiladores ARM (gcc 10.3 fixado), AVR e do host, então chips
ARM Cortex-M (STM32, RP2040, LPC176x, SAM, HC32), AVR e o Linux process
compilam. Os alvos AR100 e PRU precisam de outros compiladores e não estão incluídos.

---

## Solução de problemas

### Falta o Docker

Se o Docker não está instalado, não está rodando, ou o seu usuário não pode usá-lo,
o menu avisa no topo e desabilita o que depende dele. Escolher o motor/perfil e ver
o resumo continuam funcionando. Instale ou inicie o Docker e aperte `c`.

![Docker não instalado](../images/firmware-menu/menu-no-docker.png)

### PowerShell

O menu do PowerShell é o mesmo; só os símbolos são mais simples.

![Menu do PowerShell](../images/firmware-menu/menu-powershell.png)

### Outras dúvidas

- *Qual chip a minha placa tem?* Veja [Identifique o chip da placa](#1-identifique-o-chip-da-placa).
- *Posso usar uma configuração pronta?* Copie um `.config` para `firmware/configs/`:
  ele aparece na opção 2. Veja [Adicionando uma placa](#adicionando-uma-placa).

---

## Outras formas de obter o firmware

O menu acima é o caminho. Estas alternativas ficam para casos especiais.

Os dois builders do projeto geram um `klipper.bin` puro, renomeado para o nome de
arquivo que o bootloader da placa espera. Eles **não** rodam
`update_mks_robin.py` (esse passo XOR é só para placas MKS Robin STM32F103 cujo
bootloader descriptografa o arquivo do SD; corrompe a imagem em placas que gravam
o arquivo como está). Eles também se recusam a gerar uma imagem que sobreponha o
bootloader, e checam o tamanho compilado contra a área de aplicação do flash.

O Klipper não tem trava de versão MCU↔host — o MCU envia seu dicionário de
comandos ao host na conexão e o host se adapta. O firmware não precisa bater
exatamente com o Klipper embutido no app.

| Método | Observações |
|---|---|
| **Imagem pré-compilada** | O projeto Beam Klipper publica firmware para várias placas: <https://github.com/utkabobr/klipper/releases>. Imagens mais antigas funcionam com este app; Klipper **0.13** é o recomendado. |
| **Docker (linha de comando)** | Um comando por placa que tem config em `firmware/configs/`. |
| **Script local** | Um comando. Baixa o próprio toolchain fixado. |

### Docker (linha de comando)

A partir da raiz do repositório:

```bash
# compila a imagem uma vez (fixa Klipper v0.13.0 + Kalico v2026.10.00 + ARM gcc 10.3-2021.07)
docker compose -f firmware/docker-compose.yml build

# compila o firmware de uma placa que tem config em firmware/configs/
docker compose -f firmware/docker-compose.yml run --rm fw <placa>

# lista as configs de placa disponíveis
docker compose -f firmware/docker-compose.yml run --rm fw
```

A saída vai para `firmware/output/`. Use uma versão diferente do Klipper com
`--build-arg KLIPPER_TAG=vX.Y.Z` no `docker compose ... build`.

### Script local

```bash
./scripts/build_firmware.sh <placa | caminho/para/.config> [nome-de-saida] [tag-klipper]
```

`<placa>` resolve para `firmware/configs/<placa>.config`, ou passe um caminho
completo para qualquer `.config` salvo com `make menuconfig`. A primeira execução
baixa o `gcc-arm-none-eabi 10.3-2021.07` para `firmware/toolchain/` e clona o
Klipper em `firmware/.klipper-build/` (ambos gitignored).

Roda no **Linux** e no **macOS** (precisa de `make`, `git`, `curl`, `tar`). No **Windows** use `scripts\build_firmware.ps1 <placa>` (envolve a imagem Docker), ou rode o `.sh` dentro do WSL / Git Bash.

---

### Adicionando uma placa

#### 1. Identifique o chip da placa

O modelo da impressora não basta — o mesmo modelo muitas vezes veio com placas
diferentes (STM32F103 / F401 / GD32 / …), cada uma com um mapa de memória
diferente. Gravar o firmware do chip errado por um método que pode escrever o
bootloader pode brickar a placa. Confira a serigrafia da placa, o adesivo ou o
nome do arquivo de firmware oficial.

#### 2. Gere um `.config`

Num PC com um checkout do Klipper (ou `docker compose ... run --rm --entrypoint bash fw`):

```bash
cd /opt/klipper        # ou o seu clone do Klipper
make menuconfig
```

Opções que importam para o KlipPocket:

- **Modelo do processador** — bata com o chip exatamente.
- **Offset do bootloader** — a maioria das placas OEM tem um bootloader de 8–32
  KiB que precisa ser preservado (ex.: `CONFIG_FLASH_START_8000`). É o principal
  risco de brick.
- **Interface de comunicação** — o KlipPocket usa **USB serial** pela porta OTG
  do dispositivo. Na maioria das placas OEM isso é uma ponte USB-serial
  embarcada ligada a uma UART, não o USB nativo do MCU. Escolha a UART em que a
  porta USB está ligada.
- **Baud** — o KlipPocket só suporta **250000**.

Uma config "known-good" da comunidade para a placa é um bom ponto de partida:
carregue-a, rode `make menuconfig` uma vez para reconciliar, e salve.

**Onde encontrar `.config` de placa prontos:**

| Fonte | Observações |
|---|---|
| [`utkabobr/klipper` → `bin-templates/`](https://github.com/utkabobr/klipper/tree/master/bin-templates) | Os `.config` (Kconfig) a partir dos quais o firmware pré-compilado do Beam Klipper é gerado (~90 placas OEM). É o mais próximo deste app; um arquivo `.json` ao lado marca placas de cartão SD ("robin"). |
| [`Klipper3d/klipper` → `config/`](https://github.com/Klipper3d/klipper/tree/master/config) | `printer-*.cfg` / `generic-*.cfg` — são templates de **printer.cfg**, não `.config` de firmware, mas os comentários do cabeçalho informam o chip, o offset do bootloader e a fiação de comunicação que você precisa no `make menuconfig`. |
| [Klipper Community Discourse](https://klipper.discourse.group/) | Configs de firmware compartilhadas por placa. |
| Docs do fabricante da placa (BigTreeTech, MKS, Fysetc, TeamGloomy para STM32) | Respostas de `make menuconfig` por placa. |

#### 3. Adicione em `firmware/configs/`

Salve como `firmware/configs/<placa>.config`. Um cabeçalho de diretivas opcional
deixa o builder nomear a saída e proteger as linhas críticas de brick:

```ini
#! board_name:      <nome legível>
#! flash_filename:  <nome que o bootloader procura, ex.: firmware.bin>
#! klipper_tag:     v0.13.0
#! assert: CONFIG_MCU="<chip>"
#! assert: <qualquer linha CONFIG_ que deva sobreviver ao `make olddefconfig`>
#! assert: CONFIG_SERIAL_BAUD=250000
```

As linhas `#! assert:` são re-checadas depois do `make olddefconfig`; o build para
se alguma sumir. `#! expect_sha256: <hash>` também faz o build falhar se a saída
não bater — útil para fixar uma imagem verificada.

#### 4. Grave

- **Placas com cartão SD** — copie a saída para um cartão FAT32, renomeada para o
  que o bootloader procura, e desligue/ligue. O bootloader renomeia ou apaga o
  arquivo como confirmação e nunca escreve em si mesmo; o pior caso é "não liga",
  recuperado gravando uma imagem boa conhecida do mesmo jeito.
- **Placas DFU / `make flash`** — siga o procedimento normal do Klipper, no PC.

#### 5. Conecte

Ligue a impressora no dispositivo (OTG). O KlipPocket detecta a serial
automaticamente; a linha `[mcu] serial:` do `printer.cfg` é ignorada (mantenha uma
válida para portabilidade). Se o caminho do dispositivo muda quando o firmware
reinicia, use nomeação por VID/PID.

---

### Reprodutibilidade

Uma tag limpa do Klipper + o toolchain fixado + o mesmo `.config` produzem um
binário idêntico byte a byte (o Klipper omite hora e hostname de build para
builds de árvore limpa). A config de exemplo que acompanha o projeto usa
`#! expect_sha256`, então o build falha se o resultado divergir.

### Diretórios de scratch

`firmware/output/`, `firmware/toolchain/` e `firmware/.klipper-build/` são scratch
de build, gitignored. Commite só um `.bin` verificado em
`firmware/<slug-da-placa>/`.
