#!/usr/bin/env python3
"""
actions.py - High-level interaction engine for Android devices.
Executes taps, typing, swipes, keypresses, and assertions using dynamic UI mapping
and intelligent soft-keyboard management.
"""

import time
from typing import Optional, Tuple, Union

from .core import AdbDevice
from .mapper import ScreenMap, UiElement


class ActionEngine:
    def __init__(self, device: Optional[AdbDevice] = None):
        self.device = device or AdbDevice()

    def get_screen_map(self) -> ScreenMap:
        xml = self.device.dump_hierarchy()
        return ScreenMap(xml, self.device.get_screen_size())

    def hide_keyboard(self, force: bool = False) -> bool:
        """
        Dismisses the virtual soft-keyboard if visible.
        """
        return self.device.hide_keyboard(force=force)

    def is_keyboard_open(self) -> bool:
        return self.device.is_keyboard_open()

    def tap(self, target: Union[str, int, Tuple[int, int]], auto_close_keyboard: bool = True) -> UiElement:
        """
        Taps an element by index, text, slug, or coordinates.
        Automatically dismisses keyboard if tapping an element covered by or near the keyboard.
        """
        screen_w, screen_h = self.device.get_screen_size()

        # Handle tuple coordinates directly
        if isinstance(target, tuple) and len(target) == 2:
            x, y = target
            if auto_close_keyboard and y > (screen_h * 0.65) and self.device.is_keyboard_open():
                print("  [Auto-Dismiss] Virtual keyboard detected covering tap area, dismissing...")
                self.device.hide_keyboard()
                time.sleep(0.3)

            self.device.tap(x, y)
            smap = self.get_screen_map()
            el = smap.get_at_coordinates(x, y)
            return el or UiElement(
                index=-1, role="COORD", label=f"({x},{y})", text="", content_desc="",
                resource_id="", class_name="", bounds=(x, y, x, y), center=(x, y),
                width=1, height=1, clickable=True, focusable=False, editable=False,
                scrollable=False, checkable=False, checked=False, enabled=True,
                slug=f"coord_{x}_{y}"
            )

        smap = self.get_screen_map()
        el = smap.find(str(target))
        if not el:
            # If target not found and keyboard is open, dismiss keyboard and re-scan
            if self.device.is_keyboard_open():
                print("  [Auto-Dismiss] Target not found and keyboard is open, dismissing keyboard to reveal screen...")
                self.device.hide_keyboard()
                time.sleep(0.4)
                smap = self.get_screen_map()
                el = smap.find(str(target))

        if not el:
            raise ValueError(f"Could not find element matching '{target}' on current screen.\nAvailable:\n{smap.to_table()}")

        cx, cy = el.center

        # If keyboard is open and target is in bottom portion, dismiss keyboard first
        if auto_close_keyboard and cy > (screen_h * 0.65) and self.device.is_keyboard_open():
            print("  [Auto-Dismiss] Closing keyboard before tapping bottom element...")
            self.device.hide_keyboard()
            time.sleep(0.3)
            # Re-map after keyboard close as elements might shift
            smap = self.get_screen_map()
            re_el = smap.find(str(target))
            if re_el:
                el = re_el
                cx, cy = el.center

        self.device.tap(cx, cy)
        return el

    def double_tap(self, target: Union[str, int, Tuple[int, int]], delay_ms: int = 100) -> UiElement:
        el = self.tap(target)
        time.sleep(delay_ms / 1000.0)
        self.device.tap(el.center[0], el.center[1])
        return el

    def long_press(self, target: Union[str, int, Tuple[int, int]], duration_ms: int = 1000) -> UiElement:
        if isinstance(target, tuple):
            self.device.long_press(target[0], target[1], duration_ms)
            return self.get_screen_map().get_at_coordinates(target[0], target[1])

        smap = self.get_screen_map()
        el = smap.find(str(target))
        if not el:
            raise ValueError(f"Could not find element matching '{target}'.")
        self.device.long_press(el.center[0], el.center[1], duration_ms)
        return el

    def type_text(
        self,
        target: Optional[Union[str, int]],
        text: str,
        clear_first: bool = False,
        close_keyboard: bool = True,
    ):
        """
        Focuses input target (if given), types text, and automatically dismisses keyboard if requested.
        """
        if target is not None:
            self.tap(target, auto_close_keyboard=False)
            time.sleep(0.3)

        if clear_first:
            for _ in range(30):
                self.device.keyevent("DEL")

        self.device.type_text(text)

        if close_keyboard:
            time.sleep(0.2)
            self.device.hide_keyboard()

    def swipe(self, direction_or_coords: str, duration_ms: int = 350):
        d = direction_or_coords.strip().lower()
        w, h = self.device.get_screen_size()
        cx = w // 2
        cy = h // 2

        if d in ("up", "down", "left", "right"):
            if d == "up":
                x1, y1, x2, y2 = cx, int(h * 0.75), cx, int(h * 0.25)
            elif d == "down":
                x1, y1, x2, y2 = cx, int(h * 0.25), cx, int(h * 0.75)
            elif d == "left":
                x1, y1, x2, y2 = int(w * 0.8), cy, int(w * 0.2), cy
            elif d == "right":
                x1, y1, x2, y2 = int(w * 0.2), cy, int(w * 0.8), cy
            self.device.swipe(x1, y1, x2, y2, duration_ms)
        else:
            parts = [int(p) for p in d.split() if p.isdigit()]
            if len(parts) >= 4:
                self.device.swipe(parts[0], parts[1], parts[2], parts[3], duration_ms)
            else:
                raise ValueError(f"Invalid swipe argument: '{direction_or_coords}'. Use 'up', 'down', 'left', 'right', or 'x1 y1 x2 y2'")

    def scroll(self, direction: str = "down"):
        if direction.lower() in ("down", "forward"):
            self.swipe("up", duration_ms=400)
        else:
            self.swipe("down", duration_ms=400)

    def key(self, keycode: str):
        self.device.keyevent(keycode)

    def wait(self, seconds: float):
        time.sleep(seconds)

    def assert_text(self, expected_text: str, timeout: float = 5.0) -> UiElement:
        start = time.time()
        while time.time() - start < timeout:
            smap = self.get_screen_map()
            for el in smap.elements:
                if expected_text.lower() in el.label.lower() or expected_text.lower() in el.text.lower():
                    return el
            time.sleep(0.5)
        raise AssertionError(f"Timeout waiting for text: '{expected_text}'")

    def assert_element(self, target: str, timeout: float = 5.0) -> UiElement:
        start = time.time()
        while time.time() - start < timeout:
            smap = self.get_screen_map()
            el = smap.find(target)
            if el:
                return el
            time.sleep(0.5)
        raise AssertionError(f"Timeout waiting for element matching: '{target}'")
