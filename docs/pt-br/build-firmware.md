# Compilando o firmware do MCU

**Idiomas: [English](../build-firmware.md) · [Português (BR)](build-firmware.md) · [简体中文](../zh-Hans/build-firmware.md)**

O KlipPocket roda o host Klipper/Kalico no Android. A placa da impressora ainda
precisa do firmware do MCU, compilado e gravado uma vez num PC (o Android não
compila).

Há três formas de obtê-lo:

| Método | Observações |
|---|---|
| **Imagem pré-compilada** | O projeto Beam Klipper publica firmware para várias placas: <https://github.com/utkabobr/klipper/releases>. Imagens mais antigas funcionam com este app; Klipper **0.13** é o recomendado. |
| **Docker** | Um comando. Nada a instalar além do Docker. Reproduzível. |
| **Script local** | Um comando. Baixa o próprio toolchain fixado. |

Os dois builders do projeto geram um `klipper.bin` puro, renomeado para o nome de
arquivo que o bootloader da placa espera. Eles **não** rodam
`update_mks_robin.py` (esse passo XOR é só para placas MKS Robin STM32F103 cujo
bootloader descriptografa o arquivo do SD; corrompe a imagem em placas que gravam
o arquivo como está). Eles também se recusam a gerar uma imagem que sobreponha o
bootloader, e checam o tamanho compilado contra a área de aplicação do flash.

O Klipper não tem trava de versão MCU↔host — o MCU envia seu dicionário de
comandos ao host na conexão e o host se adapta. O firmware não precisa bater
exatamente com o Klipper embutido no app.

---

## Docker

A partir da raiz do repositório:

```bash
# compila a imagem uma vez (fixa Klipper v0.13.0 + ARM gcc 10.3-2021.07)
docker compose -f firmware/docker-compose.yml build

# compila o firmware de uma placa que tem config em firmware/configs/
docker compose -f firmware/docker-compose.yml run --rm fw <placa>

# lista as configs de placa disponíveis
docker compose -f firmware/docker-compose.yml run --rm fw
```

A saída vai para `firmware/output/`. Use uma versão diferente do Klipper com
`--build-arg KLIPPER_TAG=vX.Y.Z` no `docker compose ... build`.

## Script local

```bash
./scripts/build_firmware.sh <placa | caminho/para/.config> [nome-de-saida] [tag-klipper]
```

`<placa>` resolve para `firmware/configs/<placa>.config`, ou passe um caminho
completo para qualquer `.config` salvo com `make menuconfig`. A primeira execução
baixa o `gcc-arm-none-eabi 10.3-2021.07` para `firmware/toolchain/` e clona o
Klipper em `firmware/.klipper-build/` (ambos gitignored).

Roda no **Linux** e no **macOS** (precisa de `make`, `git`, `curl`, `tar`). No **Windows** use `scripts\build_firmware.ps1 <placa>` (envolve a imagem Docker), ou rode o `.sh` dentro do WSL / Git Bash.

---

## Adicionando uma placa

### 1. Identifique o chip da placa

O modelo da impressora não basta — o mesmo modelo muitas vezes veio com placas
diferentes (STM32F103 / F401 / GD32 / …), cada uma com um mapa de memória
diferente. Gravar o firmware do chip errado por um método que pode escrever o
bootloader pode brickar a placa. Confira a serigrafia da placa, o adesivo ou o
nome do arquivo de firmware oficial.

### 2. Gere um `.config`

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

### 3. Adicione em `firmware/configs/`

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

### 4. Grave

- **Placas com cartão SD** — copie a saída para um cartão FAT32, renomeada para o
  que o bootloader procura, e desligue/ligue. O bootloader renomeia ou apaga o
  arquivo como confirmação e nunca escreve em si mesmo; o pior caso é "não liga",
  recuperado gravando uma imagem boa conhecida do mesmo jeito.
- **Placas DFU / `make flash`** — siga o procedimento normal do Klipper, no PC.

### 5. Conecte

Ligue a impressora no dispositivo (OTG). O KlipPocket detecta a serial
automaticamente; a linha `[mcu] serial:` do `printer.cfg` é ignorada (mantenha uma
válida para portabilidade). Se o caminho do dispositivo muda quando o firmware
reinicia, use nomeação por VID/PID.

---

## Reprodutibilidade

Uma tag limpa do Klipper + o toolchain fixado + o mesmo `.config` produzem um
binário idêntico byte a byte (o Klipper omite hora e hostname de build para
builds de árvore limpa). A config de exemplo que acompanha o projeto usa
`#! expect_sha256`, então o build falha se o resultado divergir.

## Diretórios de scratch

`firmware/output/`, `firmware/toolchain/` e `firmware/.klipper-build/` são scratch
de build, gitignored. Commite só um `.bin` verificado em
`firmware/<slug-da-placa>/`.
