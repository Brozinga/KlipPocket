"""The firmware web page (assets/firmware_web): generated texts in sync, every text the script asks for exists, valid JavaScript."""
import importlib.util
import json
import os
import re
import shutil
import subprocess
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..", "..")
PAGE = os.path.join(ROOT, "app", "src", "main", "assets", "firmware_web")

spec = importlib.util.spec_from_file_location("gen_i18n", os.path.join(ROOT, "scripts", "gen_firmware_web_i18n.py"))
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def i18n():
    text = read(os.path.join(PAGE, "i18n.js"))
    body = text.split("window.I18N = ", 1)[1].split(";\nwindow.LANGUAGE_NAMES", 1)[0]
    return json.loads(body)


class PageRedraw(unittest.TestCase):
    """What keeps a redraw of the page from disturbing someone who is typing (the add-printer form, for example)."""

    @classmethod
    def setUpClass(cls):
        cls.js = read(os.path.join(PAGE, "app.js"))

    def body(self, name):
        start = self.js.index("function " + name)
        return self.js[start:start + 2600]

    def test_a_background_redraw_waits_while_a_text_field_has_the_focus(self):
        render = self.body("render(force)")
        self.assertIn("if (!force && typing()) return;", render)
        # and it must not mark the page as drawn, or the data it skipped would never show
        self.assertLess(render.index("typing()"), render.index("lastSig = sig"))

    def test_the_pollers_do_not_force_a_redraw(self):
        for name in ("pollDashboard", "loadCamera"):
            body = self.body(name).split("\n  }\n", 1)[0]
            self.assertIn("render(false)", body, name)
            self.assertNotIn("render(true)", body, name)

    def test_a_redraw_keeps_the_cursor_and_the_scroll(self):
        render = self.body("render(force)")
        for needle in ("selectionStart", "setSelectionRange", "window.scrollTo(pageX, pageY)", "preventScroll: true", "dialogTop"):
            self.assertIn(needle, render)

    def test_the_remembered_happy_hare_tab_loads_its_printers_on_reload(self):
        self.assertIn("if (tab === 'hh' && !mmu.printers && !mmu.busy) mmuOpen();", self.body("refresh()"))


class PageTexts(unittest.TestCase):
    def test_generated_file_is_up_to_date(self):
        current = read(os.path.join(PAGE, "i18n.js"))
        self.assertEqual(current, gen.build(), "run: python3 scripts/gen_firmware_web_i18n.py")

    def test_every_language_has_every_text(self):
        data = i18n()
        self.assertEqual(sorted(data), sorted(gen.LANGS))
        keys = set(data["en"])
        for lang, table in data.items():
            self.assertEqual(set(table), keys, lang)
            for k, v in table.items():
                self.assertTrue(v.strip(), "%s/%s is empty" % (lang, k))

    def test_translations_keep_their_placeholders(self):
        data = i18n()
        for k, en in data["en"].items():
            want = sorted(re.findall(r"\{\d\}", en))
            for lang, table in data.items():
                self.assertEqual(sorted(re.findall(r"\{\d\}", table[k])), want, "%s/%s" % (lang, k))

    def test_texts_used_by_the_script_exist(self):
        js = read(os.path.join(PAGE, "app.js"))
        used = set(re.findall(r"\bt\('([A-Za-z0-9]+)'", js)) | set(re.findall(r"'(Firmware[A-Za-z0-9]+)'", js))
        missing = [k for k in used if k not in i18n()["en"]]
        self.assertEqual(missing, [])

    def test_failures_and_stages_of_the_app_have_a_text(self):
        kotlin = read(os.path.join(ROOT, "app", "src", "main", "java", "ru", "ytkab0bp", "beamklipper", "firmware", "FlashCoordinator.kt"))
        js = read(os.path.join(PAGE, "app.js"))
        stages = re.search(r"enum class FlashStage \{([^}]*)\}", kotlin).group(1).split(",")
        for st in [s.strip() for s in stages if s.strip()]:
            self.assertIn(st + ":", js, "stage " + st)

    def test_the_page_makes_no_external_requests_and_has_no_inline_code(self):
        html = read(os.path.join(PAGE, "index.html"))
        self.assertNotRegex(html, r"https?://")
        self.assertNotRegex(html, r"<script(?![^>]*\bsrc=)")
        self.assertNotIn("style=", html)
        js = read(os.path.join(PAGE, "app.js"))
        self.assertNotIn("innerHTML", js)
        self.assertNotIn("eval(", js)

    @unittest.skipUnless(shutil.which("node"), "node is not installed")
    def test_scripts_are_valid_javascript(self):
        for name in ("app.js", "i18n.js"):
            r = subprocess.run(["node", "--check", os.path.join(PAGE, name)], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, name + ": " + r.stderr)


if __name__ == "__main__":
    unittest.main()
