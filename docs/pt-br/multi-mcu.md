# Usando mais de um MCU (multi-MCU)

**Idiomas: [English](../multi-mcu.md) · [Português (BR)](multi-mcu.md) · [简体中文](../zh-Hans/multi-mcu.md)**

Algumas impressoras usam uma segunda placa junto da principal: uma placa de
cabeçote, uma placa que adiciona portas (drivers, endstops, ventoinhas) ou uma
placa pequena que só carrega um sensor (acelerômetro, sonda de corrente de
Foucault). No Klipper, cada uma é uma seção `[mcu nome]` extra.

O KlipPocket **não tem uma tela dedicada a multi-MCU**. Mesmo assim, funciona
manualmente, porque o app dá a cada placa USB a sua própria porta serial
virtual, e você escreve essa porta no `printer.cfg`. Esta página mostra como.

> **Ainda não testado com duas placas.** Os passos abaixo seguem o jeito como o
> app trata dispositivos USB hoje. Se você testar, conte como foi.

## O que funciona e o que não funciona

| Configuração | No app |
|---|---|
| Placa extra com firmware **USB** (Klipper USB / CDC) | Possível, como descrito aqui |
| Placa extra no **barramento CAN** (inclusive com adaptador USB-CAN) | **Não suportado** — o Android não dá interface CAN aos apps |
| Placas que só têm pinos **UART** | Não suportado — o app fala só USB |

Ou seja, a placa extra precisa ser compilada com interface **USB** na
[geração de firmware](build-firmware.md) e gravada nela. Muitas placas vêm com
firmware CAN ou aceitam os dois, então confira qual a sua usa.

## O que você precisa

- Todas as placas alcançáveis por USB a partir do celular/tablet: um **hub USB
  com alimentação própria** é fortemente recomendado, pois o aparelho não
  alimenta várias placas.
- Motores e aquecedores da placa extra continuam precisando de fonte própria: o
  USB só alimenta a lógica da placa.
- Cada placa gravada com firmware Klipper (interface USB).

## Passo 1 — Escolha como os dispositivos são nomeados

Abra **Configurações → USB**. **Device naming** (nomeação de dispositivos)
define o nome de cada porta serial virtual. Tocar nele alterna entre os dois
modos:

| By path | By VID/PID |
|---|---|
| ![Device naming em By path](../images/multi-mcu/usb-naming-by-path.png) | ![Device naming em By VID/PID](../images/multi-mcu/usb-naming-by-vid-pid.png) |

- **By VID/PID** — usa o id de fabricante/produto USB, por exemplo `1a86_7523`.
  O nome continua o mesmo se você trocar o cabo de porta. Use quando as placas
  têm ids **diferentes** (por exemplo, a principal com CH340 e outra com outro
  chip).
- **By path** — usa o caminho no barramento USB, por exemplo
  `_dev_bus_usb_001_003`. Use quando duas placas têm o **mesmo** id. Todas as
  placas com o firmware USB do próprio Klipper aparecem com o mesmo id
  (`1d50:614e`), então duas delas dividiriam um nome em *By VID/PID*. O ponto
  fraco é que o caminho pode mudar ao desconectar e reconectar, ou ao usar
  outra porta do hub, e aí o `printer.cfg` precisa ser atualizado.

Mudar a nomeação muda os caminhos; por isso decida antes de editar o
`printer.cfg` e reinicie o perfil depois de mudar.

## Passo 2 — Descubra o caminho de cada placa

1. Conecte todas as placas (pelo hub) e aceite o pedido de permissão USB de
   cada uma.
2. Abra **Configurações → USB → List USB devices**.

Cada placa reconhecida aparece com `vid/pid`, o nome do dispositivo, o driver e,
na linha seguinte, **o caminho serial para usar no `printer.cfg`**. Exemplo
real:

A primeira placa é a principal (chip CH340, `1a86/7523`), com o caminho serial na linha seguinte. A segunda entrada (`1908/226`) é uma webcam: não tem linha de caminho porque não é uma placa Klipper. A mesma placa recebe um caminho diferente conforme o modo de nomeação:

| By path | By VID/PID |
|---|---|
| ![Lista de dispositivos USB, By path](../images/multi-mcu/usb-list-by-path.png) | ![Lista de dispositivos USB, By VID/PID](../images/multi-mcu/usb-list-by-vid-pid.png) |

Copie o caminho completo de cada placa Klipper (a linha que começa com
`/data/user/0/…`).

## Passo 3 — Escreva no `printer.cfg`

Use o caminho da placa principal em `[mcu]` e uma seção `[mcu nome]` para cada
placa adicional. Edite o `printer.cfg` pelo Fluidd/Mainsail como de costume.

```ini
[mcu]
serial: /data/user/0/com.brozinga.klippocket/files/serial/1a86_7523

[mcu toolhead]
serial: /data/user/0/com.brozinga.klippocket/files/serial/1d50_614e
```

Os pinos da placa extra levam o nome como prefixo, `nome:PINO`:

```ini
# Uma ventoinha e um endstop na placa extra
[fan_generic part_fan]
pin: toolhead:PA0

[stepper_z]
endstop_pin: toolhead:PB6

# Um acelerômetro em uma placa só de sensor
[adxl345]
cs_pin: toolhead:gpio9
spi_bus: spi1a

[resonance_tester]
accel_chip: adxl345
probe_points: 100, 100, 20
```

Os nomes dos pinos dependem da placa; use os da documentação dela. Uma seção
como `[mcu toolhead]` só precisa do `serial:`; não há linha `baud:` para
configurar (o app cuida da conexão).

Salve e reinicie. No Fluidd/Mainsail o painel **System** lista os dois MCUs, e o
`Klipper` deve chegar em **Ready**.

## O que saber

- **Comece com a placa extra conectada.** Se um MCU configurado estiver
  ausente, o Klipper não termina de iniciar e avisa *Unable to connect*.
- **Regravar ou reconectar** pode mudar o caminho em *By path*. Se o Klipper
  parar com *Unable to open serial port*, rode **List USB devices** de novo e
  atualize o caminho.
- **Mesmo chip, mesmo id:** se duas placas têm o mesmo id e você usa *By
  VID/PID*, elas disputam um nome. Troque para *By path*.
- Funciona nos dois motores, [Klipper e Kalico](kalico.md).
- Placas que dependem de software extra (como o Happy Hare) são outro assunto:
  esses módulos não estão disponíveis no app.

## Solução rápida de problemas

| Mensagem | Causa provável |
|---|---|
| `Unable to open serial port` | O caminho no `printer.cfg` não existe mais. Liste os dispositivos de novo. |
| `mcu 'toolhead': Unable to connect` | A placa não está conectada, não tem firmware USB ou está sem energia. |
| Placa ausente em **List USB devices** | A permissão não foi aceita, o cabo é só de energia ou o hub não tem alimentação. |
