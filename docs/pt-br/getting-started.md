# Primeiros passos — instalar o KlipPocket e configurar sua primeira impressora

**Idiomas: [English](../getting-started.md) · [Português (BR)](getting-started.md) · [简体中文](../zh-Hans/getting-started.md)**

Um passo a passo para quem nunca fez isso antes. Os passos servem para qualquer
impressora que já rode o firmware Klipper.

> Os prints foram feitos em um celular Android e as páginas do
> Fluidd/Mainsail no Chrome. Seu celular pode ser um pouco diferente
> (principalmente as telas de instalação do Android), mas a ordem dos passos é
> a mesma. O Fluidd mostra as mensagens no idioma do seu navegador.

## O que você precisa

- Um celular ou tablet Android (Android 5.0 ou mais novo) que ficará ligado na
  tomada ao lado da impressora.
- Um **cabo/adaptador USB OTG** para ligar a impressora ao celular.
- Uma impressora que já rode o **firmware Klipper** (se a sua não roda, veja
  [build-firmware.md](build-firmware.md)).
- O celular e o computador que você vai usar para abrir a página devem estar na
  **mesma rede Wi‑Fi**.

## 1. Baixe o APK certo

Baixe o `.apk` na página de **Releases** do projeto. Há três versões — escolha
a que combina com o processador do seu celular:

| APK | Use em |
|---|---|
| `arm64` | A maioria dos celulares de 2017 em diante (64 bits) |
| `armv7` | Celulares e tablets mais antigos (32 bits) — **na dúvida, escolha este**, ele também roda em celulares de 64 bits |
| `amd64` | Emuladores e aparelhos Android Intel/AMD |

## 2. Instale o APK

1. Abra o app **Arquivos** (ou **Meus Arquivos**) do celular e vá em **Downloads**.
2. Toque no arquivo APK.

<p align="center"><img src="../images/setup/01-apk-in-downloads.png" alt="O APK do KlipPocket na pasta Downloads" width="288"></p>

3. O Android pergunta se você quer instalar o app. Toque em **Instalar**. (Se
   disser que instalar desta origem está bloqueado, toque em **Configurações**,
   permita para o seu app de Arquivos e volte.)

<p align="center"><img src="../images/setup/02-install-prompt.png" alt="Pergunta de instalação" width="288"></p>

4. O Google Play Protect pode recomendar uma análise, porque o app não vem da
   Play Store. Toque em **Analisar app** e espere alguns segundos — quando a
   verificação disser que é seguro, a instalação continua sozinha. (Evite
   **Instalar sem análise**: em alguns aparelhos ela deixa a instalação presa em
   "Instalando…".)

<p align="center"><img src="../images/setup/03-play-protect.png" alt="Play Protect: análise do app recomendada" width="288"></p>

5. Espere o **Instalando…** terminar e toque em **Abrir**.

<p align="center"><img src="../images/setup/05-installing.png" alt="Instalando" width="288"></p>

> **O "Instalando…" nunca termina?** Em alguns aparelhos a verificação do
> Google Play Protect trava e a instalação fica minutos parada (o app em si
> está ok). Toque em **Cancelar**, abra o APK de novo e escolha **Analisar
> app**: assim que o Play Protect aprovar, a instalação termina em segundos.
> Não é preciso desativar nada.

## 3. Primeiro uso: bateria

O app precisa continuar rodando em segundo plano durante a impressão. Na
primeira tela ative as duas opções e toque em **Próximo**; quando o Android
perguntar se pode parar a otimização de bateria do KlipPocket, toque em
**Permitir**.

<p align="center"><img src="../images/setup/07-battery-dialog.png" alt="Pergunta de otimização de bateria" width="288"> <img src="../images/setup/08-battery-done.png" alt="As duas opções de bateria ativadas" width="288"></p>

Você chega à tela principal, vazia até adicionar uma impressora. (O idioma do
app pode ser trocado a qualquer momento em **Configurações → Idioma do app**.)

<p align="center"><img src="../images/setup/09-main-empty.png" alt="Tela principal vazia" width="288"></p>

## 4. Adicione sua impressora

1. Toque no botão grande **+** na parte de baixo.
2. **Nome** — qualquer um, por exemplo `Printer 1`.
3. **Config** — toque e escolha a configuração inicial do seu modelo. A lista é
   grande e em ordem alfabética; os modelos começam com `printer-` seguido da
   marca. Escolha a que corresponde à sua impressora. Se o seu modelo não estiver na
   lista, escolha `example-cartesian.cfg` e ajuste depois (passo 7).
4. **Iniciar automaticamente** — ative se quiser que esta impressora inicie
   sozinha sempre que o app abrir.
5. Toque em **Criar**.

<p align="center"><img src="../images/setup/10-new-profile.png" alt="Tela Novo perfil" width="216"> <img src="../images/setup/11-config-list.png" alt="Lista de configs" width="216"> <img src="../images/setup/12-pick-config.png" alt="Escolhendo a config da impressora" width="216"> <img src="../images/setup/13-new-profile-filled.png" alt="Formulário preenchido" width="216"></p>

Sua impressora aparece em **Instâncias**, marcada como **Parada** (Idle).

<p align="center"><img src="../images/setup/14-instance-created.png" alt="Impressora criada" width="288"></p>

## 5. Inicie e permita o acesso USB

1. Ligue a impressora ao celular com o cabo OTG.
2. Toque no botão **▶ iniciar** (o grande, embaixo, inicia todas as
   impressoras; o pequeno no cartão inicia só aquela).
3. O Android pergunta **"Permitir que o app KlipPocket acesse USB Serial?"** —
   marque **Sempre abrir o app KlipPocket quando USB Serial for conectado** e
   toque em **OK**.

<p align="center"><img src="../images/setup/16-usb-permission.png" alt="Pedido de permissão USB" width="288"></p>

> Perdeu a pergunta ou tocou em Cancelar? Feche o app completamente e abra de
> novo — a pergunta volta. Se o Fluidd/Mainsail mostrar **MCU error during
> connect**, quase sempre é esse o motivo.

O cartão muda para **Rodando** e o endereço da página web aparece no topo. O
Mainsail é `http://<IP-do-celular>:4409/` e o Fluidd é
`http://<IP-do-celular>:4408/`.

<p align="center"><img src="../images/setup/15-starting.png" alt="Impressora rodando com o endereço web" width="288"></p>

## 6. Abra o Fluidd ou o Mainsail

Em um computador ou outro celular na mesma rede Wi‑Fi, abra o endereço mostrado
no app. Para alternar entre as duas interfaces vá em **Configurações → Front
end web** e toque no bloco.

<p align="center"><img src="../images/setup/17-settings-frontend.png" alt="Configurações: bloco Front end web" width="288"></p>

## 7. Corrija os avisos de "configuração faltando"

Na primeira vez, as duas interfaces vão reclamar que alguns recursos padrão
de impressora não estão no `printer.cfg` (pausar/retomar, cancelar impressão,
…). É normal — adicione uma vez e pronto.

O **Mainsail** mostra uma caixa laranja **Missing configuration**:

<p align="center"><img src="../images/setup/20-mainsail-missing-config.png" alt="Mainsail: configuração faltando" width="720"></p>

O **Fluidd** mostra uma caixa de **avisos** com os mesmos itens:

<p align="center"><img src="../images/setup/30-fluidd-missing-config.png" alt="Fluidd: avisos" width="720"></p>

### As linhas a adicionar

Adicione este bloco no começo do `printer.cfg` (qualquer lugar fora de outra
seção serve). **O recuo de dois espaços nas linhas abaixo de `gcode:` é
obrigatório.**

```ini
[pause_resume]

[display_status]

[gcode_macro CANCEL_PRINT]
description: Cancel the actual running print
rename_existing: CANCEL_PRINT_BASE
gcode:
  TURN_OFF_HEATERS
  CANCEL_PRINT_BASE

[gcode_macro PAUSE]
rename_existing: PAUSE_BASE
gcode:
  PAUSE_BASE

[gcode_macro RESUME]
rename_existing: RESUME_BASE
gcode:
  RESUME_BASE
```

### No Mainsail

1. Clique em **MACHINE** no menu da esquerda.
2. Em **Config Files**, clique em **printer.cfg**.

<p align="center"><img src="../images/setup/21-mainsail-machine-page.png" alt="Página Machine do Mainsail" width="720"></p>

3. O editor abre. Clique numa linha vazia perto do topo e cole o bloco.
4. Clique em **SAVE & RESTART** (canto superior direito).

<p align="center"><img src="../images/setup/22-mainsail-printer-cfg-editor.png" alt="Editor do Mainsail antes" width="720"></p>
<p align="center"><img src="../images/setup/23-mainsail-cfg-added-lines.png" alt="Editor do Mainsail com o bloco colado" width="720"></p>

Depois do reinício a caixa laranja some:

<p align="center"><img src="../images/setup/24-mainsail-after-restart.png" alt="Painel do Mainsail sem avisos" width="720"></p>

### No Fluidd

1. Clique no ícone **{…} Configurar** no menu da esquerda.
2. Em **Ficheiros configuração**, clique em **printer.cfg**.

<p align="center"><img src="../images/setup/31-fluidd-configuration-page.png" alt="Página de configuração do Fluidd" width="720"></p>

3. Clique numa linha vazia perto do topo e cole o bloco. Se digitar à mão em
   vez de colar, o editor do Fluidd **não** faz o recuo sozinho — linhas
   vermelhas no editor significam que faltam os dois espaços.
4. Clique em **SALVAR E REINICIAR** na barra do topo.

<p align="center"><img src="../images/setup/32-fluidd-printer-cfg-editor.png" alt="Editor do Fluidd antes" width="720"></p>
<p align="center"><img src="../images/setup/33-fluidd-cfg-added-lines.png" alt="Editor do Fluidd com o bloco adicionado" width="720"></p>

Os avisos somem e os botões **CANCEL_PRINT**, **PAUSE** e **RESUME** aparecem
no painel:

<p align="center"><img src="../images/setup/34-fluidd-after-restart.png" alt="Painel do Fluidd depois da correção" width="720"></p>

## 8. Se o Moonraker reclamar do `[virtual_sdcard]`

O KlipPocket escreve a seção `[virtual_sdcard]` para você, com a pasta certa de
cada impressora, perto do fim do `printer.cfg` (logo acima da linha
`#*# <--- SAVE_CONFIG --->`). **Não adicione a sua.**

Se ainda aparecer uma mensagem como *"GCode path received from Klipper does not
match expected location"* — normalmente depois de copiar um `printer.cfg` de
outra instalação, ou de recriar a impressora — abra o `printer.cfg`, ache o
`[virtual_sdcard]` e coloque em `path:` **exatamente** o caminho escrito na
mensagem (ou apague a sua cópia da seção e reinicie, para o app escrever uma
nova). Depois clique em **Save & Restart**.

## 9. Ajuste o resto para a sua impressora

O arquivo inicial é um preset genérico. Confira `[mcu]`, pinos, tamanho da mesa
(`position_max`), `z_offset`, PID e malha com a sua impressora, e calibre (PID,
Z offset, malha da mesa) antes da primeira impressão. Se a sua impressora ainda
não roda Klipper, veja [build-firmware.md](build-firmware.md).

## Próximos passos

- Adicione uma webcam e use o **toque para focar** na pré-visualização: [webcam.md](webcam.md).
- Acesso remoto: [octoeverywhere.md](octoeverywhere.md) · [obico.md](obico.md).
- Add-ons opcionais: [mods/klipper-addons.md](mods/klipper-addons.md).
