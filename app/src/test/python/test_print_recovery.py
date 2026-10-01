"""Unit tests for the print_recovery Klipper add-on (no Klipper needed).

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import importlib.util
import os
import unittest

MODULE = os.path.join(os.path.dirname(__file__), '..', '..', 'main',
                      'klipper_print_recovery', 'print_recovery.py')
spec = importlib.util.spec_from_file_location('print_recovery', MODULE)
print_recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(print_recovery)


class FakeGcode:
    def __init__(self):
        self.commands = {}
        self.gcode_handlers = {}

    def register_command(self, name, func, desc=None):
        self.commands[name] = func


class FakePrinter:
    def __init__(self):
        self.gcode = FakeGcode()

    def get_reactor(self):
        return object()

    def lookup_object(self, name):
        return self.gcode

    def register_event_handler(self, event, callback):
        pass

    def get_start_args(self):
        return {'config_file': '/data/config/printer.cfg'}


class FakeConfig:
    """Mimics the parts of Klipper's ConfigWrapper the add-on uses."""

    TRUE = ('1', 'true', 'yes', 'on')

    def __init__(self, **values):
        self.values = values
        self.printer = FakePrinter()

    def get_printer(self):
        return self.printer

    def get(self, name, default=None):
        return self.values.get(name, default)

    def getfloat(self, name, default=None, **limits):
        if name not in self.values:
            return default
        return float(self.values[name])

    def getboolean(self, name, default=None):
        if name not in self.values:
            return default
        return str(self.values[name]).lower() in self.TRUE


def make(**values):
    return print_recovery.PrintRecovery(FakeConfig(**values))


class Defaults(unittest.TestCase):
    def test_documented_defaults(self):
        pr = make()
        self.assertEqual(pr.interval, 2.)
        self.assertTrue(pr.park_enable_x)
        self.assertTrue(pr.park_enable_y)
        self.assertEqual(pr.park_speed, 100.)
        self.assertEqual(pr.lift_z, 10.)
        self.assertEqual(pr.discard_lift_z, 50.)
        self.assertTrue(pr.discard_home_x)
        self.assertTrue(pr.discard_home_y)
        self.assertTrue(pr.purge)
        self.assertEqual(pr.purge_length, 20.)
        self.assertEqual(pr.purge_speed, 5.)
        self.assertEqual(pr.purge_retract, 2.)
        self.assertEqual(pr.min_extruded, 5.)
        self.assertTrue(pr.prompt)
        self.assertEqual(pr.prompt_repeat, 60.)

    def test_park_position_is_unset_when_the_line_is_left_out(self):
        pr = make()
        self.assertIsNone(pr.park_x)
        self.assertIsNone(pr.park_y)

    def test_state_file_defaults_next_to_printer_cfg(self):
        pr = make()
        self.assertEqual(pr.state_file,
                         os.path.join('/data/config', '.print_recovery.json'))

    def test_state_file_can_be_set(self):
        pr = make(state_file='/tmp/recovery.json')
        self.assertEqual(pr.state_file, '/tmp/recovery.json')

    def test_registers_the_three_commands(self):
        pr = make()
        self.assertEqual(sorted(pr.gcode.commands),
                         ['PRINT_RECOVERY_DISCARD', 'PRINT_RECOVERY_RESUME',
                          'PRINT_RECOVERY_STATUS'])


class Values(unittest.TestCase):
    def test_numbers_and_booleans_are_read(self):
        pr = make(snapshot_interval='0.5', park_enable_x='False', park_x='-6',
                  park_y='10', lift_z='5', purge='no', purge_length='10')
        self.assertEqual(pr.interval, 0.5)
        self.assertFalse(pr.park_enable_x)
        self.assertEqual(pr.park_x, -6.)
        self.assertEqual(pr.park_y, 10.)
        self.assertEqual(pr.lift_z, 5.)
        self.assertFalse(pr.purge)
        self.assertEqual(pr.purge_length, 10.)

    def test_sample_period_is_half_the_interval(self):
        self.assertEqual(make(snapshot_interval='0.5').period, 0.25)

    def test_sample_period_is_capped_for_long_intervals(self):
        self.assertEqual(make(snapshot_interval='300').period,
                         print_recovery.MAX_SAMPLE_PERIOD)

    def test_sample_period_has_a_floor_for_short_intervals(self):
        pr = make(snapshot_interval='0.1')
        self.assertEqual(pr.period, print_recovery.MIN_SAMPLE_PERIOD)


class MacroVariables(unittest.TestCase):
    def test_star_keeps_every_macro(self):
        pr = make(macro_variables='*')
        self.assertTrue(pr.macro_all)
        self.assertEqual(pr.macro_names, set())

    def test_default_is_star(self):
        self.assertTrue(make().macro_all)

    def test_none_keeps_no_macro(self):
        pr = make(macro_variables='none')
        self.assertFalse(pr.macro_all)
        self.assertEqual(pr.macro_names, set())

    def test_none_is_case_insensitive(self):
        pr = make(macro_variables='None')
        self.assertFalse(pr.macro_all)
        self.assertEqual(pr.macro_names, set())

    def test_empty_still_means_none(self):
        pr = make(macro_variables='')
        self.assertFalse(pr.macro_all)
        self.assertEqual(pr.macro_names, set())

    def test_list_is_upper_cased_and_trimmed(self):
        pr = make(macro_variables='print_start,  My_Macro ,')
        self.assertFalse(pr.macro_all)
        self.assertEqual(pr.macro_names, {'PRINT_START', 'MY_MACRO'})


class Gcmd:
    def __init__(self, **params):
        self.params = params

    def get(self, name, default=None):
        return self.params.get(name, default)


class GcodeBooleans(unittest.TestCase):
    get_bool = staticmethod(print_recovery.PrintRecovery._get_bool)

    def test_missing_parameter_uses_the_default(self):
        self.assertTrue(self.get_bool(Gcmd(), 'PURGE', True))
        self.assertFalse(self.get_bool(Gcmd(), 'PURGE', False))

    def test_accepted_true_spellings(self):
        for raw in ('1', 'true', 'True', 'YES', 'on', ' On '):
            self.assertTrue(self.get_bool(Gcmd(PURGE=raw), 'PURGE', False), raw)

    def test_accepted_false_spellings(self):
        for raw in ('0', 'false', 'False', 'no', 'off'):
            self.assertFalse(self.get_bool(Gcmd(PURGE=raw), 'PURGE', True), raw)


if __name__ == '__main__':
    unittest.main()
