# Usando o Obico

**Idiomas: [English](../obico.md) · [Português (BR)](obico.md) · [简体中文](../zh-Hans/obico.md)**

O [Obico](https://www.obico.io) é uma plataforma de impressão 3D "smart",
open-source e feita pela comunidade: monitoramento remoto, detecção de falha
de impressão e controle remoto, a partir do Obico Cloud ou do seu próprio
Obico Server auto-hospedado. O KlipPocket roda o companion real do
`moonraker-obico` (vendorizado a partir de
[TheSpaghettiDetective/moonraker-obico](https://github.com/TheSpaghettiDetective/moonraker-obico)),
adaptado para rodar direto no aparelho em vez do serviço systemd que ele
normalmente instala.

<p align="center"><img src="../images/obico-control.jpg" alt="Página de controle do Obico mostrando a impressora, a webcam ao vivo e as temperaturas" width="640"></p>

## Ativando

1. Inicie um perfil de impressora (ele precisa estar **rodando** — o Obico
   se conecta ao perfil que estiver ativo no momento).
2. Vá em **Configurações → Acesso remoto → Obico** e ative **Enable Obico**.
3. Toque em **Servidor Obico** para escolher onde ele conecta:
   - **Obico Cloud** (`app.obico.io`) — o padrão, sem precisar hospedar
     nada.
   - **Auto-hospedado** — digite a URL do seu próprio
     [Obico Server](https://www.obico.io/docs/server-guides/).
   - Trocar de servidor limpa qualquer vinculação existente, já que ela só
     vale para o servidor que a emitiu.
4. Toque em **Vincular impressora**. O KlipPocket gera seu próprio código e
   mostra ele ali mesmo (pode levar alguns segundos, depois de ativar o
   Obico, até o companion iniciar e gerar um). No app ou site do Obico,
   adicione uma impressora, escolha **Klipper (auto-instalado)**, e digite
   esse código quando solicitado — ou toque em **Abrir link** na caixa de
   diálogo, que abre a página de vinculação do próprio Obico já com o
   código preenchido.
   - Isso reproduz exatamente o que o script de instalação do próprio Obico
     faz numa instalação real de Klipper (`./install.sh` →
     `python3 -m moonraker_obico.link`): ele consulta o servidor do Obico a
     cada poucos segundos informando uma impressora não vinculada e um
     código de uso único, até você digitar esse código no lado do Obico, ou
     o app do Obico encontrá-la automaticamente por estar na mesma conta.
     Não envolve nenhum broadcast na rede local, então funciona igual pelo
     WiFi, por uma VPN, ou pelos dados móveis do celular.
   - Já tem um código no sentido contrário — um que o Obico te deu, por
     exemplo de uma configuração manual no estilo OctoPrint? A mesma caixa
     de diálogo tem um campo para digitar esse código em vez disso.

<p align="center"><img src="../images/obico-link-code.png" alt="Caixa de diálogo Vincular impressora mostrando o código gerado, o botão Abrir link e o campo de código manual" width="384"></p>

5. Depois de vinculada, sua impressora aparece no app/site do Obico. Para
   desvincular, abra **Vincular impressora** de novo — a caixa de diálogo
   mostra uma opção **Desvincular** quando já está vinculada. A vinculação
   também se completa sozinha se o app/site do Obico encontrar a impressora
   automaticamente (mesma conta) — não há botão no KlipPocket para isso, a
   caixa de diálogo simplesmente se fecha quando acontece.

## Webcam

O Obico obtém um snapshot da webcam do mesmo jeito que o Fluidd/Mainsail:
pela própria lista de webcams do Moonraker. Ative o servidor de câmera e
adicione no Fluidd ou Mainsail uma vez (a config é compartilhada entre eles)
e o Obico pega automaticamente — guia completo com prints:
[`webcam.md`](webcam.md).

A pré-visualização ao vivo via WebRTC do Obico (que usa um processo
`janus-gateway` e `ffmpeg` embutidos) **não** está disponível aqui — os dois
são binários nativos compilados para desktop Linux/Raspberry Pi no projeto
original, não para Android, e não são incluídos. Essa é uma flag de
configuração documentada e suportada (`disable_video_streaming`), não um
patch no código do companion. O Obico ainda recebe um snapshot novo
periodicamente (a cada ~10s) para monitoramento e detecção de falha,
independente dessa flag.

## Observações e limitações

- Só **uma** impressora pode ficar vinculada por vez, mesmo rodando vários
  perfis ao mesmo tempo — o Obico segue o perfil que chegou a "rodando"
  primeiro.
- A telemetria de erros própria do Obico (Sentry) fica desativada por
  padrão; a conexão real com o servidor escolhido não é afetada. Ela também
  é desativada automaticamente pelo próprio companion para qualquer servidor
  auto-hospedado, independente dessa configuração.
- **Confirmado funcionando de ponta a ponta em hardware real**: o companion
  se conecta ao Moonraker, gera um código de uso único real vindo dos
  servidores reais do Obico Cloud, e a impressora de fato vinculou por esse
  caminho. O caminho de código manual também foi verificado contra os
  servidores reais (um código propositalmente errado retorna "inválido"
  corretamente).

## Solução de problemas

- **Logs**: o `obico.log` fica junto com `klippy.log`/`moonraker.log`/
  `octoeverywhere.log` na pasta `logs` daquele perfil — acesse com um app
  gerenciador de arquivos através da entrada de armazenamento do próprio
  KlipPocket (app Arquivos do Android → "KlipPocket").
- **"Não vinculado" nunca muda depois de digitar um código**: confira se o
  código não expirou (os códigos do Obico duram pouco) e se você escolheu o
  servidor certo (Cloud ou auto-hospedado) — um código de um não funciona no
  outro.
- **Servidor auto-hospedado inacessível**: a mensagem de erro mostrada na
  caixa de vinculação é o erro de rede real (ex: "não foi possível resolver
  o host") — confira a URL primeiro, incluindo `http://` vs `https://`.
