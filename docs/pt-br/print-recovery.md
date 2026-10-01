# Recuperação de impressão — retomar após queda de energia

**Idiomas: [English](../print-recovery.md) · [Português (BR)](print-recovery.md) · [简体中文](../zh-Hans/print-recovery.md)**

Se a impressora perder energia, o cabo USB soltar ou o Klipper desligar no meio
de uma impressão, o KlipPocket pode oferecer para **continuar a impressão de
onde parou**. É um recurso de melhor esforço: funciona melhor em peças que
ainda estão bem coladas na mesa.

O celular continua ligado (tem bateria), então a posição da impressão fica
segura mesmo com a impressora desligada.

<p align="center"><img src="../images/powerless-recovery.png" alt="Fluidd perguntando se deve retomar uma impressão interrompida" width="720"></p>

## Como funciona

1. Durante a impressão, a posição no arquivo G-code, a posição do bico, as
   temperaturas, a ventoinha, velocidade/fluxo, o offset de Z e a malha da mesa
   são salvos em um arquivo pequeno a cada **2 segundos** (configurável).
2. Quando a impressora volta e o Klipper fica pronto, o Fluidd e o Mainsail
   mostram uma janela **"Print interrupted"** perguntando se deseja retomar.
3. Se tocar em **Resume print**, a impressora:
   1. aquece a mesa;
   2. levanta o bico, faz home **só em X e Y** e vai até o **batente do eixo X**
      (longe da peça) — opcional, veja `park_enable_x` / `park_enable_y`;
   3. aquece o bico ali, para nada escorrer sobre a impressão;
   4. opcionalmente purga um pouco de filamento — veja `purge`;
   5. volta à posição salva e continua imprimindo do byte salvo do arquivo.

**Discard** descarta a impressão e depois levanta o bico para longe da peça
abandonada (**50 mm** por padrão) e faz home de X e Y — tudo configurável, veja
`discard_lift_z`, `discard_home_x` e `discard_home_y`. Se fechou a janela, execute
`PRINT_RECOVERY_STATUS` no console para vê-la de novo (ela também reaparece a
cada minuto enquanto houver uma impressão pendente).

## Ativar

Adicione ao `printer.cfg` (fora de outra seção) e reinicie:

```ini
[print_recovery]
```

Tudo abaixo é opcional; os valores mostrados são os padrões. Uma configuração
que você não precisa mudar pode simplesmente ficar de fora. Uma linha sem valor
não deve ser escrita (`park_x`, `park_y` e `state_file` só entram quando você
realmente os define).

```ini
[print_recovery]
snapshot_interval: 2
park_enable_x: True
park_enable_y: True
park_speed: 100
lift_z: 10
discard_lift_z: 50
discard_home_x: True
discard_home_y: True
purge: True
purge_length: 20
purge_speed: 5
purge_retract: 2
min_extruded: 5
macro_variables: *
prompt: True
prompt_repeat: 60
```

## Configurações, linha a linha

Números são decimais simples (`2`, `0.5`, `-6`). `True`/`False` também aceitam
`1`/`0`, `yes`/`no` e `on`/`off`.

| Configuração | O que faz | Valores aceitos |
|---|---|---|
| `snapshot_interval: 2` | Segundos entre gravações da posição da impressão. Menor = retomada mais exata, porém mais gravações. | Número de `0.5` a `300` |
| `park_enable_x: True` | Ao retomar, faz home de X e vai ao batente do X para aquecer o bico longe da peça. `False`: X **não** faz home nem se move e é considerado exatamente onde estava. | `True` / `False` |
| `park_enable_y: True` | O mesmo para Y. Com os dois em `False`, o bico apenas sobe `lift_z` e aquece parado sobre a peça, e a purga é ignorada (o filamento cairia na impressão). Use só se tiver certeza de que nada se moveu. | `True` / `False` |
| `park_x: -6` | Posição X usada para aquecer e purgar após o home de X. **Deixe a linha de fora** para usar o mínimo do X da máquina, ou seja, o batente do X (por exemplo `-6`, à esquerda da mesa, em algumas máquinas). | Número dentro do curso do X |
| `park_y: 0` | O mesmo para Y. **Deixe a linha de fora** para ficar onde o home de Y deixou o bico (normalmente a frente da mesa). | Número dentro do curso do Y |
| `park_speed: 100` | Velocidade de deslocamento ao estacionar, em mm/s. | Número maior que `0` |
| `lift_z: 10` | Milímetros que o bico sobe antes do home de X/Y, para livrar a peça. | Número `0` ou maior |
| `discard_lift_z: 50` | Milímetros que o bico sobe ao usar **Discard**, para se afastar da peça abandonada. | Número `0` ou maior (`0` = não sobe) |
| `discard_home_x: True` | Faz home de X após **Discard**. | `True` / `False` |
| `discard_home_y: True` | Faz home de Y após **Discard**. | `True` / `False` |
| `purge: True` | Purga um pouco de filamento antes de voltar à impressão. Só acontece se X ou Y estiver estacionado. | `True` / `False` |
| `purge_length: 20` | Milímetros de filamento na purga. | Número `0` ou maior |
| `purge_speed: 5` | Velocidade da purga em mm/s. | Número maior que `0` |
| `purge_retract: 2` | Milímetros retraídos após a purga, para evitar fio de filamento. | Número `0` ou maior |
| `min_extruded: 5` | Milímetros que precisam ser extrudados antes da primeira gravação, para não guardar uma impressão que nem começou. | Número `0` ou maior |
| `macro_variables: *` | Quais macros têm os valores de `variable_xxx` salvos e restaurados. | `*` (todos os macros que não começam com `_`), `none` (nenhum) ou uma lista como `PRINT_START, MEU_MACRO` |
| `prompt: True` | Mostra a janela "Print interrupted" no Fluidd/Mainsail. `False`: só a mensagem no console e os comandos abaixo. | `True` / `False` |
| `prompt_repeat: 60` | Segundos entre lembretes enquanto uma impressão espera. | Número `0` ou maior (`0` = mostrar uma vez) |
| `state_file: ~/recovery.json` | Onde o snapshot é guardado. **Deixe a linha de fora** para guardá-lo junto do `printer.cfg`. | Caminho de arquivo (`~` é aceito) |

### Sobrescrever valores ao retomar manualmente

`PRINT_RECOVERY_RESUME` aceita as mesmas configurações como parâmetros, só
para aquela retomada:

```gcode
PRINT_RECOVERY_RESUME PARK_ENABLE_X=1 PARK_ENABLE_Y=0 PARK_X=-6 PURGE=0 PURGE_LENGTH=10 LIFT_Z=5
```

| Parâmetro | Equivale a | Valores aceitos |
|---|---|---|
| `PARK_ENABLE_X` | `park_enable_x` | `1`/`0`, `True`/`False`, `yes`/`no`, `on`/`off` |
| `PARK_ENABLE_Y` | `park_enable_y` | igual ao acima |
| `PARK_X` | `park_x` | número |
| `PARK_Y` | `park_y` | número |
| `PURGE` | `purge` | igual ao acima |
| `PURGE_LENGTH` | `purge_length` | número `0` ou maior |
| `LIFT_Z` | `lift_z` | número `0` ou maior |

`PRINT_RECOVERY_DISCARD` aceita `LIFT_Z` (número `0` ou maior), `HOME_X` e
`HOME_Y` (mesmos valores liga/desliga acima). O que ficar de fora usa o valor
do `printer.cfg`, ou o padrão.

## Variáveis de macro

Se seus macros guardam estado em variáveis (`variable_xxx:` na seção deles, por
exemplo um macro inicial ou um contador de camada), os valores do último
snapshot são salvos e recolocados antes de reabrir o arquivo. Só valores
simples (números, texto, `True`/`False`, listas) são mantidos. Isso não desfaz
o que um macro fez fora das variáveis. Use `macro_variables` para salvar só
alguns macros ou nenhum.

## Comandos

| Comando | O que faz |
|---|---|
| `PRINT_RECOVERY_STATUS` | Mostra a impressão interrompida e a janela de novo |
| `PRINT_RECOVERY_RESUME` | Retoma |
| `PRINT_RECOVERY_DISCARD` | Descarta |

## Quando a impressora voltar

- Se o Klipper mostrar um erro como *"Lost communication with MCU"*, use
  **Firmware Restart**. A janela aparece quando o Klipper ficar pronto.
- **Não** mova os eixos à mão: a retomada assume que o Z não mudou.

## Limites — leia

- **O Z é assumido como inalterado.** Sem energia os motores soltam. Se o
  pórtico desceu ou foi movido, o bico pode encostar na peça. Por isso o bico
  sobe (`lift_z`) antes de qualquer movimento.
- **A peça esfria.** Pode descolar ou empenar, e costuma ficar uma linha
  visível na altura da retomada. Retome logo e só se a peça ainda estiver colada.
- Alguns segundos antes da interrupção são impressos de novo (o G-code é lido
  à frente do movimento); no máximo fica um pequeno relevo.
- A placa não tem detector de queda de energia, então o bico não consegue
  estacionar nem retrair no instante do corte.
- Se o arquivo G-code for apagado ou alterado, a retomada é recusada.
- **Impressoras Delta não são suportadas:** elas não fazem home de X e Y
  separadamente.
- **`G28` personalizado:** se sua configuração altera o home (por exemplo, um
  `homing_override` que também faz home do Z), a etapa de home de X/Y da
  retomada pode se comportar mal. Confira se `G28 X Y` faz home só de X e Y.
- **Multifilamento e multiferramenta** (MMU/Happy Hare, IDEX, trocadores de
  ferramenta) não são tratados: só o extrusor ativo é salvo e restaurado.
- **Ventoinhas:** só a ventoinha da peça (`[fan]`) é restaurada. As demais
  (`fan_generic`, do controlador ou auxiliares) voltam ao que sua configuração
  ou macros definirem.
- **Câmara:** aquecedores de câmara e suas temperaturas não são salvos nem
  restaurados.
- O timelapse mantém os quadros anteriores. O Moonraker registra a retomada
  como um novo trabalho no histórico.
