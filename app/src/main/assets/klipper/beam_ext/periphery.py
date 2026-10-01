class GPIO:
    def __init__(self, *args, **kw): raise ImportError("GPIO peripheral access not available")
class Serial:
    def __init__(self, *args, **kw): raise ImportError("Serial peripheral not available")
class SPI:
    def __init__(self, *args, **kw): raise ImportError("SPI peripheral not available")
class I2C:
    def __init__(self, *args, **kw): raise ImportError("I2C peripheral not available")
class MMIO: pass
