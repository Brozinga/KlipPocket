#!/usr/bin/env python3
"""Rebuild app/src/test/python/hh_wizard_golden.json from Happy Hare's own installer.

The wizard engine (app/src/main/hh_wizard) must write the same mmu/ files as
`install.sh`. This script runs the real installer in its test mode for each
profile below and stores a hash of every file it wrote, so the unit tests can
check the engine against it without needing make, a shell or the installer.

Run it again after updating the bundled Happy Hare:

    python3 scripts/hh_wizard_golden.py --venv /path/to/venv/with/jinja2

Needs: a POSIX shell, make, and a virtualenv whose python has jinja2.
Writes only to /tmp/mmu_test (the installer's test mode) and a temp copy.
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HH = os.path.join(REPO, 'app', 'src', 'main', 'happyhare')
GOLDEN = os.path.join(REPO, 'app', 'src', 'test', 'python',
                      'hh_wizard_golden.json')
TEST_DIR = '/tmp/mmu_test'
OFFICIAL_CFG = TEST_DIR + '/printer_data/config'

PROFILES = {
    'tradrack': {'MMU_TYPE_TRADRACK_1_0': True},
    'box_turtle': {'MMU_TYPE_BOX_TURTLE_1_0': True},
    '3ms': {'MMU_TYPE_3MS_1_0': True, 'BOARD_TYPE_MMB_2_0': True,
            'PIN_EXTRUDER_SENSOR': 'mcu:PA7'},
    'emu': {'MMU_TYPE_EMU_1_0': True},
    'vvd': {'MMU_TYPE_VVD_1_0': True},
    'night_owl': {'MMU_TYPE_NIGHT_OWL_1_0': True},
    'ercf': {'MMU_FAMILY_ERCF': True},
    'tradrack_renamed': {'MMU_TYPE_TRADRACK_1_0': True,
                         'PARAM_DISPLAY_NAME': 'My Tradrack'},
}


def digest(text):
    text = text.replace(OFFICIAL_CFG, '@CFG@').replace(TEST_DIR, '@CFG@')
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def tree_hashes(root):
    out = {}
    for base, _, names in os.walk(root):
        for name in sorted(names):
            path = os.path.join(base, name)
            rel = os.path.relpath(path, root)
            if rel.endswith('.pickle') or '.old-' in rel \
               or rel == '.mmu_config':
                continue
            with open(path, encoding='utf-8', errors='replace') as f:
                out[rel] = digest(f.read())
    return dict(sorted(out.items()))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--venv', required=True,
                    help='virtualenv with jinja2 (its bin/python is used)')
    args = ap.parse_args()
    sys.path.insert(0, os.path.join(REPO, 'app', 'src', 'main', 'hh_wizard'))
    import hh_wizard as hw

    work = tempfile.mkdtemp(prefix='hh-golden-')
    try:
        copy = os.path.join(work, 'hh')
        shutil.copytree(HH, copy, ignore=shutil.ignore_patterns(
            'test', '.git', 'venv', '__pycache__'))
        os.symlink(os.path.abspath(args.venv), os.path.join(copy, 'venv'))
        golden = {'happy_hare': None, 'profiles': {}}

        class OfficialPaths(hw.Wizard):
            def _env(self, kconfig_path=None):
                env = super()._env(kconfig_path)
                env.update(KLIPPER_HOME=TEST_DIR + '/klipper',
                           CONFIG_KLIPPER_HOME=TEST_DIR + '/klipper',
                           CONFIG_MOONRAKER_HOME=TEST_DIR + '/moonraker')
                return env

        for name, symbols in PROFILES.items():
            shutil.rmtree(TEST_DIR, ignore_errors=True)
            os.makedirs(TEST_DIR)
            w = OfficialPaths(HH, OFFICIAL_CFG)
            w.reset(load_saved=False)
            for sym, value in symbols.items():
                w.set_value(sym, value)
            with hw._environ(w._env(TEST_DIR + '/.mmu_config')):
                w.kc.write_config(TEST_DIR + '/.mmu_config')
            golden['happy_hare'] = w.version
            run = subprocess.run('yes | ./install.sh -t -y -z -s', shell=True,
                                 cwd=copy, capture_output=True, text=True,
                                 env=dict(os.environ,
                                          PYTHONDONTWRITEBYTECODE='1'))
            mmu = OFFICIAL_CFG + '/mmu'
            if not os.path.isdir(mmu + '/base'):
                sys.exit('install.sh failed for %s:\n%s%s' % (
                    name, run.stdout[-1500:], run.stderr[-800:]))
            golden['profiles'][name] = {'symbols': symbols,
                                        'files': tree_hashes(mmu)}
            print('%-18s %d files' % (name,
                                      len(golden['profiles'][name]['files'])))
        with open(GOLDEN, 'w', encoding='utf-8') as f:
            json.dump(golden, f, indent=1, sort_keys=True)
            f.write('\n')
        print('wrote', GOLDEN)
    finally:
        shutil.rmtree(work, ignore_errors=True)
        shutil.rmtree(TEST_DIR, ignore_errors=True)


if __name__ == '__main__':
    main()
