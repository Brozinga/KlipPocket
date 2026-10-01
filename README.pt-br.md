# KlipPocket - Klipper brain for your 3D printer

<p align="center">
  <img src="docs/images/logo.png" alt="KlipPocket" width="260">
</p>

<p align="center">
  <a href="https://github.com/Brozinga/KlipPocket/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/KlipPocket?label=%C3%BAltima%20release&color=49CBEB" alt="Última release"></a>
  <img src="https://img.shields.io/badge/platform-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/license-GPL--3.0-4B8BBE" alt="License: GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**Leia em outros idiomas: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

**O KlipPocket transforma um celular ou tablet Android no cérebro da sua impressora 3D.**
Conecte-o à impressora por USB e ele roda o [Klipper](https://github.com/KevinOConnor/klipper) ou o [Kalico](https://github.com/KalicoDTU/kalico) para você — sem Raspberry Pi, sem SSH, sem Linux para manter.

Muitas impressoras "clássicas" ainda usam placas Marlin lentas e fechadas. O KlipPocket leva o Klipper moderno até elas, sem a complexidade de sempre: instale o app, grave o firmware na impressora uma vez e controle tudo pelas interfaces web no navegador. Tudo roda localmente no aparelho que você já tem, e as ferramentas extras — acesso remoto, monitoramento por câmera, detecção de falhas, recuperação de impressão — ficam a um toque de distância.

> **Só quer instalar?** Baixe o APK mais recente na
> [página de Releases](https://github.com/Brozinga/KlipPocket/releases/latest) — se não tiver certeza de qual, escolha `armv7` (veja
> [Escolhendo o pacote certo](#escolhendo-o-pacote-certo) abaixo).

## Funcionalidades

- **Klipper 0.13 e anteriores** — compatível com o firmware Klipper atual e com versões antigas (Kalico também é suportado)
- **Softwares atualizados** — Moonraker, Fluidd, Mainsail e Voyager UI recentes embutidos no app
- **Conexão remota** — acesse sua impressora de qualquer lugar com OctoEverywhere, Obico e outros
- **Detecção de falhas por IA** — perceba impressões com problema cedo, via Obico
- **Print Recovery** — retome uma impressão após uma queda de energia
- **Monitoramento por câmera** — use a câmera do aparelho ou uma webcam USB, com pré-visualização ao vivo, zoom e timelapse
- **Interfaces web** — Fluidd, Mainsail e Voyager UI, servidas pelo próprio aparelho

## Escolhendo o pacote certo

O KlipPocket oferece três variantes de APK:

| Arquitetura | Nome do pacote | Uso |
|-------------|----------------|-----|
| arm64 | `KlipPocket_v*_arm64.apk` | Aparelhos modernos de 64 bits |
| armv7 | `KlipPocket_v*_armv7.apk` | Aparelhos antigos de 32 bits — funciona na maioria dos aparelhos (recomendado se estiver em dúvida) |
| x86_64 | `KlipPocket_v*_amd64.apk` | Tablets x86_64, Chromebooks, emuladores Android |

**Como descobrir a arquitetura do seu aparelho:**
- **Configurações > Sobre o telefone > Arquitetura** ou **Arquitetura do kernel**
- Ou instale um app de informações de CPU, como "CPU-Z" ou "AIDA64"
- Se estiver em dúvida, escolha armv7 — é o pacote que funciona na maior variedade de aparelhos

## Início rápido

Siga estes passos, na ordem:

1. **Gere e grave o firmware na impressora.** O Klipper precisa de um firmware próprio na placa-mãe da impressora. Gere-o para a sua placa e grave-o seguindo [`docs/pt-br/build-firmware.md`](docs/pt-br/build-firmware.md).
2. **Confira a conexão.** A impressora precisa conversar com o aparelho por **USB**, e o aparelho Android precisa suportar **OTG** (USB host).
   - Alguns aparelhos **não carregam enquanto a porta USB é usada para dados**. Nesse caso a bateria descarrega em impressões longas, e a solução é uma ligação de carga soldada internamente (direto nos pinos da bateria).
   - Antes de comprar um hub ou cabo, **verifique se o seu aparelho consegue carregar e transferir dados ao mesmo tempo** pela conexão que pretende usar (veja [Qual hub USB usar?](#qual-hub-usb-usar)).
3. **Instale o app e configure-o.** Instale o APK, adicione sua primeira impressora e abra a interface web seguindo o guia ilustrado [**Primeiros passos**](docs/pt-br/getting-started.md).

> **Travou?** A aba **Logs** do app mostra os logs do Klipper, do Moonraker e do app, e deixa copiar/compartilhar sem PC.

## Documentação

Todos os guias ficam em [`docs/`](docs/pt-br/index.md) (também em English e 简体中文):

- [Primeiros passos](docs/pt-br/getting-started.md) — instale o app e configure sua primeira impressora, passo a passo
- [Compilando o firmware da MCU](docs/pt-br/build-firmware.md) — gere o firmware para gravar na placa da impressora
- [Recuperação de impressão](docs/pt-br/print-recovery.md) — retome uma impressão após queda de energia
- [Timelapse](docs/pt-br/timelapse.md) — grave um timelapse e encontre o vídeo
- [Câmera / Webcam](docs/pt-br/webcam.md) — câmera do aparelho, webcam USB, pré-visualização, zoom e toque para focar
- [OctoEverywhere](docs/pt-br/octoeverywhere.md) — acesso remoto com OctoEverywhere
- [Obico](docs/pt-br/obico.md) — acesso remoto e detecção de falhas por IA com Obico
- [Complementos do Klipper](docs/pt-br/mods/klipper-addons.md) — KAMP, LED Effect, Z Calibration, Auto Speed, TMC Autotune
- [Input shaper sem acelerômetro](docs/pt-br/mods/input-shaper-manual.md) — método da torre de ringing e macros
- [Compilando o APK](docs/pt-br/build-app.md) — compile o app você mesmo

## Quais portas as interfaces web e a câmera usam?

O endereço aparece na tela principal sempre que alguma instância está rodando (`IP` é o endereço do aparelho na sua rede). Cada interface web tem a própria porta, acompanhando o seletor de front end do app:

| Serviço | Endereço |
|---|---|
| Fluidd | `http://IP:4408/` |
| Mainsail | `http://IP:4409/` |
| Voyager UI | `http://IP:4410/` |
| Transmissão da câmera | `http://IP:8889/` |
| Snapshot da câmera (JPEG único) | `http://IP:8889/snapshot` |

## Início automático

O KlipPocket pode iniciar sozinho quando o aparelho liga, deixando a impressora pronta sem tocar na tela:

1. Ative o **Início automático** em cada perfil de impressora que deve iniciar sozinho.
2. Defina o KlipPocket como o **launcher padrão** (app de tela inicial) do aparelho.
3. Se o armazenamento do aparelho for criptografado (padrão na maioria), **remova a senha/PIN da tela de bloqueio** — o sistema só libera o app depois que você a digita.

## Aviso sobre atividade em segundo plano

Alguns fabricantes restringem apps em segundo plano, o que pode interromper uma impressão em andamento. Para evitar:

- Permita que o KlipPocket ignore a otimização de bateria quando o app pedir (é a primeira tela ao abrir).
- Permita toda a atividade em segundo plano para o app nas configurações do aparelho.
- Definir o app como launcher padrão também ajuda em sistemas mais restritivos.

## Suporte a Android TV?

Sim — deve funcionar normalmente. Note que algumas TV boxes baratas não permitem definir o KlipPocket como launcher sem desativar antes o do sistema; use ADB ou root para desativá-lo.

## Qual hub USB usar?

Hub é **suportado**, mas não é obrigatório se o seu aparelho conseguir falar com a impressora e carregar ao mesmo tempo. Se precisar de um, o recomendado é um hub com **no mínimo uma porta USB-A (para a impressora) e uma porta de energia/carregamento** (como USB-C com passagem Power Delivery), e só se o aparelho suportar carregar em modo OTG. Confirme que a combinação funciona com o seu aparelho antes de confiar nela em impressões longas.

## Limitações

- O servidor web não roda na porta padrão porque o Android/Linux não deixa apps de espaço de usuário usarem portas abaixo de 1024, e a porta 80 seria a de `http://IP`
- Alguns aparelhos resetam o caminho do dispositivo após reiniciar o firmware — nesse caso use nomeação por VID/PID
- Sem SSH (você não vai compilar firmware nem rodar serviços extras no aparelho de qualquer forma)
- Alguns aparelhos não suportam OTG e carga ao mesmo tempo — nesse caso é preciso soldar direto nos pinos da bateria (ou usar outro aparelho, você decide)
- Só é suportado o baud rate 250000 (o autor não quis repassar essa configuração ao driver USB do Android; quase toda config usa 250000 mesmo)

> **Problema mais comum:** se o seu celular não consegue carregar e falar com
> a impressora ao mesmo tempo pelo mesmo cabo, é a limitação de OTG+carga
> acima — um hub USB com fonte própria (veja [Qual hub USB usar?](#qual-hub-usb-usar)) resolve.

## Créditos

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** — portou o aplicativo para Kotlin e refez o visual.
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** — o projeto original que deu origem a este.
- Klipper, Kalico, Moonraker, Fluidd, Mainsail e os demais componentes embutidos pertencem aos seus respectivos autores (veja [O que vem dentro?](#o-que-vem-dentro)).
- **[Voyager UI](https://github.com/ozancs/voyager-ui)** de [ozancs](https://github.com/ozancs) — o terceiro front end web que você pode escolher ao lado de Fluidd e Mainsail.

## Contribuindo

Pull requests são bem-vindos!
