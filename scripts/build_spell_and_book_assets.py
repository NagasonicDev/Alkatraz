"""Generate spell, scroll, grimoire and magic-stone models for Alkatraz Modern."""
from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw
import yaml

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
MODELS = PACK / "assets/alkatraz/models/item"
TEXTURES = PACK / "assets/alkatraz/textures/item"
DEFS = PACK / "assets/minecraft/items"
SPELLS = ROOT / "core/src/main/resources/spells"
ITEMS = ROOT / "core/src/main/resources/magic/items"

PALETTES = {
    "fire": ((48, 21, 30, 255), (235, 77, 42, 255), (255, 204, 91, 255)),
    "water": ((22, 43, 65, 255), (54, 151, 197, 255), (166, 235, 239, 255)),
    "earth": ((45, 49, 37, 255), (115, 139, 74, 255), (215, 188, 112, 255)),
    "air": ((34, 48, 61, 255), (104, 187, 213, 255), (224, 246, 238, 255)),
    "light": ((67, 53, 29, 255), (232, 190, 76, 255), (255, 246, 180, 255)),
    "dark": ((36, 29, 48, 255), (113, 76, 157, 255), (211, 166, 238, 255)),
    "arcane": ((34, 31, 67, 255), (106, 95, 203, 255), (190, 188, 255, 255)),
    "healing": ((31, 59, 48, 255), (83, 177, 117, 255), (198, 247, 175, 255)),
    "summon": ((54, 43, 44, 255), (167, 112, 82, 255), (234, 193, 125, 255)),
    "utility": ((37, 48, 57, 255), (100, 143, 165, 255), (213, 226, 225, 255)),
}

ELEMENT_THEME = {"FIRE": "fire", "WATER": "water", "EARTH": "earth", "AIR": "air",
                 "LIGHT": "light", "DARK": "dark", "SHADOW": "dark", "NONE": "arcane"}
THEME_WORDS = {
    "healing": ("heal", "regeneration", "mend", "purif", "restore", "lesser_heal"),
    "summon": ("summon", "skeletal", "familiar", "conjure", "zombie", "knight"),
    "utility": ("warp", "teleport", "recall", "waystone", "dispel", "barrier", "shield", "stealth", "buff", "swift"),
    "light": ("light", "radiant", "sunfire", "radiance", "solar", "flare"),
    "dark": ("void", "shadow", "umbral", "oblivion", "nether", "dark", "tendril", "curse"),
    "fire": ("fire", "flame", "inferno", "blaze", "meteor", "ember"),
    "water": ("water", "tide", "wave", "tsunami", "whirlpool", "undertow", "geyser", "frost", "ice"),
    "earth": ("earth", "stone", "tremor", "tectonic", "mountain", "boulder", "worldbreaker", "spike"),
    "air": ("wind", "air", "storm", "thunder", "sky", "tornado", "vortex", "updraft", "cyclone"),
}


def spell_theme(key: str, data: dict) -> str:
    for theme, words in THEME_WORDS.items():
        if any(word in key for word in words):
            return theme
    return ELEMENT_THEME.get(str(data.get("element", "NONE")).upper(), "arcane")


def draw_spell(key: str, theme: str, index: int) -> Image.Image:
    dark, mid, bright = PALETTES[theme]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    # A distinct, angular seal forms a clear silhouette at inventory scale.
    d.polygon([(8, 1), (13, 4), (14, 8), (12, 13), (8, 15), (4, 13), (2, 8), (3, 4)], fill=dark)
    d.polygon([(8, 2), (12, 5), (13, 8), (11, 12), (8, 14), (5, 12), (3, 8), (4, 5)], fill=mid)
    # Six families of core sigils, rotated deterministically to keep the roster varied.
    variant = sum((i + 1) * ord(c) for i, c in enumerate(key)) % 6
    if variant == 0:
        d.polygon([(8, 3), (10, 7), (8, 6), (6, 10), (6, 7)], fill=bright)
        d.point((8, 12), fill=dark)
    elif variant == 1:
        d.ellipse((5, 5, 10, 10), fill=bright)
        d.polygon([(8, 2), (9, 5), (7, 5)], fill=bright)
        d.polygon([(13, 8), (10, 9), (10, 7)], fill=bright)
        d.polygon([(8, 13), (7, 10), (9, 10)], fill=bright)
        d.polygon([(3, 8), (6, 7), (6, 9)], fill=bright)
    elif variant == 2:
        d.line([(4, 10), (7, 7), (8, 9), (11, 5), (12, 6)], fill=bright, width=2)
        d.point((5, 5), fill=bright)
    elif variant == 3:
        d.rectangle((5, 5, 10, 10), outline=bright, width=1)
        d.line([(8, 4), (8, 12)], fill=bright, width=1)
        d.line([(4, 8), (12, 8)], fill=bright, width=1)
    elif variant == 4:
        d.polygon([(8, 3), (11, 8), (8, 12), (5, 8)], fill=bright)
        d.point((8, 7), fill=dark)
    else:
        d.arc((4, 4, 11, 11), 35, 320, fill=bright, width=2)
        d.polygon([(10, 3), (13, 4), (11, 6)], fill=bright)
        d.point((6, 8), fill=bright)
    # A tiny keyed rune notch differentiates related spells without reducing legibility.
    notch = index % 4
    d.point(((4, 7), (11, 4), (11, 11), (5, 11))[notch], fill=(255, 255, 255, 225))
    return image


def draw_scroll(key: str, theme: str) -> Image.Image:
    _, mid, bright = PALETTES[theme]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle((4, 2, 11, 13), fill=(59, 38, 29, 255))
    d.rectangle((5, 2, 10, 13), fill=(210, 174, 111, 255))
    d.rectangle((6, 3, 9, 12), fill=(239, 215, 160, 255))
    d.rectangle((4, 1, 11, 3), fill=(173, 112, 57, 255)); d.rectangle((4, 12, 11, 14), fill=(173, 112, 57, 255))
    d.point((5, 2), fill=(255, 226, 155, 255)); d.point((10, 13), fill=(255, 226, 155, 255))
    d.line((7, 5, 9, 5), fill=mid, width=1); d.line((7, 6, 8, 7, 9, 6), fill=bright, width=1)
    d.point((8, 9), fill=mid); d.point((8, 10), fill=bright)
    # Slightly different folded corner per spell.
    seed = sum(map(ord, key)) % 3
    d.point(((6, 11), (8, 11), (9, 11))[seed], fill=(173, 112, 57, 255))
    return img


def draw_book(key: str, tier: int, theme: str) -> Image.Image:
    _, mid, bright = PALETTES[theme]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    leather = [(100, 57, 35, 255), (74, 50, 104, 255), (57, 77, 111, 255), (133, 57, 40, 255), (51, 97, 115, 255), (86, 89, 61, 255), (93, 62, 117, 255), (126, 108, 44, 255), (36, 36, 54, 255)][tier]
    # Open-page edge and thick cover provide a book silhouette rather than a flat icon.
    d.polygon([(3, 3), (7, 4), (8, 5), (8, 13), (6, 12), (3, 13)], fill=(225, 214, 177, 255))
    d.polygon([(13, 3), (9, 4), (8, 5), (8, 13), (10, 12), (13, 13)], fill=(196, 183, 147, 255))
    d.line((4, 6, 7, 7), fill=(116, 104, 81, 255)); d.line((9, 7, 12, 6), fill=(116, 104, 81, 255))
    d.polygon([(2, 2), (7, 3), (8, 4), (8, 14), (6, 13), (2, 14)], fill=(38, 28, 31, 255))
    d.polygon([(3, 3), (7, 4), (7, 12), (3, 12)], fill=leather)
    d.line((4, 4, 6, 5), fill=mid, width=1)
    if tier == 0:  # leather: restrained stitched border
        d.rectangle((4, 5, 6, 10), outline=(192, 143, 82, 255))
        d.point((5, 7), fill=(237, 197, 116, 255))
    else:
        d.polygon([(5, 5), (6, 7), (5, 9), (4, 7)], fill=mid)
        d.point((5, 7), fill=bright)
        d.point((4, 10), fill=bright if tier >= 6 else mid)
    # Grimoire clasp; higher tier books gain a brighter clasp and corner studs.
    d.rectangle((7, 7, 9, 9), fill=(177, 133, 56, 255) if tier < 5 else bright)
    d.point((8, 8), fill=bright)
    if tier >= 6:
        d.point((3, 3), fill=bright); d.point((6, 11), fill=bright)
    return img


def generated_model(name: str) -> None:
    (MODELS / f"{name}.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"alkatraz:item/{name}"}
    }, indent=2) + "\n", encoding="utf-8")


def add_dispatch(material: str, cmd: int, model: str) -> None:
    path = DEFS / f"{material.lower()}.json"
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        dispatch = data.get("model", {})
        if dispatch.get("type") != "minecraft:range_dispatch" or dispatch.get("property") != "minecraft:custom_model_data":
            raise SystemExit(f"unsupported existing item definition: {path}")
        fallback = dispatch.get("fallback", {"type": "minecraft:model", "model": f"minecraft:item/{material.lower()}"})
        entries = dispatch.setdefault("entries", [])
    else:
        fallback = {"type": "minecraft:model", "model": f"minecraft:item/{material.lower()}"}
        entries = []
        data = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "fallback": fallback, "entries": entries}}
    for entry in entries:
        if int(entry["threshold"]) == cmd:
            current = entry.get("model", {}).get("model")
            if current == f"alkatraz:item/{model}":
                return
            raise SystemExit(f"CMD collision for {material} {cmd}: {current} vs {model}")
    entries.append({"threshold": cmd, "model": {"type": "minecraft:model", "model": f"alkatraz:item/{model}"}})
    entries.sort(key=lambda row: int(row["threshold"]))
    data["model"]["fallback"] = fallback
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    MODELS.mkdir(parents=True, exist_ok=True); TEXTURES.mkdir(parents=True, exist_ok=True); DEFS.mkdir(parents=True, exist_ok=True)
    grouped: dict[str, list[tuple[str, int]]] = defaultdict(list)
    spell_rows = []
    for path in sorted(SPELLS.glob("*.yml")):
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        if data.get("gui_item") is None or data.get("gui_custom_model_data") is None:
            continue
        key = str(data.get("id", path.stem)); model = f"spell_{key}"
        material, cmd = str(data["gui_item"]).upper(), int(data["gui_custom_model_data"])
        theme = spell_theme(key, data)
        spell_rows.append((key, material, cmd, model, theme))
    if len(spell_rows) != 68:
        raise SystemExit(f"expected 68 spell GUI definitions, found {len(spell_rows)}")
    if len({(material, cmd) for _, material, cmd, _, _ in spell_rows}) != len(spell_rows):
        raise SystemExit("duplicate spell material/CMD mapping")
    for index, (key, material, cmd, model, theme) in enumerate(spell_rows):
        draw_spell(key, theme, index).save(TEXTURES / f"{model}.png", optimize=True)
        generated_model(model); add_dispatch(material, cmd, model)
        grouped[theme].append((key, index))

    item_rows = []
    scroll_themes = {"magic_missile_scroll": "arcane", "heal_scroll": "healing", "fireball_scroll": "fire", "earth_spike_scroll": "earth"}
    grimoire_themes = {"leather_grimoire": "arcane", "runic_grimoire": "arcane", "blaze_grimoire": "fire", "glacier_grimoire": "water", "mountain_grimoire": "earth", "storm_grimoire": "air", "luminous_grimoire": "light", "void_grimoire": "dark"}
    for key, theme in scroll_themes.items():
        data = yaml.safe_load((ITEMS / f"{key}.yml").read_text(encoding="utf-8"))
        if "custom_model_data" not in data:
            raise SystemExit(f"scroll has no CMD: {key}")
        model = key
        draw_scroll(key, theme).save(TEXTURES / f"{model}.png", optimize=True); generated_model(model)
        material, cmd = str(data["material"]).upper(), int(data["custom_model_data"])
        add_dispatch(material, cmd, model); item_rows.append((key, material, cmd, theme))
    for tier, (key, theme) in enumerate(grimoire_themes.items()):
        data = yaml.safe_load((ITEMS / f"{key}.yml").read_text(encoding="utf-8"))
        if "custom_model_data" not in data:
            raise SystemExit(f"grimoire has no CMD: {key}")
        model = key
        draw_book(key, tier, theme).save(TEXTURES / f"{model}.png", optimize=True); generated_model(model)
        material, cmd = str(data["material"]).upper(), int(data["custom_model_data"])
        add_dispatch(material, cmd, model); item_rows.append((key, material, cmd, theme))
    stone = yaml.safe_load((ITEMS / "magic_stone.yml").read_text(encoding="utf-8"))
    model = "magic_stone"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (12, 5), (13, 10), (8, 15), (3, 10), (4, 5)], fill=(50, 34, 81, 255))
    d.polygon([(8, 3), (11, 6), (10, 10), (8, 12), (5, 9), (6, 5)], fill=(161, 93, 203, 255))
    d.polygon([(8, 3), (8, 10), (6, 5)], fill=(221, 177, 255, 255)); d.point((9, 8), fill=(247, 229, 255, 255))
    img.save(TEXTURES / f"{model}.png", optimize=True); generated_model(model)
    material, cmd = str(stone["material"]).upper(), int(stone["custom_model_data"])
    add_dispatch(material, cmd, model); item_rows.append(("magic_stone", material, cmd, "arcane"))

    # Native-size thematic contact sheets live outside the distributable pack.
    review = ROOT / "resource-packs/review/spells"; review.mkdir(parents=True, exist_ok=True)
    for theme, spell_list in grouped.items():
        cols = 8; rows = (len(spell_list) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 64, rows * 82), (31, 34, 42, 255)); sd = ImageDraw.Draw(sheet)
        for pos, (key, _) in enumerate(spell_list):
            x, y = (pos % cols) * 64, (pos // cols) * 82
            icon = Image.open(TEXTURES / f"spell_{key}.png").resize((48, 48), Image.Resampling.NEAREST)
            sheet.alpha_composite(icon, (x + 8, y + 2))
            label = re.sub(r"[^a-z0-9_]", "", key.lower())[:11]
            sd.text((x + 2, y + 54), label, fill=(230, 234, 242, 255))
        sheet.save(review / f"{theme}.png", optimize=True)
    item_sheet = Image.new("RGBA", (7 * 80, 2 * 94), (31, 34, 42, 255)); sd = ImageDraw.Draw(item_sheet)
    for pos, (key, _, _, _) in enumerate(item_rows):
        x, y = (pos % 7) * 80, (pos // 7) * 94
        icon = Image.open(TEXTURES / f"{key}.png").resize((64, 64), Image.Resampling.NEAREST)
        item_sheet.alpha_composite(icon, (x + 8, y + 1))
        sd.text((x + 2, y + 69), key[:13], fill=(230, 234, 242, 255))
    item_sheet.save(ROOT / "resource-packs/review/scrolls-grimoires-stone.png", optimize=True)
    for name, material, cmd, _, _ in spell_rows:
        model = f"spell_{name}"
        definition = json.loads((DEFS / f"{material.lower()}.json").read_text(encoding="utf-8"))
        matches = [entry for entry in definition["model"]["entries"]
                   if int(entry["threshold"]) == cmd
                   and entry.get("model", {}).get("model") == f"alkatraz:item/{model}"]
        if len(matches) != 1 or Image.open(TEXTURES / f"{model}.png").size != (16, 16):
            raise SystemExit(f"spell asset verification failed: {name} ({material}, {cmd})")
    for name, material, cmd, _ in item_rows:
        definition = json.loads((DEFS / f"{material.lower()}.json").read_text(encoding="utf-8"))
        matches = [entry for entry in definition["model"]["entries"]
                   if int(entry["threshold"]) == cmd
                   and entry.get("model", {}).get("model") == f"alkatraz:item/{name}"]
        if len(matches) != 1 or Image.open(TEXTURES / f"{name}.png").size != (16, 16):
            raise SystemExit(f"item asset verification failed: {name} ({material}, {cmd})")
    print(f"Generated {len(spell_rows)} spell icons, {len(item_rows)} scroll/book/stone assets, {len(grouped)} spell review sheets")


if __name__ == "__main__":
    main()
