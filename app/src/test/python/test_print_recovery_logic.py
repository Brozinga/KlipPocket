"""Behaviour tests for the print_recovery Klipper add-on: the state file, the
sampling, the prompt and the resume/discard commands, against fakes of the
Klipper objects it talks to (no Klipper needed).

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import importlib.util
import json
import os
import shutil
import tempfile
import time
import unittest

MODULE = os.path.join(os.path.dirname(__file__), '..', '..', 'main',
                      'klipper_print_recovery', 'print_recovery.py')
spec = importlib.util.spec_from_file_location('print_recovery_logic', MODULE)
pr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pr)


class CommandError(Exception):
    pass


class FakeGcode:
    def __init__(self):
        self.commands = {}
        self.gcode_handlers = {}
        self.info = []
        self.raw = []
        self.scripts = []

    def register_command(self, name, func, desc=None):
        self.commands[name] = func
        self.gcode_handlers[name] = func

    def respond_info(self, msg, log=True):
        self.info.append(msg)

    def respond_raw(self, msg):
        self.raw.append(msg)

    def run_script_from_command(self, script):
        self.scripts.append(script)

    def all_scripts(self):
        return "\n".join(self.scripts)


class FakeReactor:
    NEVER = 9999999999.

    def __init__(self):
        self.now = 1000.
        self.timers = []

    def monotonic(self):
        return self.now

    def register_timer(self, callback, when):
        self.timers.append((callback, when))
        return object()


class FakeToolhead:
    def __init__(self, position=(100., 100., 5., 0.), homed='xyz'):
        self.position = list(position)
        self.homed = homed
        self.set_calls = []

    def get_position(self):
        return list(self.position)

    def set_position(self, pos, homing_axes=()):
        self.position = list(pos)
        self.set_calls.append((list(pos), homing_axes))

    def get_status(self, eventtime):
        return {'print_time': 50., 'estimated_print_time': 100.,
                'extruder': 'extruder', 'homed_axes': self.homed,
                'axis_minimum': [-6., 0., 0., 0.],
                'axis_maximum': [235., 235., 250., 0.],
                'max_velocity': 300., 'max_accel': 3000.,
                'minimum_cruise_ratio': 0.5, 'square_corner_velocity': 5.}


class FakeSdcard:
    def __init__(self, dirname, active=False):
        self.sdcard_dirname = dirname
        self.active = active
        self.file_position = 5000
        self.file_size = 10000

    def is_active(self):
        return self.active


class FakeStats:
    def __init__(self):
        self.state = 'standby'
        self.filename = 'cube.gcode'
        self.info_current_layer = 3
        self.info_total_layer = 10
        self.filament_used = 50.

    def get_status(self, eventtime):
        return {'filament_used': self.filament_used}


class FakeStatusObject:
    def __init__(self, status):
        self.status = status

    def get_status(self, eventtime):
        return self.status


class FakeMacro:
    def __init__(self, variables):
        self.variables = variables


class FakePrinter:
    def __init__(self, cfg_dir, reactor, gcode):
        self.reactor = reactor
        self.gcode = gcode
        self.cfg_dir = cfg_dir
        self.toolhead = FakeToolhead()
        self.sd = FakeSdcard(os.path.join(cfg_dir, 'gcodes'))
        self.stats = FakeStats()
        self.objects = {
            'gcode': gcode,
            'toolhead': self.toolhead,
            'virtual_sdcard': self.sd,
            'print_stats': self.stats,
            'gcode_move': FakeStatusObject({
                'gcode_position': [10., 20., 0.6, 1.5],
                'homing_origin': [0., 0., 0.05, 0.],
                'speed': 50., 'speed_factor': 1.1, 'extrude_factor': 0.95,
                'absolute_coordinates': True, 'absolute_extrude': True}),
            'heater_bed': FakeStatusObject({'target': 60.}),
            'extruder': FakeStatusObject({'target': 210.,
                                          'pressure_advance': 0.05,
                                          'smooth_time': 0.04}),
            'fan': FakeStatusObject({'speed': 0.5}),
            'display_status': FakeStatusObject({'progress': 0.5}),
        }
        self.handlers = {}

    def get_reactor(self):
        return self.reactor

    def get_start_args(self):
        return {'config_file': os.path.join(self.cfg_dir, 'printer.cfg')}

    def lookup_object(self, name, default=None):
        if name in self.objects:
            return self.objects[name]
        if default is None and name in ('bed_mesh',):
            return None
        return default

    def lookup_objects(self, module):
        return [(n, o) for n, o in self.objects.items()
                if n == module or n.startswith(module + ' ')]

    def register_event_handler(self, event, callback):
        self.handlers[event] = callback


class FakeConfig:
    def __init__(self, printer, **values):
        self.printer = printer
        self.values = values

    def get_printer(self):
        return self.printer

    def get(self, name, default=None):
        return self.values.get(name, default)

    def getfloat(self, name, default=None, **limits):
        return float(self.values[name]) if name in self.values else default

    def getboolean(self, name, default=None):
        if name not in self.values:
            return default
        return str(self.values[name]).lower() in ('1', 'true', 'yes', 'on')


class Gcmd:
    def __init__(self, **params):
        self.params = params
        self.info = []
        self.error = CommandError

    def get(self, name, default=None):
        return self.params.get(name, default)

    def get_float(self, name, default=None, **limits):
        return float(self.params[name]) if name in self.params else default

    def respond_info(self, msg, log=True):
        self.info.append(msg)


class Base(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.dir, 'gcodes'))
        self.reactor = FakeReactor()
        self.gcode = FakeGcode()
        self.printer = FakePrinter(self.dir, self.reactor, self.gcode)

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def make(self, **values):
        values.setdefault('language', 'en')
        return pr.PrintRecovery(FakeConfig(self.printer, **values))

    def snapshot(self, **over):
        data = {
            'active': True, 'filename': 'cube.gcode', 'file_position': 5000,
            'file_size': 10000, 'progress': 0.5, 'saved_at': time.time(),
            'print_time': 40., 'layer': 3, 'total_layer': 10,
            'raw_position': [100., 100., 0.6, 0.],
            'gcode_position': [100., 100., 0.6, 12.5],
            'homing_origin': [0., 0., 0.05, 0.],
            'speed': 50., 'speed_factor': 1.1, 'extrude_factor': 0.95,
            'absolute_coordinates': True, 'absolute_extrude': True,
            'extruder': 'extruder', 'extruder_target': 210.,
            'bed_target': 60., 'fan_speed': 0.5, 'pressure_advance': 0.05,
            'smooth_time': 0.04, 'max_velocity': 300., 'max_accel': 3000.,
            'square_corner_velocity': 5., 'minimum_cruise_ratio': 0.5,
            'excluded_objects': [], 'current_object': None,
            'macro_variables': {}, 'mesh': None,
        }
        data.update(over)
        return data

    def write_gcode(self, name='cube.gcode', size=10000):
        with open(os.path.join(self.dir, 'gcodes', name), 'wb') as f:
            f.write(b'G1 X1\n' * (size // 6) + b' ' * (size % 6))


class StateFile(Base):
    def test_state_is_written_and_read_back(self):
        pr_ = self.make()
        pr_._write_state(self.snapshot())
        self.assertEqual(pr_._read_state()['filename'], 'cube.gcode')

    def test_the_state_file_lives_next_to_printer_cfg(self):
        pr_ = self.make()
        self.assertEqual(pr_.state_file,
                         os.path.join(self.dir, '.print_recovery.json'))

    def test_writing_leaves_no_temp_file(self):
        pr_ = self.make()
        pr_._write_state(self.snapshot())
        self.assertFalse(os.path.exists(pr_.state_file + '.tmp'))

    def test_a_missing_file_reads_as_no_state(self):
        self.assertIsNone(self.make()._read_state())

    def test_an_inactive_snapshot_is_ignored(self):
        pr_ = self.make()
        pr_._write_state(self.snapshot(active=False))
        self.assertIsNone(pr_._read_state())

    def test_a_snapshot_without_a_filename_is_ignored(self):
        pr_ = self.make()
        pr_._write_state(self.snapshot(filename=''))
        self.assertIsNone(pr_._read_state())

    def test_a_corrupt_file_is_ignored(self):
        pr_ = self.make()
        with open(pr_.state_file, 'w') as f:
            f.write('{"active": tru')
        with self.assertLogs(level='ERROR'):
            self.assertIsNone(pr_._read_state())

    def test_a_file_that_is_not_an_object_is_ignored(self):
        pr_ = self.make()
        with open(pr_.state_file, 'w') as f:
            json.dump([1, 2], f)
        self.assertIsNone(pr_._read_state())

    def test_clearing_removes_the_file_and_the_history(self):
        pr_ = self.make()
        pr_._write_state(self.snapshot())
        pr_.history.append({'print_time': 1.})
        pr_.recording = True
        pr_._clear_state()
        self.assertFalse(os.path.exists(pr_.state_file))
        self.assertEqual(len(pr_.history), 0)
        self.assertFalse(pr_.recording)

    def test_clearing_without_a_file_is_harmless(self):
        self.make()._clear_state()

    def test_an_unwritable_location_is_reported_once(self):
        pr_ = self.make(state_file=os.path.join(self.dir, 'missing', 's.json'))
        with self.assertLogs(level='ERROR') as logs:
            pr_._write_state(self.snapshot())
            pr_._write_state(self.snapshot())
        self.assertEqual(len(logs.records), 1)
        self.assertTrue(pr_.write_failed)


class PlainValues(unittest.TestCase):
    plain = staticmethod(pr.PrintRecovery._plain)

    def test_basic_values_are_kept(self):
        for v in (None, True, 3, 2.5, 'text'):
            self.assertTrue(self.plain(v), v)

    def test_lists_and_dicts_of_basic_values_are_kept(self):
        self.assertTrue(self.plain([1, 'a', [2.0, None]]))
        self.assertTrue(self.plain({'a': 1, 'b': {'c': [True]}}))

    def test_other_objects_are_dropped(self):
        self.assertFalse(self.plain(object()))
        self.assertFalse(self.plain({1: 'a'}))
        self.assertFalse(self.plain([object()]))
        self.assertFalse(self.plain(lambda: 1))

    def test_very_deep_values_are_dropped(self):
        deep = [[[[[[1]]]]]]
        self.assertFalse(self.plain(deep))


class MacroState(Base):
    def macros(self, **macros):
        for name, variables in macros.items():
            self.printer.objects['gcode_macro ' + name] = FakeMacro(variables)

    def test_all_macros_are_saved_except_private_ones(self):
        self.macros(PRINT_START={'layer': 3}, _HELPER={'x': 1}, MY={'a': 'b'})
        saved = self.make(macro_variables='*')._macro_state()
        self.assertEqual(saved, {'PRINT_START': {'layer': 3}, 'MY': {'a': 'b'}})

    def test_a_list_saves_only_those_macros(self):
        self.macros(PRINT_START={'layer': 3}, MY={'a': 'b'})
        saved = self.make(macro_variables='my')._macro_state()
        self.assertEqual(saved, {'MY': {'a': 'b'}})

    def test_none_saves_nothing(self):
        self.macros(PRINT_START={'layer': 3})
        self.assertEqual(self.make(macro_variables='none')._macro_state(), {})

    def test_only_plain_values_are_saved(self):
        self.macros(M={'ok': 1, 'bad': object(), 'nested': {'k': [1, 2]}})
        self.assertEqual(self.make()._macro_state(),
                         {'M': {'ok': 1, 'nested': {'k': [1, 2]}}})

    def test_macros_without_variables_are_skipped(self):
        self.macros(EMPTY={})
        self.assertEqual(self.make()._macro_state(), {})

    def test_saved_variables_are_put_back(self):
        self.macros(M={'layer': 0, 'keep': 'x'})
        pr_ = self.make()
        pr_._restore_macro_variables(self.snapshot(
            macro_variables={'M': {'layer': 7, 'unknown': 1}, 'GONE': {'a': 1}}))
        self.assertEqual(self.printer.objects['gcode_macro M'].variables,
                         {'layer': 7, 'keep': 'x'})


class Language(unittest.TestCase):
    norm = staticmethod(pr.PrintRecovery._normalize_lang)

    def test_supported_languages_are_recognised(self):
        self.assertEqual(self.norm('pt-BR'), 'pt')
        self.assertEqual(self.norm('pt_PT'), 'pt')
        self.assertEqual(self.norm('ru'), 'ru')
        self.assertEqual(self.norm('en-US'), 'en')

    def test_chinese_splits_into_simplified_and_traditional(self):
        self.assertEqual(self.norm('zh-CN'), 'zh')
        self.assertEqual(self.norm('zh'), 'zh')
        self.assertEqual(self.norm('zh-TW'), 'zh-TW')
        self.assertEqual(self.norm('zh_Hant'), 'zh-TW')
        self.assertEqual(self.norm('zh-HK'), 'zh-TW')

    def test_anything_else_is_english(self):
        self.assertEqual(self.norm('de'), 'en')
        self.assertEqual(self.norm(''), 'en')


class Texts(unittest.TestCase):
    def test_every_language_has_every_text_the_english_one_has(self):
        for lang, texts in pr.TEXTS.items():
            self.assertEqual(set(texts), set(pr.TEXTS['en']), lang)

    def test_format_placeholders_match_the_english_text(self):
        import re
        spec = re.compile(r'%(?:\(\w+\))?[-+ #0]*\d*(?:\.\d+)?[sdfr]')
        for lang, texts in pr.TEXTS.items():
            for key, text in texts.items():
                self.assertEqual(sorted(spec.findall(text)),
                                 sorted(spec.findall(pr.TEXTS['en'][key])),
                                 '%s/%s' % (lang, key))


class Describe(Base):
    def test_a_forced_language_is_used(self):
        self.assertEqual(self.make(language='pt')._t('resume'),
                         pr.TEXTS['pt']['resume'])
        self.assertEqual(self.make(language='zh-TW')._ui_language(), 'zh-TW')

    def test_the_description_names_file_progress_and_position(self):
        d = self.make()._describe(self.snapshot(saved_at=time.time() - 180))
        self.assertEqual(d['name'], 'cube.gcode')
        self.assertEqual(d['pct'], 50)
        self.assertIn('Z 0.60 mm', d['where'])
        self.assertIn('3', d['where'])
        self.assertIn('3', d['ago'])

    def test_no_layer_info_leaves_only_the_height(self):
        d = self.make()._describe(self.snapshot(layer=None, total_layer=None))
        self.assertEqual(d['where'], 'Z 0.60 mm')

    def test_old_snapshots_are_described_in_hours(self):
        d = self.make()._describe(self.snapshot(saved_at=time.time() - 3 * 3600))
        self.assertIn('h', d['ago'].lower())

    def test_the_stopped_text_mentions_the_file(self):
        self.assertIn('cube.gcode', self.make()._stopped_text(self.snapshot()))

    def test_status_reports_no_pending_print(self):
        self.assertEqual(self.make().get_status(), {
            'pending': False, 'filename': None, 'progress': None,
            'saved_at': None, 'snapshot_interval': 2.})

    def test_status_reports_the_pending_print(self):
        pr_ = self.make()
        pr_.pending = self.snapshot()
        status = pr_.get_status()
        self.assertTrue(status['pending'])
        self.assertEqual(status['filename'], 'cube.gcode')
        self.assertEqual(status['progress'], 0.5)


class Prompt(Base):
    def test_the_prompt_offers_resume_and_discard(self):
        pr_ = self.make()
        pr_.pending = self.snapshot()
        pr_._show_prompt()
        text = "\n".join(self.gcode.info)
        self.assertIn('action:prompt_begin', text)
        self.assertIn('|PRINT_RECOVERY_RESUME|primary', text)
        self.assertIn('|PRINT_RECOVERY_DISCARD|error', text)
        self.assertEqual(self.gcode.info[-1], 'action:prompt_show')

    def test_nothing_is_shown_without_a_pending_print(self):
        self.make()._show_prompt()
        self.assertEqual(self.gcode.info, [])

    def test_the_prompt_asks_about_parking_only_when_x_is_parked(self):
        parked = self.make(park_enable_x='True')
        parked.pending = self.snapshot()
        parked._show_prompt()
        first = list(self.gcode.info)
        self.gcode.info.clear()
        unparked = self.make(park_enable_x='False')
        unparked.pending = self.snapshot()
        unparked._show_prompt()
        self.assertNotEqual(first, self.gcode.info)

    def test_ending_the_prompt_sends_prompt_end(self):
        self.make()._end_prompt()
        self.assertEqual(self.gcode.info, ['action:prompt_end'])

    def test_the_reminder_repeats_while_a_print_waits(self):
        pr_ = self.make(prompt_repeat='60')
        pr_.pending = self.snapshot()
        self.assertEqual(pr_._prompt_handler(500.), 560.)

    def test_the_reminder_stops_when_configured_to_show_once(self):
        pr_ = self.make(prompt_repeat='0')
        pr_.pending = self.snapshot()
        self.assertEqual(pr_._prompt_handler(500.), self.reactor.NEVER)
        self.assertTrue(self.gcode.info)

    def test_the_reminder_stops_without_a_pending_print(self):
        self.assertEqual(self.make()._prompt_handler(500.), self.reactor.NEVER)

    def test_the_reminder_stops_while_printing(self):
        pr_ = self.make()
        pr_.pending = self.snapshot()
        self.printer.stats.state = 'printing'
        self.assertEqual(pr_._prompt_handler(500.), self.reactor.NEVER)
        self.assertEqual(self.gcode.info, [])

    def test_the_reminder_stops_after_a_shutdown_or_while_resuming(self):
        pr_ = self.make()
        pr_.pending = self.snapshot()
        pr_.shutdown = True
        self.assertEqual(pr_._prompt_handler(500.), self.reactor.NEVER)
        pr_.shutdown = False
        pr_.resuming = True
        self.assertEqual(pr_._prompt_handler(500.), self.reactor.NEVER)

    def test_respond_maps_message_types_to_console_prefixes(self):
        pr_ = self.make()
        for kind, prefix in (('command', '// '), ('error', '!! '), ('echo', 'echo: '), ('other', 'echo: ')):
            pr_.cmd_RESPOND(Gcmd(MSG='hi', TYPE=kind))
            self.assertEqual(self.gcode.raw[-1], prefix + 'hi')

    def test_status_command_without_a_pending_print(self):
        gcmd = Gcmd()
        self.make().cmd_PRINT_RECOVERY_STATUS(gcmd)
        self.assertEqual(gcmd.info, ['print_recovery: ' + pr.TEXTS['en']['none']])

    def test_status_command_shows_the_prompt_again(self):
        pr_ = self.make()
        pr_.pending = self.snapshot()
        gcmd = Gcmd()
        pr_.cmd_PRINT_RECOVERY_STATUS(gcmd)
        self.assertIn('cube.gcode', gcmd.info[0])
        self.assertIn('action:prompt_show', self.gcode.info)


class Sampling(Base):
    def setUp(self):
        super().setUp()
        self.printer.stats.state = 'printing'
        self.printer.sd.active = True
        self.pr = self.make(snapshot_interval='2')

    def sample(self, at=1000.):
        self.pr._sample(at)

    def test_nothing_is_saved_when_not_printing(self):
        self.printer.stats.state = 'standby'
        self.sample()
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_nothing_is_saved_before_the_minimum_extrusion(self):
        self.printer.stats.filament_used = 1.
        self.sample()
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_a_snapshot_is_saved_while_printing(self):
        self.sample()
        data = self.pr._read_state()
        self.assertEqual(data['filename'], 'cube.gcode')
        self.assertEqual(data['file_position'], 5000)
        self.assertEqual(data['progress'], 0.5)
        self.assertEqual(data['layer'], 3)
        self.assertEqual(data['bed_target'], 60.)
        self.assertEqual(data['extruder_target'], 210.)
        self.assertEqual(data['fan_speed'], 0.5)
        self.assertTrue(data['active'])
        self.assertTrue(self.pr.recording)

    def test_saves_are_spaced_by_the_interval(self):
        self.sample(1000.)
        first = os.path.getmtime(self.pr.state_file)
        os.utime(self.pr.state_file, (first - 100, first - 100))
        self.printer.sd.file_position = 6000
        self.sample(1001.)
        self.assertEqual(self.pr._read_state()['file_position'], 5000)
        self.sample(1003.)
        self.assertEqual(self.pr._read_state()['file_position'], 6000)

    def test_only_moves_the_mcu_already_ran_are_saved(self):
        # the toolhead is at print_time 50 but the MCU is only at 100 - 0.3
        pr_ = self.pr
        pr_.history.append({'print_time': 1., 'file_size': 10000,
                            'file_position': 100, 'filename': 'cube.gcode'})
        self.sample()
        self.assertEqual(pr_._read_state()['file_position'], 5000)

    def test_a_sample_that_has_not_executed_yet_is_not_saved(self):
        self.printer.toolhead.get_status = lambda t: dict(
            FakeToolhead().get_status(t), estimated_print_time=10.)
        self.sample()
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_history_is_trimmed_to_twenty_seconds(self):
        for t in (0., 10., 30., 60.):
            self.printer.toolhead.get_status = (lambda tt: lambda th: dict(
                FakeToolhead().get_status(th), print_time=tt))(t)
            self.pr.last_write = 10 ** 9
            self.sample()
        self.assertTrue(all(60. - s['print_time'] <= pr.HISTORY_SECONDS
                            for s in self.pr.history))

    def test_finishing_a_print_clears_the_snapshot(self):
        self.sample()
        self.assertTrue(os.path.exists(self.pr.state_file))
        self.printer.stats.state = 'complete'
        self.sample(1010.)
        self.assertFalse(os.path.exists(self.pr.state_file))
        self.assertFalse(self.pr.recording)

    def test_cancelling_a_print_clears_the_snapshot(self):
        self.sample()
        self.printer.stats.state = 'cancelled'
        self.sample(1010.)
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_nothing_is_cleared_when_nothing_was_recorded(self):
        self.pr.state_file = os.path.join(self.dir, 'keep.json')
        with open(self.pr.state_file, 'w') as f:
            f.write('{}')
        self.printer.stats.state = 'complete'
        self.sample()
        self.assertTrue(os.path.exists(self.pr.state_file))

    def test_sampling_pauses_while_resuming(self):
        self.pr.resuming = True
        self.sample()
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_a_pending_print_is_forgotten_once_a_new_one_is_saved(self):
        self.pr.pending = self.snapshot()
        self.sample()
        self.assertIsNone(self.pr.pending)

    def test_the_sample_handler_reschedules_itself(self):
        self.assertEqual(self.pr._sample_handler(1000.), 1000. + self.pr.period)

    def test_the_sample_handler_stops_after_a_shutdown(self):
        self.pr.shutdown = True
        self.assertEqual(self.pr._sample_handler(1000.), self.reactor.NEVER)

    def test_a_failure_while_sampling_does_not_stop_the_timer(self):
        del self.printer.objects['gcode_move']
        with self.assertLogs(level='ERROR'):
            self.assertEqual(self.pr._sample_handler(1000.), 1000. + self.pr.period)


class Discard(Base):
    def setUp(self):
        super().setUp()
        self.pr = self.make()
        self.pr.pending = self.snapshot()
        self.pr._write_state(self.snapshot())

    def test_discard_forgets_the_print(self):
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd())
        self.assertIsNone(self.pr.pending)
        self.assertFalse(os.path.exists(self.pr.state_file))

    def test_discard_lifts_the_nozzle_and_homes_x_and_y(self):
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd())
        scripts = self.gcode.all_scripts()
        self.assertIn('G1 Z50.000 F600', scripts)
        self.assertIn('G28 X Y', scripts)

    def test_the_lift_is_limited_by_the_room_above_the_nozzle(self):
        self.printer.toolhead.position = [100., 100., 240., 0.]
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd())
        self.assertIn('G1 Z10.000 F600', self.gcode.all_scripts())

    def test_no_move_when_there_is_no_room(self):
        self.printer.toolhead.position = [100., 100., 249., 0.]
        gcmd = Gcmd()
        self.pr.cmd_PRINT_RECOVERY_DISCARD(gcmd)
        self.assertNotIn('G1 Z', self.gcode.all_scripts())
        self.assertIn(pr.TEXTS['en']['no_room'], "".join(gcmd.info))

    def test_the_lift_and_homing_can_be_overridden(self):
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd(LIFT_Z='20', HOME_X='0', HOME_Y='1'))
        scripts = self.gcode.all_scripts()
        self.assertIn('G1 Z20.000 F600', scripts)
        self.assertIn('G28 Y', scripts)
        self.assertNotIn('G28 X', scripts)

    def test_nothing_moves_when_lift_and_homing_are_off(self):
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd(LIFT_Z='0', HOME_X='0', HOME_Y='0'))
        self.assertEqual(self.gcode.scripts, [])
        self.assertIsNone(self.pr.pending)

    def test_nothing_moves_while_a_print_is_running(self):
        self.printer.sd.active = True
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd())
        self.assertEqual(self.gcode.scripts, [])

    def test_z_is_assumed_when_the_printer_is_not_homed(self):
        self.printer.toolhead.homed = ''
        self.pr.cmd_PRINT_RECOVERY_DISCARD(Gcmd())
        self.assertEqual(self.printer.toolhead.set_calls[0][1], 'z')
        self.assertEqual(self.printer.toolhead.set_calls[0][0][2], 0.6)

    def test_discard_without_a_pending_print_only_reports(self):
        pr_ = self.make()
        gcmd = Gcmd()
        pr_.cmd_PRINT_RECOVERY_DISCARD(gcmd)
        self.assertEqual(self.gcode.scripts, [])


class Resume(Base):
    def setUp(self):
        super().setUp()
        self.write_gcode()
        self.pr = self.make()
        self.pr.pending = self.snapshot()

    def resume(self, **params):
        gcmd = Gcmd(**params)
        self.pr.cmd_PRINT_RECOVERY_RESUME(gcmd)
        return self.gcode.all_scripts()

    def test_there_must_be_a_print_to_resume(self):
        self.pr.pending = None
        with self.assertRaises(CommandError):
            self.pr.cmd_PRINT_RECOVERY_RESUME(Gcmd())

    def test_it_refuses_while_a_print_is_running(self):
        self.printer.stats.state = 'printing'
        with self.assertRaises(CommandError) as ctx:
            self.pr.cmd_PRINT_RECOVERY_RESUME(Gcmd())
        self.assertIn('already running', str(ctx.exception))

    def test_it_refuses_when_the_file_is_gone(self):
        os.remove(os.path.join(self.dir, 'gcodes', 'cube.gcode'))
        with self.assertRaises(CommandError) as ctx:
            self.pr.cmd_PRINT_RECOVERY_RESUME(Gcmd())
        self.assertIn('no longer exists', str(ctx.exception))

    def test_it_refuses_when_the_file_changed(self):
        self.write_gcode(size=9000)
        with self.assertRaises(CommandError) as ctx:
            self.pr.cmd_PRINT_RECOVERY_RESUME(Gcmd())
        self.assertIn('changed', str(ctx.exception))

    def test_it_refuses_without_room_to_lift(self):
        self.pr.pending = self.snapshot(raw_position=[100., 100., 249.5, 0.])
        with self.assertRaises(CommandError) as ctx:
            self.pr.cmd_PRINT_RECOVERY_RESUME(Gcmd())
        self.assertIn('Z room', str(ctx.exception))
        self.assertFalse(self.pr.resuming)

    def test_the_default_resume_heats_parks_purges_and_continues(self):
        s = self.resume()
        lines = s.splitlines()
        for expected in ('BED_MESH_CLEAR', 'M140 S60.0', 'G28 X Y',
                         'M104 S210.0', 'M190 S60.0', 'M109 S210.0',
                         'M23 cube.gcode', 'M26 S5000', 'M24'):
            self.assertIn(expected, lines)
        self.assertLess(lines.index('G28 X Y'), lines.index('M109 S210.0'))
        self.assertLess(lines.index('M109 S210.0'), lines.index('M23 cube.gcode'))
        self.assertLess(lines.index('M26 S5000'), lines.index('M24'))

    def test_the_nozzle_parks_at_the_x_stop_by_default(self):
        self.assertIn('G1 X-6.000', self.resume())

    def test_the_park_position_can_be_overridden(self):
        s = self.resume(PARK_X='10', PARK_Y='20')
        self.assertIn('X10.000 Y20.000', s)

    def test_the_default_purge_extrudes_and_retracts(self):
        s = self.resume()
        self.assertIn('G1 E20.000 F300', s)
        self.assertIn('G1 E-2.000 F1800', s)
        self.assertIn('G1 E2.000 F1200', s)

    def test_the_purge_can_be_turned_off(self):
        s = self.resume(PURGE='0')
        self.assertNotIn('G1 E20.000', s)

    def test_the_purge_length_can_be_overridden(self):
        self.assertIn('G1 E5.000 F300', self.resume(PURGE_LENGTH='5'))

    def test_without_parking_nothing_is_homed_or_purged(self):
        s = self.resume(PARK_ENABLE_X='0', PARK_ENABLE_Y='0')
        self.assertNotIn('G28', s)
        self.assertNotIn('G1 E20.000', s)
        self.assertEqual(self.printer.toolhead.set_calls[-1][1], 'zxy')

    def test_only_the_parked_axes_are_homed(self):
        s = self.resume(PARK_ENABLE_Y='0')
        self.assertIn('G28 X', s)
        self.assertNotIn('G28 X Y', s)

    def test_z_is_assumed_and_lifted_before_anything_moves(self):
        s = self.resume(LIFT_Z='5')
        self.assertEqual(self.printer.toolhead.set_calls[0][0][2], 0.6)
        self.assertLess(s.index('G1 Z5.000 F600'), s.index('G28'))

    def test_the_gcode_offset_and_position_are_restored(self):
        s = self.resume()
        self.assertIn('SET_GCODE_OFFSET X=0.0000 Y=0.0000 Z=0.0500', s)
        self.assertIn('G1 X100.000 Y100.000', s)
        self.assertIn('G1 Z0.600 F300', s)

    def test_the_printing_state_is_restored(self):
        s = self.resume()
        for expected in ('M82', 'G92 E12.50000', 'M220 S110.00', 'M221 S95.00',
                         'G1 F3000.0', 'SET_VELOCITY_LIMIT VELOCITY=300.000',
                         'ACCEL=3000.000', 'MINIMUM_CRUISE_RATIO=0.5000',
                         'SET_PRESSURE_ADVANCE ADVANCE=0.05000', 'M73 P50', 'G90'):
            self.assertIn(expected, s)

    def test_layer_info_is_restored(self):
        self.assertIn('SET_PRINT_STATS_INFO TOTAL_LAYER=10 CURRENT_LAYER=3', self.resume())

    def test_excluded_objects_are_restored(self):
        self.printer.objects['exclude_object'] = FakeStatusObject({})
        self.pr.pending = self.snapshot(excluded_objects=['part_1'], current_object='part_2')
        s = self.resume()
        self.assertIn('EXCLUDE_OBJECT NAME=part_1', s)
        self.assertIn('EXCLUDE_OBJECT_START NAME=part_2', s)

    def test_a_relative_extruder_is_restored(self):
        self.pr.pending = self.snapshot(absolute_extrude=False, absolute_coordinates=False)
        s = self.resume()
        self.assertIn('M83', s.splitlines())
        self.assertIn('G91', s.splitlines())

    def test_the_fan_is_restored_only_when_the_printer_has_one(self):
        self.printer.objects.pop('fan')
        self.printer.objects['fan'] = FakeStatusObject({})
        self.assertIn('M106 S128', self.resume())

    def test_resuming_forgets_the_pending_print_and_records_again(self):
        self.resume()
        self.assertIsNone(self.pr.pending)
        self.assertTrue(self.pr.recording)
        self.assertFalse(self.pr.resuming)
        self.assertIn('action:prompt_end', self.gcode.info)
        self.assertIn('Print recovery: resuming', self.gcode.raw)

    def test_the_bed_is_not_heated_when_the_snapshot_had_it_off(self):
        self.pr.pending = self.snapshot(bed_target=0.)
        s = self.resume()
        self.assertNotIn('M140', s)
        self.assertNotIn('M190', s)

    def test_the_nozzle_is_not_heated_when_the_snapshot_had_it_off(self):
        self.pr.pending = self.snapshot(extruder_target=0.)
        s = self.resume()
        self.assertNotIn('M104', s)
        self.assertNotIn('M109', s)


if __name__ == '__main__':
    unittest.main()
