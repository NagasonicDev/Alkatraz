"""Generate research connector art and configured PUA font glyphs."""
from __future__ import annotations

import json
from pathlib import Path

import yaml
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
MODELS = PACK / "assets/alkatraz/models/item"
TEXTURES = PACK / "assets/alkatraz/textures/item"
ITEM_DEFS = PACK / "assets/minecraft/items"

SHAPES = ("straight_h", "straight_v", "corner_ne", "corner_se", "corner_sw", "corner_nw")
STATES = ("completed", "available", "in_progress", "locked", "hidden")
COLORS = {
    "completed": ((111, 71, 201, 255), (190, 151, 255, 255)),
    "available": ((238, 103, 210, 255), (255, 190, 239, 255)),
    "in_progress": ((240, 82, 91, 255), (255, 204, 107, 255)),
    "locked": ((100, 57, 68, 180), (147, 103, 113, 190)),
    "hidden": ((56, 48, 57, 105), (82, 72, 84, 120)),
}
MATERIAL = {"completed": "PURPLE_STAINED_GLASS_PANE", "available": "PINK_STAINED_GLASS_PANE",
            "in_progress": "RED_DYE", "locked": "RED_DYE", "hidden": "RED_DYE"}


def connector_texture(shape: str, state: str) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    core, hi = COLORS[state]
    # All pieces meet on the cell centerline; the 3px band joins without gaps.
    if shape == "straight_h": points = [(0, 7), (15, 7)]
    elif shape == "straight_v": points = [(7, 0), (7, 15)]
    elif shape == "corner_ne": points = [(7, 0), (7, 7), (15, 7)]
    elif shape == "corner_se": points = [(7, 15), (7, 7), (15, 7)]
    elif shape == "corner_sw": points = [(8, 15), (8, 7), (0, 7)]
    else: points = [(8, 0), (8, 7), (0, 7)]
    draw.line(points, fill=core, width=3)
    # Bright upper edge gives depth, and a state marker makes progress recognizable.
    offset_points = [(x, y - 1 if shape in ("straight_h", "corner_ne", "corner_se", "corner_sw", "corner_nw") else y)
                     for x, y in points]
    if state not in ("hidden", "locked"):
        draw.line(offset_points, fill=hi, width=1)
    if state == "available":
        draw.rectangle((6, 6, 8, 8), fill=hi)
    elif state == "in_progress":
        draw.point((7, 7), fill=hi)
        draw.point((8, 7), fill=(255, 245, 184, 255))
    elif state == "completed":
        draw.point((7, 7), fill=hi)
    return img


def merge_dispatch(material: str, entries: list[dict]) -> None:
    path = ITEM_DEFS / f"{material.lower()}.json"
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    model = data.get("model", {})
    if model and (model.get("type") != "minecraft:range_dispatch" or model.get("property") != "minecraft:custom_model_data"):
        raise SystemExit(f"unsupported existing item definition: {path}")
    fallback = model.get("fallback", {"type": "minecraft:model", "model": f"minecraft:item/{material.lower()}"})
    by_cmd = {int(e["threshold"]): e for e in model.get("entries", [])}
    for entry in entries:
        cmd = int(entry["threshold"])
        if cmd in by_cmd:
            if by_cmd[cmd].get("model", {}).get("model") == entry["model"]["model"]:
                continue
            by_cmd[cmd] = entry
            continue
        by_cmd[cmd] = entry
    path.write_text(json.dumps({"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                                         "fallback": fallback, "entries": [by_cmd[k] for k in sorted(by_cmd)]}},
                               indent=2) + "\n", encoding="utf-8")


def make_connectors() -> None:
    ITEM_DEFS.mkdir(parents=True, exist_ok=True)
    grouped: dict[str, list[dict]] = {}
    for shape in SHAPES:
        for state in STATES:
            key = f"{shape}_{state}"
            cmd = int(yaml.safe_load((ROOT / "core/src/main/resources/texturepack.yml").read_text(encoding="utf-8"))
                      ["connector_textures"][key])
            name = f"connector_{key}"
            connector_texture(shape, state).save(TEXTURES / f"{name}.png", optimize=True)
            (MODELS / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated",
                "textures": {"layer0": f"alkatraz:item/{name}"}}, indent=2) + "\n", encoding="utf-8")
            grouped.setdefault(MATERIAL[state], []).append({"threshold": cmd,
                "model": {"type": "minecraft:model", "model": f"alkatraz:item/{name}"}})
    for material, entries in grouped.items():
        merge_dispatch(material, entries)


def make_research_nodes() -> None:
    """Add explicit model dispatches for node icons and their four progress states."""
    config = yaml.safe_load((ROOT / "core/src/main/resources/texturepack.yml").read_text(encoding="utf-8"))
    research = yaml.safe_load((ROOT / "core/src/main/resources/research.yml").read_text(encoding="utf-8"))
    node_cmds = config.get("research_node_textures", {})
    state_materials = ("RED_DYE", "RED_CONCRETE", "WRITABLE_BOOK", "ENCHANTED_BOOK")
    grouped: dict[str, list[dict]] = {}
    for node_id, node in research.get("nodes", {}).items():
        if node_id not in node_cmds:
            raise SystemExit(f"research node has no texture CMD: {node_id}")
        icon_material = str(node.get("icon", "PAPER")).upper()
        if icon_material == "BOOK_AND_QUILL":
            icon_material = "WRITABLE_BOOK"
        cmd = int(node_cmds[node_id])
        for material in {*state_materials, icon_material}:
            model_name = f"research_node_{material.lower()}"
            model_path = MODELS / f"{model_name}.json"
            model_path.parent.mkdir(parents=True, exist_ok=True)
            model_path.write_text(json.dumps({"parent": "minecraft:item/generated",
                "textures": {"layer0": f"minecraft:item/{material.lower()}"}}, indent=2) + "\n", encoding="utf-8")
            grouped.setdefault(material, []).append({"threshold": cmd,
                "model": {"type": "minecraft:model", "model": f"alkatraz:item/{model_name}"}})
    for material, entries in grouped.items():
        merge_dispatch(material, entries)


def make_research_pan_arrows() -> None:
    """Draw and dispatch the eight directional controls around the research graph."""
    config = yaml.safe_load((ROOT / "core/src/main/resources/texturepack.yml").read_text(encoding="utf-8"))
    vectors = {"up_left": (-1, -1), "up": (0, -1), "up_right": (1, -1), "left": (-1, 0),
               "right": (1, 0), "down_left": (-1, 1), "down": (0, 1), "down_right": (1, 1)}
    grouped: list[dict] = []
    for direction, (dx, dy) in vectors.items():
        cmd = config.get("research_pan_arrows", {}).get(direction)
        if cmd is None:
            raise SystemExit(f"research pan arrow has no texture CMD: {direction}")
        end = (8 + dx * 6, 8 + dy * 6)
        start = (8 - dx * 2, 8 - dy * 2)
        back = (end[0] - dx * 4, end[1] - dy * 4)
        perp = (-dy, dx)
        head_a = (back[0] + perp[0] * 3, back[1] + perp[1] * 3)
        head_b = (back[0] - perp[0] * 3, back[1] - perp[1] * 3)
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.line((start, end), fill=(28, 42, 61, 255), width=5)
        draw.line((end, head_a), fill=(28, 42, 61, 255), width=5)
        draw.line((end, head_b), fill=(28, 42, 61, 255), width=5)
        draw.line((start, end), fill=(93, 188, 255, 255), width=3)
        draw.line((end, head_a), fill=(93, 188, 255, 255), width=3)
        draw.line((end, head_b), fill=(93, 188, 255, 255), width=3)
        draw.point(start, fill=(225, 245, 255, 255))
        key = f"research_pan_arrow_{direction}"
        image.save(TEXTURES / f"{key}.png", optimize=True)
        (MODELS / f"{key}.json").write_text(json.dumps({"parent": "minecraft:item/generated",
            "textures": {"layer0": f"alkatraz:item/{key}"}}, indent=2) + "\n", encoding="utf-8")
        grouped.append({"threshold": int(cmd), "model": {"type": "minecraft:model", "model": f"alkatraz:item/{key}"}})
    merge_dispatch("ARROW", grouped)


def make_font() -> None:
    config = yaml.safe_load((ROOT / "core/src/main/resources/texturepack.yml").read_text(encoding="utf-8"))
    font_dir = PACK / "assets/alkatraz/textures/font"
    font_dir.mkdir(parents=True, exist_ok=True)
    chars: set[str] = set()
    icons = config["ascii_codes"].get("icons") or {}
    configured = list(config["ascii_codes"]["menu_titles"].values()) + list(icons.values())
    for string in configured:
        chars.update(string)
    providers = []
    # These bitmap glyphs are full inventory backdrops, not 16x16 item icons.
    # Preserve the authored PNGs and set provider height to the source pixel
    # height so Minecraft displays their 176px canvas at native GUI scale.
    for char in sorted(chars):
        if char == "\uf808":
            # Inventory titles start 8px inside the GUI; shift the backdrop to
            # the left edge so its 176px canvas spans the complete inventory.
            providers.append({"type": "space", "advances": {char: -8}})
            continue
        path = font_dir / f"glyph_{ord(char):04x}.png"
        if not path.is_file():
            raise SystemExit(f"missing authored GUI glyph: {path}")
        with Image.open(path) as image:
            height = image.height
        providers.append({"type": "bitmap", "file": f"alkatraz:font/{path.name}", "height": height,
                          "ascent": 13, "chars": [char]})
    target = PACK / "assets/minecraft/font/default.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (PACK / "font-glyph-map.json").write_text(json.dumps({"configured_strings": configured,
        "codepoints": {f"U+{ord(c):04X}": f"assets/alkatraz/textures/font/glyph_{ord(c):04x}.png" for c in sorted(chars)}},
        ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    make_connectors()
    make_research_nodes()
    make_research_pan_arrows()
    make_font()
    print("Generated 30 connector variants and glyph providers for configured menu/icon strings")
