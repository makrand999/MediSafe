#!/usr/bin/env python3
import sys
import argparse
import time
import os
from device import AdbDevice, DEFAULT_PACKAGE, DEFAULT_ACTIVITY
from inspector import inspect_xml, load_last_snapshot

def get_target_coordinates(target_str, elements, dev):
    # Case 1: Coordinate "x y"
    parts = target_str.strip().split()
    if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
        return int(parts[0]), int(parts[1])

    # Case 2: Index (e.g. "3" or "#3")
    idx_str = target_str.lstrip("#")
    if idx_str.isdigit():
        idx = int(idx_str)
        for el in elements:
            if el.index == idx:
                return el.center
        print(f"Error: Element index #{idx} not found in snapshot.")
        return None

    # Case 3: Text match (case-insensitive substring)
    target_lower = target_str.lower()
    for el in elements:
        if target_lower in el.text.lower() or target_lower in el.desc.lower():
            return el.center

    # Case 4: Resource ID match
    for el in elements:
        if target_lower in el.res_id.lower():
            return el.center

    print(f"Error: Target '{target_str}' could not be resolved.")
    return None


def cmd_snapshot(dev, args):
    w, h = dev.get_screen_size()
    xml = dev.dump_hierarchy()
    dev.take_screenshot("/tmp/screen.png")
    elements = inspect_xml(xml, screen_w=w, screen_h=h)
    
    print("=== CURRENT SCREEN SNAPSHOT ===")
    print(f"Device: {dev.serial} | Resolution: {w}x{h}")
    app_info = dev.get_current_app()
    print(f"Focus: {app_info}")
    print(f"Screenshot saved to: /tmp/screen.png")
    print("\nInteractive & Visible Elements:")
    for el in elements:
        print(el.summary())
    if not elements:
        print("(No interactive or text elements detected)")
    print("================================")
    return elements


def cmd_tap(dev, args):
    elements = load_last_snapshot()
    target = args.target
    coords = get_target_coordinates(target, elements, dev)
    if coords:
        x, y = coords
        print(f"Tapping at ({x}, {y}) for '{target}'...")
        dev.tap(x, y)
        time.sleep(0.5)
        if args.snapshot:
            cmd_snapshot(dev, args)
    else:
        sys.exit(1)


def cmd_type(dev, args):
    if args.target:
        elements = load_last_snapshot()
        coords = get_target_coordinates(args.target, elements, dev)
        if coords:
            print(f"Tapping target '{args.target}' before typing...")
            dev.tap(coords[0], coords[1])
            time.sleep(0.3)
        else:
            sys.exit(1)

    print(f"Typing: \"{args.text}\"")
    dev.input_text(args.text)
    time.sleep(0.3)
    if args.snapshot:
        cmd_snapshot(dev, args)


def cmd_clear_text(dev, args):
    # Select all and backspace or send backspaces
    count = args.count if hasattr(args, "count") and args.count else 50
    print(f"Deleting {count} characters...")
    # Send keyevent 67 (KEYCODE_DEL) in a burst
    for _ in range(count):
        dev.keyevent(67)
    time.sleep(0.2)


def cmd_scroll(dev, args):
    w, h = dev.get_screen_size()
    direction = args.direction.lower()
    cx, cy = w // 2, h // 2
    
    if direction == "down":
        # Swipe up to scroll down
        dev.swipe(cx, int(h * 0.75), cx, int(h * 0.25), duration_ms=400)
    elif direction == "up":
        # Swipe down to scroll up
        dev.swipe(cx, int(h * 0.25), cx, int(h * 0.75), duration_ms=400)
    elif direction == "left":
        dev.swipe(int(w * 0.8), cy, int(w * 0.2), cy, duration_ms=400)
    elif direction == "right":
        dev.swipe(int(w * 0.2), cy, int(w * 0.8), cy, duration_ms=400)
    else:
        print(f"Unknown scroll direction: {direction}")
        sys.exit(1)

    print(f"Scrolled {direction}")
    time.sleep(0.5)
    if args.snapshot:
        cmd_snapshot(dev, args)


def cmd_key(dev, args):
    key_map = {
        "back": 4,
        "home": 3,
        "enter": 66,
        "tab": 61,
        "space": 62,
        "del": 67,
        "escape": 111,
        "app_switch": 187,
        "recents": 187,
    }
    key_name = args.name.lower()
    keycode = key_map.get(key_name)
    if keycode is None:
        if key_name.isdigit():
            keycode = int(key_name)
        else:
            print(f"Unknown key: {key_name}. Allowed: {list(key_map.keys())}")
            sys.exit(1)

    print(f"Pressing key: {key_name} (keycode {keycode})")
    dev.keyevent(keycode)
    time.sleep(0.5)
    if args.snapshot:
        cmd_snapshot(dev, args)


def cmd_launch(dev, args):
    pkg = args.package or DEFAULT_PACKAGE
    act = args.activity or (DEFAULT_ACTIVITY if pkg == DEFAULT_PACKAGE else None)
    print(f"Launching {pkg}...")
    dev.launch_app(pkg, act)
    time.sleep(1.5)
    cmd_snapshot(dev, args)


def cmd_stop(dev, args):
    pkg = args.package or DEFAULT_PACKAGE
    print(f"Stopping {pkg}...")
    dev.stop_app(pkg)


def cmd_restart(dev, args):
    cmd_stop(dev, args)
    time.sleep(0.5)
    cmd_launch(dev, args)


def cmd_wait(dev, args):
    target = args.target.lower()
    timeout = args.timeout
    start = time.time()
    print(f"Waiting for '{target}' (timeout: {timeout}s)...")
    
    while time.time() - start < timeout:
        w, h = dev.get_screen_size()
        xml = dev.dump_hierarchy()
        elements = inspect_xml(xml, screen_w=w, screen_h=h)
        for el in elements:
            if target in el.text.lower() or target in el.desc.lower() or target in el.res_id.lower():
                print(f"Found element matching '{target}': {el.summary()}")
                return
        time.sleep(1.0)

    print(f"Timed out waiting for '{target}' after {timeout} seconds.")
    sys.exit(1)


def cmd_assert(dev, args):
    target = args.text.lower()
    elements = load_last_snapshot()
    if not elements:
        w, h = dev.get_screen_size()
        xml = dev.dump_hierarchy()
        elements = inspect_xml(xml, screen_w=w, screen_h=h)

    for el in elements:
        if target in el.text.lower() or target in el.desc.lower():
            print(f"ASSERTION PASSED: Found text '{args.text}' in element: {el.summary()}")
            return

    print(f"ASSERTION FAILED: Text '{args.text}' not found in current UI.")
    sys.exit(1)


def cmd_logs(dev, args):
    limit = args.limit
    stdout, stderr, _ = dev.run_adb(["logcat", "-d", "-t", str(limit), "*:E"])
    print(f"=== Last {limit} error lines from logcat ===")
    print(stdout)


def main():
    parser = argparse.ArgumentParser(description="Android ADB Testing & Navigation Tool for Agents")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # snapshot
    p_snap = subparsers.add_parser("snapshot", help="Inspect screen hierarchy and take screenshot")

    # tap
    p_tap = subparsers.add_parser("tap", help="Tap on element by index, text, id, or x y")
    p_tap.add_argument("target", help="Target index (e.g. 2), text ('Add'), id, or 'x y'")
    p_tap.add_argument("--snapshot", action="store_true", help="Auto snapshot after tap")

    # type
    p_type = subparsers.add_parser("type", help="Type text into target or active element")
    p_type.add_argument("text", help="Text to type")
    p_type.add_argument("--target", help="Optional target index or text to tap before typing")
    p_type.add_argument("--snapshot", action="store_true", help="Auto snapshot after typing")

    # clear-text
    p_clear = subparsers.add_parser("clear-text", help="Clear text field")
    p_clear.add_argument("--count", type=int, default=30, help="Number of backspaces")

    # scroll
    p_scroll = subparsers.add_parser("scroll", help="Scroll screen")
    p_scroll.add_argument("direction", choices=["up", "down", "left", "right"], help="Scroll direction")
    p_scroll.add_argument("--snapshot", action="store_true", help="Auto snapshot after scroll")

    # key
    p_key = subparsers.add_parser("key", help="Press Android key")
    p_key.add_argument("name", help="Key name (back, home, enter, tab, etc.)")
    p_key.add_argument("--snapshot", action="store_true", help="Auto snapshot after keypress")

    # launch
    p_launch = subparsers.add_parser("launch", help="Launch application")
    p_launch.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Package name (default {DEFAULT_PACKAGE})")
    p_launch.add_argument("--activity", default=DEFAULT_ACTIVITY, help=f"Activity name (default {DEFAULT_ACTIVITY})")

    # stop
    p_stop = subparsers.add_parser("stop", help="Stop application")
    p_stop.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Package name (default {DEFAULT_PACKAGE})")

    # restart
    p_restart = subparsers.add_parser("restart", help="Restart application")
    p_restart.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Package name (default {DEFAULT_PACKAGE})")
    p_restart.add_argument("--activity", default=DEFAULT_ACTIVITY, help=f"Activity name")

    # wait
    p_wait = subparsers.add_parser("wait", help="Wait for element or text")
    p_wait.add_argument("target", help="Text or ID to wait for")
    p_wait.add_argument("--timeout", type=int, default=10, help="Timeout in seconds")

    # assert
    p_assert = subparsers.add_parser("assert", help="Assert text appears in snapshot")
    p_assert.add_argument("text", help="Text expected to be visible")

    # logs
    p_logs = subparsers.add_parser("logs", help="Get crash and error logs")
    p_logs.add_argument("--limit", type=int, default=50, help="Number of log lines")

    args = parser.parse_args()

    dev = AdbDevice()

    commands = {
        "snapshot": cmd_snapshot,
        "tap": cmd_tap,
        "type": cmd_type,
        "clear-text": cmd_clear_text,
        "scroll": cmd_scroll,
        "key": cmd_key,
        "launch": cmd_launch,
        "stop": cmd_stop,
        "restart": cmd_restart,
        "wait": cmd_wait,
        "assert": cmd_assert,
        "logs": cmd_logs,
    }

    cmd_fn = commands.get(args.command)
    if cmd_fn:
        cmd_fn(dev, args)

if __name__ == "__main__":
    main()
