import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'beam_ext'))

def main():
    sys.path.append("klippy")
    import klippy
    klippy.main()
