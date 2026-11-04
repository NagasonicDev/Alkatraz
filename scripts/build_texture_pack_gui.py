"""Create the pixel-art standard GUI models and material dispatch definitions."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
MODELS = PACK / "assets/alkatraz/models/item"
TEXTURES = PACK / "assets/alkatraz/textures/item"
ITEM_DEFS = PACK / "assets/minecraft/items"

ASSETS = {
    "blank_pane": ("GRAY_STAINED_GLASS_PANE", 4004, "pane_blank"),
    "border_pane": ("GRAY_STAINED_GLASS_PANE", 4005, "pane_border"),
    "header_pane": ("GRAY_STAINED_GLASS_PANE", 4006, "pane_header"),
    "next_page": ("ARROW", 4001, "next"), "prev_page": ("ARROW", 4002, "previous"),
    "back_button": ("BARRIER", 4003, "back"), "confirm_button": ("LIME_CONCRETE", 4007, "check"),
    "cancel_button": ("RED_CONCRETE", 4008, "cross"), "info_button": ("KNOWLEDGE_BOOK", 4009, "info"),
    "close_button": ("BARRIER", 4010, "close"), "search_button": ("COMPASS", 4011, "search"),
    "sort_button": ("HOPPER", 4012, "sort"), "create_button": ("EMERALD", 4013, "plus"),
    "duplicate_button": ("ENDER_PEARL", 4014, "duplicate"),
}

PALETTES = {
    "next": ("#26333D", "#E8F3F8", "#78C8E8"), "previous": ("#26333D", "#E8F3F8", "#78C8E8"),
    "back": ("#451D28", "#FFD5D8", "#FF777F"), "check": ("#164E2C", "#D8FFE2", "#65E08A"),
    "cross": ("#54202A", "#FFE1E1", "#FF6F77"), "info": ("#173C65", "#E0F3FF", "#6BC8FF"),
    "close": ("#451D28", "#FFD5D8", "#FF777F"), "search": ("#244E70", "#E0F5FF", "#6BC8FF"),
    "sort": ("#655026", "#FFF2C6", "#F4C65E"), "plus": ("#165C36", "#D8FFE6", "#62E59B"),
    "duplicate": ("#433078", "#E8DFFF", "#AC91FF"),
}


def draw_icon(canvas, icon, fg, accent):
    d = ImageDraw.Draw(canvas)
    p = lambda xy, color=fg: d.point(xy, fill=color)
    line = lambda points, color=fg, width=2: d.line(points, fill=color, width=width)
    if icon in ("next", "previous", "back"):
        direction = -1 if icon in ("previous", "back") else 1
        x0, x1 = (4, 12) if direction > 0 else (3, 11)
        line([(x0, 8), (x1, 8)], width=2)
        tip = x1 if direction > 0 else x0
        tail = tip - 3 * direction
        line([(tail, 5), (tip, 8), (tail, 11)], color=accent, width=2)
        if icon == "back":
            line([(4, 4), (2, 4), (2, 12), (4, 12)], color=accent, width=1)
    elif icon in ("check", "cross", "close"):
        if icon == "check":
            line([(3, 8), (6, 11), (13, 4)], color=accent, width=3)
        else:
            line([(4, 4), (12, 12)], color=accent, width=2)
            line([(12, 4), (4, 12)], color=accent, width=2)
    elif icon == "info":
        d.ellipse((3, 3, 12, 12), outline=accent, width=1)
        d.rectangle((7, 7, 8, 11), fill=fg)
        d.rectangle((7, 5, 8, 6), fill=accent)
    elif icon == "search":
        d.ellipse((2, 2, 9, 9), outline=accent, width=2)
        line([(9, 9), (14, 14)], color=fg, width=2)
    elif icon == "sort":
        for y, x in [(4, 4), (7, 6), (10, 8), (13, 10)]:
            line([(3, y), (x, y)], color=fg, width=1)
        line([(11, 4), (13, 6), (11, 8)], color=accent, width=1)
    elif icon == "plus":
        line([(8, 3), (8, 13)], color=accent, width=2)
        line([(3, 8), (13, 8)], color=fg, width=2)
    elif icon == "duplicate":
        d.rectangle((3, 3, 9, 10), outline=fg, width=1)
        d.rectangle((6, 6, 12, 13), outline=accent, width=1)


def make_texture(key, kind):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if kind.startswith("pane_"):
        d = ImageDraw.Draw(img)
        if kind == "pane_blank":
            d.rectangle((1, 1, 14, 14), fill=(90, 96, 104, 42), outline=(175, 184, 193, 35))
        elif kind == "pane_border":
            d.rectangle((1, 1, 14, 14), outline=(190, 199, 207, 100), width=1)
            for xy in ((2, 2), (13, 2), (2, 13), (13, 13)):
                d.point(xy, fill=(220, 226, 232, 125))
        else:
            d.rectangle((1, 1, 14, 14), fill=(104, 114, 126, 50), outline=(205, 214, 224, 110))
            d.rectangle((2, 2, 13, 4), fill=(228, 235, 242, 95))
        return img
    _, fg, accent = PALETTES[kind]
    # A compact dark outline gives each mark a crisp silhouette over vanilla GUI items.
    draw_icon(img, kind, fg, accent)
    return img


def main():
    MODELS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    ITEM_DEFS.mkdir(parents=True, exist_ok=True)
    by_material = {}
    for key, (material, cmd, icon) in ASSETS.items():
        texture_name = f"gui_{key}"
        image = make_texture(key, icon)
        image.save(TEXTURES / f"{texture_name}.png", optimize=True)
        (MODELS / f"{texture_name}.json").write_text(json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"alkatraz:item/{texture_name}"}
        }, indent=2) + "\n", encoding="utf-8")
        by_material.setdefault(material, []).append({"threshold": cmd, "model": {
            "type": "minecraft:model", "model": f"alkatraz:item/{texture_name}"}})

    for material, entries in by_material.items():
        path = ITEM_DEFS / f"{material.lower()}.json"
        existing = json.loads(path.read_text(encoding="utf-8")) if path.exists() else None
        dispatch = existing.get("model") if existing else None
        if dispatch and dispatch.get("type") == "minecraft:range_dispatch" and dispatch.get("property") == "minecraft:custom_model_data":
            fallback = dispatch.get("fallback", {"type": "minecraft:model", "model": f"minecraft:item/{material.lower()}"})
            merged = {e["threshold"]: e for e in dispatch.get("entries", [])}
        else:
            fallback = {"type": "minecraft:model", "model": f"minecraft:item/{material.lower()}"}
            merged = {}
        for entry in entries:
            if entry["threshold"] in merged:
                if merged[entry["threshold"]].get("model", {}).get("model") == entry["model"]["model"]:
                    continue
                raise SystemExit(f"GUI CMD collision in {material}: {entry['threshold']}")
            merged[entry["threshold"]] = entry
        path.write_text(json.dumps({"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                                                "fallback": fallback, "entries": [merged[k] for k in sorted(merged)]}},
                                   indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(ASSETS)} GUI icons and dispatch definitions for {len(by_material)} materials")


if __name__ == "__main__":
    main()
