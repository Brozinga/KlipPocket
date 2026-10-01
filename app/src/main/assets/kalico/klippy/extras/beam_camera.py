import urllib.request
import logging

# See beam_beeper.py: the app's web server's port depends on the selected
# front end, so it is read from a file WebService keeps updated instead of
# being hardcoded.
WEB_PORT_FILE = "${WEB_PORT_FILE}"
DEFAULT_WEB_PORT = 4409

def _web_port():
    try:
        with open(WEB_PORT_FILE, 'r') as f:
            return int(f.read().strip())
    except (IOError, OSError, ValueError):
        return DEFAULT_WEB_PORT

class BeamCamera:
    def __init__(self, config):
        self.printer = config.get_printer()
        self.gcode = self.printer.lookup_object('gcode')
        self.gcode.register_command(
            'SET_CAMERA_FLASHLIGHT', self.cmd_SET_CAMERA_FLASHLIGHT,
            desc=self.cmd_SET_CAMERA_FLASHLIGHT_help)
        self.gcode.register_command(
            'SET_CAMERA_FOCUS', self.cmd_SET_CAMERA_FOCUS,
            desc=self.cmd_SET_CAMERA_FOCUS_help)

    cmd_SET_CAMERA_FLASHLIGHT_help = 'Sets camera flashlight'
    def cmd_SET_CAMERA_FLASHLIGHT(self, gcmd):
        enabled = gcmd.get('ENABLED')
        try:
            response = urllib.request.urlopen('http://127.0.0.1:' + str(_web_port()) + '/beam/set_camera_flashlight?enabled=' + enabled)
            data = response.read()
            response.close()
        except Exception:
            logging.exception('Failed to make a request to set_camera_flashlight')

    cmd_SET_CAMERA_FOCUS_help = 'Sets camera focus'
    def cmd_SET_CAMERA_FOCUS(self, gcmd):
        autofocus = gcmd.get('AUTOFOCUS').lower() == 'true'
        if not autofocus:
            focus_distance = gcmd.get_float('FOCUS_DISTANCE')
        else:
            focus_distance = 0
        try:
            response = urllib.request.urlopen('http://127.0.0.1:' + str(_web_port()) + '/beam/set_camera_focus?autofocus=' + str(autofocus).lower() + '&focus=' + str(focus_distance))
            data = response.read()
            response.close()
        except Exception:
            logging.exception('Failed to make a request to set_camera_focus')

def load_config(config):
    return BeamCamera(config)
