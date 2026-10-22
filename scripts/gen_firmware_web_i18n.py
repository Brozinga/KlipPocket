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
FirmwareStartAddressHint FirmwareDeleteAll FirmwareDeleteOneConfirm FirmwareDeleteAllConfirm""".split()

# Texts that exist only on the page. {0}, {1} are filled in by the page.
WEB_ONLY = {
    "pageTitle": ("KlipPocket · Firmware", "KlipPocket · Firmware", "KlipPocket · Прошивка", "KlipPocket · 固件", "KlipPocket · 韌體"),
    "pinTitle": ("Enter the PIN", "Informe o PIN", "Введите PIN", "输入 PIN", "輸入 PIN"),
    "pinHint": ("The PIN is shown in the app, in the Firmware tab.", "O PIN aparece no app, na aba Firmware.",
                "PIN показан в приложении, на вкладке «Прошивка».", "PIN 显示在应用的“固件”标签页中。", "PIN 顯示在應用程式的「韌體」分頁中。"),
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
