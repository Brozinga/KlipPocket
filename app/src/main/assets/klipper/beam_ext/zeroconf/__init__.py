IPVersion = object
class Zeroconf:
    def __init__(self, *args, **kwargs): pass
    def close(self): pass
    def register_service(self, info, ttl=60): pass
    def unregister_service(self, info): pass
    def get_service_info(self, type_, name, timeout=3000): return None
class ServiceInfo:
    def __init__(self, *args, **kwargs): pass
class ServiceBrowser:
    def __init__(self, zc, type_, handlers=None, listener=None): pass
    def cancel(self): pass
from .asyncio import AsyncServiceInfo, AsyncZeroconf
