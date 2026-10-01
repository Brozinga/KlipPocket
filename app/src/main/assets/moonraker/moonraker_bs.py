import os
import importlib.util
import sys

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    sub = os.path.join(here, "moonraker")
    sys.path.insert(0, sub)
    init_py = os.path.join(sub, "__init__.py")
    if os.path.isfile(init_py):
        s1 = importlib.util.spec_from_file_location("moonraker", init_py, submodule_search_locations=[sub])
        m1 = importlib.util.module_from_spec(s1)
        sys.modules["moonraker"] = m1
        s1.loader.exec_module(m1)
    entry = os.path.join(sub, "server.py")
    spec = importlib.util.spec_from_file_location("moonraker.server", entry)
    m = importlib.util.module_from_spec(spec)
    sys.modules["moonraker.server"] = m
    if "moonraker" in sys.modules:
        setattr(sys.modules["moonraker"], "server", m)
    spec.loader.exec_module(m)
    m.main()
