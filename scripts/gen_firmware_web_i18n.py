#!/usr/bin/env python3
"""Builds app/src/main/assets/firmware_web/i18n.js (the texts of the firmware web page, 5 languages).

Most texts are the app's own Firmware tab strings (res/values*/strings.xml), so the page and the app always say the same
thing in the same words; the few web-only texts are in WEB_ONLY below. Run it again after changing those strings:

    python3 scripts/gen_firmware_web_i18n.py          # writes the file
    python3 scripts/gen_firmware_web_i18n.py --check  # exits 1 when the file is out of date (used by the tests)
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "firmware_web", "i18n.js")

# page language code -> resource folder
LANGS = {"en": "values", "pt-BR": "values-pt-rBR", "ru": "values-ru", "zh-CN": "values-zh", "zh-TW": "values-zh-rTW"}
LANGUAGE_NAMES = {"en": "English", "pt-BR": "Português (BR)", "ru": "Русский", "zh-CN": "简体中文", "zh-TW": "繁體中文"}

FROM_APP = """ListUSBTitle ListUSBNoDevices FirmwareTitle FirmwareDownload FirmwareInstall FirmwareDownloadHint FirmwareDownloadUrl
FirmwareDownloadStart FirmwareDownloading FirmwareDownloaded FirmwareDownloadFailed FirmwareFiles FirmwareNoFiles FirmwareDelete
FirmwareStepOf FirmwareStepFile FirmwareStepDevice FirmwareStepMode FirmwareNext FirmwareBack FirmwareCancel FirmwareConfirm
FirmwareRefresh FirmwareSelectDevice FirmwareModeUsb FirmwareModeUart FirmwareModeDfu FirmwareModeRp2040 FirmwareModeUsbDesc FirmwareModeUartDesc
FirmwareModeDfuDesc FirmwareModeRp2040Desc FirmwareWarnTitle FirmwareWarnText FirmwareSummary FirmwareFlashing FirmwareStagePrepare
FirmwareStageEnterBootsel FirmwareStageWaitBootsel FirmwareStagePermission FirmwareStageHandshake FirmwareStageErase FirmwareStageWrite
FirmwareStageVerify FirmwareStageReboot FirmwareStageWaitDevice FirmwareSuccess FirmwareFailed FirmwareFailWrongDevice
FirmwareFailFileInvalid FirmwareFailPermission FirmwareFailUsb FirmwareFailVerify FirmwareFailCancelled FirmwareFailBootsel
FirmwareFailNotSerial FirmwareFailNoAnswer FirmwareFailIncompat FirmwareFailAddress FirmwareFailNeedsRp2040 FirmwareDetail
FirmwareDone FirmwareKeepOpen FirmwareNoReturn FirmwareExperimentalNote FirmwareExperimentalWarn FirmwareStartAddress
FirmwareStartAddressHint FirmwareDeleteAll FirmwareDeleteOneConfirm FirmwareDeleteAllConfirm
EngineFrontend FirmwareEngine WebFrontend USB USBDeviceNaming Camera EnableCamera CameraSource CameraRotation CameraResolution CameraZoom CameraZoomUnsupported RemoteAccess EnableOctoEverywhere LinkOctoEverywhere Obico EnableObico ObicoServer ObicoServerCloud ObicoServerSelfHosted ObicoServerSelfHostedHint ObicoServerHint ObicoLink ObicoLinked ObicoNotLinked ObicoLinkHint ObicoGeneratedCodeLabel ObicoOpenLink ObicoWaitingForCode ObicoManualCodeHint ObicoLinkCodeHint ObicoLinkAction ObicoUnlink ObicoInvalidCode ObicoNetworkError ObicoAlreadyLinkedHint AppSettings AppLanguage CameraPreviewFocusHint CameraPreviewFocusSaved CameraPreviewFocusReset CameraPreviewFocusUnsupported CameraPreviewConnecting LinkOctoEverywhereNotReady
NewInstance InstanceName InstanceDefaultNameHint InstanceConfig InstanceConfigHint InstanceConfigLoading Autostart InstanceCreate
LogsLoading LogsRefresh LogsCopy LogsCopied LogsClear LogsClearTitle LogsClearMessage LogsCleared LogsClearFailed""".split()

# Texts that exist only on the page. {0}, {1} are filled in by the page.
WEB_ONLY = {
    "pageTitle": ("KlipPocket Dashboard",) * 5,
    "pinTitle": ("Enter the PIN", "Informe o PIN", "Введите PIN", "输入 PIN", "輸入 PIN"),
    "pinHint": ("The PIN is shown in the app, on the home screen.", "O PIN aparece no app, na tela inicial.",
                "PIN показан в приложении, на главном экране.", "PIN 显示在应用的主屏幕上。", "PIN 顯示在應用程式的主畫面上。"),
    "pinEnter": ("Enter", "Entrar", "Войти", "进入", "進入"),
    "pinWrong": ("Wrong PIN.", "PIN incorreto.", "Неверный PIN.", "PIN 错误。", "PIN 錯誤。"),
    "pinLocked": ("Too many attempts. Try again in {0} s.", "Muitas tentativas. Tente de novo em {0} s.",
                  "Слишком много попыток. Повторите через {0} с.", "尝试次数过多，请在 {0} 秒后重试。", "嘗試次數過多，請在 {0} 秒後重試。"),
    "logout": ("Log out", "Sair", "Выйти", "退出", "登出"),
    "upload": ("Upload file", "Enviar arquivo", "Загрузить файл", "上传文件", "上傳檔案"),
    "uploadDrop": ("or drop a .bin, .hex or .uf2 file here", "ou solte aqui um arquivo .bin, .hex ou .uf2",
                   "или перетащите сюда файл .bin, .hex или .uf2", "或将 .bin、.hex、.uf2 文件拖到这里", "或將 .bin、.hex、.uf2 檔案拖曳到這裡"),
    "uploading": ("Uploading… {0}%", "Enviando… {0}%", "Загрузка… {0}%", "正在上传… {0}%", "正在上傳… {0}%"),
    "uploadSaved": ("Uploaded: {0}", "Enviado: {0}", "Загружено: {0}", "已上传：{0}", "已上傳：{0}"),
    "uploadFailed": ("Upload failed: {0}", "Falha no envio: {0}", "Ошибка загрузки: {0}", "上传失败：{0}", "上傳失敗：{0}"),
    "uploadTooBig": ("The file is bigger than {0} MB.", "O arquivo é maior que {0} MB.", "Файл больше {0} МБ.", "文件大于 {0} MB。", "檔案大於 {0} MB。"),
    "uploadType": ("Only .bin, .hex and .uf2 files are accepted.", "Só arquivos .bin, .hex e .uf2 são aceitos.",
                   "Принимаются только файлы .bin, .hex и .uf2.", "只接受 .bin、.hex 和 .uf2 文件。", "只接受 .bin、.hex 和 .uf2 檔案。"),
    "pasteLink": ("Paste link", "Colar link", "Вставить ссылку", "粘贴链接", "貼上連結"),
    "linkPlaceholder": ("https://…/firmware.bin", "https://…/firmware.bin", "https://…/firmware.bin", "https://…/firmware.bin", "https://…/firmware.bin"),
    "flashFirmware": ("Flash firmware", "Gravar firmware", "Прошить", "写入固件", "寫入韌體"),
    "experimentalTitle": ("Experimental function", "Função experimental", "Экспериментальная функция", "实验性功能", "實驗性功能"),
    "experimentalText": ("Flashing from this page is experimental right now and may cause problems, including leaving the board unusable. Do you want to continue?",
                         "A gravação por esta página é experimental no momento e pode causar problemas, inclusive deixar a placa inutilizável. Deseja continuar?",
                         "Прошивка с этой страницы сейчас экспериментальная и может вызвать проблемы, вплоть до выхода платы из строя. Продолжить?",
                         "目前通过此页面写入固件属于实验性功能，可能造成问题，甚至使主板无法使用。是否继续？",
                         "目前透過此頁面寫入韌體屬於實驗性功能，可能造成問題，甚至使主機板無法使用。是否繼續？"),
    "continue": ("Continue", "Continuar", "Продолжить", "继续", "繼續"),
    "usbPermission": ("Android may ask for USB permission on the phone: accept it there.",
                      "O Android pode pedir permissão USB no celular: aceite lá.",
                      "Android может запросить разрешение USB на телефоне: подтвердите его там.",
                      "Android 可能会在手机上请求 USB 权限，请在手机上允许。", "Android 可能會在手機上要求 USB 權限，請在手機上允許。"),
    "selectFileFirst": ("Choose a file.", "Escolha um arquivo.", "Выберите файл.", "请选择文件。", "請選擇檔案。"),
    "selectDevice": ("Choose the device from the list.", "Escolha o dispositivo da lista.", "Выберите устройство из списка.", "请从列表中选择设备。", "請從列表中選擇裝置。"),
    "language": ("Language", "Idioma", "Язык", "语言", "語言"),
    "theme": ("Theme", "Tema", "Тема", "主题", "主題"),
    "themeDark": ("Dark", "Escuro", "Тёмная", "深色", "深色"),
    "themeLight": ("Light", "Claro", "Светлая", "浅色", "淺色"),
    "log": ("Log", "Registro", "Журнал", "日志", "日誌"),
    "connectionLost": ("Connection to the app lost. Trying again…", "Conexão com o app perdida. Tentando de novo…",
                       "Связь с приложением потеряна. Повторяем…", "与应用的连接已断开，正在重试…", "與應用程式的連線已中斷，正在重試…"),
    "resumed": ("A firmware write is in progress.", "Uma gravação de firmware está em andamento.", "Идёт прошивка.", "正在写入固件。", "正在寫入韌體。"),
    "busy": ("Another operation is already running.", "Outra operação já está em andamento.", "Другая операция уже выполняется.", "已有其他操作正在进行。", "已有其他操作正在進行。"),
    "serverError": ("The app could not do that.", "O app não conseguiu fazer isso.", "Приложение не смогло это выполнить.", "应用无法完成此操作。", "應用程式無法完成此操作。"),
    "badLink": ("This link is not valid: use an https link to a .bin, .hex, .uf2 or .zip file.",
                "Este link não é válido: use um link https para um arquivo .bin, .hex, .uf2 ou .zip.",
                "Недопустимая ссылка: нужна ссылка https на файл .bin, .hex, .uf2 или .zip.",
                "链接无效：请使用指向 .bin、.hex、.uf2 或 .zip 文件的 https 链接。", "連結無效：請使用指向 .bin、.hex、.uf2 或 .zip 檔案的 https 連結。"),
    "fileKind": ("Type", "Tipo", "Тип", "类型", "類型"),
    "close": ("Close", "Fechar", "Закрыть", "关闭", "關閉"),
    "tabDashboard": ("Dashboard", "Painel", "Панель", "仪表盘", "儀表板"),
    "hostTitle": ("Host device", "Aparelho (host)", "Устройство (хост)", "主机设备", "主機裝置"),
    "hostModel": ("Model", "Modelo", "Модель", "型号", "型號"),
    "hostSoc": ("Chip", "Chip", "Чип", "芯片", "晶片"),
    "hostAndroid": ("Android", "Android", "Android", "Android", "Android"),
    "hostAbi": ("Architecture", "Arquitetura", "Архитектура", "架构", "架構"),
    "hostCores": ("CPU cores", "Núcleos de CPU", "Ядра ЦП", "CPU 核心", "CPU 核心"),
    "hostKernel": ("Kernel", "Kernel", "Ядро", "内核", "核心"),
    "hostUptime": ("Uptime", "Ligado há", "Время работы", "运行时间", "運作時間"),
    "hostAddress": ("Network address", "Endereço de rede", "Сетевой адрес", "网络地址", "網路位址"),
    "hostApp": ("KlipPocket version", "Versão do KlipPocket", "Версия KlipPocket", "KlipPocket 版本", "KlipPocket 版本"),
    "usageTitle": ("Live usage", "Uso em tempo real", "Загрузка в реальном времени", "实时使用情况", "即時使用情況"),
    "cpuTitle": ("Processor", "Processador", "Процессор", "处理器", "處理器"),
    "cpuDevice": ("Device", "Aparelho", "Устройство", "设备", "裝置"),
    "cpuApp": ("KlipPocket", "KlipPocket", "KlipPocket", "KlipPocket", "KlipPocket"),
    "cpuHidden": ("This Android version does not let apps read the whole device's processor use: only KlipPocket is shown.", "Esta versão do Android não deixa os apps lerem o uso de processador do aparelho todo: só o do KlipPocket é mostrado.", "Эта версия Android не позволяет приложениям читать загрузку процессора всего устройства: показан только KlipPocket.", "此 Android 版本不允许应用读取整机处理器使用率，仅显示 KlipPocket 的部分。", "此 Android 版本不允許應用程式讀取整機處理器使用率，僅顯示 KlipPocket 的部分。"),
    "memTitle": ("Memory (RAM)", "Memória (RAM)", "Память (ОЗУ)", "内存 (RAM)", "記憶體 (RAM)"),
    "memUsed": ("In use", "Em uso", "Занято", "已用", "已用"),
    "memAvailable": ("Available", "Disponível", "Доступно", "可用", "可用"),
    "memTotal": ("Total", "Total", "Всего", "总计", "總計"),
    "swapTitle": ("Swap", "Swap", "Подкачка", "交换空间", "交換空間"),
    "storageTitle": ("Storage", "Armazenamento", "Хранилище", "存储", "儲存空間"),
    "storageInternal": ("Internal storage", "Armazenamento interno", "Внутренняя память", "内部存储", "內部儲存空間"),
    "storageCard": ("SD card", "Cartão SD", "SD-карта", "SD 卡", "SD 卡"),
    "storageFree": ("{0} free of {1}", "{0} livres de {1}", "свободно {0} из {1}", "可用 {0}，共 {1}", "可用 {0}，共 {1}"),
    "tempTitle": ("Temperature and battery", "Temperatura e bateria", "Температура и батарея", "温度与电池", "溫度與電池"),
    "tempCpu": ("Processor", "Processador", "Процессор", "处理器", "處理器"),
    "tempBattery": ("Battery", "Bateria", "Батарея", "电池", "電池"),
    "batteryLevel": ("Battery level", "Nível da bateria", "Заряд батареи", "电量", "電量"),
    "charging": ("charging", "carregando", "заряжается", "充电中", "充電中"),
    "seconds": ("Last {0} s", "Últimos {0} s", "Последние {0} с", "最近 {0} 秒", "最近 {0} 秒"),
    "appProcesses": ("Processes: {0}", "Processos: {0}", "процессов: {0}", "{0} 个进程", "{0} 個程序"),
    "printersTitle": ("Printers and MCUs", "Impressoras e MCUs", "Принтеры и MCU", "打印机与 MCU", "印表機與 MCU"),
    "noPrinters": ("No printer profile yet.", "Nenhum perfil de impressora ainda.", "Профилей принтеров пока нет.", "还没有打印机配置。", "還沒有印表機設定檔。"),
    "stateIDLE": ("Stopped", "Parada", "Остановлен", "已停止", "已停止"),
    "stateSTARTING": ("Starting", "Iniciando", "Запускается", "正在启动", "正在啟動"),
    "stateRUNNING": ("Running", "Rodando", "Работает", "运行中", "執行中"),
    "stateSTOPPING": ("Stopping", "Parando", "Останавливается", "正在停止", "正在停止"),
    "mcuNone": ("MCU data appears when the printer is running and Klipper is connected.", "Os dados dos MCUs aparecem quando a impressora está rodando e o Klipper conectado.", "Данные MCU появятся, когда принтер запущен и Klipper подключён.", "打印机运行且 Klipper 已连接后，会显示 MCU 数据。", "印表機執行且 Klipper 已連線後，會顯示 MCU 資料。"),
    "mcuChip": ("Chip", "Chip", "Чип", "芯片", "晶片"),
    "mcuVersion": ("Firmware", "Firmware", "Прошивка", "固件", "韌體"),
    "mcuClock": ("Clock", "Clock", "Частота", "时钟", "時脈"),
    "logsTitle": ("Logs", "Logs", "Журналы", "日志", "日誌"),
    "logsLive": ("Live", "Ao vivo", "Онлайн", "实时", "即時"),
    "logsEmpty": ("(empty)", "(vazio)", "(пусто)", "（空）", "（空）"),
    "logsFailed": ("Could not load this log.", "Não foi possível carregar este log.", "Не удалось загрузить журнал.", "无法加载此日志。", "無法載入此日誌。"),
    "cardCpuTemp": ("Processor temperature", "Temperatura do processador", "Температура процессора", "处理器温度", "處理器溫度"),
    "cardGpuTemp": ("GPU temperature", "Temperatura da GPU", "Температура GPU", "GPU 温度", "GPU 溫度"),
    "cardSkinTemp": ("Surface temperature", "Temperatura da superfície", "Температура корпуса", "机身表面温度", "機身表面溫度"),
    "cardBatTemp": ("Battery temperature", "Temperatura da bateria", "Температура батареи", "电池温度", "電池溫度"),
    "cardBattery": ("Battery", "Bateria", "Батарея", "电池", "電池"),
    "cardVoltage": ("Battery voltage", "Tensão da bateria", "Напряжение батареи", "电池电压", "電池電壓"),
    "cardFreq": ("Processor frequency", "Frequência do processador", "Частота процессора", "处理器频率", "處理器頻率"),
    "cardLoad": ("System load", "Carga do sistema", "Нагрузка системы", "系统负载", "系統負載"),
    "cardWifi": ("Wi-Fi signal", "Sinal do Wi-Fi", "Сигнал Wi-Fi", "Wi-Fi 信号", "Wi-Fi 訊號"),
    "cardDown": ("Network download", "Download da rede", "Скорость загрузки", "网络下载", "網路下載"),
    "cardUp": ("Network upload", "Upload da rede", "Скорость отдачи", "网络上传", "網路上傳"),
    "tabSettings": ("Settings", "Configurações", "Настройки", "设置", "設定"),
    "tabCamera": ("Camera", "Câmera", "Камера", "摄像头", "相機"),
    "ctlStartAll": ("Start all", "Iniciar todos", "Запустить все", "全部启动", "全部啟動"),
    "ctlStopAll": ("Stop all", "Parar todos", "Остановить все", "全部停止", "全部停止"),
    "ctlStart": ("Start", "Iniciar", "Запустить", "启动", "啟動"),
    "ctlStop": ("Stop", "Parar", "Остановить", "停止", "停止"),
    "ctlNoSlots": ("No free slot to start another printer.", "Não há vaga livre para iniciar outra impressora.", "Нет свободного слота для запуска ещё одного принтера.", "没有空闲位置来启动另一台打印机。", "沒有空閒位置來啟動另一台印表機。"),
    "saved": ("Saved.", "Salvo.", "Сохранено.", "已保存。", "已儲存。"),
    "cameraPermission": ("Android needs the camera permission: accept it on the phone.", "O Android precisa da permissão da câmera: aceite no celular.", "Android требуется разрешение на камеру: подтвердите его на телефоне.", "Android 需要摄像头权限，请在手机上允许。", "Android 需要相機權限，請在手機上允許。"),
    "cameraOff": ("The camera is turned off.", "A câmera está desligada.", "Камера выключена.", "摄像头已关闭。", "相機已關閉。"),
    "cameraDown": ("No picture yet: the camera starts together with a printer. Start one and wait a few seconds.", "Ainda sem imagem: a câmera inicia junto com uma impressora. Inicie uma e aguarde alguns segundos.", "Изображения пока нет: камера запускается вместе с принтером. Запустите принтер и подождите несколько секунд.", "暂无画面：摄像头会随打印机一起启动。请启动一台打印机并等待几秒。", "暫無畫面：相機會隨印表機一起啟動。請啟動一台印表機並等待幾秒。"),
    "octoOpen": ("Open the OctoEverywhere link", "Abrir o link do OctoEverywhere", "Открыть ссылку OctoEverywhere", "打开 OctoEverywhere 链接", "開啟 OctoEverywhere 連結"),
    "save": ("Save", "Salvar", "Сохранить", "保存", "儲存"),
    "settingsHint": ("Same settings as the app's Settings screen: a change here is a change in the app.", "As mesmas configurações da tela Configurações do app: o que muda aqui muda no app.", "Те же настройки, что и на экране «Настройки» приложения: изменение здесь — это изменение в приложении.", "与应用“设置”页面相同：在这里的更改就是在应用中的更改。", "與應用程式「設定」頁面相同：在這裡的變更就是在應用程式中的變更。"),
    "na": ("N/A", "N/D", "Н/Д", "不可用", "不可用"),
    "devicePath": ("Path", "Caminho", "Путь", "路径", "路徑"),
}


def unescape(text):
    return text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@")


def to_placeholders(text):
    return re.sub(r"%(\d+)\$[sd]", lambda m: "{%d}" % (int(m.group(1)) - 1), text)


def load(folder):
    root = ET.parse(os.path.join(RES, folder, "strings.xml")).getroot()
    return {e.get("name"): to_placeholders(unescape("".join(e.itertext()))) for e in root.iter("string")}


def build():
    out = {}
    for i, (code, folder) in enumerate(LANGS.items()):
        app = load(folder)
        base = load("values")
        table = {k: app.get(k, base[k]) for k in FROM_APP}
        table.update({k: v[i] for k, v in WEB_ONLY.items()})
        out[code] = table
    names = json.dumps(LANGUAGE_NAMES, ensure_ascii=False)
    body = json.dumps(out, ensure_ascii=False, indent=1, sort_keys=True)
    return ("// Generated by scripts/gen_firmware_web_i18n.py from the app's strings. Do not edit by hand.\n"
            "window.I18N = %s;\nwindow.LANGUAGE_NAMES = %s;\n" % (body, names))


if __name__ == "__main__":
    text = build()
    if "--check" in sys.argv:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        sys.exit(0 if current == text else 1)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(text)
    print("wrote", os.path.relpath(OUT, ROOT))
