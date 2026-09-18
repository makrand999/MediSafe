# Medac App Use & Automated Testing Tool (`app-use`)

A complete automated device interaction, touch mapping, and test execution tool suite for Android. It eliminates the need to manually guess or compute touch coordinates when testing mobile apps.

---

## Key Features

1. **Intelligent Screen Mapping (`app-use map`)**:
   - Parses the live UI hierarchy and Jetpack Compose composables.
   - Extracts all interactive elements (buttons, inputs, tabs, checkboxes, switches, scrollable areas) and visible text.
   - Calculates exact center coordinates `(x, y)` and bounding boxes `[left, top, right, bottom]`.
   - Generates stable semantic slugs (`button_add_medication`, `input_search`, `tab_medicines`) and sequential indices (`#0`, `#1`, ...).

2. **Set-of-Marks Visual Annotations (`app-use snapshot`)**:
   - Captures clean device screenshots and overlays high-contrast, color-coded bounding boxes and badges (`#0`, `#1`, ...).
   - Generates:
     - `screen_clean.png` (raw screenshot)
     - `screen_annotated.png` (Set-of-Marks visual overlay)
     - `screen_map.json` (machine-readable dictionary)

3. **Smart Interactivity & Auto-Keyboard Management**:
   - `tap <target>`: Tap by label (`"Medicines"`), index (`#0`), slug (`button_add`), or coordinates (`540 2155`).
   - `type <target> <text>`: Tap-to-focus and types text into inputs. Automatically closes the soft-keyboard to ensure bottom navigation items are accessible.
   - `close-keyboard`: Checks and dismisses the IME keyboard if visible.
   - `swipe <up|down|left|right>` / `scroll <down|up>`: Smooth gestures.
   - `key <KEYCODE>`: Hardware key events (BACK, HOME, ENTER, etc.).
   - `assert <text>`: Waits and asserts text visibility.

4. **Automated Test Recording & Playback**:
   - `app-use record -o my_test.json`: Interactive session that records touches and inputs into reusable test scripts.
   - `app-use run my_test.json`: Plays back test scripts, dynamically resolving elements so layout shifts never break tests. Produces pass/fail markdown and JSON reports.

5. **App Screen Cartographer (`docs/UI_TOUCH_MAP.md`)**:
   - Explores app screens and maintains a complete UI Touch Map catalog with annotated screenshots for all screens in the app.

---

## Quick Reference & Commands

```bash
# 1. System health check
./tools/app-use doctor

# 2. Inspect all touchable targets on current screen
./tools/app-use map

# 3. Capture clean & annotated Set-of-Marks screenshots
./tools/app-use snapshot -o screenshots/my_screen

# 4. Tap an element by label, index, or slug
./tools/app-use tap "Medicines"
./tools/app-use tap "#2"
./tools/app-use tap button_add_medication

# 5. Type text into an input field (auto-dismisses keyboard)
./tools/app-use type "Search" "Dolo"

# 6. Close the virtual keyboard
./tools/app-use close-keyboard

# 7. Gesture and navigation
./tools/app-use scroll down
./tools/app-use scroll up
./tools/app-use key BACK

# 8. Run an automated test suite
./tools/app-use run tools/app_use/tests/dashboard_navigation_test.json

# 9. Record a new test flow interactively
./tools/app-use record -o tests/new_flow.json

# 10. Re-build the full app touch catalog
python3 -m tools.app_use.build_catalog
```

---

## Catalog Documentation

See [`docs/UI_TOUCH_MAP.md`](../docs/UI_TOUCH_MAP.md) for the complete reference catalog of all screens in Medac and their mapped touch points.
