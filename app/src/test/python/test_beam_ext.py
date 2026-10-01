"""Unit tests for the beam_beeper/beam_camera Klipper extras.

They live under app/src/main/klipper_beam_ext/ (the single canonical
source), and a build copies them verbatim into klippy/extras/ for both
Klipper and Kalico — that location matters: Klipper's config loader resolves
a [beam_beeper]/[beam_camera] section via
importlib.import_module('extras.' + name), so anywhere else the section
would fail with "not a valid config section" regardless of what the module
itself does.

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import importlib.util
import logging
import os
import tempfile
import unittest
from unittest import mock

SOURCE = os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'klipper_beam_ext')
ASSETS = os.path.join(os.path.dirname(__file__), '..', '..', 'main', 'assets')


def load(name):
    path = os.path.join(SOURCE, name + '.py')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FakeGcode:
    def __init__(self):
        self.commands = {}

    def register_command(self, name, func, desc=None):
        self.commands[name] = (func, desc)


class FakePrinter:
    def __init__(self):
        self.gcode = FakeGcode()

    def lookup_object(self, name):
        return self.gcode


class FakeConfig:
    def __init__(self):
        self.printer = FakePrinter()

    def get_printer(self):
        return self.printer


class Gcmd:
    def __init__(self, **params):
        self.params = params

    def get(self, name, default=None):
        return self.params[name] if name in self.params else default

    def get_int(self, name):
        return int(self.params[name])

    def get_float(self, name):
        return float(self.params[name])


class FakeResponse:
    def read(self):
        return b'{"ok": true}'

    def close(self):
        pass


class Extras(unittest.TestCase):
    def urls(self, module_name, command, port_file=None, **params):
        module = load(module_name)
        if port_file is not None:
            module.WEB_PORT_FILE = port_file
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands[command][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               return_value=FakeResponse()) as urlopen:
            func(Gcmd(**params))
        return [c.args[0] for c in urlopen.call_args_list]

    @staticmethod
    def port_of(url):
        return int(url.split('//')[1].split('/')[0].split(':')[1])


class Beeper(Extras):
    def test_registers_play_tone(self):
        extra = load('beam_beeper').load_config(FakeConfig())
        self.assertEqual(list(extra.gcode.commands), ['PLAY_TONE'])

    def test_play_tone_sends_duration_and_frequency(self):
        urls = self.urls('beam_beeper', 'PLAY_TONE', DURATION='250', FREQUENCY='440')
        self.assertEqual(len(urls), 1)
        self.assertIn('/beam/play_tone?', urls[0])
        self.assertIn('duration=250', urls[0])
        self.assertIn('frequency=440', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('beam_beeper')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['PLAY_TONE'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(DURATION='1', FREQUENCY='1'))


class Camera(Extras):
    def test_registers_flashlight_and_focus(self):
        extra = load('beam_camera').load_config(FakeConfig())
        self.assertEqual(sorted(extra.gcode.commands),
                         ['SET_CAMERA_FLASHLIGHT', 'SET_CAMERA_FOCUS'])

    def test_flashlight_passes_the_state_through(self):
        urls = self.urls('beam_camera', 'SET_CAMERA_FLASHLIGHT', ENABLED='true')
        self.assertEqual(len(urls), 1)
        self.assertIn('/beam/set_camera_flashlight?enabled=true', urls[0])

    def test_autofocus_ignores_the_distance(self):
        urls = self.urls('beam_camera', 'SET_CAMERA_FOCUS', AUTOFOCUS='True')
        self.assertIn('autofocus=true', urls[0])
        self.assertIn('focus=0', urls[0])

    def test_manual_focus_sends_the_distance(self):
        urls = self.urls('beam_camera', 'SET_CAMERA_FOCUS',
                         AUTOFOCUS='false', FOCUS_DISTANCE='2.5')
        self.assertIn('autofocus=false', urls[0])
        self.assertIn('focus=2.5', urls[0])

    def test_a_failed_request_is_logged_not_raised(self):
        module = load('beam_camera')
        extra = module.load_config(FakeConfig())
        func = extra.gcode.commands['SET_CAMERA_FLASHLIGHT'][0]
        with mock.patch.object(module.urllib.request, 'urlopen',
                               side_effect=OSError('refused')):
            with self.assertLogs(level=logging.ERROR):
                func(Gcmd(ENABLED='true'))


class ConfigSectionLoading(unittest.TestCase):
    """Covers the bug found while verifying the port fix end to end: on a
    real device, [beam_beeper]/[beam_camera] failed with "Section ... is not
    a valid config section" because the files lived under beam_ext/ (a bare
    sys.path entry) instead of klippy/extras/ — Klipper only resolves a
    section to extras.<name> via importlib.import_module('extras.' + name).
    """
    def test_the_extras_are_placed_where_klipper_s_loader_looks(self):
        # klipper_bs.py adds "<root>/klippy" to sys.path, so "extras" resolves
        # to <root>/klippy/extras — the extras must land inside that package
        # for `importlib.import_module('extras.beam_beeper')` to succeed.
        for engine in ('klipper', 'kalico'):
            for name in ('beam_beeper', 'beam_camera'):
                path = os.path.join(ASSETS, engine, 'klippy', 'extras', name + '.py')
                if engine == 'klipper' and not os.path.exists(path):
                    # gitignored/generated only by a Gradle build; kalico's
                    # copy below is tracked and always present.
                    continue
                self.assertTrue(os.path.exists(path), path)

    def test_they_are_no_longer_under_beam_ext(self):
        for engine in ('klipper', 'kalico'):
            for name in ('beam_beeper.py', 'beam_camera.py'):
                self.assertFalse(
                    os.path.exists(os.path.join(ASSETS, engine, 'beam_ext', name)))

    def test_kalico_s_tracked_copy_matches_the_canonical_source(self):
        for name in ('beam_beeper.py', 'beam_camera.py'):
            kalico_copy = os.path.join(ASSETS, 'kalico', 'klippy', 'extras', name)
            with open(os.path.join(SOURCE, name)) as f:
                source_text = f.read()
            with open(kalico_copy) as f:
                kalico_text = f.read()
            self.assertEqual(source_text, kalico_text, name)


class WebPort(Extras):
    """The original bug this whole file covers: the extras used to hardcode
    port 8888, while the app's web server listens on 4408/4409/4410
    depending on the selected front end, so PLAY_TONE and SET_CAMERA_*
    silently did nothing. They now read the port from a file (WEB_PORT_FILE)
    WebService keeps updated with the real port.
    """
    served_ports = {4408, 4409, 4410}

    def test_the_old_wrong_port_is_gone(self):
        for name in ('beam_beeper', 'beam_camera'):
            self.assertNotIn('8888', open(load(name).__file__).read())

    def test_the_placeholder_is_still_present_for_bundleinstaller_to_patch(self):
        for name in ('beam_beeper', 'beam_camera'):
            text = open(load(name).__file__).read()
            self.assertIn('${WEB_PORT_FILE}', text)

    def test_without_a_real_port_file_it_falls_back_to_mainsail_s_port(self):
        # Loaded straight from source, WEB_PORT_FILE is the literal,
        # unpatched placeholder text (BundleInstaller only substitutes it in
        # the app's own unpacked copy) — opening it fails, so the default
        # applies, same as a fresh install before the app has served anything.
        for module_name, command, params in (
                ('beam_beeper', 'PLAY_TONE', dict(DURATION='1', FREQUENCY='1')),
                ('beam_camera', 'SET_CAMERA_FLASHLIGHT', dict(ENABLED='true'))):
            url = self.urls(module_name, command, **params)[0]
            self.assertEqual(4409, self.port_of(url))

    def test_it_reads_whichever_port_the_app_is_really_serving_on(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            for served_port in (4408, 4409, 4410):
                with open(port_file, 'w') as f:
                    f.write(str(served_port))
                for module_name, command, params in (
                        ('beam_beeper', 'PLAY_TONE', dict(DURATION='1', FREQUENCY='1')),
                        ('beam_camera', 'SET_CAMERA_FOCUS', dict(AUTOFOCUS='true'))):
                    url = self.urls(module_name, command, port_file=port_file, **params)[0]
                    self.assertEqual(served_port, self.port_of(url))

    def test_surrounding_whitespace_in_the_port_file_is_tolerated(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            with open(port_file, 'w') as f:
                f.write('  4410\n')
            url = self.urls('beam_beeper', 'PLAY_TONE', port_file=port_file,
                            DURATION='1', FREQUENCY='1')[0]
            self.assertEqual(4410, self.port_of(url))

    def test_a_garbage_port_file_falls_back_to_mainsail_s_port(self):
        with tempfile.TemporaryDirectory() as tmp:
            port_file = os.path.join(tmp, 'web_port')
            with open(port_file, 'w') as f:
                f.write('not-a-port')
            url = self.urls('beam_beeper', 'PLAY_TONE', port_file=port_file,
                            DURATION='1', FREQUENCY='1')[0]
            self.assertEqual(4409, self.port_of(url))

    def test_the_resolved_port_is_always_one_the_app_actually_serves(self):
        for served_port in (None,) + tuple(self.served_ports):
            with tempfile.TemporaryDirectory() as tmp:
                port_file = os.path.join(tmp, 'web_port')
                kwargs = {}
                if served_port is not None:
                    with open(port_file, 'w') as f:
                        f.write(str(served_port))
                    kwargs['port_file'] = port_file
                url = self.urls('beam_beeper', 'PLAY_TONE',
                                DURATION='1', FREQUENCY='1', **kwargs)[0]
                self.assertIn(self.port_of(url), self.served_ports)


if __name__ == '__main__':
    unittest.main()
