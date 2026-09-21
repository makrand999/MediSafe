# Android ADB Agent Navigation & Testing Toolkit

A lightweight perception-action toolkit allowing LLM agents to interact, test, and navigate the Android application through ADB using indexed UI elements rather than hardcoded coordinates.

---

## 1. Overview & Directory Structure

```
tools/adb_agent/
├── agent_tool.py    # Main CLI entry point for agents
├── device.py        # ADB abstraction layer (shell, input, capture, app lifecycle)
├── inspector.py     # uiautomator XML parser & interactive element indexer
└── README.md        # This context & usage guide
```

- **Perception:** Dumps the UI accessibility hierarchy (`uiautomator dump`), filters and sorts interactive nodes top-to-bottom / left-to-right, assigns 1-based indices (`[#1]`, `[#2]`, ...), and pulls a screenshot to `/tmp/screen.png`.
- **State Caching:** Element coordinates are persisted in `/tmp/adb_last_snapshot.json` so actions can reference elements by numeric index (`tap 4`).
- **Action Execution:** Converts indices, text selectors, or keys into safe ADB shell input commands with full character escaping (including spaces, `#`, quotes, symbols).

---

## 2. Prerequisites & Device Connection

The device connects via ADB (local emulator or Windows reverse SSH tunnel on port `5037`):

```bash
# Check connected device
adb devices -l
# Expected: emulator-5554  device  model:ASUS_I001D
```

If using the Windows SSH tunnel:
```powershell
# On Windows PowerShell:
ssh -N -v -R 127.0.0.1:5037:127.0.0.1:5037 root@<SERVER_IP>
```

---

## 3. Command Reference

All commands run via `python3 tools/adb_agent/agent_tool.py <command> [arguments]`.

### `launch`
Launches the target app (defaults to `com.example.medac/.MainActivity`) and prints an immediate UI snapshot.
```bash
python3 tools/adb_agent/agent_tool.py launch
# Custom package/activity:
python3 tools/adb_agent/agent_tool.py launch --package com.example.medac --activity com.example.medac.MainActivity
```

### `snapshot`
Captures the current screen hierarchy, extracts visible/interactive elements with indices, and saves a screenshot to `/tmp/screen.png`.
```bash
python3 tools/adb_agent/agent_tool.py snapshot
```

### `tap`
Taps an element. Accepts:
- Numeric index (e.g. `3` or `#3`) from the last snapshot.
- Text substring (e.g. `"Log in"`, `"Medicines"`, `"Save"`).
- Resource ID (e.g. `"searchEditText"`).
- Exact coordinates (`"x y"`).
```bash
python3 tools/adb_agent/agent_tool.py tap 3
python3 tools/adb_agent/agent_tool.py tap "Log in"
python3 tools/adb_agent/agent_tool.py tap "960 934"

# Use --snapshot to automatically re-dump UI after tapping:
python3 tools/adb_agent/agent_tool.py tap 13 --snapshot
```

### `type`
Types text into an input field. Special characters (`#`, spaces, `&`, quotes) are automatically escaped for ADB.
```bash
# Type into active/focused field:
python3 tools/adb_agent/agent_tool.py type "user@example.com"

# Tap target element first, then type:
python3 tools/adb_agent/agent_tool.py type "user@example.com" --target 3
python3 tools/adb_agent/agent_tool.py type "#SecretPass123" --target 7 --snapshot
```

### `clear-text`
Sends backspace keyevents to clear an active text field.
```bash
python3 tools/adb_agent/agent_tool.py clear-text
python3 tools/adb_agent/agent_tool.py clear-text --count 50
```

### `scroll`
Performs directional gesture scrolling (`down`, `up`, `left`, `right`).
```bash
python3 tools/adb_agent/agent_tool.py scroll down
python3 tools/adb_agent/agent_tool.py scroll up --snapshot
```

### `key`
Sends Android hardware/navigation keyevents.
Supported names: `back`, `home`, `enter`, `tab`, `space`, `del`, `escape`, `recents`.
```bash
python3 tools/adb_agent/agent_tool.py key back
python3 tools/adb_agent/agent_tool.py key enter --snapshot
```

### `wait`
Polls the UI until the specified text or element ID appears (useful for async screens/network loads).
```bash
python3 tools/adb_agent/agent_tool.py wait "My Medicines" --timeout 10
```

### `assert`
Verifies that expected text exists on the current screen. Exits with return code 0 on success, 1 on failure.
```bash
python3 tools/adb_agent/agent_tool.py assert "PARACIP-500"
```

### `stop` & `restart`
App lifecycle management.
```bash
python3 tools/adb_agent/agent_tool.py stop
python3 tools/adb_agent/agent_tool.py restart
```

### `logs`
Dumps recent error logs from Logcat filtered by severity.
```bash
python3 tools/adb_agent/agent_tool.py logs --limit 50
```

---

## 4. End-to-End Agent Workflow Example

### Example: Automated Login & Navigating to Medicines

```bash
# 1. Launch Medac app
python3 tools/adb_agent/agent_tool.py launch

# 2. Inspect elements (snapshot shows Email field as #3, Password field as #7)
# 3. Enter credentials
python3 tools/adb_agent/agent_tool.py type "makarandshinde8i@gmail.com" --target 3
python3 tools/adb_agent/agent_tool.py type "#Freefire123456" --target 7

# 4. Tap "Log in" and refresh snapshot
python3 tools/adb_agent/agent_tool.py tap "Log in" --snapshot

# 5. Navigate to Medicines tab (tab labeled "Medicines")
python3 tools/adb_agent/agent_tool.py tap "Medicines" --snapshot

# 6. Assert medication is visible
python3 tools/adb_agent/agent_tool.py assert "PARACIP-500"
```
