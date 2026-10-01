class Event:
    def __init__(self, wd=0, mask=0, cookie=0, name=""):
        self.wd = wd
        self.mask = mask
        self.cookie = cookie
        self.name = name
class INotify:
    def __init__(self): pass
    def add_watch(self, path, mask=0): return 0
    def rm_watch(self, wd): pass
    def read(self, timeout=None, read_delay=None): return []
    def close(self): pass
    def __enter__(self): return self
    def __exit__(self, *args): pass
flags = {}
masks = {}
events = {}
