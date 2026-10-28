# Usando o Kalico

**Idiomas: [English](../kalico.md) · [Português (BR)](kalico.md) · [简体中文](../zh-Hans/kalico.md)**

O [Kalico](https://github.com/KalicoCrew/kalico) é um fork do Klipper mantido
pela comunidade, que permite recursos e comportamentos que o Klipper deixa de
fora por poderem ser arriscados. O KlipPocket traz o **Kalico v2026.10.00** ao
lado do Klipper, e você escolhe qual deles roda a sua impressora.

## Trocando o motor

1. Pare o perfil da impressora, se estiver rodando.
2. Abra **Configurações** e toque em **Motor de firmware** para alternar entre
   Klipper e Kalico.
3. Inicie o perfil de novo.

As versões embutidas aparecem em **Configurações → Versões dos softwares**. Seus
perfis e arquivos não são alterados na troca.

## Uma mudança que o seu `printer.cfg` pode precisar

O Kalico liga o `[respond]` por padrão, o que dá a ele um `M118` próprio. Se a
sua configuração define um `[gcode_macro M118]`, o Kalico para com
`gcode command M118 already registered`. Troque a macro por uma seção
`[respond]`, que vale tanto no Klipper quanto no Kalico:

```ini
[respond]
```

## O que funciona

Verificado numa impressora real (uma Neptune 3 Pro, por USB), com o Fluidd do app:

- Iniciar, conectar à placa da impressora e abrir a interface web.
- `M118` e `RESPOND` nativos do Kalico, `BED_MESH_CHECK` e
  `RELOAD_GCODE_MACROS` (recarrega macros sem reiniciar).
- Os padrões do Kalico ativos: `[respond]`, `[force_move]` e `[exclude_object]`
  carregam sem estar na configuração.
- Uma configuração real carregando sem erros: KAMP, macros do Timelapse,
  Auto Speed, `bed_mesh`, `probe`, `safe_z_home`, `input_shaper`,
  `pause_resume`, `display_status` e o sensor de filamento.

O Happy Hare (multifilamento) também carrega e responde aos comandos `MMU_*` no
Kalico; veja [Happy Hare](happy-hare.md).

Só a carga foi verificada (o módulo importa e inicia, mas não rodou numa
impressão): LED Effect, TMC Autotune, Print Recovery, os extras de buzzer e
câmera, e o Z Calibration.

## O que não funciona, ou não foi testado

- **Z Calibration** precisa de um fim de curso físico no Z, também no Klipper.
  Com uma sonda como endstop do Z (`endstop_pin: probe:z_virtual_endstop`) ele
  para com *A virtual endstop for z is not supported*.
- **Configurações com `[gcode_macro M118]`** não iniciam no Kalico até a macro
  ser trocada, como mostrado acima.
- **Firmware da placa compilado para Klipper** funciona, mas o Kalico avisa que
  recomenda regravá-lo para o Kalico.
- **Placas opcionais** (`is_non_critical`) não foram testadas.
- O Kalico muda alguns comportamentos, como o `controller_fan` ganhar um
  `standby_speed`. Veja a
  [documentação do Kalico](https://docs.kalico.gg/Kalico_Additions.html).
