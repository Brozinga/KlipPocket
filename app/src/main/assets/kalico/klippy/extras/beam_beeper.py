import urllib.request
import logging

# The app's embedded web server listens on whichever port the selected front
# end uses (Fluidd 4408, Mainsail 4409, Voyager UI 4410), not a fixed port —
# WEB_PORT_FILE is patched at install time to the absolute path of a small
# file WebService keeps updated with that port. Anything wrong with reading
# it falls back to Mainsail's port, the app's own default front end.
WEB_PORT_FILE = "${WEB_PORT_FILE}"
DEFAULT_WEB_PORT = 4409

def _web_port():
    try:
        with open(WEB_PORT_FILE, 'r') as f:
            return int(f.read().strip())
    except (IOError, OSError, ValueError):
        return DEFAULT_WEB_PORT

class BeamBeeper:
    def __init__(self, config):
        self.printer = config.get_printer()
        self.gcode = self.printer.lookup_object('gcode')
        self.gcode.register_command(
            'PLAY_TONE', self.cmd_PLAY_TONE,
            desc=self.cmd_PLAY_TONE_help)

    cmd_PLAY_TONE_help = 'Plays tone'
    def cmd_PLAY_TONE(self, gcmd):
        duration = gcmd.get_int('DURATION')
        frequency = gcmd.get_int('FREQUENCY')
        try:
            response = urllib.request.urlopen('http://127.0.0.1:' + str(_web_port()) + '/beam/play_tone?duration=' + str(duration) + '&frequency=' + str(frequency))
            data = response.read()
            response.close()
        except Exception:
            logging.exception('Failed to make a request to play_tone')

def load_config(config):
    return BeamBeeper(config)
