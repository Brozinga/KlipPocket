# Print recovery: resume an interrupted print after a power loss, a lost
# printer connection or a firmware shutdown.
#
# While a print runs the position in the G-code file, the toolhead position
# and the printing state (temperatures, fan, speed/flow factors, offsets, bed
# mesh, ...) are saved to a small JSON file every few seconds. After the
# printer is back, Fluidd/Mainsail are asked (action:prompt) whether the print
# should be resumed. Resuming heats the bed, homes X/Y only, parks the nozzle
# at the X stop, heats the nozzle there (so it cannot ooze onto the part),
# optionally purges, then goes back to the saved position and continues the
# file from the saved byte offset.
#
# Dormant unless a [print_recovery] section is present in printer.cfg.
#
# This file may be distributed under the terms of the GNU GPLv3 license.
import collections, io, json, logging, os, time
try:
    from urllib.request import urlopen
except ImportError:
    from urllib2 import urlopen

MIN_SAMPLE_PERIOD = 0.2
MAX_SAMPLE_PERIOD = 0.5
HISTORY_SECONDS = 20.
# Extra time a queued move is assumed to still need after its print_time
EXECUTED_MARGIN = 0.3
OBJECT_SCAN_LIMIT = 4 * 1024 * 1024

# Texts shown in the Fluidd/Mainsail prompt and console, by UI language.
# Missing languages fall back to English.
TEXTS = {
    'en': {
        'discard_lift': "lifting the nozzle %.0f mm",
        'no_room': "not enough Z room: the nozzle was not moved",
        'title': "Print interrupted",
        'stopped': "%(name)s stopped at %(pct)d%% (%(where)s), saved %(ago)s.",
        'ask': "Resume it? The bed heats first, then the nozzle heats "
               "before the print continues.",
        'ask_park': "Resume it? The bed heats first, then the nozzle heats "
                    "parked at the X stop so it cannot drip on the part.",
        'warn': "Only resume if the part is still stuck to the bed and the "
                "axes were not moved by hand.",
        'resume': "Resume print", 'discard': "Discard",
        'layer': "layer %d/%d", 'min': "%d min ago", 'hour': "%.1f h ago",
        'none': "nothing to resume",
        'discarded': "interrupted print discarded",
        'bed': "heating the bed to %.0f C",
        'home': "homing X/Y",
        'nozzle_park': "heating the nozzle to %.0f C at the X stop",
        'nozzle_here': "heating the nozzle to %.0f C in place",
        'purge': "purging %.1f mm",
        'back': "returning to the print",
        'resumed': "resumed %(name)s from byte %(pos)d",
        'mesh_fail': "WARNING the bed mesh could not be restored",
    },
    'pt': {
        'discard_lift': "subindo o bico %.0f mm",
        'no_room': "sem espaço em Z: o bico não foi movido",
        'title': "Impressão interrompida",
        'stopped': "%(name)s parou em %(pct)d%% (%(where)s), salvo %(ago)s.",
        'ask': "Deseja retomar? A mesa aquece primeiro e depois o bico "
               "aquece antes de a impressão continuar.",
        'ask_park': "Deseja retomar? A mesa aquece primeiro e depois o bico "
                    "aquece parado no batente do eixo X, para não escorrer "
                    "sobre a peça.",
        'warn': "Só retome se a peça ainda estiver colada na mesa e os eixos "
                "não tiverem sido movidos à mão.",
        'resume': "Retomar impressão", 'discard': "Descartar",
        'layer': "camada %d/%d", 'min': "há %d min", 'hour': "há %.1f h",
        'none': "nada para retomar",
        'discarded': "impressão interrompida descartada",
        'bed': "aquecendo a mesa a %.0f C",
        'home': "fazendo home de X/Y",
        'nozzle_park': "aquecendo o bico a %.0f C no batente do eixo X",
        'nozzle_here': "aquecendo o bico a %.0f C no lugar",
        'purge': "purgando %.1f mm",
        'back': "voltando para a impressão",
        'resumed': "%(name)s retomado a partir do byte %(pos)d",
        'mesh_fail': "AVISO: não foi possível restaurar a malha da mesa",
    },
    'ru': {
        'discard_lift': "подъём сопла на %.0f мм",
        'no_room': "недостаточно хода по Z: сопло не двигалось",
        'title': "Печать прервана",
        'stopped': "%(name)s остановлена на %(pct)d%% (%(where)s), "
                   "сохранено %(ago)s.",
        'ask': "Продолжить? Сначала нагревается стол, затем сопло, "
               "после чего печать продолжится.",
        'ask_park': "Продолжить? Сначала нагревается стол, затем сопло "
                    "нагревается в крайней точке оси X, чтобы пластик не "
                    "капал на модель.",
        'warn': "Продолжайте, только если модель всё ещё приклеена к столу "
                "и оси не двигали руками.",
        'resume': "Продолжить печать", 'discard': "Отменить",
        'layer': "слой %d/%d", 'min': "%d мин назад", 'hour': "%.1f ч назад",
        'none': "нечего продолжать",
        'discarded': "прерванная печать отменена",
        'bed': "нагрев стола до %.0f C",
        'home': "парковка X/Y",
        'nozzle_park': "нагрев сопла до %.0f C в крайней точке X",
        'nozzle_here': "нагрев сопла до %.0f C на месте",
        'purge': "прочистка %.1f мм",
        'back': "возврат к печати",
        'resumed': "%(name)s продолжен с байта %(pos)d",
        'mesh_fail': "ВНИМАНИЕ: не удалось восстановить карту стола",
    },
    'zh': {
        'discard_lift': "抬高喷嘴 %.0f mm",
        'no_room': "Z 行程不足：喷嘴未移动",
        'title': "打印已中断",
        'stopped': "%(name)s 在 %(pct)d%% 处中断（%(where)s），%(ago)s保存。",
        'ask': "是否继续？先加热热床，再加热喷嘴，然后继续打印。",
        'ask_park': "是否继续？先加热热床，然后喷嘴在 X 限位处加热，"
                    "避免耗材滴在模型上。",
        'warn': "仅当模型仍粘在热床上且未手动移动过轴时才继续。",
        'resume': "继续打印", 'discard': "放弃",
        'layer': "第 %d/%d 层", 'min': "%d 分钟前", 'hour': "%.1f 小时前",
        'none': "没有可恢复的打印",
        'discarded': "已放弃中断的打印",
        'bed': "热床加热到 %.0f C",
        'home': "X/Y 回零",
        'nozzle_park': "在 X 限位处将喷嘴加热到 %.0f C",
        'nozzle_here': "原地将喷嘴加热到 %.0f C",
        'purge': "清洗 %.1f mm",
        'back': "返回打印位置",
        'resumed': "已从字节 %(pos)d 继续 %(name)s",
        'mesh_fail': "警告：无法恢复热床网格",
    },
    'zh-TW': {
        'discard_lift': "抬高噴嘴 %.0f mm",
        'no_room': "Z 行程不足：噴嘴未移動",
        'title': "列印已中斷",
        'stopped': "%(name)s 在 %(pct)d%% 處中斷（%(where)s），%(ago)s儲存。",
        'ask': "是否繼續？先加熱熱床，再加熱噴嘴，然後繼續列印。",
        'ask_park': "是否繼續？先加熱熱床，然後噴嘴在 X 限位處加熱，"
                    "避免線材滴在模型上。",
        'warn': "僅當模型仍黏在熱床上且未手動移動過軸時才繼續。",
        'resume': "繼續列印", 'discard': "放棄",
        'layer': "第 %d/%d 層", 'min': "%d 分鐘前", 'hour': "%.1f 小時前",
        'none': "沒有可恢復的列印",
        'discarded': "已放棄中斷的列印",
        'bed': "熱床加熱到 %.0f C",
        'home': "X/Y 歸零",
        'nozzle_park': "在 X 限位處將噴嘴加熱到 %.0f C",
        'nozzle_here': "原地將噴嘴加熱到 %.0f C",
        'purge': "清洗 %.1f mm",
        'back': "返回列印位置",
        'resumed': "已從位元組 %(pos)d 繼續 %(name)s",
        'mesh_fail': "警告：無法恢復熱床網格",
    },
}
LANG_CACHE_SECONDS = 30.
WEB_PORTS = ((4408, 'fluidd', 'uiSettings.general.locale'),
             (4409, 'mainsail', 'general.locale'))

class PrintRecovery:
    def __init__(self, config):
        self.printer = config.get_printer()
        self.reactor = self.printer.get_reactor()
        self.gcode = self.printer.lookup_object('gcode')
        self.interval = config.getfloat('snapshot_interval', 2.,
                                        minval=0.5, maxval=300.)
        self.park_enable_x = config.getboolean('park_enable_x', True)
        self.park_enable_y = config.getboolean('park_enable_y', True)
        self.discard_lift_z = config.getfloat('discard_lift_z', 50.,
                                              minval=0.)
        self.discard_home_x = config.getboolean('discard_home_x', True)
        self.discard_home_y = config.getboolean('discard_home_y', True)
        self.language = config.get('language', 'auto')
        self.park_x = config.getfloat('park_x', None)
        self.park_y = config.getfloat('park_y', None)
        self.park_speed = config.getfloat('park_speed', 100., above=0.)
        self.lift_z = config.getfloat('lift_z', 10., minval=0.)
        self.purge = config.getboolean('purge', True)
        self.purge_length = config.getfloat('purge_length', 20., minval=0.)
        self.purge_speed = config.getfloat('purge_speed', 5., above=0.)
        self.purge_retract = config.getfloat('purge_retract', 2., minval=0.)
        # Macro variables to keep: '*' = every macro not starting with '_',
        # 'none' (or empty) = none, or a comma separated list of macro names
        raw = config.get('macro_variables', '*').strip()
        if raw.lower() == 'none':
            raw = ''
        self.macro_all = raw == '*'
        self.macro_names = set(n.strip().upper() for n in raw.split(',')
                               if n.strip() and raw != '*')
        self.min_extruded = config.getfloat('min_extruded', 5., minval=0.)
        self.prompt = config.getboolean('prompt', True)
        self.prompt_repeat = config.getfloat('prompt_repeat', 60., minval=0.)
        self.period = min(MAX_SAMPLE_PERIOD,
                          max(MIN_SAMPLE_PERIOD, self.interval / 2.))
        state_file = config.get('state_file', None)
        if state_file is None:
            cfg_dir = os.path.dirname(
                self.printer.get_start_args()['config_file'])
            state_file = os.path.join(cfg_dir, '.print_recovery.json')
        self.state_file = os.path.expanduser(state_file)
        self.history = collections.deque()
        self.mesh_cache = (None, None)
        self.lang_cache = (0., 'en')
        self.pending = None
        self.recording = False
        self.resuming = False
        self.shutdown = False
        self.last_write = 0.
        self.write_failed = False
        self.sample_timer = self.prompt_timer = None
        self.printer.register_event_handler("klippy:ready", self._handle_ready)
        self.printer.register_event_handler("klippy:shutdown",
                                            self._handle_shutdown)
        self.gcode.register_command(
            'PRINT_RECOVERY_RESUME', self.cmd_PRINT_RECOVERY_RESUME,
            desc=self.cmd_PRINT_RECOVERY_RESUME_help)
        self.gcode.register_command(
            'PRINT_RECOVERY_DISCARD', self.cmd_PRINT_RECOVERY_DISCARD,
            desc=self.cmd_PRINT_RECOVERY_DISCARD_help)
        self.gcode.register_command(
            'PRINT_RECOVERY_STATUS', self.cmd_PRINT_RECOVERY_STATUS,
            desc=self.cmd_PRINT_RECOVERY_STATUS_help)

    # ---- lifecycle -------------------------------------------------------
    def _handle_ready(self):
        self.shutdown = False
        if 'RESPOND' not in self.gcode.gcode_handlers:
            # Fluidd/Mainsail send RESPOND when a prompt is closed. The stock
            # [respond] is not loaded here since it also takes over M118,
            # which many configs define as a macro.
            self.gcode.register_command('RESPOND', self.cmd_RESPOND)
        self.pending = self._read_state()
        if self.pending is not None:
            logging.info("print_recovery: interrupted print found: %s at "
                         "byte %d", self.pending.get('filename'),
                         self.pending.get('file_position', 0))
            if self.prompt:
                self.prompt_timer = self.reactor.register_timer(
                    self._prompt_handler, self.reactor.monotonic() + 5.)
        self.sample_timer = self.reactor.register_timer(
            self._sample_handler, self.reactor.monotonic() + 1.)

    def _handle_shutdown(self):
        # Keep whatever was saved last: that is what a resume will use
        self.shutdown = True

    def get_status(self, eventtime=None):
        p = self.pending
        return {
            'pending': p is not None,
            'filename': p.get('filename') if p else None,
            'progress': p.get('progress') if p else None,
            'saved_at': p.get('saved_at') if p else None,
            'snapshot_interval': self.interval,
        }

    # ---- state file ------------------------------------------------------
    def _read_state(self):
        try:
            with io.open(self.state_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except (IOError, OSError):
            return None
        except ValueError:
            logging.exception("print_recovery: unreadable state file")
            return None
        if not isinstance(data, dict) or not data.get('active') \
           or not data.get('filename'):
            return None
        return data

    def _write_state(self, data):
        tmp = self.state_file + '.tmp'
        try:
            with io.open(tmp, 'w', encoding='utf-8') as f:
                f.write(json.dumps(data, sort_keys=True))
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.state_file)
            self.write_failed = False
        except (IOError, OSError):
            if not self.write_failed:
                logging.exception("print_recovery: unable to save state")
            self.write_failed = True

    def _clear_state(self):
        self.history.clear()
        self.recording = False
        try:
            os.remove(self.state_file)
        except (IOError, OSError):
            pass

    # ---- sampling --------------------------------------------------------
    def _status(self, name, eventtime):
        obj = self.printer.lookup_object(name, None)
        if obj is None or not hasattr(obj, 'get_status'):
            return {}
        try:
            return obj.get_status(eventtime) or {}
        except Exception:
            return {}

    def _mesh_state(self):
        bm = self.printer.lookup_object('bed_mesh', None)
        zmesh = bm.get_mesh() if bm is not None else None
        if zmesh is None:
            return None
        if self.mesh_cache[0] is zmesh:
            return self.mesh_cache[1]
        try:
            mesh = {
                'name': zmesh.get_profile_name(),
                'params': dict(zmesh.get_mesh_params()),
                'probed_matrix': zmesh.get_probed_matrix(),
                'offsets': list(getattr(zmesh, 'mesh_offsets', [0., 0.])),
            }
            json.dumps(mesh)
        except Exception:
            logging.exception("print_recovery: bed mesh not saved")
            mesh = None
        self.mesh_cache = (zmesh, mesh)
        return mesh

    @staticmethod
    def _plain(value, depth=0):
        # Only values that survive JSON and repr() unchanged are kept
        if depth > 4:
            return False
        if value is None or isinstance(value, (bool, int, float, str)):
            return True
        if isinstance(value, list):
            return all(PrintRecovery._plain(v, depth + 1) for v in value)
        if isinstance(value, dict):
            return all(isinstance(k, str) and
                       PrintRecovery._plain(v, depth + 1)
                       for k, v in value.items())
        return False

    def _macro_state(self):
        if not self.macro_all and not self.macro_names:
            return {}
        saved = {}
        for name, obj in self.printer.lookup_objects('gcode_macro'):
            macro = name[len('gcode_macro '):].upper()
            if name == 'gcode_macro' or not obj.variables:
                continue
            if self.macro_all:
                if macro.startswith('_'):
                    continue
            elif macro not in self.macro_names:
                continue
            values = dict((k, v) for k, v in obj.variables.items()
                          if self._plain(v))
            if values:
                saved[macro] = values
        return saved

    def _take_sample(self, eventtime, sd, stats):
        toolhead = self.printer.lookup_object('toolhead')
        th = toolhead.get_status(eventtime)
        gm = self.printer.lookup_object('gcode_move').get_status(eventtime)
        ext = self._status(th.get('extruder', 'extruder'), eventtime)
        bed = self._status('heater_bed', eventtime)
        fan = self._status('fan', eventtime)
        disp = self._status('display_status', eventtime)
        excl = self._status('exclude_object', eventtime)
        sample = {
            'print_time': th['print_time'],
            'file_position': sd.file_position,
            'file_size': sd.file_size,
            'raw_position': list(toolhead.get_position()),
            'gcode_position': list(gm['gcode_position']),
            'homing_origin': list(gm['homing_origin']),
            'speed': gm['speed'],
            'speed_factor': gm['speed_factor'],
            'extrude_factor': gm['extrude_factor'],
            'absolute_coordinates': gm['absolute_coordinates'],
            'absolute_extrude': gm.get('absolute_extrude',
                                       gm.get('absolute_extrusion', True)),
            'extruder': th.get('extruder', 'extruder'),
            'extruder_target': ext.get('target', 0.),
            'bed_target': bed.get('target', 0.),
            'fan_speed': fan.get('speed', 0.),
            'pressure_advance': ext.get('pressure_advance'),
            'smooth_time': ext.get('smooth_time'),
            'max_velocity': th.get('max_velocity'),
            'max_accel': th.get('max_accel'),
            'minimum_cruise_ratio': th.get('minimum_cruise_ratio'),
            'max_accel_to_decel': th.get('max_accel_to_decel'),
            'square_corner_velocity': th.get('square_corner_velocity'),
            'progress': disp.get('progress'),
            'layer': stats.info_current_layer,
            'total_layer': stats.info_total_layer,
            'excluded_objects': list(excl.get('excluded_objects', [])),
            'current_object': excl.get('current_object'),
            'macro_variables': self._macro_state(),
        }
        return sample

    def _sample_handler(self, eventtime):
        if self.shutdown:
            return self.reactor.NEVER
        try:
            self._sample(eventtime)
        except Exception:
            logging.exception("print_recovery: sampling failed")
        return eventtime + self.period

    def _sample(self, eventtime):
        sd = self.printer.lookup_object('virtual_sdcard', None)
        stats = self.printer.lookup_object('print_stats', None)
        if sd is None or stats is None or self.resuming:
            return
        state = stats.state
        if state in ('complete', 'cancelled'):
            if self.recording:
                logging.info("print_recovery: print %s, state cleared", state)
                self._clear_state()
            return
        if state != 'printing' or not sd.is_active():
            return
        if stats.get_status(eventtime)['filament_used'] < self.min_extruded:
            return
        sample = self._take_sample(eventtime, sd, stats)
        sample['filename'] = stats.filename
        self.history.append(sample)
        while self.history and \
                sample['print_time'] - self.history[0]['print_time'] \
                > HISTORY_SECONDS:
            self.history.popleft()
        if eventtime - self.last_write < self.interval:
            return
        # Save the newest sample whose moves the MCU has already executed:
        # G-code is read ahead of the motion, and what is still queued is
        # lost when the power drops.
        th = self.printer.lookup_object('toolhead').get_status(eventtime)
        done = th['estimated_print_time'] - EXECUTED_MARGIN
        chosen = None
        for s in self.history:
            if s['print_time'] <= done:
                chosen = s
            else:
                break
        if chosen is None:
            return
        data = dict(chosen)
        data['active'] = True
        data['saved_at'] = time.time()
        data['file_size'] = sd.file_size
        if chosen['file_size']:
            data['progress'] = float(chosen['file_position']) \
                / chosen['file_size']
        data['mesh'] = self._mesh_state()
        self._write_state(data)
        self.last_write = eventtime
        self.recording = True
        if self.pending is not None:
            self.pending = None

    # ---- prompt ----------------------------------------------------------
    def _ui_language(self):
        lang = self.language.strip()
        if lang.lower() != 'auto':
            return self._normalize_lang(lang)
        now = self.reactor.monotonic()
        if now - self.lang_cache[0] < LANG_CACHE_SECONDS:
            return self.lang_cache[1]
        found = 'en'
        for port, ns, key in WEB_PORTS:
            try:
                url = ("http://127.0.0.1:%d/server/database/item"
                       "?namespace=%s&key=%s" % (port, ns, key))
                resp = urlopen(url, timeout=1.5)
                value = json.loads(resp.read().decode('utf-8'))
                value = value['result']['value']
                if isinstance(value, str) and value:
                    found = self._normalize_lang(value)
                # else the front end has no language saved: English
                self.lang_cache = (now, found)
                return found
            except Exception:
                continue
        return found

    @staticmethod
    def _normalize_lang(code):
        code = code.replace('_', '-').lower()
        if code.startswith('zh'):
            return 'zh-TW' if ('tw' in code or 'hant' in code
                               or 'hk' in code) else 'zh'
        for lang in ('pt', 'ru'):
            if code.startswith(lang):
                return lang
        return 'en'

    def _t(self, key):
        texts = TEXTS.get(self._ui_language(), TEXTS['en'])
        return texts.get(key) or TEXTS['en'][key]

    def _describe(self, p):
        name = p.get('filename', '?')
        pct = int(round((p.get('progress') or 0.) * 100))
        where = "Z %.2f mm" % (p['gcode_position'][2],)
        if p.get('layer') is not None and p.get('total_layer'):
            where = "%s, %s" % (self._t('layer') % (p['layer'],
                                                    p['total_layer']), where)
        mins = int((time.time() - p.get('saved_at', time.time())) / 60.)
        ago = self._t('min') % mins if mins < 120 \
            else self._t('hour') % (mins / 60.)
        return {'name': name, 'pct': pct, 'where': where, 'ago': ago}

    def _stopped_text(self, p):
        return self._t('stopped') % self._describe(p)

    def _show_prompt(self):
        p = self.pending
        if p is None:
            return
        info = lambda m: self.gcode.respond_info("action:" + m, log=False)
        info("prompt_begin " + self._t('title'))
        info("prompt_text " + self._stopped_text(p))
        info("prompt_text " + self._t('ask_park' if self.park_enable_x
                                      else 'ask'))
        info("prompt_text " + self._t('warn'))
        info("prompt_button_group_start")
        info("prompt_button %s|PRINT_RECOVERY_RESUME|primary"
             % (self._t('resume'),))
        info("prompt_button %s|PRINT_RECOVERY_DISCARD|error"
             % (self._t('discard'),))
        info("prompt_button_group_end")
        info("prompt_show")

    def _end_prompt(self):
        self.gcode.respond_info("action:prompt_end", log=False)

    def _prompt_handler(self, eventtime):
        if self.pending is None or self.shutdown or self.resuming:
            return self.reactor.NEVER
        stats = self.printer.lookup_object('print_stats', None)
        if stats is not None and stats.state == 'printing':
            return self.reactor.NEVER
        self._show_prompt()
        if self.prompt_repeat <= 0.:
            return self.reactor.NEVER
        return eventtime + self.prompt_repeat

    def cmd_RESPOND(self, gcmd):
        msg = gcmd.get('MSG', '')
        kind = gcmd.get('TYPE', 'echo')
        prefix = {'command': '// ', 'error': '!! '}.get(kind, 'echo: ')
        self.gcode.respond_raw(prefix + msg)

    # ---- commands --------------------------------------------------------
    cmd_PRINT_RECOVERY_STATUS_help = \
        "Show the interrupted print that can be resumed, if any"
    def cmd_PRINT_RECOVERY_STATUS(self, gcmd):
        if self.pending is None:
            gcmd.respond_info("print_recovery: " + self._t('none'))
            return
        gcmd.respond_info("print_recovery: " + self._stopped_text(self.pending))
        self._show_prompt()

    cmd_PRINT_RECOVERY_DISCARD_help = \
        "Forget the interrupted print, lift the nozzle and home X/Y " \
        "(LIFT_Z= HOME_X= HOME_Y=)"
    def cmd_PRINT_RECOVERY_DISCARD(self, gcmd):
        p = self.pending
        self.pending = None
        self._clear_state()
        self._end_prompt()
        gcmd.respond_info("print_recovery: " + self._t('discarded'))
        if p is None:
            return
        lift = gcmd.get_float('LIFT_Z', self.discard_lift_z, minval=0.)
        home_x = self._get_bool(gcmd, 'HOME_X', self.discard_home_x)
        home_y = self._get_bool(gcmd, 'HOME_Y', self.discard_home_y)
        if lift <= 0. and not (home_x or home_y):
            return
        sd = self.printer.lookup_object('virtual_sdcard')
        stats = self.printer.lookup_object('print_stats')
        if sd.is_active() or stats.state == 'printing':
            return
        self._clear_nozzle(gcmd, p, lift, home_x, home_y)

    def _clear_nozzle(self, gcmd, p, lift, home_x, home_y):
        # Get the nozzle away from the abandoned part
        info = gcmd.respond_info
        toolhead = self.printer.lookup_object('toolhead')
        th = toolhead.get_status(self.reactor.monotonic())
        raw = p.get('raw_position')
        if raw is None:
            return
        homed = th.get('homed_axes', '')
        pos = list(toolhead.get_position())
        if 'z' not in homed:
            # Z is assumed to be where the last snapshot left it
            pos[2] = raw[2]
            toolhead.set_position(pos, homing_axes="z")
        room = th['axis_maximum'][2] - pos[2]
        lift = min(lift, room)
        if lift < 2.:
            info("print_recovery: " + self._t('no_room'))
            return
        info("print_recovery: " + self._t('discard_lift') % (lift,))
        self._run("G91\nG1 Z%.3f F600\nG90" % (lift,))
        axes = ("X " if home_x else "") + ("Y" if home_y else "")
        if axes.strip():
            info("print_recovery: " + self._t('home'))
            self._run("G28 " + axes.strip())

    cmd_PRINT_RECOVERY_RESUME_help = \
        "Resume the interrupted print (PARK_ENABLE_X= PARK_ENABLE_Y= " \
        "PARK_X= PARK_Y= PURGE= PURGE_LENGTH= LIFT_Z=)"
    def cmd_PRINT_RECOVERY_RESUME(self, gcmd):
        p = self.pending
        if p is None:
            raise gcmd.error("print_recovery: no interrupted print to resume")
        sd = self.printer.lookup_object('virtual_sdcard')
        stats = self.printer.lookup_object('print_stats')
        if sd.is_active() or stats.state == 'printing':
            raise gcmd.error("print_recovery: a print is already running")
        toolhead = self.printer.lookup_object('toolhead')
        th = toolhead.get_status(self.reactor.monotonic())
        amin, amax = th['axis_minimum'], th['axis_maximum']
        park_x = gcmd.get_float('PARK_X', self.park_x
                                if self.park_x is not None else amin[0])
        park_y = gcmd.get_float('PARK_Y', self.park_y)
        purge = self._get_bool(gcmd, 'PURGE', self.purge)
        park_ex = self._get_bool(gcmd, 'PARK_ENABLE_X', self.park_enable_x)
        park_ey = self._get_bool(gcmd, 'PARK_ENABLE_Y', self.park_enable_y)
        purge_len = gcmd.get_float('PURGE_LENGTH', self.purge_length,
                                   minval=0.)
        lift = gcmd.get_float('LIFT_Z', self.lift_z, minval=0.)
        path = os.path.join(sd.sdcard_dirname, p['filename'])
        try:
            size = os.path.getsize(path)
        except OSError:
            raise gcmd.error("print_recovery: %s no longer exists"
                             % (p['filename'],))
        if p.get('file_size') and size != p['file_size']:
            raise gcmd.error("print_recovery: %s changed since it was "
                             "interrupted" % (p['filename'],))
        self.resuming = True
        try:
            self._end_prompt()
            self._resume(gcmd, p, sd, amax, park_ex, park_ey, park_x, park_y,
                         purge, purge_len, lift)
        finally:
            self.resuming = False

    @staticmethod
    def _get_bool(gcmd, name, default):
        raw = gcmd.get(name, None)
        if raw is None:
            return default
        return raw.strip().lower() in ('1', 'true', 'yes', 'on')

    def _run(self, script):
        self.gcode.run_script_from_command(script)

    def _resume(self, gcmd, p, sd, amax, park_ex, park_ey, park_x, park_y,
                purge, purge_len, lift):
        info = gcmd.respond_info
        raw = p['raw_position']
        gpos = p['gcode_position']
        org = p['homing_origin']
        bed = p.get('bed_target') or 0.
        ext = p.get('extruder_target') or 0.
        fast = self.park_speed * 60.
        room = amax[2] - raw[2]
        if lift > 0. and room < min(lift, 2.):
            raise gcmd.error("print_recovery: not enough Z room (%.1f mm) to "
                             "lift the nozzle before homing X/Y" % (room,))
        lift = max(0., min(lift, room))
        info("print_recovery: " + self._t('bed') % (bed,))
        script = ["G90", "BED_MESH_CLEAR",
                  "SET_GCODE_OFFSET X=0 Y=0 Z=0"]
        if bed:
            script.append("M140 S%.1f" % (bed,))
        self._run("\n".join(script))
        # Z is assumed unchanged: set it and lift. With parking, X/Y are then
        # homed and the nozzle waits at the X stop; without it, X/Y are also
        # assumed unchanged and the nozzle heats where it is (lifted).
        toolhead = self.printer.lookup_object('toolhead')
        pos = list(toolhead.get_position())
        pos[2] = raw[2]
        axes = "z"
        if not park_ex:
            pos[0] = raw[0]
            axes += "x"
        if not park_ey:
            pos[1] = raw[1]
            axes += "y"
        toolhead.set_position(pos, homing_axes=axes)
        self._run("G91\nG1 Z%.3f F600\nG90" % (lift,))
        park = park_ex or park_ey
        if park:
            info("print_recovery: " + self._t('home'))
            self._run("G28 " + " ".join(a for a, on in
                                        (("X", park_ex), ("Y", park_ey)) if on))
            move = []
            if park_ex and park_x is not None:
                move.append("X%.3f" % (park_x,))
            if park_ey and park_y is not None:
                move.append("Y%.3f" % (park_y,))
            if move:
                self._run("G90\nG1 %s F%.0f" % (" ".join(move), fast))
        info("print_recovery: "
             + self._t('nozzle_park' if park else 'nozzle_here') % (ext,))
        script = ["M83"]
        if ext:
            script.append("M104 S%.1f" % (ext,))
        if bed:
            script.append("M190 S%.1f" % (bed,))
        if ext:
            script.append("M109 S%.1f" % (ext,))
        self._run("\n".join(script))
        retract = p_retract = 0.
        # Purging in place would drop filament on the part: only when parked
        if park and purge and purge_len > 0.:
            info("print_recovery: " + self._t('purge') % (purge_len,))
            retract = p_retract = self.purge_retract
            script = ["G1 E%.3f F%.0f" % (purge_len, self.purge_speed * 60.)]
            if retract:
                script.append("G1 E-%.3f F1800" % (retract,))
            script.append("G4 P800")
            self._run("\n".join(script))
        self._restore_mesh(p)
        info("print_recovery: " + self._t('back'))
        script = ["SET_GCODE_OFFSET X=%.4f Y=%.4f Z=%.4f"
                  % (org[0], org[1], org[2]),
                  "G90",
                  "G1 Z%.3f F600" % (gpos[2] + lift,),
                  "G1 X%.3f Y%.3f F%.0f" % (gpos[0], gpos[1], fast),
                  "G1 Z%.3f F300" % (gpos[2],)]
        if retract:
            script += ["M83", "G1 E%.3f F1200" % (retract,)]
        self._run("\n".join(script))
        self._restore_state(p)
        self._reopen_file(gcmd, p, sd)
        self.pending = None
        self.recording = True
        info("print_recovery: " + self._t('resumed')
             % {'name': p['filename'], 'pos': p['file_position']})

    def _restore_mesh(self, p):
        mesh = p.get('mesh')
        if not mesh:
            return
        try:
            module = __import__('extras.bed_mesh', fromlist=['ZMesh'])
            bm = self.printer.lookup_object('bed_mesh')
            zmesh = module.ZMesh(mesh['params'], mesh.get('name'))
            zmesh.build_mesh(mesh['probed_matrix'])
            zmesh.set_mesh_offsets(mesh.get('offsets', [0., 0.]))
            bm.set_mesh(zmesh)
        except Exception:
            logging.exception("print_recovery: bed mesh not restored")
            self.gcode.respond_info("print_recovery: "
                                    + self._t('mesh_fail'))

    def _restore_state(self, p):
        script = []
        script.append("M82" if p['absolute_extrude'] else "M83")
        script.append("G92 E%.5f" % (p['gcode_position'][3],))
        script.append("M220 S%.2f" % (p['speed_factor'] * 100.,))
        script.append("M221 S%.2f" % (p['extrude_factor'] * 100.,))
        script.append("G1 F%.1f" % (p['speed'] * 60.,))
        vel = []
        if p.get('max_velocity'):
            vel.append("VELOCITY=%.3f" % (p['max_velocity'],))
        if p.get('max_accel'):
            vel.append("ACCEL=%.3f" % (p['max_accel'],))
        if p.get('square_corner_velocity') is not None:
            vel.append("SQUARE_CORNER_VELOCITY=%.3f"
                       % (p['square_corner_velocity'],))
        if p.get('minimum_cruise_ratio') is not None:
            vel.append("MINIMUM_CRUISE_RATIO=%.4f"
                       % (p['minimum_cruise_ratio'],))
        elif p.get('max_accel_to_decel'):
            vel.append("ACCEL_TO_DECEL=%.3f" % (p['max_accel_to_decel'],))
        if vel:
            script.append("SET_VELOCITY_LIMIT " + " ".join(vel))
        if p.get('pressure_advance') is not None:
            script.append("SET_PRESSURE_ADVANCE ADVANCE=%.5f SMOOTH_TIME=%.4f"
                          % (p['pressure_advance'],
                             p.get('smooth_time') or 0.04))
        if 'fan' in self.printer.objects:
            fan = p.get('fan_speed') or 0.
            script.append("M106 S%d" % int(round(fan * 255.))
                          if fan > 0. else "M107")
        if self.printer.lookup_object('display_status', None) is not None \
           and p.get('progress') is not None:
            script.append("M73 P%d" % int(round(p['progress'] * 100.)))
        script.append("G90" if p['absolute_coordinates'] else "G91")
        self._run("\n".join(script))
        self._restore_macro_variables(p)

    def _restore_macro_variables(self, p):
        for macro, values in (p.get('macro_variables') or {}).items():
            obj = self.printer.lookup_object('gcode_macro ' + macro, None)
            if obj is None:
                continue
            # Same swap SET_GCODE_VARIABLE does; unknown names are skipped
            merged = dict(obj.variables)
            for k, v in values.items():
                if k in merged:
                    merged[k] = v
            obj.variables = merged

    def _object_definitions(self, path, limit):
        lines = []
        try:
            with io.open(path, 'r', encoding='utf-8', errors='replace',
                         newline='') as f:
                read = 0
                for line in f:
                    read += len(line)
                    if read > OBJECT_SCAN_LIMIT or read > limit:
                        break
                    if line.startswith('EXCLUDE_OBJECT_DEFINE'):
                        lines.append(line.strip())
        except (IOError, OSError):
            logging.exception("print_recovery: object scan failed")
        return lines

    def _reopen_file(self, gcmd, p, sd):
        path = os.path.join(sd.sdcard_dirname, p['filename'])
        # Lets add-ons (timelapse) tell this apart from a new print
        self.gcode.respond_raw("Print recovery: resuming")
        self._run("M23 %s" % (p['filename'],))
        self._run("M26 S%d" % (p['file_position'],))
        script = []
        if p.get('total_layer'):
            script.append("SET_PRINT_STATS_INFO TOTAL_LAYER=%d "
                          "CURRENT_LAYER=%d"
                          % (p['total_layer'], p.get('layer') or 0))
        if self.printer.lookup_object('exclude_object', None) is not None:
            script += self._object_definitions(path, p['file_position'])
            for name in p.get('excluded_objects', []):
                script.append("EXCLUDE_OBJECT NAME=%s" % (name,))
            if p.get('current_object'):
                script.append("EXCLUDE_OBJECT_START NAME=%s"
                              % (p['current_object'],))
        script.append("M24")
        self._run("\n".join(script))

def load_config(config):
    return PrintRecovery(config)
