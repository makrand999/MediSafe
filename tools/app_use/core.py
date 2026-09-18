#!/usr/bin/env python3
"""
core.py - Device bridge, ADB executor, screen dimensions, UI hierarchy capture,
keyboard state management, and screenshot utilities for the App Use tool suite.
"""

import os
import re
import shlex
import subprocess
import tempfile
import time
from typing import Dict, List, Optional, Tuple


class AdbDevice:
    def __init__(self, serial: Optional[str] = None):
        self.serial = serial or self._detect_device()
        self._screen_size: Optional[Tuple[int, int]] = None
        self._density: Optional[int] = None

    def _base_cmd(self) -> List[str]:
        if self.serial:
            return ["adb", "-s", self.serial]
        return ["adb"]

    def _detect_device(self) -> str:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
        lines = [line.strip() for line in res.stdout.splitlines() if line.strip() and not line.startswith("List")]
        devices = [line.split()[0] for line in lines if "device" in line]
        if not devices:
            raise RuntimeError("No active Android device or emulator detected via ADB. Please connect a device.")
        if len(devices) == 1:
            return devices[0]
        env_dev = os.environ.get("ANDROID_SERIAL")
        if env_dev and env_dev in devices:
            return env_dev
        return devices[0]

    def run_cmd(self, args: List[str], check: bool = True, binary: bool = False) -> bytes:
        cmd = self._base_cmd() + args
        res = subprocess.run(cmd, capture_output=True, check=check)
        return res.stdout

    def shell(self, command: str, check: bool = True) -> str:
        cmd = self._base_cmd() + ["shell", command]
        res = subprocess.run(cmd, capture_output=True, text=True, check=check)
        return res.stdout.strip()

    def get_screen_size(self) -> Tuple[int, int]:
        if self._screen_size is not None:
            return self._screen_size
        out = self.shell("wm size")
        match = re.search(r"(?:Override|Physical) size:\s*(\d+)x(\d+)", out)
        if match:
            self._screen_size = (int(match.group(1)), int(match.group(2)))
            return self._screen_size
        self._screen_size = (1080, 2400)
        return self._screen_size

    def get_density(self) -> int:
        if self._density is not None:
            return self._density
        out = self.shell("wm density")
        match = re.search(r"(?:Override|Physical) density:\s*(\d+)", out)
        if match:
            self._density = int(match.group(1))
            return self._density
        self._density = 440
        return self._density

    def is_screen_awake(self) -> bool:
        out = self.shell("dumpsys power")
        return "mWakefulness=Awake" in out

    def wake_and_unlock(self):
        if not self.is_screen_awake():
            self.shell("input keyevent KEYCODE_WAKEUP")
            time.sleep(0.3)
        self.shell("wm dismiss-keyguard")

    def is_keyboard_open(self) -> bool:
        """
        Checks if the soft input method (IME keyboard) is currently displayed on screen.
        """
        out = self.shell("dumpsys input_method", check=False)
        return "mInputShown=true" in out

    def hide_keyboard(self, force: bool = False) -> bool:
        """
        Closes the virtual keyboard if it is visible.
        Returns True if keyboard was dismissed, False if it was not open.
        """
        if force or self.is_keyboard_open():
            self.shell("input keyevent KEYCODE_BACK")
            time.sleep(0.3)
            return True
        return False

    def get_current_focus(self) -> Dict[str, str]:
        out = self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        package, activity = "", ""
        m = re.search(r"mCurrentFocus=Window\{[^\}]*\s+([^\s/]+)/([^\s\}]+)", out)
        if m:
            package, activity = m.group(1), m.group(2)
        else:
            m2 = re.search(r"mFocusedApp=ActivityRecord\{[^\}]*\s+([^\s/]+)/([^\s\}]+)", out)
            if m2:
                package, activity = m2.group(1), m2.group(2)
        return {"package": package, "activity": activity}

    def dump_hierarchy(self) -> str:
        dump_path = "/data/local/tmp/app_use_dump.xml"
        self.shell(f"uiautomator dump {dump_path} >/dev/null 2>&1")
        xml_content = self.shell(f"cat {dump_path}")
        if not xml_content or "<hierarchy" not in xml_content:
            self.shell("uiautomator dump /sdcard/app_use_dump.xml >/dev/null 2>&1")
            xml_content = self.shell("cat /sdcard/app_use_dump.xml")
        return xml_content

    def take_screenshot(self, output_path: str) -> str:
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        raw = self.run_cmd(["exec-out", "screencap", "-p"], check=True, binary=True)
        with open(output_path, "wb") as f:
            f.write(raw)
        return output_path

    def tap(self, x: int, y: int):
        self.shell(f"input tap {x} {y}")

    def double_tap(self, x: int, y: int, delay_ms: int = 100):
        self.shell(f"input tap {x} {y} && sleep {delay_ms / 1000.0} && input tap {x} {y}")

    def long_press(self, x: int, y: int, duration_ms: int = 1000):
        self.shell(f"input swipe {x} {y} {x} {y} {duration_ms}")

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300):
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")

    def type_text(self, text: str):
        escaped = text.replace(" ", "%s").replace("&", "\\&").replace("\"", "\\\"").replace("'", "\\'")
        self.shell(f"input text \"{escaped}\"")

    def keyevent(self, keycode: str):
        code = keycode.upper()
        if not code.startswith("KEYCODE_") and not code.isdigit():
            code = f"KEYCODE_{code}"
        self.shell(f"input keyevent {code}")

    def launch_app(self, package_name: str, activity_name: Optional[str] = None):
        self.shell(f"monkey -p {package_name} -c android.intent.category.LAUNCHER 1")

    def stop_app(self, package_name: str):
        self.shell(f"am force-stop {package_name}")

    def clear_app_data(self, package_name: str):
        self.shell(f"pm clear {package_name}")
