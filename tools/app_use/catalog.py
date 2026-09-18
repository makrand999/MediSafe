#!/usr/bin/env python3
"""
catalog.py - App Screen Cartographer and UI Touch Dictionary Generator.
Catalogs screens across the app, capturing annotated screenshots and generating
a persistent master reference document (docs/UI_TOUCH_MAP.md) of all touch targets.
"""

import json
import os
import time
from typing import Dict, List, Optional

from .actions import ActionEngine
from .annotator import annotate_screenshot
from .core import AdbDevice
from .mapper import ScreenMap


class AppCataloger:
    def __init__(self, output_dir: str = "docs/touch_catalog", device: Optional[AdbDevice] = None):
        self.output_dir = output_dir
        self.device = device or AdbDevice()
        self.actions = ActionEngine(self.device)
        self.screens: Dict[str, Dict] = {}

    def capture_screen(self, screen_name: str) -> Dict:
        os.makedirs(self.output_dir, exist_ok=True)
        slug_name = screen_name.lower().replace(" ", "_")

        clean_png = os.path.join(self.output_dir, f"{slug_name}_clean.png")
        annotated_png = os.path.join(self.output_dir, f"{slug_name}_annotated.png")
        map_json = os.path.join(self.output_dir, f"{slug_name}_map.json")

        self.device.take_screenshot(clean_png)
        smap = self.actions.get_screen_map()
        annotate_screenshot(clean_png, smap, annotated_png)

        with open(map_json, "w") as f:
            json.dump(smap.to_dict(), f, indent=2)

        entry = {
            "name": screen_name,
            "slug": slug_name,
            "clean_image": clean_png,
            "annotated_image": annotated_png,
            "json_path": map_json,
            "elements": [e.to_dict() for e in smap.elements if e.clickable or e.editable or e.scrollable or e.checkable],
            "all_elements_count": len(smap.elements),
            "table": smap.to_table(interactive_only=True),
        }
        self.screens[slug_name] = entry
        print(f"Captured screen '{screen_name}': {len(entry['elements'])} interactive targets mapped.")
        return entry

    def generate_master_markdown(self, output_md: str = "docs/UI_TOUCH_MAP.md"):
        os.makedirs(os.path.dirname(os.path.abspath(output_md)), exist_ok=True)
        lines = [
            "# Medac App UI Touch & Input Map",
            "",
            "This document maps all interactive buttons, inputs, tabs, and touch targets across the app.",
            "Use this catalog for automated testing and agent interaction so coordinates and element selectors never need to be guessed.",
            "",
            f"**Device Screen Resolution**: `{self.device.get_screen_size()[0]}x{self.device.get_screen_size()[1]}`",
            f"**Last Updated**: {time.strftime('%Y-%m-%d %H:%M:%S')}",
            "",
            "---",
            "",
            "## Table of Screens",
            "",
        ]

        for slug, data in self.screens.items():
            lines.append(f"- [{data['name']}](#{slug}) ({len(data['elements'])} interactive targets)")

        lines.append("")
        lines.append("---")
        lines.append("")

        for slug, data in self.screens.items():
            lines.extend([
                f"<a id=\"{slug}\"></a>",
                f"## {data['name']}",
                "",
                f"- **Annotated Screenshot**: `file://{os.path.abspath(data['annotated_image'])}`",
                f"- **Data File**: `file://{os.path.abspath(data['json_path'])}`",
                f"- **Interactive Elements Count**: {len(data['elements'])}",
                "",
                "### Interactive Elements Table",
                "",
                "```",
                data["table"],
                "```",
                "",
                "---",
                "",
            ])

        with open(output_md, "w") as f:
            f.write("\n".join(lines))
        print(f"Master touch documentation written to: {output_md}")

    def generate_master_json(self, output_json: str = "docs/ui_touch_map.json"):
        os.makedirs(os.path.dirname(os.path.abspath(output_json)), exist_ok=True)
        data = {
            "device": self.device.serial,
            "resolution": list(self.device.get_screen_size()),
            "screens": self.screens,
        }
        with open(output_json, "w") as f:
            json.dump(data, f, indent=2)
        print(f"Master touch JSON written to: {output_json}")


if __name__ == "__main__":
    cat = AppCataloger()
    cat.capture_screen("Current Screen")
    cat.generate_master_markdown()
    cat.generate_master_json()
