# Usando o Happy Hare (multifilamento / MMU)

**Idiomas: [English](../happy-hare.md) · [Português (BR)](happy-hare.md) · [简体中文](../zh-Hans/happy-hare.md)**

O [Happy Hare](https://github.com/moggieuk/Happy-Hare) é o driver do Klipper
para unidades multimaterial (MMU): ERCF, Tradrack, BoxTurtle, 3MS, 3D Chameleon,
HTLF, KMS, MMX e muitas outras. O KlipPocket traz o **Happy Hare v4.1.0** e o
componente dele para o Moonraker, tanto para o Klipper quanto para o
[Kalico](kalico.md). Nada é ligado até a sua configuração pedir, então
impressoras sem MMU não são afetadas.

> **Ainda não testado com uma MMU física.** O Happy Hare embutido foi validado
> rodando a suíte de testes dele na versão do Python do app e iniciando o Klipper
> e o Kalico com uma configuração Tradrack gerada, em placas simuladas (os
> objetos `mmu` carregam e os comandos `MMU_*` respondem). Se você testar em
> hardware real, conte como foi.

## O que você precisa

- Uma placa da MMU gravada com o firmware **USB** do Klipper. Uma placa que só
  fala **CAN** (por exemplo uma MMB em modo CAN) ou só **UART** não é alcançada
  pelo app; veja [Multi-MCU](multi-mcu.md#o-que-funciona-e-o-que-não-funciona).
- A placa acessível por USB a partir do celular/tablet, de preferência por um hub
  com alimentação própria. Motores e servos precisam da própria fonte.
- Um computador por alguns minutos, para gerar a configuração do Happy Hare
  (Passo 1). Nada precisa ser instalado no celular.

## Passo 1 — Gerar a configuração do Happy Hare em um computador

O Happy Hare escreve a configuração a partir de um menu guiado: você responde
perguntas sobre a sua MMU (tipo, número de gates, placa, sensores) em vez de
editar pinos à mão. Em um computador Linux ou macOS:

```sh
git clone --branch v4.1.0 https://github.com/moggieuk/Happy-Hare.git
cd Happy-Hare
./install.sh -i -t
```

O `-t` é o modo de teste do Happy Hare: grava o resultado em `/tmp/mmu_test` e
não exige Klipper nem Moonraker instalados. Ao terminar, você encontra em
`/tmp/mmu_test/printer_data/config`:

- a pasta **`mmu`** (`base`, `macros`, `optional` e `mmu_vars.cfg`);
- o `printer.cfg` e o `moonraker.conf` que o Happy Hare edita, mostrando as
  linhas que ele adiciona (os Passos 2 e 4 usam isso).

Use a mesma versão do Happy Hare do app (v4.1.0): o formato da configuração muda
entre versões, e o Happy Hare recusa uma configuração feita por uma versão mais
antiga.

## Passo 2 — Copiar a configuração para o app

1. Abra a pasta de configuração do perfil da impressora, a que contém o
   `printer.cfg`. Há dois caminhos: o gerenciador de arquivos de configuração do
   Fluidd/Mainsail (crie as pastas e envie os arquivos) ou a entrada do
   KlipPocket no app Arquivos do Android, que mostra a pasta `config` de cada
   perfil.
2. Copie a pasta **`mmu`** inteira para lá, de modo que existam `mmu/base/…`,
   `mmu/macros/…` e `mmu/mmu_vars.cfg`.
3. Adicione estas duas linhas perto do topo do `printer.cfg` (o Happy Hare as
   adiciona no resultado do modo de teste):

   ```ini
   [include mmu/macros/*.cfg]
   [include mmu/base/*.cfg]
   ```

   Se no instalador você escolheu o menu opcional ou as macros de cliente,
   adicione também a linha `[include mmu/optional/…]` correspondente que ele
   escreveu.
4. Confira que o `printer.cfg` ainda tem uma seção `[pause_resume]` e que as suas
   macros `PAUSE`, `RESUME` e `CANCEL_PRINT` não estão definidas duas vezes (o
   Happy Hare já as fornece). Uma duplicata faz o Klipper parar com
   `gcode command … already registered`.

O `mmu/base/mmu_macro_vars.cfg` gerado traz um bloco `[save_variables]` cujo
`filename` é um caminho do computador (por exemplo
`/tmp/mmu_test/printer_data/config/mmu/mmu_vars.cfg`). **Você não precisa
alterá-lo:** quando essa pasta não existe no celular, o app usa o mesmo caminho
dentro da pasta de configuração do próprio perfil, e o Happy Hare guarda o estado
em `mmu/mmu_vars.cfg`, junto dos seus outros arquivos. Mantenha esse arquivo ao
fazer backup ou mover o perfil.

## Passo 3 — Apontar o Happy Hare para a placa

O `mmu/base/mmu_hardware.cfg` do Happy Hare declara a placa da MMU como um MCU
extra:

```ini
[mcu unit0]
serial: -enter-device-path-
```

Conecte a placa, abra **Configurações → USB → List USB devices** e troque
o texto provisório pelo caminho mostrado para ela. O [Multi-MCU](multi-mcu.md)
explica a lista de dispositivos e os dois modos de nomeação. Rode **List USB
devices** de novo se o caminho mudar depois de regravar o firmware.

## Passo 4 — Ligar o componente do Moonraker

O componente do Happy Hare no Moonraker pré-processa o G-code enviado para as
trocas de ferramenta e mantém o Spoolman em sincronia. Adicione ao
`moonraker.conf` do perfil da impressora:

```ini
[mmu_server]
enable_file_preprocessor: True
enable_toolchange_next_pos: True
update_spoolman_location: True
```

**Não** copie a seção `[update_manager happy-hare]` do `moonraker.conf` do
instalador: o Happy Hare é atualizado junto com o app, e não existe checkout git
no celular para o gerenciador de atualizações usar.

Reinicie o perfil da impressora. No console do Fluidd/Mainsail, `MMU_STATUS`
deve mostrar o resumo da MMU e `MMU_HELP` lista todos os comandos. O log da MMU é
gravado ao lado do `klippy.log`, como `mmu.log`.

## O que funciona

Verificado com uma configuração Tradrack gerada, no Klipper e no Kalico:

- O Happy Hare inicia (`Happy Hare v4.1.0 controlling 1 units`) com o tratamento
  de `[save_variables]` do app.
- Os comandos `MMU_*` respondem, como `MMU_STATUS`, `MMU_HELP`,
  `MMU_TEST_CONFIG`, `MMU_GATE_MAP`, `MMU_TTG_MAP`, `MMU_ENDLESS_SPOOL`,
  `MMU_SENSORS`, `MMU_TEST_MOVE` e `MMU_STATS`.
- O Moonraker carrega o `[mmu_server]` e conclui a inicialização.
- A suíte de testes do Happy Hare (2.543 testes) passa na versão do Python que o
  app traz.

## O que não funciona, ou não foi testado

- **Hardware real:** gates, seletor, servo, encoder, sensores e uma troca de
  ferramenta completa não foram executados em uma MMU física.
- **Placas CAN e UART** não são alcançadas (veja "O que você precisa").
- **Recuperação de impressão** só salva e restaura o extrusor ativo, então uma
  impressão interrompida por queda de energia não pode ser retomada no gate
  certo. Veja [Recuperação de impressão](print-recovery.md).
- **Leitores NFC/RFID e scanners TD-1** carregam, mas não foram testados com o
  dispositivo. Decifrar tags NFC Bambu/Creality exige uma biblioteca
  (`pycryptodome`) que o app não traz; o Happy Hare então deixa esse recurso
  desligado.
- **As telas próprias do Happy Hare** para Mainsail e KlipperScreen são
  projetos separados que conversam com o Moonraker. O Fluidd/Mainsail do app
  envia qualquer comando `MMU_*` pelo console ou por macros.
- **Mudar a MMU no meio do perfil** (adicionar unidades) significa rodar o
  instalador de novo com as respostas anteriores; copie a nova pasta `mmu` sobre a
  antiga, mas mantenha o seu `mmu/mmu_vars.cfg`.

## Solução rápida de problemas

| Mensagem | Causa provável |
|---|---|
| `MMU_STATUS` responde `Unknown command` | Falta a linha `[include mmu/base/*.cfg]` ou a pasta `mmu`, então o Happy Hare nunca carregou. |
| `Looks like Happy Hare is not installed correctly - cannot find happy_hare_version` | A configuração não foi feita pelo instalador do Happy Hare. Gere com a v4.1.0 (Passo 1). |
| `Looks like you upgraded (vX -> v4.1.0)?` | A configuração foi gerada por um Happy Hare mais antigo. Gere de novo com a v4.1.0. |
| `gcode command … already registered` | Uma macro do `printer.cfg` duplica uma que o Happy Hare fornece (por exemplo `PAUSE`, `RESUME` ou `CANCEL_PRINT`). Remova a sua. |
| `mcu 'unit0': Unable to connect` / `Unable to open serial port` | O `serial:` do `[mcu unit0]` ainda é o texto provisório, a placa não tem firmware USB, ou o caminho mudou. |
| `Happy Hare's Klipper modules failed to load` | Reporte. O conselho de `install.sh` nessa mensagem não vale para o app. |
