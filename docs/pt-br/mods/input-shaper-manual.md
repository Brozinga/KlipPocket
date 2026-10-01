# Input shaper sem acelerômetro (método da torre de ringing)

**Idiomas: [English](../../mods/input-shaper-manual.md) · [Português (BR)](input-shaper-manual.md) · [简体中文](../../zh-Hans/mods/input-shaper-manual.md)**

O teste automático de ressonância do Klipper (`TEST_RESONANCES`,
`SHAPER_CALIBRATE`) precisa de um acelerômetro ADXL345 / LIS2DW / MPU-9250 no
toolhead. Muitas impressoras não têm um, e adicionar um no KlipPocket exige um
sensor SPI/I²C ligado ao MCU da impressora (o aparelho Android não ajuda nisso).

O **teste da torre de ringing** te dá os mesmos dois números com olho + paquímetro:

1. a **frequência de ringing** para X e para Y → `shaper_freq_x` / `shaper_freq_y`
2. uma **aceleração máxima** segura, de brinde

É o método que a própria doc do Klipper descreve pro caso sem acelerômetro:
<https://www.klipper3d.org/Resonance_Compensation.html>

---

## 1. Preparação (uma vez)

Adicione uma seção de input shaper **vazia** ao `printer.cfg` (pra o teste poder
desligá-la, e pra você ter onde escrever o resultado):

```ini
[input_shaper]
```

Adicione a macro de teste (fim deste arquivo) ao `printer.cfg` ou a um arquivo
`[include]`ído.

Baixe o modelo de teste de ringing do Klipper, **`ringing_tower.stl`**:
<https://github.com/Klipper3d/klipper/raw/master/docs/prints/ringing_tower.stl>

---

## 2. Fatie (isso importa — se errar, os números são lixo)

| Configuração | Valor |
|---|---|
| Altura de camada | 0,2–0,25 mm |
| Paredes / perímetros | 2 |
| Camadas de topo / fundo | **0** |
| Preenchimento | **0 %** |
| Velocidade do perímetro externo | **100 mm/s**, fixa — anote esse número como `V` |
| Desacelerar em camadas curtas / tempo mínimo de camada | **off** (0 s) |
| Velocidade dinâmica / auto, rampas de "outer wall speed" | off |
| Opções de input shaper / "vibração"/"ringing" do fatiador | off |
| Imprima a torre **perto do centro** da mesa | sim |

A torre precisa ser impressa com velocidade de parede externa **constante**. Se o
seu fatiador insiste em desacelerar as primeiras camadas, suba o tempo mínimo de
camada pra 0 e desative os limites de velocidade de "cooling" só pra esta
impressão.

---

## 3. Rode o teste

No console do Fluidd/Mainsail, com a mesa + bico na temperatura e a impressora
homada:

```
RINGING_TOWER
```

Essa macro:
- desliga o input shaping e o pressure advance,
- coloca square-corner-velocity baixo e cruise-ratio em 0 (pra a aceleração ser
  o que de fato comanda as quinas),
- arma um `TUNING_TOWER` que **sobe a aceleração em 300 mm/s² a cada 5 mm de Z**,
  começando em 1500 mm/s².

Agora inicie a impressão do `ringing_tower.stl` pela lista de arquivos. Observe;
se a cabeça começar a patinar / perder passo feio, pare — tudo abaixo daquela
altura ainda vale.

---

## 4. Leia a torre

Você tem **dois** resultados.

### 4a. Aceleração máxima

Ache a altura de Z onde o ringing (os ecos fantasma depois de cada entalhe) fica
aceitavelmente pequeno. A aceleração na altura `Z` é:

```
accel = START + FACTOR * floor(Z_mm / BAND)
      = 1500  + 300    * floor(Z_mm / 5)
```

Exemplo: limpo em Z = 40 mm → `1500 + 300 * 8 = 3900 mm/s²`. Use ~**80 %** disso
como `max_accel` pras impressões reais.

### 4b. Frequência de ringing (a importante)

A torre tem entalhes nas **faces X** e nas **faces Y**. O ringing aparece como uma
onda que decai *depois* de cada entalhe, na face **perpendicular ao eixo que se
moveu**:

- ringing na parede **virada pra X** → ressonância **X**
- ringing na parede **virada pra Y** → ressonância **Y**

Com o paquímetro, meça a distância cobrindo **quantas oscilações completas você
conseguir** (digamos 4), depois divida:

```
D = (distância total medida) / (número de oscilações)      [mm por oscilação]
f = V / D                                                  [Hz]
```

onde `V` é a velocidade do perímetro externo que você pôs no fatiador (100 mm/s).

Exemplo: 4 oscilações somam 8,0 mm → D = 2,0 mm → `f = 100 / 2,0 = 50 Hz`.

Faça isso para X e para Y separado — em geral são diferentes. Numa bed-slinger, o
eixo da mesa móvel (Y) costuma ser o mais baixo dos dois.

---

## 5. Aplique o resultado

```ini
[input_shaper]
shaper_type_x: mzv
shaper_freq_x: 50          # sua frequência X medida
shaper_type_y: mzv
shaper_freq_y: 38          # sua frequência Y medida
```

Guia de `shaper_type`:

| Situação | Tipo |
|---|---|
| Padrão, bom pra tudo | `mzv` |
| Frequência baixa (< 25 Hz) ou ainda vê ringing | `ei` |
| Muito baixa / muito ruidosa | `2hump_ei` |
| Quer o mínimo de suavização e a freq é alta e limpa | `zv` |

Depois rode a torre uma vez com o input shaper **ligado** (edite a variável
`_DISABLE_SHAPER` da macro pra 0, ou simplesmente não pare em `RINGING_TOWER`) pra
confirmar que o ringing sumiu na sua aceleração alvo.

Também limite a aceleração pra a "suavização" do shaper ficar sã — como regra,
mantenha `max_accel` de forma que `max_accel / min(shaper_freq_x, shaper_freq_y)`
fique em torno de ≤ 100 (o Klipper avisa acima disso).

---

## 6. A macro

Adicione isto ao `printer.cfg` (ou a um arquivo `[include]`ído):

```ini
[gcode_macro RINGING_TOWER]
description: Arma o teste da torre de ringing (input shaper sem acelerometro)
gcode:
    # ---- parametros (todos opcionais) ----
    {% set start_accel = params.START|default(1500)|int %}   # accel da 1a banda (mm/s^2)
    {% set factor      = params.FACTOR|default(300)|int %}    # incremento de accel por banda
    {% set band        = params.BAND|default(5)|int %}        # altura de cada banda (mm)
    {% set v           = params.SPEED|default(100)|int %}     # veloc. do perimetro externo no slicer (mm/s)
    {% set keep_shaper = params.KEEP_SHAPER|default(0)|int %} # 1 = nao desligar o input_shaper

    {% if not keep_shaper and printer.configfile.settings.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X=0 SHAPER_FREQ_Y=0
    {% endif %}
    {% if printer.extruder is defined %}
        SET_PRESSURE_ADVANCE ADVANCE=0
    {% endif %}

    SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=1 MINIMUM_CRUISE_RATIO=0 ACCEL={start_accel} ACCEL_TO_DECEL={start_accel}

    TUNING_TOWER COMMAND=SET_VELOCITY_LIMIT PARAMETER=ACCEL START={start_accel} FACTOR={factor} BAND={band}

    M118 Ringing tower ARMADO.
    M118 accel(Z) = {start_accel} + {factor} * floor(Z/{band})
    M118 Imprima ringing_tower.stl (2 perimetros, 0 top/bottom, 0% infill, perimetro externo a {v} mm/s constante).
    M118 Depois: meca D entre as oscilacoes; frequencia f = {v} / D (Hz). Faca X e Y separado.

[gcode_macro RINGING_TOWER_RESET]
description: Restaura limites normais apos o teste da torre de ringing
gcode:
    {% set cfg = printer.configfile.settings %}
    SET_VELOCITY_LIMIT VELOCITY={cfg.printer.max_velocity} ACCEL={cfg.printer.max_accel} SQUARE_CORNER_VELOCITY={cfg.printer.square_corner_velocity|default(5)} MINIMUM_CRUISE_RATIO={cfg.printer.minimum_cruise_ratio|default(0.5)}
    {% if cfg.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X={cfg.input_shaper.shaper_freq_x|default(0)} SHAPER_FREQ_Y={cfg.input_shaper.shaper_freq_y|default(0)}
    {% endif %}
    M118 Limites restaurados do printer.cfg.
```

Uso:

```
RINGING_TOWER                 # padrão: START=1500 FACTOR=300 BAND=5 SPEED=100
RINGING_TOWER START=1000 FACTOR=200 SPEED=80
# ... imprima ringing_tower.stl, meça, calcule f = SPEED / D ...
RINGING_TOWER_RESET
```
