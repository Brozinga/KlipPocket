class CryptoError(Exception): pass
class Signer:
    def __init__(self, *args, **kwargs): raise CryptoError("libnacl not available on Android")
class Verifier:
    def __init__(self, *args, **kwargs): raise CryptoError("libnacl not available on Android")
