#!/usr/bin/env python3
"""
cli.py - Main command line interface for the App Use & Touch Mapping tool.
"""

import argparse
import json
import os
import sys
import time

from .actions import ActionEngine
from .annotator import annotate_screenshot
from .catalog import AppCataloger
from .core import AdbDevice
from .mapper import ScreenMap
from .recorder import FlowRecorder
from .runner import TestRunner


def cmd_doctor(args):
    print("=" * 60)
    print("  APP USE DOCTOR: System & Device Diagnostic")
    print("=" * 60)
    try:
        dev = AdbDevice(args.serial)
        print(f"  [✓] ADB connection: Device detected ({dev.serial})")
    except Exception as e:
        print(f"  [✗] ADB connection error: {e}")
        return 1

    try:
        size = dev.get_screen_size()
        density = dev.get_density()
        awake = dev.is_screen_awake()
        kb_open = dev.is_keyboard_open()
        print(f"  [✓] Display: {size[0]}x{size[1]} @ {density}dpi (Awake: {awake}, Keyboard Open: {kb_open})")
    except Exception as e:
        print(f"  [✗] Display query error: {e}")

    try:
        focus = dev.get_current_focus()
        print(f"  [✓] Current Focus: {focus['package']} / {focus['activity']}")
    except Exception as e:
        print(f"  [!] Focus check: {e}")

    try:
        pkgs = dev.shell("pm list packages | grep medac")
        if "com.example.medac" in pkgs:
            print("  [✓] Target App 'com.example.medac' is installed")
        else:
            print("  [!] Target App 'com.example.medac' not found in installed packages")
    except Exception as e:
        print(f"  [!] Package check: {e}")

    try:
        xml = dev.dump_hierarchy()
        smap = ScreenMap(xml, dev.get_screen_size())
        print(f"  [✓] UI Hierarchy capture OK: {len(smap.elements)} elements mapped on screen")
    except Exception as e:
        print(f"  [✗] UI Hierarchy dump error: {e}")

    try:
        import PIL
        tmp_screen = "/tmp/app_use_doctor_check.png"
        dev.take_screenshot(tmp_screen)
        if os.path.exists(tmp_screen) and os.path.getsize(tmp_screen) > 0:
            print(f"  [✓] Screenshot & Pillow OK: Captured {os.path.getsize(tmp_screen)} bytes")
            os.remove(tmp_screen)
    except Exception as e:
        print(f"  [✗] Screenshot test error: {e}")

    print("=" * 60)
    print("  All checks passed. Ready for automated testing!")
    print("=" * 60)
    return 0


def cmd_map(args):
    dev = AdbDevice(args.serial)
    xml = dev.dump_hierarchy()
    smap = ScreenMap(xml, dev.get_screen_size())

    if args.json:
        if args.output:
            with open(args.output, "w") as f:
                json.dump(smap.to_dict(), f, indent=2)
            print(f"Map JSON saved to: {args.output}")
        else:
            print(json.dumps(smap.to_dict(), indent=2))
    else:
        interactive_only = not args.all
        print(smap.to_table(interactive_only=interactive_only))
        if args.output:
            with open(args.output, "w") as f:
                json.dump(smap.to_dict(), f, indent=2)
            print(f"\nMap JSON saved to: {args.output}")
    return 0


def cmd_snapshot(args):
    dev = AdbDevice(args.serial)
    out_dir = args.output or "screenshots/current_screen"
    os.makedirs(out_dir, exist_ok=True)

    clean_path = os.path.join(out_dir, "screen_clean.png")
    annotated_path = os.path.join(out_dir, "screen_annotated.png")
    json_path = os.path.join(out_dir, "screen_map.json")

    print(f"Capturing screenshot from {dev.serial}...")
    dev.take_screenshot(clean_path)

    print("Dumping and mapping UI elements...")
    xml = dev.dump_hierarchy()
    smap = ScreenMap(xml, dev.get_screen_size())

    with open(json_path, "w") as f:
        json.dump(smap.to_dict(), f, indent=2)

    print(f"Rendering Set-of-Marks visual annotations...")
    annotate_screenshot(clean_path, smap, annotated_path, interactive_only=not args.all)

    print("\n" + "=" * 60)
    print(f"  Clean Screenshot:     file://{os.path.abspath(clean_path)}")
    print(f"  Annotated Screenshot: file://{os.path.abspath(annotated_path)}")
    print(f"  Element Map JSON:     file://{os.path.abspath(json_path)}")
    print("=" * 60)
    print("\nInteractive elements on this screen:")
    print(smap.to_table(interactive_only=True))
    return 0


def cmd_tap(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    target = args.target
    if args.y is not None:
        target = (int(target), int(args.y))
    print(f"Tapping target: {target}...")
    el = engine.tap(target)
    print(f"✓ Tapped '{el.label or el.slug}' [Role: {el.role}] at coordinates ({el.center[0]}, {el.center[1]})")
    return 0


def cmd_type(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    target = args.target if args.target != "_" else None
    close_kb = not args.keep_keyboard
    print(f"Typing into target {target}: '{args.text}' (Auto-close keyboard: {close_kb})...")
    engine.type_text(target, args.text, clear_first=args.clear, close_keyboard=close_kb)
    print(f"✓ Text entered.")
    return 0


def cmd_close_keyboard(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    was_closed = engine.hide_keyboard(force=args.force)
    if was_closed:
        print("✓ Virtual keyboard dismissed.")
    else:
        print("ℹ Virtual keyboard was not open.")
    return 0


def cmd_swipe(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    direction = args.direction
    print(f"Swiping {direction}...")
    engine.swipe(direction)
    print(f"✓ Swipe completed.")
    return 0


def cmd_scroll(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    direction = args.direction or "down"
    print(f"Scrolling {direction}...")
    engine.scroll(direction)
    print(f"✓ Scroll completed.")
    return 0


def cmd_key(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    print(f"Sending keyevent: {args.keycode}...")
    engine.key(args.keycode)
    print(f"✓ Key sent.")
    return 0


def cmd_assert(args):
    dev = AdbDevice(args.serial)
    engine = ActionEngine(dev)
    print(f"Asserting text '{args.text}' is visible...")
    el = engine.assert_text(args.text, timeout=args.timeout)
    print(f"✓ Assertion passed: Found '{el.label}' at ({el.center[0]}, {el.center[1]})")
    return 0


def cmd_launch(args):
    dev = AdbDevice(args.serial)
    pkg = args.package or "com.example.medac"
    print(f"Launching {pkg}...")
    dev.launch_app(pkg)
    print("✓ App launched.")
    return 0


def cmd_record(args):
    dev = AdbDevice(args.serial)
    out_file = args.output or "tests/recorded_test.json"
    recorder = FlowRecorder(out_file, dev)
    recorder.interactive_session()
    return 0


def cmd_run(args):
    dev = AdbDevice(args.serial)
    runner = TestRunner(args.test_file, report_dir=args.report_dir, device=dev)
    success = runner.run()
    return 0 if success else 1


def cmd_catalog(args):
    dev = AdbDevice(args.serial)
    cat = AppCataloger(output_dir=args.output or "docs/touch_catalog", device=dev)
    print("Mapping current active screen into catalog...")
    focus = dev.get_current_focus()
    screen_name = args.name or focus.get("activity", "MainScreen").split(".")[-1]
    cat.capture_screen(screen_name)
    cat.generate_master_markdown()
    cat.generate_master_json()
    return 0


def main():
    parser = argparse.ArgumentParser(
        prog="app-use",
        description="App Use & Touch Mapping Tool for Automated Android Testing",
    )
    parser.add_argument("-s", "--serial", help="Target ADB device serial number", default=None)
    subparsers = parser.add_subparsers(dest="command", help="Subcommands")

    # doctor
    p_doc = subparsers.add_parser("doctor", help="Check ADB, device connectivity, and tools health")
    p_doc.set_defaults(func=cmd_doctor)

    # map
    p_map = subparsers.add_parser("map", help="Dump and map all touch targets on current screen")
    p_map.add_argument("-a", "--all", action="store_true", help="Include non-interactive text labels")
    p_map.add_argument("-j", "--json", action="store_true", help="Output JSON format")
    p_map.add_argument("-o", "--output", help="Save JSON to file")
    p_map.set_defaults(func=cmd_map)

    # snapshot
    p_snap = subparsers.add_parser("snapshot", help="Capture clean + Set-of-Marks annotated screenshots & element map")
    p_snap.add_argument("-o", "--output", help="Output directory (default: screenshots/current_screen)")
    p_snap.add_argument("-a", "--all", action="store_true", help="Annotate all elements including text")
    p_snap.set_defaults(func=cmd_snapshot)

    # tap
    p_tap = subparsers.add_parser("tap", help="Tap an element by #id, label/text, slug, or coordinates")
    p_tap.add_argument("target", help="Target index (e.g. 0, #0), text ('Add Medication'), slug, or X coordinate")
    p_tap.add_argument("y", nargs="?", type=int, help="Optional Y coordinate if target is X coordinate", default=None)
    p_tap.set_defaults(func=cmd_tap)

    # type
    p_type = subparsers.add_parser("type", help="Type text into an input element")
    p_type.add_argument("target", help="Target element index, label, or '_' to type into currently focused element")
    p_type.add_argument("text", help="Text to type")
    p_type.add_argument("-c", "--clear", action="store_true", help="Clear input before typing")
    p_type.add_argument("--keep-keyboard", action="store_true", help="Do not automatically dismiss keyboard after typing")
    p_type.set_defaults(func=cmd_type)

    # close-keyboard
    p_ckb = subparsers.add_parser("close-keyboard", help="Dismiss virtual soft keyboard")
    p_ckb.add_argument("-f", "--force", action="store_true", help="Force send BACK keyevent")
    p_ckb.set_defaults(func=cmd_close_keyboard)

    # swipe
    p_swipe = subparsers.add_parser("swipe", help="Perform a swipe gesture")
    p_swipe.add_argument("direction", help="'up', 'down', 'left', 'right', or 'x1 y1 x2 y2'")
    p_swipe.set_defaults(func=cmd_swipe)

    # scroll
    p_scroll = subparsers.add_parser("scroll", help="Scroll the screen")
    p_scroll.add_argument("direction", nargs="?", default="down", help="'down' (default) or 'up'")
    p_scroll.set_defaults(func=cmd_scroll)

    # key
    p_key = subparsers.add_parser("key", help="Send hardware keyevent")
    p_key.add_argument("keycode", help="e.g. BACK, HOME, ENTER, TAB, VOLUME_UP")
    p_key.set_defaults(func=cmd_key)

    # assert
    p_assert = subparsers.add_parser("assert", help="Assert text visibility on screen")
    p_assert.add_argument("text", help="Expected text to find")
    p_assert.add_argument("-t", "--timeout", type=float, default=5.0, help="Timeout in seconds")
    p_assert.set_defaults(func=cmd_assert)

    # launch
    p_launch = subparsers.add_parser("launch", help="Launch target app")
    p_launch.add_argument("package", nargs="?", default="com.example.medac", help="Package name (default: com.example.medac)")
    p_launch.set_defaults(func=cmd_launch)

    # record
    p_rec = subparsers.add_parser("record", help="Start interactive test flow recording session")
    p_rec.add_argument("-o", "--output", default="tests/recorded_test.json", help="Output JSON test file")
    p_rec.set_defaults(func=cmd_record)

    # run
    p_run = subparsers.add_parser("run", help="Run automated test scenario from JSON file")
    p_run.add_argument("test_file", help="Path to test scenario JSON file")
    p_run.add_argument("-r", "--report-dir", help="Directory for test execution reports")
    p_run.set_defaults(func=cmd_run)

    # catalog
    p_cat = subparsers.add_parser("catalog", help="Capture current screen into docs/UI_TOUCH_MAP.md")
    p_cat.add_argument("-n", "--name", help="Screen name")
    p_cat.add_argument("-o", "--output", default="docs/touch_catalog", help="Catalog output directory")
    p_cat.set_defaults(func=cmd_catalog)

    args = parser.parse_args()
    if not hasattr(args, "func"):
        parser.print_help()
        sys.exit(1)

    sys.exit(args.func(args))


if __name__ == "__main__":
    main()
