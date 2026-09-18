#!/usr/bin/env python3
"""
annotator.py - Generates Set-of-Marks (SoM) visual annotations on screenshots,
highlighting bounding boxes, touch center points, and numbered badges for every element.
"""

import os
from typing import List, Optional, Tuple
from PIL import Image, ImageDraw, ImageFont

from .mapper import ScreenMap, UiElement

# High-contrast modern color palette for UI roles
ROLE_COLORS = {
    "BUTTON": (16, 185, 129, 255),    # Emerald
    "INPUT": (245, 158, 11, 255),     # Amber / Orange
    "TAB": (99, 102, 241, 255),       # Indigo
    "SWITCH": (236, 72, 153, 255),    # Pink
    "CHECKBOX": (236, 72, 153, 255),  # Pink
    "SCROLLVIEW": (6, 182, 212, 255),  # Cyan
    "IMAGE": (139, 92, 246, 255),     # Violet
    "TEXT": (148, 163, 184, 180),     # Subtle Slate
}

FALLBACK_COLOR = (59, 130, 246, 255)  # Blue


def _get_font(size: int = 24) -> ImageFont.ImageFont:
    font_candidates = [
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        "/system/fonts/Roboto-Bold.ttf",
    ]
    for p in font_candidates:
        if os.path.isfile(p):
            try:
                return ImageFont.truetype(p, size=size)
            except Exception:
                pass
    return ImageFont.load_default()


def annotate_screenshot(
    image_path: str,
    screen_map: ScreenMap,
    output_path: str,
    interactive_only: bool = False,
    draw_crosshairs: bool = True,
) -> str:
    """
    Renders bounding boxes, badges (#0, #1), and touch crosshairs on the screenshot.
    """
    img = Image.open(image_path).convert("RGBA")
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    font_badge = _get_font(size=22)
    font_sub = _get_font(size=16)

    elements_to_draw = (
        [e for e in screen_map.elements if e.clickable or e.editable or e.scrollable or e.checkable]
        if interactive_only
        else screen_map.elements
    )

    for el in elements_to_draw:
        color = ROLE_COLORS.get(el.role, FALLBACK_COLOR)
        l, top, r, b = el.bounds
        cx, cy = el.center

        # 1. Bounding box
        line_width = 3 if el.clickable or el.editable else 2
        draw.rectangle([l, top, r, b], outline=color, width=line_width)

        # 2. Touch center crosshair
        if draw_crosshairs and (el.clickable or el.editable):
            ch_r = 6
            # Red center dot
            draw.ellipse([cx - ch_r, cy - ch_r, cx + ch_r, cy + ch_r], fill=(239, 68, 68, 240), outline=(255, 255, 255, 255), width=2)

        # 3. Badge with element number and role
        badge_text = f"#{el.index}"
        if el.role in ("INPUT", "TAB", "SWITCH"):
            badge_text += f":{el.role[:3]}"

        try:
            bbox = draw.textbbox((0, 0), badge_text, font=font_badge)
            tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        except Exception:
            tw, th = 30, 20

        pad_x, pad_y = 6, 4
        bw = tw + pad_x * 2
        bh = th + pad_y * 2

        # Position badge at top-left of element bounds, clamped inside image
        bx = max(4, min(l, img.width - bw - 4))
        by = max(4, top - bh - 2 if top - bh >= 4 else top + 2)

        # Badge background
        draw.rounded_rectangle([bx, by, bx + bw, by + bh], radius=6, fill=color)
        # Badge text (white)
        draw.text((bx + pad_x, by + pad_y - 1), badge_text, fill=(255, 255, 255, 255), font=font_badge)

    # Composite overlay onto main screenshot
    annotated = Image.alpha_composite(img, overlay)
    annotated = annotated.convert("RGB")
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    annotated.save(output_path, "PNG")
    return output_path


if __name__ == "__main__":
    import subprocess
    from .core import AdbDevice
    dev = AdbDevice()
    xml = dev.dump_hierarchy()
    smap = ScreenMap(xml, dev.get_screen_size())
    dev.take_screenshot("/tmp/test_clean.png")
    out = annotate_screenshot("/tmp/test_clean.png", smap, "/tmp/test_annotated.png")
    print("Annotated saved to:", out)
