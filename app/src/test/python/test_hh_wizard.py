"""Tests of the Happy Hare setup wizard engine (app/src/main/hh_wizard).

Needs jinja2 (the Happy Hare builder uses it); the tests are skipped without it.

    python3 -m unittest discover -s app/src/test/python -p 'test_hh_wizard.py'

The files the engine writes are compared with the ones Happy Hare's own
install.sh wrote for the same answers (hashes in hh_wizard_golden.json, rebuilt
by scripts/hh_wizard_golden.py), so the wizard cannot drift from the installer.
"""
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import unittest

try:
    import jinja2  # noqa: F401
except ImportError:
    jinja2 = None

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, '..', '..', 'main')
sys.path.insert(0, os.path.join(MAIN, 'hh_wizard'))
HH_ROOT = os.path.join(MAIN, 'happyhare')
GOLDEN = os.path.join(HERE, 'hh_wizard_golden.json')

if jinja2 is not None:
    sys.dont_write_bytecode = True
    import hh_wizard as hw


def tree(root):
    out = {}
    for base, _, names in os.walk(root):
        for n in names:
            p = os.path.join(base, n)
            out[os.path.relpath(p, root)] = p
    return out


def slurp(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return f.read()


def digest(path, config_dir):
    with open(path, encoding='utf-8', errors='replace') as f:
        text = f.read()
    text = text.replace(os.path.realpath(config_dir), '@CFG@') \
               .replace(config_dir, '@CFG@')
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


@unittest.skipIf(jinja2 is None, 'jinja2 is not installed')
class Base(unittest.TestCase):
    PRINTER = '[mcu]\nserial: /dev/null\n\n[printer]\nkinematics: none\n'
    MOONRAKER = '[server]\nport: 7125\n'

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix='hh-wizard-test-')
        self.addCleanup(shutil.rmtree, self.dir, True)
        self.write('printer.cfg', self.PRINTER)
        self.write('moonraker.conf', self.MOONRAKER)

    def write(self, name, text):
        with open(os.path.join(self.dir, name), 'w') as f:
            f.write(text)

    def read(self, name):
        with open(os.path.join(self.dir, name)) as f:
            return f.read()

    def wizard(self, symbols=None, load_saved=False):
        w = hw.Wizard(HH_ROOT, self.dir)
        w.reset(load_saved=load_saved)
        for sym, value in (symbols or {}).items():
            w.set_value(sym, value)
        return w

    TRADRACK = {'MMU_TYPE_TRADRACK_1_0': True}
    BOX_TURTLE = {'MMU_TYPE_BOX_TURTLE_1_0': True}


class MatchesTheInstaller(Base):
    def test_every_profile_writes_the_files_install_sh_writes(self):
        with open(GOLDEN) as f:
            golden = json.load(f)
        self.assertEqual(golden['happy_hare'], self.wizard().version,
                         'Happy Hare was updated: rerun '
                         'scripts/hh_wizard_golden.py')
        for name, profile in golden['profiles'].items():
            with self.subTest(profile=name):
                shutil.rmtree(os.path.join(self.dir, 'mmu'), True)
                w = self.wizard(profile['symbols'])
                w.generate()
                made = tree(os.path.join(self.dir, 'mmu'))
                made.pop('.mmu_config')
                made = {k: v for k, v in made.items()
                        if not k.endswith('.pickle')}
                self.assertEqual(sorted(made), sorted(profile['files']))
                for rel, path in made.items():
                    self.assertEqual(digest(path, self.dir),
                                     profile['files'][rel], rel)


class Questions(Base):
    def test_the_catalog_lists_every_mmu_type_and_each_can_be_picked(self):
        w = self.wizard()
        types = w.mmu_types()
        self.assertGreaterEqual(len(types), 15)
        names = {t['symbol'] for t in types}
        self.assertTrue({'MMU_TYPE_TRADRACK_1_0', 'MMU_TYPE_BOX_TURTLE_1_0',
                         'MMU_TYPE_3MS_1_0'} <= names)
        for t in types:
            with self.subTest(mmu=t['symbol']):
                w.set_value(t['symbol'], True)
                self.assertEqual(w.current_type(), t['symbol'])

    def test_the_top_page_starts_with_the_mmu_and_board_questions(self):
        page = self.wizard().page()
        kinds = [(i['kind'], i['title']) for i in page['items']]
        self.assertIn(('choice', 'MMU Type'), kinds)
        self.assertIn(('choice', 'Board type'), kinds)
        self.assertIn(('menu', 'MCU connection'), kinds)

    def test_the_menus_that_hold_paths_and_services_are_not_asked(self):
        w = self.wizard(self.TRADRACK)
        seen = []

        def walk(path):
            page = w.page(path)
            for item in page['items']:
                seen.append(item)
                if item['kind'] == 'menu' and item['enabled']:
                    walk(item['path'])
        walk('')
        symbols = {i.get('symbol') for i in seen}
        self.assertFalse(symbols & set(hw.HIDDEN_SYMBOLS))
        self.assertNotIn('Paths & Services', [i['title'] for i in seen])
        self.assertGreater(len(seen), 200)

    def test_every_page_of_every_visible_menu_can_be_built(self):
        # Odd symbol kinds (arrays, floats, boolints...) must not break a page
        w = self.wizard(self.BOX_TURTLE)
        kinds = set()

        def walk(path):
            for item in w.page(path)['items']:
                kinds.add(item['kind'])
                json.dumps(item)
                if item['kind'] == 'menu' and item['enabled']:
                    walk(item['path'])
        walk('')
        self.assertTrue({'bool', 'choice', 'menu', 'text', 'int',
                         'float'} <= kinds, kinds)

    def test_the_answers_decide_which_questions_exist(self):
        def questions(symbols):
            w = self.wizard(symbols)
            found = set()

            def walk(path):
                for item in w.page(path)['items']:
                    if item.get('symbol'):
                        found.add(item['symbol'])
                    if item['kind'] == 'menu' and item['enabled']:
                        walk(item['path'])
            walk('')
            return found
        tradrack = questions(self.TRADRACK)
        turtle = questions(self.BOX_TURTLE)
        self.assertNotEqual(tradrack, turtle)
        self.assertTrue(tradrack - turtle and turtle - tradrack)

    def test_a_choice_reports_its_options_and_the_selected_one(self):
        w = self.wizard(self.TRADRACK)
        mmu = [i for i in w.page()['items'] if i['title'] == 'MMU Type'][0]
        self.assertEqual(mmu['value'], 'MMU_TYPE_TRADRACK_1_0')
        self.assertIn('MMU_TYPE_TRADRACK_1_0',
                      [o['symbol'] for o in mmu['options']])
        self.assertFalse(mmu['default'])

    def test_setting_and_unsetting_a_value(self):
        w = self.wizard(self.TRADRACK)
        sym = w.kc.syms['PARAM_DISPLAY_NAME']
        w.set_value('PARAM_DISPLAY_NAME', 'My MMU')
        self.assertEqual(sym.str_value, 'My MMU')
        self.assertIn('PARAM_DISPLAY_NAME', w.values())
        w.unset_value('PARAM_DISPLAY_NAME')
        self.assertNotIn('PARAM_DISPLAY_NAME', w.values())

    def test_bad_answers_are_refused(self):
        w = self.wizard(self.TRADRACK)
        with self.assertRaises(hw.WizardError):
            w.set_value('NOT_A_SYMBOL', 1)
        with self.assertRaises(hw.WizardError):
            w.unset_value('NOT_A_SYMBOL')
        numeric = [n for n, s in w.kc.syms.items()
                   if s.orig_type == w.kl.INT and s.visibility][:1]
        if numeric:
            with self.assertRaises(hw.WizardError):
                w.set_value(numeric[0], 'abc')

    def test_the_markup_of_the_text_menu_is_removed(self):
        self.assertEqual(hw.clean('a [[B]]b[[/B]] [[C:3]]c[[RESET]]'),
                         'a b c')
        w = self.wizard(self.TRADRACK)
        text = json.dumps(w.page())
        self.assertNotRegex(text, r'\[\[(?:B|DIM|REV|U|C:|VALUE)')


    def test_what_a_choice_asks_after_it_is_listed_under_it(self):
        w = self.wizard(self.BOX_TURTLE)
        top = w.page()['items']
        mmu = [i for i, it in enumerate(top) if it['title'] == 'MMU Type'][0]
        below = top[mmu + 1]
        self.assertEqual(below['kind'], 'choice')
        self.assertGreater(below['indent'], top[mmu]['indent'])
        menu = [i for i in top if i['title'] == 'MCU connection'][0]
        items = w.page(menu['path'])['items']
        path = [i for i in items if i.get('symbol') == 'MMU_SERIAL_DEVICE_OTHER']
        self.assertEqual(len(path), 1)
        w.set_value('MMU_SERIAL_DEVICE_OTHER', '/data/serial/abc')
        again = w.page(menu['path'])['items']
        self.assertEqual([i['value'] for i in again
                          if i.get('symbol') == 'MMU_SERIAL_DEVICE_OTHER'],
                         ['/data/serial/abc'])

    def test_the_installers_ascii_hare_is_not_listed(self):
        items = self.wizard(self.TRADRACK).page()['items']
        titles = [i['title'] for i in items]
        self.assertFalse([t for t in titles if '(\\_/)' in t or '*,*' in t])
        self.assertTrue(any(t.startswith('Configuration') for t in titles))
        self.assertFalse(any(t.startswith('(")_(")') for t in titles))


class Incomplete(Base):
    def test_a_type_without_a_board_is_reported_and_not_written(self):
        w = self.wizard({'MMU_TYPE_3D_CHAMELEON_1_0': True})
        review = w.review()
        self.assertTrue(any('step_pin' in p for p in review['problems']))
        with self.assertRaises(hw.WizardError) as ctx:
            w.generate()
        self.assertTrue(ctx.exception.problems)
        self.assertFalse(os.path.exists(os.path.join(self.dir, 'mmu')))

    def test_the_answers_that_complete_it_are_accepted(self):
        w = self.wizard({'MMU_TYPE_3D_CHAMELEON_1_0': True,
                         'BOARD_TYPE_MMB_2_0': True,
                         'MMU_HAS_SENSOR_SHARED_EXIT': True,
                         'PIN_SHARED_EXIT_SENSOR': '^unit0:PC7'})
        self.assertEqual(w.review()['problems'], [])

    def test_a_complete_type_has_no_problems(self):
        self.assertEqual(self.wizard(self.TRADRACK).review()['problems'], [])

    def test_the_checks_only_flag_what_cannot_work(self):
        out = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, out, True)
        os.makedirs(os.path.join(out, 'base'))
        with open(os.path.join(out, 'base', 'mmu_hardware_unit0.cfg'),
                  'w') as f:
            f.write('# step_pin:\nstep_pin: unit0:PA1\ndir_pin:\n'
                    'x_pin: ^:PD5\nname: [[X]]\n')
        with open(os.path.join(out, 'base', 'mmu.cfg'), 'w') as f:
            f.write('extruder_switch_pin:\n')
        found = self.wizard().find_problems(out)
        self.assertEqual(len(found), 3, found)
        self.assertFalse(any('mmu.cfg' in p for p in found))


class Generate(Base):
    def test_the_vars_file_path_points_into_the_printer_config(self):
        self.wizard(self.TRADRACK).generate()
        found = False
        base = os.path.join(self.dir, 'mmu', 'base')
        for name in os.listdir(base):
            with open(os.path.join(base, name)) as f:
                if os.path.join(self.dir, 'mmu', 'mmu_vars.cfg') in f.read():
                    found = True
        self.assertTrue(found)

    def test_the_includes_are_added_once_with_a_backup(self):
        w = self.wizard(self.TRADRACK)
        result = w.generate(include_in_printer=True)
        self.assertTrue(result['added'])
        printer = self.read('printer.cfg')
        self.assertEqual(printer.count('[include mmu/base/*.cfg]'), 1)
        self.assertEqual(printer.count('[include mmu/macros/*.cfg]'), 1)
        self.assertIn('serial: /dev/null', printer)
        w.generate(include_in_printer=True)
        printer = self.read('printer.cfg')
        self.assertEqual(printer.count('[include mmu/base/*.cfg]'), 1)
        baks = [n for n in os.listdir(self.dir) if n.startswith('printer.cfg.')]
        self.assertTrue(baks)

    def test_moonraker_gets_mmu_server_and_no_update_manager(self):
        self.wizard(self.TRADRACK).generate(include_in_printer=True)
        text = self.read('moonraker.conf')
        self.assertIn('[mmu_server]', text)
        self.assertIn('enable_file_preprocessor: True', text)
        self.assertNotIn('update_manager', text)
        self.assertIn('port: 7125', text)

    def test_declining_leaves_printer_cfg_and_moonraker_untouched(self):
        result = self.wizard(self.TRADRACK).generate()
        self.assertFalse(result['added'])
        self.assertEqual(self.read('printer.cfg'), self.PRINTER)
        self.assertEqual(self.read('moonraker.conf'), self.MOONRAKER)
        self.assertIn('[include mmu/base/*.cfg]', result['includes'])
        self.assertIn('[mmu_server]', result['moonraker'])

    def test_a_printer_without_moonraker_conf_still_works(self):
        os.remove(os.path.join(self.dir, 'moonraker.conf'))
        self.wizard(self.TRADRACK).generate(include_in_printer=True)
        self.assertIn('[include mmu/base/*.cfg]', self.read('printer.cfg'))

    def test_changing_the_mmu_replaces_everything(self):
        self.wizard(self.TRADRACK).generate()
        stray = os.path.join(self.dir, 'mmu', 'old.cfg')
        with open(stray, 'w') as f:
            f.write('# a hand edit')
        edited = os.path.join(self.dir, 'mmu', 'macros', 'mmu_purge.cfg')
        with open(edited, 'a') as f:
            f.write('\n# hand edit\n')
        self.wizard(self.BOX_TURTLE).generate()
        self.assertFalse(os.path.exists(stray))
        with open(edited) as f:
            self.assertNotIn('hand edit', f.read())
        self.assertEqual(self.wizard(load_saved=True).current_type(),
                         'MMU_TYPE_BOX_TURTLE_1_0')

    def test_calibration_is_reset_unless_asked_to_keep_it(self):
        self.wizard(self.TRADRACK).generate()
        vars_cfg = os.path.join(self.dir, 'mmu', 'mmu_vars.cfg')
        with open(vars_cfg, 'a') as f:
            f.write('\n# my calibration\n')
        self.wizard(self.TRADRACK).generate(keep_state=True)
        with open(vars_cfg) as f:
            self.assertIn('my calibration', f.read())
        self.wizard(self.TRADRACK).generate()
        with open(vars_cfg) as f:
            self.assertNotIn('my calibration', f.read())

    def test_a_failure_leaves_the_old_folder_as_it_was(self):
        self.wizard(self.TRADRACK).generate()
        before = {k: slurp(v) for k, v in
                  tree(os.path.join(self.dir, 'mmu')).items()}
        w = self.wizard({'MMU_TYPE_3D_CHAMELEON_1_0': True})
        with self.assertRaises(hw.WizardError):
            w.generate()
        after = {k: slurp(v) for k, v in
                 tree(os.path.join(self.dir, 'mmu')).items()}
        self.assertEqual(before, after)
        self.assertFalse(os.path.exists(os.path.join(self.dir, '.mmu.new')))

    def test_the_saved_answers_come_back_when_reopened(self):
        self.wizard(dict(self.TRADRACK, PARAM_DISPLAY_NAME='Shop MMU')) \
            .generate()
        w = self.wizard(load_saved=True)
        self.assertEqual(w.kc.syms['PARAM_DISPLAY_NAME'].str_value,
                         'Shop MMU')
        status = w.status()
        self.assertTrue(status['installed'])
        self.assertEqual(status['type'], 'MMU_TYPE_TRADRACK_1_0')
        fresh = self.wizard(load_saved=False)
        self.assertNotEqual(fresh.kc.syms['PARAM_DISPLAY_NAME'].str_value,
                            'Shop MMU')

    def test_status_follows_the_printer_cfg(self):
        w = self.wizard(self.TRADRACK)
        self.assertFalse(w.status()['installed'])
        w.generate()
        self.assertFalse(w.status()['in_printer'])
        w.add_to_printer()
        self.assertTrue(w.status()['in_printer'])

    def test_a_missing_config_folder_is_an_error(self):
        w = self.wizard(self.TRADRACK)
        w.config_dir = os.path.join(self.dir, 'nope')
        w.mmu_dir = os.path.join(w.config_dir, 'mmu')
        with self.assertRaises(hw.WizardError):
            w.generate()

    def test_the_generated_files_parse_with_klippers_section_rules(self):
        self.wizard(self.TRADRACK).generate()
        for rel, path in tree(os.path.join(self.dir, 'mmu', 'base')).items():
            with open(path) as f:
                text = f.read()
            self.assertRegex(text, r'(?m)^\[[^\]]+\]', rel)
            self.assertNotIn('[[', text, rel)
        self.assertTrue(os.path.isfile(
            os.path.join(self.dir, 'mmu', 'base', 'mmu_hardware_unit0.cfg')))


class JsonEntryPoint(Base):
    def call(self, **req):
        req.update(hh_root=HH_ROOT, config_dir=self.dir)
        return json.loads(hw.handle(json.dumps(req)))

    def test_a_full_session_through_the_json_calls(self):
        opened = self.call(op='open')
        self.assertTrue(opened['ok'])
        self.assertFalse(opened['installed'])
        self.assertTrue(opened['types'])
        self.assertTrue(self.call(op='set', symbol='MMU_TYPE_TRADRACK_1_0',
                                  value=True)['ok'])
        top = self.call(op='page', path='')
        self.assertTrue(top['ok'])
        self.assertTrue(top['items'])
        review = self.call(op='review')
        self.assertEqual(review['problems'], [])
        done = self.call(op='generate', include=True)
        self.assertTrue(done['ok'] and done['added'])
        self.assertIn('[include mmu/base/*.cfg]', self.read('printer.cfg'))
        self.assertTrue(self.call(op='status')['installed'])

    def test_errors_come_back_as_json_not_exceptions(self):
        self.call(op='open')
        bad = self.call(op='set', symbol='NOPE', value=1)
        self.assertFalse(bad['ok'])
        self.assertIn('NOPE', bad['error'])
        self.assertFalse(self.call(op='nonsense')['ok'])
        with self.assertLogs(level='ERROR'):
            self.assertFalse(json.loads(hw.handle('not json'))['ok'])

    def test_generate_reports_what_is_missing(self):
        self.call(op='open')
        self.call(op='reset')
        self.call(op='set', symbol='MMU_TYPE_3D_CHAMELEON_1_0', value=True)
        res = self.call(op='generate')
        self.assertFalse(res['ok'])
        self.assertTrue(res['problems'])

    def test_a_reset_forgets_the_saved_answers_when_asked(self):
        self.wizard(self.TRADRACK).generate()
        self.call(op='open')
        self.call(op='reset', load_saved=False)
        page = self.call(op='page', path='')
        mmu = [i for i in page['items'] if i['title'] == 'MMU Type'][0]
        self.assertNotEqual(mmu['value'], 'MMU_TYPE_TRADRACK_1_0')
        self.call(op='reset', load_saved=True)
        page = self.call(op='page', path='')
        mmu = [i for i in page['items'] if i['title'] == 'MMU Type'][0]
        self.assertEqual(mmu['value'], 'MMU_TYPE_TRADRACK_1_0')


if __name__ == '__main__':
    unittest.main()
