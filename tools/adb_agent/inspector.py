import xml.etree.ElementTree as ET
import re
import json
import os

SNAPSHOT_CACHE_FILE = "/tmp/adb_last_snapshot.json"

class UIElement:
    def __init__(self, index, tag, text, desc, res_id, bounds, center, clickable, editable, scrollable):
        self.index = index
        self.tag = tag
        self.text = text
        self.desc = desc
        self.res_id = res_id
        self.bounds = bounds
        self.center = center
        self.clickable = clickable
        self.editable = editable
        self.scrollable = scrollable

    def to_dict(self):
        return {
            "index": self.index,
            "tag": self.tag,
            "text": self.text,
            "desc": self.desc,
            "id": self.res_id,
            "bounds": self.bounds,
            "center": self.center,
            "clickable": self.clickable,
            "editable": self.editable,
            "scrollable": self.scrollable,
        }

    def summary(self):
        parts = [f"[#{self.index}] {self.tag}"]
        if self.text:
            parts.append(f'text="{self.text}"')
        if self.desc:
            parts.append(f'desc="{self.desc}"')
        if self.res_id:
            short_id = self.res_id.split("/")[-1]
            parts.append(f'id="{short_id}"')
        flags = []
        if self.clickable: flags.append("clickable")
        if self.editable: flags.append("editable")
        if self.scrollable: flags.append("scrollable")
        if flags:
            parts.append(f"({', '.join(flags)})")
        parts.append(f"at {self.center}")
        return " ".join(parts)


def parse_bounds(bounds_str):
    # format: [x1,y1][x2,y2]
    m = re.findall(r"\[(\d+),(\d+)\]", bounds_str)
    if len(m) == 2:
        x1, y1 = int(m[0][0]), int(m[0][1])
        x2, y2 = int(m[1][0]), int(m[1][1])
        cx = (x1 + x2) // 2
        cy = (y1 + y2) // 2
        return [x1, y1, x2, y2], (cx, cy)
    return [0, 0, 0, 0], (0, 0)


def inspect_xml(xml_content, screen_w=1080, screen_h=1920):
    if not xml_content.strip():
        return []

    try:
        root = ET.fromstring(xml_content)
    except Exception as e:
        print(f"Error parsing XML: {e}")
        return []

    elements = []
    
    def walk(node):
        text = node.attrib.get("text", "").strip()
        desc = node.attrib.get("content-desc", "").strip()
        res_id = node.attrib.get("resource-id", "").strip()
        cls = node.attrib.get("class", "").split(".")[-1]
        clickable = node.attrib.get("clickable", "false") == "true"
        focusable = node.attrib.get("focusable", "false") == "true"
        scrollable = node.attrib.get("scrollable", "false") == "true"
        bounds_str = node.attrib.get("bounds", "[0,0][0,0]")
        bounds, center = parse_bounds(bounds_str)

        # Check if editable (EditText or contains EditText in class)
        editable = "EditText" in node.attrib.get("class", "")

        # Determine if node is relevant: has meaningful text, description, or is interactive
        has_content = bool(text or desc or res_id)
        is_interactive = clickable or editable or scrollable

        # Ignore offscreen or 0-area bounds
        w = bounds[2] - bounds[0]
        h = bounds[3] - bounds[1]
        visible = w > 0 and h > 0 and bounds[2] > 0 and bounds[0] < screen_w and bounds[3] > 0 and bounds[1] < screen_h

        if visible and (has_content or is_interactive):
            elements.append({
                "tag": cls,
                "text": text,
                "desc": desc,
                "res_id": res_id,
                "bounds": bounds,
                "center": center,
                "clickable": clickable,
                "editable": editable,
                "scrollable": scrollable,
                "y": center[1],
                "x": center[0],
            })

        for child in node:
            walk(child)

    walk(root)

    # Sort primarily top-to-bottom, secondarily left-to-right
    elements.sort(key=lambda e: (e["y"] // 30, e["x"]))

    parsed_elements = []
    for idx, e in enumerate(elements, start=1):
        el = UIElement(
            index=idx,
            tag=e["tag"],
            text=e["text"],
            desc=e["desc"],
            res_id=e["res_id"],
            bounds=e["bounds"],
            center=e["center"],
            clickable=e["clickable"],
            editable=e["editable"],
            scrollable=e["scrollable"]
        )
        parsed_elements.append(el)

    # Save to cache
    save_snapshot(parsed_elements)
    return parsed_elements


def save_snapshot(elements, path=SNAPSHOT_CACHE_FILE):
    data = [el.to_dict() for el in elements]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def load_last_snapshot(path=SNAPSHOT_CACHE_FILE):
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return [
        UIElement(
            index=d["index"],
            tag=d["tag"],
            text=d["text"],
            desc=d["desc"],
            res_id=d["id"],
            bounds=d["bounds"],
            center=tuple(d["center"]),
            clickable=d["clickable"],
            editable=d["editable"],
            scrollable=d["scrollable"],
        )
        for d in data
    ]
