#!/usr/bin/env python3
"""
mapper.py - Parses Android UI hierarchy XML into interactive UI elements,
handling Jetpack Compose semantics, container text promotion, coordinate extraction,
and semantic slug generation.
"""

import re
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional, Tuple


@dataclass
class UiElement:
    index: int
    role: str
    label: str
    text: str
    content_desc: str
    resource_id: str
    class_name: str
    bounds: Tuple[int, int, int, int]  # left, top, right, bottom
    center: Tuple[int, int]            # cx, cy
    width: int
    height: int
    clickable: bool
    focusable: bool
    editable: bool
    scrollable: bool
    checkable: bool
    checked: bool
    enabled: bool
    slug: str

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def parse_bounds(bounds_str: str) -> Tuple[int, int, int, int]:
    matches = re.findall(r"\[(\d+),(\d+)\]", bounds_str)
    if len(matches) == 2:
        return (
            int(matches[0][0]),
            int(matches[0][1]),
            int(matches[1][0]),
            int(matches[1][1]),
        )
    return (0, 0, 0, 0)


def slugify(text: str) -> str:
    s = re.sub(r"[^\w\s-]", "", text.strip().lower())
    s = re.sub(r"[\s_-]+", "_", s)
    return s[:35].strip("_")


class ScreenMap:
    def __init__(self, raw_xml: str, screen_size: Tuple[int, int] = (1080, 2400)):
        self.raw_xml = raw_xml
        self.screen_width, self.screen_height = screen_size
        self.elements: List[UiElement] = []
        self._parse()

    def _collect_descendant_text(self, node: ET.Element) -> Tuple[List[str], List[str]]:
        texts = []
        descs = []
        t = node.attrib.get("text", "").strip()
        d = node.attrib.get("content-desc", "").strip()
        if t:
            texts.append(t)
        if d:
            descs.append(d)
        for child in node:
            c_texts, c_descs = self._collect_descendant_text(child)
            texts.extend(c_texts)
            descs.extend(c_descs)
        return texts, descs

    def _determine_role(self, node: ET.Element, cls: str, is_editable: bool, is_clickable: bool, is_scrollable: bool, is_checkable: bool) -> str:
        cls_lower = cls.lower()
        if is_editable or "edittext" in cls_lower or node.attrib.get("password") == "true":
            return "INPUT"
        if is_checkable or "checkbox" in cls_lower or "switch" in cls_lower or "radio" in cls_lower:
            return "SWITCH" if "switch" in cls_lower else "CHECKBOX"
        if "button" in cls_lower:
            return "BUTTON"
        if "tab" in cls_lower or "navigation" in cls_lower:
            return "TAB"
        if is_scrollable:
            return "SCROLLVIEW"
        if is_clickable:
            return "BUTTON"
        if "image" in cls_lower:
            return "IMAGE"
        return "TEXT"

    def _parse(self):
        if not self.raw_xml or "<hierarchy" not in self.raw_xml:
            return

        try:
            root = ET.fromstring(self.raw_xml)
        except Exception:
            return

        raw_candidates = []

        # First pass: collect nodes with their XML metadata
        for node in root.iter("node"):
            attrib = node.attrib
            bounds = parse_bounds(attrib.get("bounds", ""))
            left, top, right, bottom = bounds
            width = right - left
            height = bottom - top

            # Filter out non-visible or degenerate bounds
            if width <= 0 or height <= 0:
                continue
            if right <= 0 or bottom <= 0 or left >= self.screen_width or top >= self.screen_height:
                continue

            text = attrib.get("text", "").strip()
            desc = attrib.get("content-desc", "").strip()
            res_id = attrib.get("resource-id", "").strip()
            cls_name = attrib.get("class", "").strip()
            clickable = attrib.get("clickable", "false").lower() == "true"
            focusable = attrib.get("focusable", "false").lower() == "true"
            scrollable = attrib.get("scrollable", "false").lower() == "true"
            checkable = attrib.get("checkable", "false").lower() == "true"
            checked = attrib.get("checked", "false").lower() == "true"
            enabled = attrib.get("enabled", "true").lower() == "true"
            password = attrib.get("password", "false").lower() == "true"

            # Determine if it is an input/editable field
            is_editable = "edittext" in cls_name.lower() or (focusable and ("text" in cls_name.lower() or password))

            # Compose often puts text inside a child of a clickable View
            desc_texts, desc_descs = self._collect_descendant_text(node)
            all_text_list = []
            if text:
                all_text_list.append(text)
            for dt in desc_texts:
                if dt not in all_text_list:
                    all_text_list.append(dt)

            all_desc_list = []
            if desc:
                all_desc_list.append(desc)
            for dd in desc_descs:
                if dd not in all_desc_list:
                    all_desc_list.append(dd)

            primary_label = " ".join(all_text_list) if all_text_list else " ".join(all_desc_list)
            if not primary_label and res_id:
                primary_label = res_id.split("/")[-1]

            role = self._determine_role(node, cls_name, is_editable, clickable, scrollable, checkable)

            # We care primarily about interactable elements or meaningful text elements
            is_interactive = clickable or is_editable or scrollable or checkable
            has_content = bool(text or desc)

            # Ignore root wrappers with no interaction
            if not is_interactive and not has_content:
                continue
            # Ignore full-screen backgrounds with no action
            if not is_interactive and width >= self.screen_width and height >= self.screen_height:
                continue

            cx = (left + right) // 2
            cy = (top + bottom) // 2

            raw_candidates.append({
                "role": role,
                "label": primary_label,
                "text": text,
                "content_desc": desc,
                "resource_id": res_id,
                "class_name": cls_name,
                "bounds": bounds,
                "center": (cx, cy),
                "width": width,
                "height": height,
                "clickable": clickable,
                "focusable": focusable,
                "editable": is_editable,
                "scrollable": scrollable,
                "checkable": checkable,
                "checked": checked,
                "enabled": enabled,
                "is_interactive": is_interactive,
            })

        # Deduplicate overlapping clickable containers & identical bounds
        # Keep the most specific and informative element
        filtered = []
        for cand in raw_candidates:
            b = cand["bounds"]
            # Check if an element with nearly identical bounds already exists
            dup = None
            for ex in filtered:
                ex_b = ex["bounds"]
                if abs(ex_b[0] - b[0]) < 5 and abs(ex_b[1] - b[1]) < 5 and abs(ex_b[2] - b[2]) < 5 and abs(ex_b[3] - b[3]) < 5:
                    dup = ex
                    break
            if dup:
                # Merge information: if new candidate has better label or interactivity, upgrade
                if not dup["is_interactive"] and cand["is_interactive"]:
                    dup["is_interactive"] = True
                    dup["role"] = cand["role"]
                    dup["clickable"] = cand["clickable"]
                if not dup["label"] and cand["label"]:
                    dup["label"] = cand["label"]
                if not dup["text"] and cand["text"]:
                    dup["text"] = cand["text"]
                if not dup["content_desc"] and cand["content_desc"]:
                    dup["content_desc"] = cand["content_desc"]
            else:
                filtered.append(cand)

        # Prioritize interactive elements first, then visible text anchors
        interactive_elements = [c for c in filtered if c["is_interactive"]]
        non_interactive_elements = [c for c in filtered if not c["is_interactive"] and c["label"]]

        final_list = interactive_elements + non_interactive_elements
        slug_counts: Dict[str, int] = {}

        self.elements = []
        for idx, item in enumerate(final_list):
            base_slug = slugify(item["label"]) if item["label"] else slugify(item["role"])
            if not base_slug:
                base_slug = f"elem_{idx}"

            slug_key = f"{item['role'].lower()}_{base_slug}"
            slug_counts[slug_key] = slug_counts.get(slug_key, 0) + 1
            if slug_counts[slug_key] > 1:
                final_slug = f"{slug_key}_{slug_counts[slug_key]}"
            else:
                final_slug = slug_key

            self.elements.append(
                UiElement(
                    index=idx,
                    role=item["role"],
                    label=item["label"],
                    text=item["text"],
                    content_desc=item["content_desc"],
                    resource_id=item["resource_id"],
                    class_name=item["class_name"],
                    bounds=item["bounds"],
                    center=item["center"],
                    width=item["width"],
                    height=item["height"],
                    clickable=item["clickable"],
                    focusable=item["focusable"],
                    editable=item["editable"],
                    scrollable=item["scrollable"],
                    checkable=item["checkable"],
                    checked=item["checked"],
                    enabled=item["enabled"],
                    slug=final_slug,
                )
            )

    def find(self, target: str) -> Optional[UiElement]:
        t = target.strip()
        # 1. Match index (#2, 2)
        if t.startswith("#") and t[1:].isdigit():
            idx = int(t[1:])
            return self.get_by_index(idx)
        if t.isdigit():
            idx = int(t)
            el = self.get_by_index(idx)
            if el:
                return el

        # 2. Match coordinates (e.g. "540,2155" or "540 2155")
        coord_m = re.match(r"^(\d+)[\s,]+(\d+)$", t)
        if coord_m:
            x, y = int(coord_m.group(1)), int(coord_m.group(2))
            return self.get_at_coordinates(x, y)

        # 3. Match exact slug
        for el in self.elements:
            if el.slug == t or el.slug.lower() == t.lower():
                return el

        # 4. Match exact label/text/desc
        for el in self.elements:
            if el.label.lower() == t.lower() or el.text.lower() == t.lower() or el.content_desc.lower() == t.lower():
                return el

        # 5. Match partial substring
        for el in self.elements:
            if t.lower() in el.label.lower() or t.lower() in el.slug.lower():
                return el

        return None

    def get_by_index(self, index: int) -> Optional[UiElement]:
        for el in self.elements:
            if el.index == index:
                return el
        return None

    def get_at_coordinates(self, x: int, y: int) -> Optional[UiElement]:
        # Search from smallest matching element upwards (most specific)
        matches = []
        for el in self.elements:
            l, top, r, b = el.bounds
            if l <= x <= r and top <= y <= b:
                matches.append(el)
        if not matches:
            return None
        # Return candidate with smallest area
        matches.sort(key=lambda e: e.width * e.height)
        return matches[0]

    def to_table(self, interactive_only: bool = False) -> str:
        items = [e for e in self.elements if e.clickable or e.editable or e.scrollable or e.checkable] if interactive_only else self.elements
        if not items:
            return "No matching UI elements found."

        headers = ["#", "Role", "Label / Text", "Center (x,y)", "Bounds", "Slug"]
        rows = []
        for el in items:
            lbl = el.label[:30] + ("..." if len(el.label) > 30 else "")
            rows.append([
                f"#{el.index}",
                el.role,
                lbl or "-",
                f"({el.center[0]},{el.center[1]})",
                f"[{el.bounds[0]},{el.bounds[1]}][{el.bounds[2]},{el.bounds[3]}]",
                el.slug,
            ])

        # Compute column widths
        col_widths = [len(h) for h in headers]
        for row in rows:
            for i, val in enumerate(row):
                col_widths[i] = max(col_widths[i], len(val))

        def format_row(r):
            return " | ".join(val.ljust(col_widths[i]) for i, val in enumerate(r))

        divider = "-+-".join("-" * w for w in col_widths)
        table_lines = [
            format_row(headers),
            divider,
        ]
        for row in rows:
            table_lines.append(format_row(row))

        return "\n".join(table_lines)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "screen_size": [self.screen_width, self.screen_height],
            "total_elements": len(self.elements),
            "elements": [e.to_dict() for e in self.elements],
        }

    def to_markdown(self) -> str:
        table_str = self.to_table(interactive_only=False)
        return f"### Mapped Screen Elements ({len(self.elements)} total)\n\n```\n{table_str}\n```\n"


if __name__ == "__main__":
    import subprocess
    xml = subprocess.check_output(["adb", "shell", "cat", "/sdcard/window_dump.xml"]).decode("utf-8")
    smap = ScreenMap(xml, (1080, 2392))
    print(smap.to_table())
