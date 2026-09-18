#!/usr/bin/env python3
"""
runner.py - Automated test playback engine.
Loads test scenarios (JSON), dynamically resolves UI elements on screen,
executes interactions, verifies assertions, and produces comprehensive test reports.
"""

import json
import os
import sys
import time
from typing import Any, Dict, List, Optional

from .actions import ActionEngine
from .annotator import annotate_screenshot
from .core import AdbDevice


class TestRunner:
    def __init__(self, test_file: str, report_dir: Optional[str] = None, device: Optional[AdbDevice] = None):
        self.test_file = test_file
        self.device = device or AdbDevice()
        self.actions = ActionEngine(self.device)
        self.report_dir = report_dir or os.path.join(
            "test_reports", time.strftime("%Y%m%d_%H%M%S")
        )
        self.results: List[Dict[str, Any]] = []

    def run(self) -> bool:
        if not os.path.exists(self.test_file):
            raise FileNotFoundError(f"Test scenario file not found: {self.test_file}")

        with open(self.test_file, "r") as f:
            data = json.load(f)

        test_name = data.get("name", os.path.basename(self.test_file))
        steps = data.get("steps", [])
        os.makedirs(self.report_dir, exist_ok=True)

        print("=" * 60)
        print(f"  RUNNING TEST SUITE: {test_name}")
        print(f"  Target Device: {self.device.serial}")
        print(f"  Total Steps:   {len(steps)}")
        print(f"  Report Directory: {self.report_dir}")
        print("=" * 60)

        all_passed = True
        overall_start = time.time()

        for idx, step in enumerate(steps):
            step_num = step.get("step", idx + 1)
            action = step.get("action", "").lower()
            step_start = time.time()
            step_res = {
                "step": step_num,
                "action": action,
                "status": "PASS",
                "error": None,
                "duration": 0.0,
                "screenshot": None,
            }

            print(f"\n[Step {step_num}/{len(steps)}] Action: {action} | Params: {step}")

            try:
                if action == "tap":
                    target = step.get("target") or step.get("label") or step.get("slug")
                    tapped = self.actions.tap(target)
                    step_res["details"] = f"Tapped {tapped.label} at ({tapped.center[0]},{tapped.center[1]})"

                elif action == "type":
                    target = step.get("target")
                    text = step.get("text", "")
                    self.actions.type_text(target, text, clear_first=step.get("clear", False))
                    step_res["details"] = f"Typed into {target}: '{text}'"

                elif action in ("swipe", "scroll"):
                    direction = step.get("direction", "up")
                    self.actions.swipe(direction)
                    step_res["details"] = f"Swiped {direction}"

                elif action == "key":
                    code = step.get("keycode", "BACK")
                    self.actions.key(code)
                    step_res["details"] = f"Pressed key {code}"

                elif action == "assert_text":
                    expected = step.get("text", "")
                    timeout = step.get("timeout", 5.0)
                    found = self.actions.assert_text(expected, timeout=timeout)
                    step_res["details"] = f"Asserted visible text: '{found.label}'"

                elif action == "assert_element":
                    target = step.get("target", "")
                    timeout = step.get("timeout", 5.0)
                    found = self.actions.assert_element(target, timeout=timeout)
                    step_res["details"] = f"Asserted element exists: '{found.slug}'"

                elif action == "wait":
                    sec = step.get("seconds", 1.0)
                    self.actions.wait(sec)
                    step_res["details"] = f"Waited {sec}s"

                elif action == "launch":
                    pkg = step.get("package", "com.example.medac")
                    self.device.launch_app(pkg)
                    step_res["details"] = f"Launched app {pkg}"

                else:
                    raise ValueError(f"Unknown action type: {action}")

                # Slight delay between actions for animations
                time.sleep(step.get("delay_after", 0.5))

            except Exception as e:
                step_res["status"] = "FAIL"
                step_res["error"] = str(e)
                all_passed = False
                print(f"  FAILED: {e}")

                # Capture failure screenshot and map
                fail_screen = os.path.join(self.report_dir, f"failure_step_{step_num}.png")
                fail_annotated = os.path.join(self.report_dir, f"failure_step_{step_num}_annotated.png")
                try:
                    self.device.take_screenshot(fail_screen)
                    smap = self.actions.get_screen_map()
                    annotate_screenshot(fail_screen, smap, fail_annotated)
                    step_res["screenshot"] = fail_annotated
                except Exception:
                    pass

                # If continue_on_error is not True, stop
                if not step.get("continue_on_error", False):
                    step_res["duration"] = round(time.time() - step_start, 2)
                    self.results.append(step_res)
                    break

            step_res["duration"] = round(time.time() - step_start, 2)
            self.results.append(step_res)
            print(f"  RESULT: {step_res['status']} ({step_res['duration']}s)")

        total_dur = round(time.time() - overall_start, 2)
        self._generate_report(test_name, total_dur, all_passed)
        return all_passed

    def _generate_report(self, test_name: str, duration: float, passed: bool):
        report_data = {
            "test_name": test_name,
            "passed": passed,
            "duration_seconds": duration,
            "total_steps": len(self.results),
            "passed_steps": sum(1 for r in self.results if r["status"] == "PASS"),
            "failed_steps": sum(1 for r in self.results if r["status"] == "FAIL"),
            "steps": self.results,
        }

        # Save JSON report
        json_path = os.path.join(self.report_dir, "test_report.json")
        with open(json_path, "w") as f:
            json.dump(report_data, f, indent=2)

        # Save Markdown report
        md_lines = [
            f"# Test Report: {test_name}",
            "",
            f"- **Status**: {'PASSED' if passed else 'FAILED'}",
            f"- **Duration**: {duration}s",
            f"- **Total Steps**: {len(self.results)}",
            f"- **Passed Steps**: {report_data['passed_steps']}",
            f"- **Failed Steps**: {report_data['failed_steps']}",
            "",
            "## Steps Summary",
            "",
            "| Step | Action | Status | Duration | Details |",
            "|---|---|---|---|---|",
        ]
        for r in self.results:
            err = f" (Error: {r['error']})" if r["error"] else ""
            details = r.get("details", "") + err
            md_lines.append(f"| {r['step']} | `{r['action']}` | **{r['status']}** | {r['duration']}s | {details} |")

        md_path = os.path.join(self.report_dir, "test_report.md")
        with open(md_path, "w") as f:
            f.write("\n".join(md_lines) + "\n")

        print("\n" + "=" * 60)
        print(f"  TEST RUN {'PASSED' if passed else 'FAILED'} in {duration}s")
        print(f"  Summary saved to: {md_path}")
        print("=" * 60)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: runner.py <test_scenario.json>")
        sys.exit(1)
    runner = TestRunner(sys.argv[1])
    success = runner.run()
    sys.exit(0 if success else 1)
