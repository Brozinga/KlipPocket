# Usando o Happy Hare (multifilamento / MMU)

**Idiomas: [English](../happy-hare.md) · [Português (BR)](happy-hare.md) · [简体中文](../zh-Hans/happy-hare.md)**

O [Happy Hare](https://github.com/moggieuk/Happy-Hare) é o driver do Klipper
para unidades multimaterial (MMU): ERCF, Tradrack, BoxTurtle, 3MS, 3D Chameleon,
HTLF, KMS, MMX e muitas outras. O KlipPocket traz o **Happy Hare v4.1.0** e o
componente dele para o Moonraker, tanto para o Klipper quanto para o
[Kalico](kalico.md). Nada é ligado até sua configuração pedir, então impressoras
sem MMU não são afetadas.

Você configura o Happy Hare **no app** (ou no [dashboard](dashboard.md)): responde
às mesmas perguntas do menu de instalação do próprio Happy Hare e o KlipPocket
escreve os arquivos, sem precisar de computador.

## O que você precisa

- Uma placa da MMU com firmware **USB** do Klipper, conectada ao celular/tablet
  (de preferência por um hub com alimentação). Motores e servos precisam de
  fonte própria.
- Um perfil de impressora já criado no KlipPocket.

## Configurar no app

1. Abra **Configurações → Happy Hare** e escolha a impressora (com uma só, ela
   abre direto). Toque em **Set up Happy Hare** (Configurar o Happy Hare).

   <p align="center"><img src="../images/happy-hare/app/01-settings.jpg" alt="Configurações com a entrada Happy Hare" width="240"> <img src="../images/happy-hare/app/02-home.jpg" alt="Configurar o Happy Hare" width="240"></p>

2. Escolha a sua MMU. O Happy Hare preenche os padrões que o projetista dela
   recomenda.

   <p align="center"><img src="../images/happy-hare/app/03-mmu.jpg" alt="A lista de MMUs" width="240"></p>

3. Percorra as perguntas. Elas são o menu do próprio Happy Hare e aparecem em
   inglês. Abra uma seção para ver as perguntas; toque numa para mudá-la e use
   **Restaurar o padrão** na janela dela para desfazer. Uma chave esmaecida é
   fixada pelas suas outras respostas. As perguntas que dependem de uma resposta
   aparecem logo abaixo dela (por exemplo a versão do Turtle Neck sob o tipo de
   MMU).

   <p align="center"><img src="../images/happy-hare/app/04-questions.jpg" alt="As perguntas" width="240"></p>

4. Abra **MCU connection** e defina o dispositivo da placa da MMU. Em
   **Enter serial device path**, escolha a placa conectada na lista ou digite o
   caminho dela (veja **Configurações → USB → Listar dispositivos USB**).

   <p align="center"><img src="../images/happy-hare/app/05-mcu.jpg" alt="MCU connection" width="240"> <img src="../images/happy-hare/app/06-device-path.jpg" alt="Caminho do dispositivo serial" width="240"></p>

5. Na primeira página, toque em **Revisar e criar**. Se aparecer **Respostas que
   ainda faltam**, volte e preencha. Depois escolha:

   - **Adicionar o Happy Hare ao printer.cfg e ao moonraker.conf**: ligado, o
     KlipPocket adiciona as linhas (é guardada uma cópia de cada arquivo).
     Desligado, você adiciona à mão, veja
     [Adicionando as linhas você mesmo](#adicionando-as-linhas-você-mesmo).
   - **Zerar também a calibração e o estado dos gates**: ligado quando você
     escolheu outra MMU. Deixe desligado se só mudou configurações, para manter
     a calibração em `mmu/mmu_vars.cfg`.

   A pasta `mmu` da impressora é apagada e criada de novo, então alterações
   feitas à mão nela são perdidas. Toque em **Criar a configuração**.

   <p align="center"><img src="../images/happy-hare/app/07-review.jpg" alt="Revisar e criar" width="240"> <img src="../images/happy-hare/app/08-done.jpg" alt="Configuração criada" width="240"></p>

6. Reinicie o perfil da impressora (ou envie **Firmware Restart**) para o Klipper
   carregar o Happy Hare.

## Configurar no dashboard

O [dashboard](dashboard.md) tem os mesmos passos na aba **Happy Hare**, ao lado de
Configurações: escolher a impressora, **Configurar o Happy Hare**, a MMU, as
perguntas e **Revisar e criar**. Em **MCU connection** uma lista oferece os
dispositivos USB que o celular enxerga.

<p align="center"><img src="../images/happy-hare/web/01-home.jpg" alt="Aba Happy Hare" width="420"> <img src="../images/happy-hare/web/02-mmu.jpg" alt="Escolher a MMU" width="420"></p>
<p align="center"><img src="../images/happy-hare/web/04-usb.jpg" alt="MCU connection" width="420"> <img src="../images/happy-hare/web/05-review.jpg" alt="Revisar e criar" width="420"></p>

## Adicionando as linhas você mesmo

Se você deixou **Adicionar o Happy Hare ao printer.cfg e ao moonraker.conf**
desligado, a última tela mostra as linhas a adicionar (ou toque em **Adicionar
agora** para o KlipPocket fazer isso).

<p align="center"><img src="../images/happy-hare/app/09-manual.jpg" alt="Linhas a adicionar" width="240"> <img src="../images/happy-hare/web/07-manual.jpg" alt="Linhas a adicionar no dashboard" width="420"></p>

Abra a pasta de configuração do perfil da impressora, a que tem o `printer.cfg`
(o gerenciador de arquivos de configuração do Fluidd/Mainsail, ou a entrada do
KlipPocket no app Arquivos do Android), e:

1. Adicione as linhas `[include …]` mostradas no **início** do `printer.cfg`.
   Normalmente são:

   ```ini
   [include mmu/base/*.cfg]
   [include mmu/macros/*.cfg]
   ```

   mais `[include mmu/optional/client_macros.cfg]` (e `mmu_menu.cfg`) quando
   essas opções estão ligadas; a tela mostra exatamente quais.
2. Adicione isto no **final** do `moonraker.conf`:

   ```ini
   [mmu_server]
   enable_file_preprocessor: True
   enable_toolchange_next_pos: True
   update_spoolman_location: True
   ```

   Não adicione uma seção `[update_manager happy-hare]`: o app atualiza o Happy
   Hare junto com o próprio app.
3. Confira se o `printer.cfg` tem uma seção `[pause_resume]` e não define
   `PAUSE`, `RESUME` ou `CANCEL_PRINT` próprios (o Happy Hare já os fornece).
   Uma duplicata faz o Klipper parar com `gcode command … already registered`.
4. Reinicie o perfil da impressora.

## Trocar a MMU ou as configurações depois

Abra **Configurações → Happy Hare** e a impressora de novo.

<p align="center"><img src="../images/happy-hare/app/10-installed.jpg" alt="Uma impressora com Happy Hare" width="240"></p>

- **Editar as configurações** abre as respostas que você salvou; mude o que
  precisar e crie a configuração de novo.
- **Trocar a MMU (começar do zero)** parte dos padrões da MMU que você escolher.
  Ao criar a configuração, a pasta `mmu` antiga é apagada e uma nova é escrita
  (deixe **Zerar também a calibração e o estado dos gates** ligado). A placa USB
  usada antes é oferecida de novo, então você não precisa escolhê-la outra vez.

## Ver a MMU nas interfaces web

Depois que o Klipper carrega o Happy Hare, o Fluidd e o Mainsail que acompanham
o app mostram a MMU na página **Dashboard**, sem nada para ligar: o Fluidd tem um
cartão **MMU** e o Mainsail um painel **MMU**, com os gates, a ferramenta
selecionada, o estado do filamento e os botões para pré-carregar, ejetar e
recuperar. Mova ou esconda-os pelas opções de layout do dashboard de cada
interface.

No console do Fluidd ou do Mainsail, `MMU_STATUS` mostra o resumo da MMU e
`MMU_HELP` lista todos os comandos. O log do próprio Happy Hare fica junto do
`klippy.log`, como `mmu.log`.

O [KlipperScreen for Happy Hare](https://github.com/moggieuk/KlipperScreen-Happy-Hare-Edition)
é um projeto separado para telas touch.

## Solução rápida de problemas

| Mensagem | Causa provável |
|---|---|
| `MMU_STATUS` responde `Unknown command` | Falta a linha `[include mmu/base/*.cfg]` no `printer.cfg`, ou a pasta `mmu` não está lá. |
| `gcode command … already registered` | Um macro do `printer.cfg` duplica um que o Happy Hare fornece (por exemplo `PAUSE`, `RESUME` ou `CANCEL_PRINT`). Remova o seu. |
| `mcu 'unit0': Unable to connect` / `Unable to open serial port` | A placa não tem firmware USB, ou o caminho mudou depois de regravar: escolha-a de novo em **MCU connection** e crie a configuração outra vez. |
| `Happy Hare's Klipper modules failed to load` | Avise. O conselho do `install.sh` nessa mensagem não vale para o app. |
