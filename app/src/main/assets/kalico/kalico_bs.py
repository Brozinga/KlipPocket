import os
import importlib.util
import sys

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    sys.path.insert(0, os.path.join(here, "beam_ext"))
    sub = os.path.join(here, "klippy")
    sys.path.insert(0, sub)
    entry = os.path.join(sub, "klippy.py")
    spec = importlib.util.spec_from_file_location("klippy", entry, submodule_search_locations=[sub])
    m = importlib.util.module_from_spec(spec)
    sys.modules["klippy"] = m
    spec.loader.exec_module(m)
    if hasattr(m, "main"):
        m.main()
    else:
        from klippy.printer import main as _m
        _m()
