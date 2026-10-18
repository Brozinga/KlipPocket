"""Z Calibration must import under both engines' layouts.

Klipper puts klippy/ on sys.path, so `mcu` is a top-level module. Kalico
makes klippy a package, where a top-level `import mcu` has no parent package
and fails. The mod tries the package-relative import first and falls back.

Run from the repository root:
    python3 -m unittest discover -s app/src/test/python -v
"""
import importlib.util
import os
import sys
import types
import unittest

SOURCE = os.path.join(os.path.dirname(__file__), '..', '..', 'main',
                      'klipper_z_calibration', 'z_calibration.py')


class MCU_endstop:
    pass


def load(name, package):
    spec = importlib.util.spec_from_file_location(
        name, SOURCE, submodule_search_locations=None)
    module = importlib.util.module_from_spec(spec)
    module.__package__ = package
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def package(name):
    module = types.ModuleType(name)
    module.__path__ = []
    sys.modules[name] = module
    return module


class ZCalibrationImportTest(unittest.TestCase):
    def setUp(self):
        self.saved = dict(sys.modules)
        self.addCleanup(self.restore)

    def restore(self):
        for key in list(sys.modules):
            if key not in self.saved:
                del sys.modules[key]
        sys.modules.update(self.saved)

    def test_kalico_layout_uses_the_package_mcu(self):
        package('kalico_klippy')
        package('kalico_klippy.extras')
        mcu = types.ModuleType('kalico_klippy.mcu')
        mcu.MCU_endstop = MCU_endstop
        sys.modules['kalico_klippy.mcu'] = mcu
        module = load('kalico_klippy.extras.z_calibration', 'kalico_klippy.extras')
        self.assertIs(module.MCU_endstop, MCU_endstop)

    def test_klipper_layout_falls_back_to_the_top_level_mcu(self):
        package('extras')
        mcu = types.ModuleType('mcu')
        mcu.MCU_endstop = MCU_endstop
        sys.modules['mcu'] = mcu
        module = load('extras.z_calibration', 'extras')
        self.assertIs(module.MCU_endstop, MCU_endstop)


if __name__ == '__main__':
    unittest.main()
