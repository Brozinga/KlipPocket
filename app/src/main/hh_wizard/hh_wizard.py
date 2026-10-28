# Happy Hare setup wizard engine.
#
# Does what Happy Hare's install.sh menu does, without a shell, make or a
# terminal: it reads the installer's Kconfig, shows the questions that apply to
# the answers given so far, and writes the mmu/ folder with the installer's own
# build functions (installer/build.py), so the files are the ones install.sh
# would write for a fresh install.
#
# Everything goes through JSON-friendly dicts so the Android app and the web
# dashboard can drive it the same way (see handle()).
#
# This file may be distributed under the terms of the GNU GPLv3 license.
import contextlib, io, json, logging, os, re, shutil, sys, tempfile, time

UNIT = 'unit0'
CONFIG_NAME = '.mmu_config'
MARKUP = re.compile(r'\[\[/?(?:B|BOLD|DIM|U|UNDERLINE|REV|REVERSE|RESET|'
                    r'C:\d+|COLOR:\d+)\]\]')
VALUE_MARKUP = re.compile(r'\[\[VALUE:(\w+)(?::\d+)?\]\]')
# Never asked in the app: where Happy Hare lives is decided by the app
HIDDEN_MENUS = ('Paths & Services',)
HIDDEN_SYMBOLS = ('REPETIER', 'REPETIER_NAME', 'OCTOPRINT',
                  'PARAM_HAPPY_HARE_HOME', 'KLIPPER_HOME', 'MOONRAKER_HOME',
                  'KLIPPER_CONFIG_HOME', 'PARAM_GCODES_PATH',
                  'PRINTER_CONFIG_FILE', 'MOONRAKER_CONFIG_FILE',
                  'PARAM_MMU_VARS_CFG', 'SERVICE_KLIPPER', 'SERVICE_MOONRAKER',
                  'INIT_SYSTEMD')


# Lines that mean the answers are not enough for a working config: a pin with
# no board in front of it (":PD5"), a pin option left empty, a template token
# that was never filled in, or an example answer that was never replaced.
MALFORMED_PIN = re.compile(r'^\s*[A-Za-z_][A-Za-z_0-9]*\s*:\s*\^?~?!?:')
EMPTY_PIN = re.compile(r'^\s*(?:step|dir|enable)_pin\s*:\s*$')
TOKEN = re.compile(r'\[\[|\[%')
# The installer's example answers for the device path and the CAN id
PLACEHOLDER = re.compile(r'-enter-[a-z-]+-')


class WizardError(Exception):
    def __init__(self, message, problems=None):
        super().__init__(message)
        self.problems = problems or []


def clean(text):
    """Prompt/help text without the menuconfig colour markup."""
    return MARKUP.sub('', text or '').strip()


@contextlib.contextmanager
def _environ(values):
    saved = {k: os.environ.get(k) for k in values}
    os.environ.update({k: str(v) for k, v in values.items()})
    try:
        yield
    finally:
        for k, v in saved.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v


@contextlib.contextmanager
def _chdir(path):
    prev = os.getcwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(prev)


class Wizard:
    def __init__(self, hh_root, config_dir, printer_cfg='printer.cfg',
                 moonraker_conf='moonraker.conf'):
        self.hh_root = os.path.abspath(hh_root)
        self.config_dir = os.path.abspath(config_dir)
        self.printer_cfg = printer_cfg
        self.moonraker_conf = moonraker_conf
        self.installer = os.path.join(self.hh_root, 'installer')
        self.mmu_dir = os.path.join(self.config_dir, 'mmu')
        self.saved_config = os.path.join(self.mmu_dir, CONFIG_NAME)
        for p in (os.path.join(self.installer, 'lib', 'kconfiglib'),
                  self.hh_root):
            if p not in sys.path:
                sys.path.insert(0, p)
        import kconfiglib
        self.kl = kconfiglib
        self.kc = None
        self.nodes = {}
        self.paths = {}
        self.loaded_from = None
        self.version = self._hh_version()
        self.per_gate = False
        self.reset()

    # ---- Kconfig ---------------------------------------------------------
    def _hh_version(self):
        path = os.path.join(self.hh_root, 'extras', 'mmu', 'mmu_constants.py')
        with io.open(path, encoding='utf-8') as f:
            m = re.search(r'^VERSION = "(.*?)"', f.read(), re.M)
        return m.group(1) if m else ''

    def _env(self, kconfig_path=None):
        return {
            'srctree': self.installer, 'SRC': self.hh_root,
            'HH_VERSION': self.version,
            'KLIPPER_HOME': self.config_dir,
            'CONFIG_KLIPPER_HOME': self.config_dir,
            'CONFIG_KLIPPER_CONFIG_HOME': self.config_dir,
            'CONFIG_MOONRAKER_HOME': self.config_dir,
            'CONFIG_SERVICE_KLIPPER': 'klipper.service',
            # install.sh turns this on for an MMU with one MCU per gate (EMU):
            # it changes which questions the Kconfig has
            'F_PER_GATE_MCU': 'y' if self.per_gate else '',
            'KCONFIG_PARENT': '',
            'KCONFIG_CONFIG': kconfig_path or self.saved_config,
            'UNIT_NAME': UNIT, 'MCU_NAME': UNIT, 'UNIT_INDEX': '0',
            'F_MULTI_UNIT': '', 'F_MULTI_UNIT_ENTRY_POINT': '',
        }

    def _parse(self):
        kl = self.kl
        with _environ(self._env()), _chdir(self.installer):
            kc = kl.Kconfig('Kconfig')
        return kc

    def reset(self, load_saved=True):
        """Back to the defaults, or to the answers saved by the last run."""
        self.per_gate = False
        self.kc = self._parse()
        self._index()
        self.loaded_from = None
        if load_saved and os.path.isfile(self.saved_config):
            self.kc.load_config(self.saved_config, filter_defaults=False)
            self.loaded_from = self.saved_config
            self._sync_shape()

    def _sync_shape(self):
        """Re-read the Kconfig when the answers change its shape.

        An MMU with one MCU per gate needs the tree parsed with
        F_PER_GATE_MCU=y (and the reverse), keeping the answers given.
        """
        sym = self.kc.syms.get('MMU_HAS_PER_GATE_MCU')
        want = sym is not None and sym.tri_value == 2
        if want == self.per_gate:
            return
        kept = [(name, s.user_value) for name, s in self.kc.syms.items()
                if s.user_value is not None]
        self.per_gate = want
        self.kc = self._parse()
        self._index()
        for name, value in kept:
            target = self.kc.syms.get(name)
            if target is not None:
                target.set_value(value)

    def _index(self):
        self.nodes, self.paths = {}, {}

        def walk(node, prefix):
            i = 0
            n = node.list
            while n:
                path = prefix + [i]
                key = '.'.join(map(str, path))
                self.nodes[key] = n
                self.paths[id(n)] = key
                if n.list:
                    walk(n, path)
                i += 1
                n = n.next
        walk(self.kc.top_node, [])

    # ---- visibility ------------------------------------------------------
    def _normally_visible(self, node):
        kl = self.kl
        return bool(node.prompt and kl.expr_value(node.prompt[1])
                    and not (node.item == kl.MENU
                             and not kl.expr_value(node.visibility)))

    def _forced(self, node):
        return hasattr(node, 'forceshow') \
            and bool(self.kl.expr_value(node.forceshow))

    def _visible(self, node):
        return bool(node.prompt
                    and (self._normally_visible(node) or self._forced(node)))

    def _shown(self, menu):
        """The nodes menuconfig would list inside `menu`, flattened."""
        kl = self.kl

        def rec(node):
            res = []
            while node:
                if self._visible(node):
                    res.append(node)
                    if node.list and not node.is_menuconfig:
                        res += rec(node.list)
                elif node.list and isinstance(node.item, kl.Symbol):
                    shown = rec(node.list)
                    if shown:
                        res.append(node)
                        if not node.is_menuconfig:
                            res += shown
                node = node.next
            return res
        return rec(menu.list)

    @staticmethod
    def reflow(text):
        """Join the hard-wrapped lines of a help text into paragraphs.

        Lines stay apart where the text is a list, an indented block or a
        heading that ends with a colon.
        """
        out = []
        for line in text.split('\n'):
            plain = line.strip()
            if out and out[-1] and plain and not line[:1].isspace() \
                    and not re.match(r'([-*]|\d+[.)])\s', plain) \
                    and not out[-1].rstrip().endswith((':', '.', '!', '?')) \
                    and not out[-1][:1].isspace():
                out[-1] = out[-1].rstrip() + ' ' + plain
            else:
                out.append(line if line.strip() else '')
        return '\n'.join(out)

    def _text(self, text):
        """Prompt or help text with the live [[VALUE:SYM]] parts filled in."""
        def value(m):
            sym = self.kc.syms.get(m.group(1))
            return sym.str_value if sym is not None else ''
        return clean(VALUE_MARKUP.sub(value, text or ''))

    # ---- pages -----------------------------------------------------------
    def page(self, path=''):
        """The questions of one menu (top level when `path` is empty)."""
        menu = self.nodes[path] if path else self.kc.top_node
        items, crumbs = [], []
        if path:
            node = menu
            while node is not None and node is not self.kc.top_node:
                if node.prompt:
                    crumbs.append({'path': self.paths[id(node)],
                                   'title': self._text(node.prompt[0])})
                node = node.parent
            crumbs.reverse()
        for node in self._shown(menu):
            item = self._item(node, menu)
            if item is None:
                continue
            items.append(item)
            if isinstance(node.item, self.kl.Choice):
                # What a choice carries besides its options (the device path
                # asked after picking "Other / manually entered") is listed
                # right below it
                for sub in self._shown(node):
                    if isinstance(sub.item, self.kl.Symbol) \
                            and sub.item.choice is node.item:
                        continue
                    extra = self._item(sub, node)
                    if extra is not None:
                        extra['indent'] += 1
                        items.append(extra)
        # Hidden answers the app decides itself
        items = [i for i in items if i.get('symbol') not in HIDDEN_SYMBOLS
                 and i.get('title') not in HIDDEN_MENUS]
        return {'path': path, 'crumbs': crumbs,
                'title': crumbs[-1]['title'] if crumbs else 'Happy Hare',
                'help': self.reflow(self._text(getattr(menu, 'help', ''))) if path else '',
                'items': items}

    def _depth(self, node, menu):
        depth = 0
        while node.parent is not None and node.parent is not menu:
            node = node.parent
            if not node.is_menuconfig:
                depth += 1
        return depth

    def _item(self, node, menu):
        kl = self.kl
        item = node.item
        base = {'id': self.paths[id(node)],
                'title': self._text(node.prompt[0]) if node.prompt else '',
                'help': self.reflow(self._text(getattr(node, 'help', '')))}
        base['indent'] = self._depth(node, menu)
        if item == kl.COMMENT:
            title = base['title']
            title = re.sub(r'^\(\"\)_\(\"\)\s*', '', title)
            # The installer's ASCII hare is not a question or a note
            if title in ('', '_') or re.fullmatch(r'[()\\_/*,\s"]+', title):
                return None
            heading = title.startswith('_')
            base.update(kind='heading' if heading else 'note',
                        title=title[1:].strip() if heading else title)
            return base
        if item == kl.MENU:
            base.update(kind='menu', path=base['id'],
                        enabled=self._normally_visible(node))
            return base
        if isinstance(item, kl.Choice):
            return self._choice(node, base)
        sym = item
        base['symbol'] = sym.name
        if sym.orig_type in (kl.BOOL, kl.TRISTATE, kl.BOOLINT):
            base.update(kind='bool',
                        value=sym.str_value in ('y', '1') if
                        sym.orig_type != kl.BOOLINT else sym.str_value == '1',
                        fixed=len(sym.assignable) <= 1)
            if sym.choice is not None:
                return None
        else:
            kind = {kl.INT: 'int', kl.HEX: 'int', kl.FLOAT: 'float'}.get(
                sym.orig_type, 'text')
            base.update(kind=kind, value=sym.str_value,
                        validator=getattr(getattr(sym, 'validator', None),
                                          'pattern',
                                          getattr(sym, 'validator', None)))
            if getattr(sym, 'array_editor', None):
                base['separator'] = sym.array_editor[0] \
                    if isinstance(sym.array_editor, (list, tuple)) \
                    else sym.array_editor
        base['default'] = sym.user_value is None
        return base

    def _choice(self, node, base):
        choice = node.item
        options = []
        for member in choice.syms:
            for mnode in member.nodes:
                if self._visible(mnode):
                    options.append({'symbol': member.name,
                                    'title': self._text(mnode.prompt[0]),
                                    'help': self.reflow(self._text(
                                        getattr(mnode, 'help', '')))})
                    break
        selected = choice.selection
        base.update(kind='choice', options=options,
                    value=selected.name if selected is not None else None,
                    default=(choice.user_selection is None))
        return base

    # ---- answers ---------------------------------------------------------
    def set_value(self, symbol, value):
        kl = self.kl
        sym = self.kc.syms.get(symbol)
        if sym is None:
            raise WizardError('unknown symbol %s' % symbol)
        if sym.orig_type in (kl.BOOL, kl.TRISTATE, kl.BOOLINT):
            if isinstance(value, str):
                value = value.lower() in ('1', 'true', 'y', 'yes', 'on')
            ok = sym.set_value(2 if value else 0)
        else:
            text = str(value)
            pattern = getattr(sym, 'validator', None)
            pattern = getattr(pattern, 'pattern', pattern)
            if pattern and not re.fullmatch(pattern, text.strip()):
                raise WizardError('invalid value for %s' % symbol)
            if sym.orig_type in (kl.INT, kl.FLOAT, kl.HEX):
                try:
                    float(text) if sym.orig_type == kl.FLOAT else int(text, 0)
                except ValueError:
                    raise WizardError('%s is not a number' % text)
            ok = sym.set_value(text)
        if not ok:
            raise WizardError('%s cannot be set to %r now' % (symbol, value))
        self._sync_shape()

    def unset_value(self, symbol):
        sym = self.kc.syms.get(symbol)
        if sym is None:
            raise WizardError('unknown symbol %s' % symbol)
        sym.unset_value()
        self._sync_shape()

    def values(self):
        """Every symbol the user set, for the review and for tests."""
        out = {}
        for name, sym in self.kc.syms.items():
            if sym.user_value is not None:
                out[name] = sym.user_value
        return out

    # ---- catalog and review ----------------------------------------------
    def mmu_types(self):
        choice = self._type_choice()
        if choice is None:
            return []
        out = []
        for member in choice.syms:
            for mnode in member.nodes:
                if self._visible(mnode):
                    out.append({'symbol': member.name,
                                'title': self._text(mnode.prompt[0]),
                                'selected': choice.selection is member})
                    break
        return out

    def _type_choice(self):
        # An unnamed choice: the one holding the MMU_TYPE_* members
        for ch in self.kc.choices:
            if any(s.name.startswith('MMU_TYPE_') for s in ch.syms):
                return ch
        return None

    def current_type(self):
        for t in self.mmu_types():
            if t['selected']:
                return t['symbol']
        return None

    def warnings(self):
        """Notes Happy Hare's own menu shows as warnings for these answers."""
        found = []
        node = self.kc.top_node.list
        stack = []

        def rec(n, in_warn):
            while n:
                title = self._text(n.prompt[0]) if n.prompt else ''
                warn = in_warn or ('warning' in title.lower()
                                   and n.item == self.kl.MENU)
                if n.item == self.kl.COMMENT and in_warn \
                        and self._visible(n) and title not in ('', '_') \
                        and not title.startswith('_'):
                    found.append(title)
                if n.list:
                    rec(n.list, warn)
                n = n.next
        rec(node, False)
        return found

    # ---- generation ------------------------------------------------------
    def _load_build(self):
        import installer.build as build
        return build

    def _template_files(self):
        base = os.path.join(self.hh_root, 'config', 'base')
        names = sorted(f for f in os.listdir(base) if f.endswith('.cfg')
                       and os.path.getsize(os.path.join(base, f)) > 0)
        return names

    def render_all(self, out_dir, kconfig_path=None, keep_state_from=None):
        """Write the whole mmu/ tree the way `make build` + install does."""
        build = self._load_build()
        kconfig_path = kconfig_path or os.path.join(out_dir, CONFIG_NAME)
        env = dict(self._env(kconfig_path))
        env.update(SRC=self.hh_root, OUT=out_dir,
                   F_CFG_UPGRADE_MODE='replace')
        mmu_out = os.path.join(out_dir, 'mmu')
        os.makedirs(mmu_out, exist_ok=True)
        log = io.StringIO()
        handler = logging.StreamHandler(log)
        handler.setFormatter(logging.Formatter('%(levelname)s %(message)s'))
        root = logging.getLogger()
        root.addHandler(handler)
        try:
            with _environ(env), _chdir(self.hh_root):
                self.kc.write_config(kconfig_path)
                try:
                    build.pre_parse_kconfig(kconfig_path)
                    for name in self._template_files():
                        src = os.path.join(self.hh_root, 'config', 'base',
                                           name)
                        dest_name = name
                        if name in ('mmu_hardware.cfg', 'mmu_parameters.cfg'):
                            dest_name = name[:-4] + '_' + UNIT + '.cfg'
                        dest = os.path.join(mmu_out, 'base', dest_name)
                        os.makedirs(os.path.dirname(dest), exist_ok=True)
                        build.build(src, dest, kconfig_path, [])
                except SystemExit as e:
                    raise WizardError('the Happy Hare builder stopped (%s): %s'
                                      % (e.code, log.getvalue()[-600:]))
        finally:
            root.removeHandler(handler)
        # Files that are copied as they are
        for sub in ('macros', 'optional'):
            src = os.path.join(self.hh_root, 'config', sub)
            if not os.path.isdir(src):
                continue
            dst = os.path.join(mmu_out, sub)
            os.makedirs(dst, exist_ok=True)
            for name in sorted(os.listdir(src)):
                p = os.path.join(src, name)
                if name.endswith('.cfg') and os.path.getsize(p) > 0:
                    shutil.copy2(p, os.path.join(dst, name))
        vars_dst = os.path.join(mmu_out, 'mmu_vars.cfg')
        if keep_state_from and os.path.isfile(keep_state_from):
            shutil.copy2(keep_state_from, vars_dst)
        else:
            shutil.copy2(os.path.join(self.hh_root, 'config', 'mmu_vars.cfg'),
                         vars_dst)
        shutil.copy2(kconfig_path, os.path.join(mmu_out, CONFIG_NAME))
        # The builder's scratch copy of the answers is not part of the install
        for name in os.listdir(out_dir):
            if name.endswith('.pickle') or name.endswith('.tmp'):
                os.remove(os.path.join(out_dir, name))
        return mmu_out

    def find_problems(self, mmu_out):
        """Lines of the generated base/*.cfg that cannot work as they are."""
        found = []
        base = os.path.join(mmu_out, 'base')
        for name in sorted(os.listdir(base)):
            with io.open(os.path.join(base, name), encoding='utf-8') as f:
                for n, line in enumerate(f, 1):
                    if line.lstrip().startswith('#'):
                        continue
                    # Sensors in mmu.cfg may be left empty: only the hardware
                    # file's pins (steppers, drivers) must all be set
                    empty = name.startswith('mmu_hardware') \
                        and EMPTY_PIN.match(line)
                    if MALFORMED_PIN.match(line) or empty \
                       or TOKEN.search(line) or PLACEHOLDER.search(line):
                        found.append('%s:%d %s' % (name, n, line.strip()))
        return found

    def review(self):
        """Build into a scratch folder: what Happy Hare warns about and what
        the answers still lack, without touching the printer."""
        stage = tempfile.mkdtemp(prefix='hh-wizard-')
        try:
            built = self.render_all(stage)
            problems = self.find_problems(built)
        finally:
            shutil.rmtree(stage, ignore_errors=True)
        return {'warnings': self.warnings(), 'problems': problems}

    def generate(self, include_in_printer=False, keep_state=False,
                 force=False):
        """Replace the mmu/ folder with a freshly generated one.

        The old folder is deleted only after the new one has been built, so a
        failure leaves the printer as it was.
        """
        if not os.path.isdir(self.config_dir):
            raise WizardError('no config folder %s' % self.config_dir)
        keep = os.path.join(self.mmu_dir, 'mmu_vars.cfg') if keep_state \
            else None
        stage = tempfile.mkdtemp(prefix='hh-wizard-')
        try:
            built = self.render_all(stage, keep_state_from=keep)
            problems = [] if force else self.find_problems(built)
            if problems:
                raise WizardError('the answers are not enough for a working '
                                  'config', problems)
            staged = os.path.join(self.config_dir, '.mmu.new')
            shutil.rmtree(staged, ignore_errors=True)
            shutil.copytree(built, staged)
            shutil.rmtree(self.mmu_dir, ignore_errors=True)
            os.rename(staged, self.mmu_dir)
        finally:
            shutil.rmtree(stage, ignore_errors=True)
        self.loaded_from = self.saved_config
        result = {'folder': self.mmu_dir, 'includes': self.include_lines(),
                  'moonraker': self.moonraker_lines(), 'added': False}
        if include_in_printer:
            self.add_to_printer()
            result['added'] = True
        return result

    # ---- printer.cfg / moonraker.conf -----------------------------------
    def include_lines(self):
        """The includes install_includes() puts in printer.cfg, in file order."""
        def on(name):
            sym = self.kc.syms.get(name)
            return sym is not None and sym.str_value in ('y', '1')
        lines = []
        if on('INSTALL_12864_MENU'):
            lines.append('[include mmu/optional/mmu_menu.cfg]')
        lines += ['[include mmu/base/*.cfg]', '[include mmu/macros/*.cfg]']
        if on('INSTALL_CLIENT_MACROS'):
            lines.append('[include mmu/optional/client_macros.cfg]')
        return lines

    def moonraker_lines(self):
        return ['[mmu_server]', 'enable_file_preprocessor: True',
                'enable_toolchange_next_pos: True',
                'update_spoolman_location: True']

    def _backup(self, path):
        if os.path.isfile(path):
            shutil.copy2(path, path + '.hh-%s.bak' % time.strftime(
                '%Y%m%d-%H%M%S'))

    def add_to_printer(self):
        """Include Happy Hare in printer.cfg and enable it in moonraker.conf."""
        build = self._load_build()
        printer = os.path.join(self.config_dir, self.printer_cfg)
        moonraker = os.path.join(self.config_dir, self.moonraker_conf)
        kconfig = os.path.join(self.mmu_dir, CONFIG_NAME)
        env = dict(self._env(kconfig))
        env.update(SRC=self.hh_root, OUT=self.mmu_dir)
        with _environ(env), _chdir(self.hh_root):
            if os.path.isfile(printer):
                self._backup(printer)
                build.pre_parse_kconfig(kconfig)
                build.install_includes(printer, kconfig)
            if os.path.isfile(moonraker):
                self._backup(moonraker)
                self._moonraker_section(moonraker, build)

    def _moonraker_section(self, path, build):
        builder = build.ConfigBuilder(path)
        if not builder.has_section('mmu_server'):
            builder.add_section('mmu_server')
        for line in self.moonraker_lines()[1:]:
            option, value = line.split(': ', 1)
            if not builder.has_option('mmu_server', option):
                builder.set('mmu_server', option, value)
        with io.open(path, 'w', encoding='utf-8') as f:
            f.write(builder.write())

    def saved_type(self):
        """The MMU type of the saved configuration, or None."""
        if not os.path.isfile(self.saved_config):
            return None
        choice = self._type_choice()
        names = {m.name for m in choice.syms} if choice is not None else set()
        with io.open(self.saved_config, encoding='utf-8') as f:
            for line in f:
                m = re.match(r'CONFIG_([A-Z0-9_]+)=y', line)
                if m and m.group(1) in names:
                    return m.group(1)
        return None

    def status(self):
        printer = os.path.join(self.config_dir, self.printer_cfg)
        text = ''
        if os.path.isfile(printer):
            with io.open(printer, encoding='utf-8', errors='replace') as f:
                text = f.read()
        return {'installed': os.path.isdir(self.mmu_dir)
                and os.path.isfile(self.saved_config),
                'in_printer': 'include mmu/base/*.cfg' in text,
                'version': self.version, 'type': self.saved_type(),
                'loaded_saved': self.loaded_from is not None}


# ---- JSON entry point used by the app and the dashboard -----------------
_wizards = {}


def handle(request_json):
    """One call: {"hh_root","config_dir","op",...} -> JSON text.

    The wizard of a printer lives as long as the process; `open` only reports
    where the printer stands and never changes the answers (use `reset`).
    """
    try:
        req = json.loads(request_json)
        key = (req['hh_root'], req['config_dir'])
        op = req['op']
        if key not in _wizards:
            _wizards[key] = Wizard(req['hh_root'], req['config_dir'],
                                   req.get('printer_cfg', 'printer.cfg'),
                                   req.get('moonraker_conf',
                                           'moonraker.conf'))
        w = _wizards[key]
        if op == 'open':
            res = dict(w.status(), types=w.mmu_types())
        elif op == 'status':
            res = w.status()
        elif op == 'page':
            res = w.page(req.get('path', ''))
        elif op == 'set':
            w.set_value(req['symbol'], req['value'])
            res = {}
        elif op == 'unset':
            w.unset_value(req['symbol'])
            res = {}
        elif op == 'reset':
            w.reset(load_saved=bool(req.get('load_saved', False)))
            res = {}
        elif op == 'review':
            res = w.review()
        elif op == 'warnings':
            res = {'warnings': w.warnings()}
        elif op == 'generate':
            res = w.generate(bool(req.get('include')),
                             bool(req.get('keep_state')))
        elif op == 'add_to_printer':
            w.add_to_printer()
            res = {}
        else:
            raise WizardError('unknown op %s' % op)
        res['ok'] = True
        return json.dumps(res)
    except WizardError as e:
        return json.dumps({'ok': False, 'error': str(e),
                           'problems': e.problems})
    except Exception as e:  # never let the service die on a bad request
        logging.exception('hh_wizard')
        return json.dumps({'ok': False, 'error': '%s: %s' % (
            type(e).__name__, e)})
