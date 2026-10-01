# Usando o SimplyPrint

**Idiomas: [English](../simplyprint.md) · [Português (BR)](simplyprint.md) · [简体中文](../zh-Hans/simplyprint.md)**

O [SimplyPrint](https://simplyprint.io) é uma plataforma de impressão 3D com
monitoramento remoto, gerenciamento de impressões e detecção de falhas por IA.
O Moonraker embutido no KlipPocket já inclui a conexão com o SimplyPrint, então
não há nada para instalar: basta ligá-la na configuração do Moonraker e vincular
a impressora à sua conta do SimplyPrint.

<p align="center"><img src="../images/simply-print-view.png" alt="Painel do SimplyPrint mostrando a impressora, a webcam ao vivo e as temperaturas" width="640"></p>

## O que você precisa

- Um perfil de impressora **rodando** no KlipPocket, com o Fluidd ou o Mainsail
  aberto no navegador — veja [`getting-started.md`](getting-started.md).
- Uma conta gratuita em [simplyprint.io](https://simplyprint.io).

## 1. Ligue no `moonraker.conf`

1. Abra o `moonraker.conf` no editor do Fluidd/Mainsail
   (Fluidd: **{…} Configuration**, Mainsail: **Machine**).
2. Adicione esta linha bem no final do arquivo, se ela ainda não estiver lá:

   ```ini
   [simplyprint]
   ```

   > Confira antes se o arquivo já não tem uma seção `[simplyprint]`. Uma seção
   > duplicada faz o Moonraker se recusar a iniciar.

3. Clique em **Salvar e reiniciar**.

## 2. Pegue o código de configuração

1. Espere alguns segundos até o Moonraker voltar e clique no ícone de **sino**
   na barra superior do Fluidd/Mainsail.
2. Abra a notificação **SimplyPrint Setup Request** e copie o código de
   configuração que ela mostra.

## 3. Vincule a impressora

1. Acesse [simplyprint.io](https://simplyprint.io) e faça login.
2. Escolha **Add Printer** e cole o código de configuração.
3. Confirme a conexão. A impressora passa a aparecer no seu painel do
   SimplyPrint.

Se a impressora já aparecer em **Pending Printers** no SimplyPrint, você pode
adicioná-la por lá, sem digitar o código.

## Webcam

O SimplyPrint pega a câmera do mesmo jeito que o Fluidd/Mainsail: pela lista de
webcams do próprio Moonraker. Ative o servidor de câmera e adicione-o no Fluidd
ou no Mainsail uma vez (a configuração é compartilhada entre os dois) e o
SimplyPrint passa a usá-la — passo a passo completo com prints:
[`webcam.md`](webcam.md).

## Observações

- Cada perfil de impressora tem o próprio `moonraker.conf`, então repita os
  passos para cada perfil que você quiser no SimplyPrint.
- O SimplyPrint exige Moonraker v0.8.0 ou mais novo; o que vem embutido aqui é
  mais recente.

## Guia oficial

As instruções do próprio SimplyPrint para impressoras Klipper:
<https://simplyprint.io/setup-guide/methods/klipper-powered>
