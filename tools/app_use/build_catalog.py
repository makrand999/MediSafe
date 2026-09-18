#!/usr/bin/env python3
"""
build_catalog.py - Automatically navigates through Medac's primary screens
and builds a comprehensive UI Touch Map & Set-of-Marks visual dictionary.
"""

import time
from tools.app_use.actions import ActionEngine
from tools.app_use.catalog import AppCataloger
from tools.app_use.core import AdbDevice


def catalog_medac():
    dev = AdbDevice()
    actions = ActionEngine(dev)
    cataloger = AppCataloger(output_dir="docs/touch_catalog", device=dev)

    print("Starting automated Medac screen touch mapping...")

    # 1. Dashboard / Today
    try:
        actions.tap("Today")
    except Exception:
        pass
    time.sleep(1)
    cataloger.capture_screen("Today Dashboard")

    # 2. Add Medication Overlay
    try:
        print("Navigating to Add Medication...")
        actions.tap("Add Medication")
        time.sleep(1)
        cataloger.capture_screen("Add Medication Screen")
        # Go back to Dashboard
        actions.key("BACK")
        time.sleep(0.8)
    except Exception as e:
        print(f"Could not catalog Add Medication: {e}")

    # 3. Calendar View
    try:
        print("Navigating to Calendar View...")
        actions.tap("Calendar View")
        time.sleep(1)
        cataloger.capture_screen("Calendar Screen")
        actions.key("BACK")
        time.sleep(0.8)
    except Exception as e:
        print(f"Could not catalog Calendar View: {e}")

    # 4. History Log
    try:
        print("Navigating to History Log...")
        actions.tap("History Log")
        time.sleep(1)
        cataloger.capture_screen("History Log Screen")
        actions.key("BACK")
        time.sleep(0.8)
    except Exception as e:
        print(f"Could not catalog History Log: {e}")

    # 5. Refill Tracker
    try:
        print("Navigating to Refill Tracker...")
        actions.tap("Refill Tracker")
        time.sleep(1)
        cataloger.capture_screen("Refill Tracker Screen")
        actions.key("BACK")
        time.sleep(0.8)
    except Exception as e:
        print(f"Could not catalog Refill Tracker: {e}")

    # 6. Medicines Tab
    try:
        print("Navigating to Medicines Tab...")
        actions.tap("Medicines")
        time.sleep(1)
        cataloger.capture_screen("Medicines Screen")
    except Exception as e:
        print(f"Could not catalog Medicines: {e}")

    # 7. Reminders Tab
    try:
        print("Navigating to Reminders Tab...")
        actions.tap("Reminders")
        time.sleep(1)
        cataloger.capture_screen("Reminders Screen")
    except Exception as e:
        print(f"Could not catalog Reminders: {e}")

    # 8. Profile Tab
    try:
        print("Navigating to Profile Tab...")
        actions.tap("Profile")
        time.sleep(1)
        cataloger.capture_screen("Profile Screen")
    except Exception as e:
        print(f"Could not catalog Profile: {e}")

    # Return to Today
    try:
        actions.tap("Today")
        time.sleep(0.5)
    except Exception:
        pass

    print("\nCompiling Master UI Touch Map Documentation...")
    cataloger.generate_master_markdown("docs/UI_TOUCH_MAP.md")
    cataloger.generate_master_json("docs/ui_touch_map.json")
    print("Cataloging complete!")


if __name__ == "__main__":
    catalog_medac()
