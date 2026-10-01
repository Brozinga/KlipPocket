# Usando o OctoEverywhere

**Idiomas: [English](../octoeverywhere.md) · [Português (BR)](octoeverywhere.md) · [简体中文](../zh-Hans/octoeverywhere.md)**

O [OctoEverywhere](https://octoeverywhere.com) dá acesso remoto à sua
impressora (webcam, status, controle) pelo site octoeverywhere.com ou pelo
app mobile, sem precisar abrir portas no roteador. O KlipPocket roda o
companion real do OctoEverywhere para Klipper/Moonraker, adaptado para
rodar direto no aparelho em vez do serviço systemd que ele normalmente
instala.

<p align="center"><img src="../images/camera-octoeverywhere-settings.png" alt="Tela de configurações mostrando as seções Câmera e Acesso remoto" width="336"></p>

<p align="center"><img src="../images/octoeverywhere-view.jpg" alt="Quick View do OctoEverywhere mostrando a webcam ao vivo, o status e as temperaturas" width="480"></p>

## Ativando

1. Inicie um perfil de impressora (ele precisa estar **rodando** — o
   OctoEverywhere se conecta ao perfil que estiver ativo no momento).
2. Vá em **Configurações → Acesso remoto** e ative **Enable OctoEverywhere**.
3. Espere alguns segundos para ele gerar um ID de impressora, depois toque
   em **Vincular impressora**. Um QR code vai aparecer — escaneie com a
   câmera do celular (ou abra o link no próprio aparelho) para concluir a
   vinculação com sua conta OctoEverywhere, igual em qualquer outra
   instalação do OctoEverywhere.
   - Se aparecer "Not ready yet", é só o companion ainda não ter terminado
     de iniciar — espere um pouco e toque em **Vincular impressora**
     de novo.
4. Depois de vinculada, sua impressora aparece no octoeverywhere.com e no
   app mobile do OctoEverywhere.
   - O companion só verifica se está vinculado uma vez, no momento em que
     conecta — não recebe uma atualização ao vivo para uma sessão que já
     está aberta. Se o "Go to Klipper" no octoeverywhere.com continuar
     dizendo que a impressora não está conectada logo depois de você
     terminar a vinculação, desative e reative o **Enable OctoEverywhere**
     uma vez para forçar uma reconexão.

## Webcam

O OctoEverywhere encontra a webcam do mesmo jeito que o Fluidd/Mainsail:
pela própria lista de webcams do Moonraker, não por algo que o KlipPocket
registre automaticamente. Ative o servidor de câmera e adicione no Fluidd
ou Mainsail uma vez (a config é compartilhada entre eles) e o
OctoEverywhere pega automaticamente — guia completo com prints:
[`webcam.md`](webcam.md).

## Observações e limitações

- Só **uma** impressora pode ficar vinculada por vez, mesmo rodando vários
  perfis ao mesmo tempo — o OctoEverywhere segue o perfil que chegou a
  "rodando" primeiro. Desative e reative depois de trocar de perfil para
  mover a vinculação.
- A telemetria de erros própria do OctoEverywhere (Sentry) fica desativada
  por padrão; a conexão real de acesso remoto com o octoeverywhere.com não
  é afetada.
- Isso ainda não foi testado em hardware de ponta a ponta (ou seja, com uma
  conta de verdade vinculada e usada a partir do octoeverywhere.com) — o
  companion roda e se conecta ao Moonraker corretamente, mas avise se
  encontrar algum problema ao vincular ou usar remotamente.

## Solução de problemas

- **Logs**: o `octoeverywhere.log` fica junto com `klippy.log`/
  `moonraker.log` na pasta `logs` daquele perfil — acesse com um app
  gerenciador de arquivos através da entrada de armazenamento do próprio
  KlipPocket (app Arquivos do Android → "KlipPocket"), o mesmo lugar onde
  você já olharia os logs do Klipper/Moonraker.
- **Vincular do zero novamente**: desative o OctoEverywhere, apague a pasta
  `octoeverywhere` daquele perfil no armazenamento privado do app (não é a
  pasta pública acima — precisa de um gerenciador de arquivos com root, ou
  `adb shell run-as`), depois ative de novo para gerar um novo ID de
  impressora e vincular outra vez. Também dá para desvincular/vincular a
  impressora direto pelas configurações da sua conta no octoeverywhere.com.
