# Add-ons do Klipper embutidos no KlipPocket

**Idiomas: [English](../../mods/klipper-addons.md) · [Português (BR)](klipper-addons.md) · [简体中文](../../zh-Hans/mods/klipper-addons.md)**

Estes add-ons são embutidos mas inativos — o Klipper carrega um deles apenas
quando a `[seção]` correspondente está presente no `printer.cfg`, então uma
configuração que não os referencia não é afetada. Foram selecionados para rodar
sob Android / Chaquopy (Python 3.10, sem compilador no aparelho, sem `pip` no
nível do usuário).

| Mod | Seção pra habilitar | Precisa de |
|---|---|---|
| KAMP (mesh + purga adaptativa) | `[include KAMP/KAMP_Settings.cfg]` | `[exclude_object]` (core), `[bed_mesh]` |
| LED Effect | `[led_effect NOME]` | um `[neopixel]` / `[dotstar]` endereçável |
| Z Calibration | `[z_calibration]` | probe + um ponto metálico fixo de referência |
| Auto Speed | `[auto_speed]` | nada (mede passos perdidos) |
| TMC Autotune | `[autotune_tmc stepper_x]` … | `[tmc2209]`/`[tmc5160]`/… já configurado |
| Probe eddy-current | `[probe_eddy_current]` + `[ldc1612]` | sensor BTT Eddy / LDC1612 (é **core** do Klipper, já presente) |

> Input shaper sem acelerômetro: veja
> [input-shaper-manual.md](input-shaper-manual.md).

---

## KAMP — Klipper Adaptive Meshing & Purging

Semeado automaticamente em `<instância>/config/KAMP/` no primeiro start do Klipper
(5 arquivos, não sobrescrito se já existir).

**Habilitar:** adicione ao `printer.cfg` (perto do topo, depois de
`[exclude_object]`):

```ini
[include KAMP/KAMP_Settings.cfg]
```

Depois abra `KAMP/KAMP_Settings.cfg` no editor de config do Fluidd/Mainsail e
descomente as partes que quiser:

```ini
[include Adaptive_Meshing.cfg]   # sonda só a área que a peça ocupa
[include Line_Purge.cfg]         # linha de purga na frente da peça de verdade
[include Smart_Park.cfg]         # estaciona perto da peça pro aquecimento final
#[include Voron_Purge.cfg]       # purga em blob no logo da Voron (precisa da macro do logo)
```

**Ligue na sua sequência de start** — no `PRINT_START`, troque as chamadas simples
de mesh / purga:

```ini
BED_MESH_CALIBRATE ADAPTIVE=1     # em vez de um BED_MESH_CALIBRATE simples
SMART_PARK                        # logo antes do aquecimento final do bico
LINE_PURGE                        # em vez de uma linha de purga escrita à mão
```

O fatiador precisa emitir `EXCLUDE_OBJECT_DEFINE` (OrcaSlicer / Creality Print:
ligar "Label objects"; PrusaSlicer/SuperSlicer: habilitar "Label objects"; Cura:
plugin *Exclude Objects*) — é isso que diz ao KAMP onde a peça realmente está.

As configurações ficam na macro `_KAMP_Settings` (quantidade de purga, vazão,
margens, altura do smart-park).

---

## LED Effect (`julianschill/klipper-led_effect`)

Efeitos animados para uma fita de LED endereçável (barra de progresso, gradiente
de temperatura, luz da câmara, estados "imprimindo / aquecendo / pronto").

**Habilitar:** você precisa de um LED endereçável primeiro, ex.:

```ini
[neopixel chamber]
pin: PA8
chain_count: 20
color_order: GRB

[led_effect panel_progress]
leds:
    neopixel:chamber
autostart: false
frame_rate: 24
layers:
    progress  10 0 add  (0,0,1),(0,1,0)
```

Comandos: `SET_LED_EFFECT EFFECT=panel_progress`, `STOP_LED_EFFECTS`. Sintaxe
completa de layers:
<https://github.com/julianschill/klipper-led_effect/blob/master/docs/LED_Effect.md>

Obs.: o Happy Hare v4 traz o **próprio** `[mmu_led_effect]` para os LEDs de status
do MMU — isso é separado e independente deste `[led_effect]`.

---

## Z Calibration (`protoloft/klipper_z_calibration`)

Calcula o Z-offset ao vivo a partir de três medições — bico num ponto metálico,
probe no mesmo ponto, probe na mesa — de forma que ele continua correto entre
trocas de bico e mudanças de temperatura. Útil principalmente com um probe de
switch/klicky/indutivo e uma aba metálica fixa perto da mesa.

**Habilitar:**

```ini
[z_calibration]
nozzle_xy_position:   <x>,<y>     # bico sobre o ponto metálico de referência
switch_xy_position:   <x>,<y>     # probe sobre o mesmo ponto
bed_xy_position:      <x>,<y>     # um ponto seguro na mesa (centro do mesh serve)
switch_offset:        0.5         # folga trigger-para-toque do seu probe, comece com ~0.5
start_gcode:          <macro de deploy do probe, se for dockável>
```

Rode `CALIBRATE_Z` depois de homar + QGL/mesh. Doc:
<https://github.com/protoloft/klipper_z_calibration>

---

## Auto Speed (`Anonoei/klipper_auto_speed`)

Descobre sua aceleração e velocidade máximas reais movendo o toolhead e detectando
passos perdidos (compara a posição comandada vs. a medida do stepper depois de um
home). Sem acelerômetro.

**Habilitar:**

```ini
[auto_speed]
margin: 20            # margem de segurança dos limites dos eixos
```

`z` não é opção de config — é parâmetro do comando `AUTO_SPEED` (`AUTO_SPEED Z=50`),
caso você queira mover pra uma altura Z específica antes do teste.

Rode `AUTO_SPEED` (varredura completa, ~vários minutos) ou `AUTO_SPEED_VELOCITY` /
`AUTO_SPEED_ACCEL`. Ele imprime os `max_velocity` / `max_accel` recomendados. O
gráfico de variância opcional precisa de `matplotlib` (não instalado) — o
resultado numérico não. Doc: <https://github.com/Anonoei/klipper_auto_speed>

---

## TMC Autotune (`andrewmcgr/klipper_tmc_autotune`)

Calcula bons valores de registrador do driver TMC (controle de corrente, `PWM`,
`CoolStep`, limiares `StealthChop`/`SpreadCycle`) a partir do datasheet do motor,
em vez de ajustar na mão. Atualizado pro `main` do upstream (2026-08).

**Habilitar** — uma seção por driver que você já tem configurado:

```ini
[autotune_tmc stepper_x]
motor: ldo-42sth40-1004ah        # procure seu motor em extras/motor_database.cfg
[autotune_tmc stepper_y]
motor: ldo-42sth40-1004ah
[autotune_tmc extruder]
motor: ldo-36sth20-1004ahg
```

Se seu motor não estiver no `motor_database.cfg`, escolha o mais próximo ou
adicione. Doc: <https://github.com/andrewmcgr/klipper_tmc_autotune>

---

## Probe eddy-current (BTT Eddy, LDC1612 genérico) — core do Klipper

Nenhum mod necessário — `[probe_eddy_current]` e `[ldc1612]` fazem parte do
Klipper 0.13 embutido. Configure conforme a doc do Klipper
(<https://www.klipper3d.org/Eddy_Probe.html>). Essa é a **única** opção de probe
de varredura segura no Android (veja a próxima seção).

### Por que não Beacon / Cartographer

Tanto `beacon.py` quanto `cartographer.py` sobem um `multiprocessing.Process`
para fazer streaming das amostras, e o ajuste de modelo do Cartographer quer
`scipy`. `multiprocessing` não é confiável no Chaquopy no Android (não há `fork()`
seguro do processo do app), e `scipy` não está instalado. Eles **não estão
embutidos**. Se você tem esse hardware, o caminho `[probe_eddy_current]` estilo
BTT Eddy é o suportado.
