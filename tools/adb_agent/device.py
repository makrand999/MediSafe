import subprocess
import time
import os
import re

DEFAULT_PACKAGE = "com.example.medac"
DEFAULT_ACTIVITY = "com.example.medac.MainActivity"

class AdbDevice:
    def __init__(self, serial=None):
        self.serial = serial
        if not self.serial:
            self.serial = self._detect_device()

    def _detect_device(self):
        output = subprocess.check_output(["adb", "devices"]).decode()
        lines = [line.strip() for line in output.strip().split("\n")[1:] if line.strip()]
        devices = [line.split()[0] for line in lines if "device" in line.split()]
        if not devices:
            raise RuntimeError("No connected Android devices or emulators found.")
        return devices[0]

    def run_adb(self, cmd, timeout=30):
        full_cmd = ["adb", "-s", self.serial] + cmd
        res = subprocess.run(full_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        return res.stdout.decode('utf-8', errors='replace'), res.stderr.decode('utf-8', errors='replace'), res.returncode

    def shell(self, cmd, timeout=30):
        stdout, stderr, code = self.run_adb(["shell", cmd], timeout=timeout)
        return stdout

    def get_screen_size(self):
        out = self.shell("wm size")
        m = re.search(r"(\d+)x(\d+)", out)
        if m:
            return int(m.group(1)), int(m.group(2))
        return 1080, 1920

    def dump_hierarchy(self, local_path="/tmp/window_dump.xml"):
        self.shell("rm -f /sdcard/window_dump.xml")
        self.shell("uiautomator dump /sdcard/window_dump.xml")
        self.run_adb(["pull", "/sdcard/window_dump.xml", local_path])
        if os.path.exists(local_path):
            with open(local_path, "r", encoding="utf-8", errors="replace") as f:
                return f.read()
        return ""

    def take_screenshot(self, local_path="/tmp/screen.png"):
        self.shell("screencap -p /sdcard/screen.png")
        self.run_adb(["pull", "/sdcard/screen.png", local_path])
        return local_path

    def tap(self, x, y):
        self.shell(f"input tap {int(x)} {int(y)}")

    def swipe(self, x1, y1, x2, y2, duration_ms=300):
        self.shell(f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {int(duration_ms)}")

    def keyevent(self, keycode):
        self.shell(f"input keyevent {keycode}")

    def input_text(self, text):
        # Escape characters for adb shell input text
        escaped = (
            text.replace(" ", "%s")
            .replace("#", "\\#")
            .replace("&", "\\&")
            .replace("'", "\\'")
            .replace('"', '\\"')
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("|", "\\|")
            .replace(";", "\\;")
        )
        self.shell(f"input text {escaped}")

    def launch_app(self, package=DEFAULT_PACKAGE, activity=DEFAULT_ACTIVITY):
        if activity:
            self.shell(f"am start -n {package}/{activity}")
        else:
            self.shell(f"monkey -p {package} -c android.intent.category.LAUNCHER 1")

    def stop_app(self, package=DEFAULT_PACKAGE):
        self.shell(f"am force-stop {package}")

    def clear_data(self, package=DEFAULT_PACKAGE):
        self.shell(f"pm clear {package}")

    def get_current_app(self):
        out = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        return out.strip()
