"""Build the jewelry and dyed-leather equipment assets from the asset manifest."""
from __future__ import annotations

import json
from pathlib import Path

import yaml
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
MANIFEST = PACK / "asset-manifest.json"
MODELS = PACK / "assets/alkatraz/models/item"
TEXTURES = PACK / "assets/alkatraz/textures/item"
ITEM_DEFS = PACK / "assets/minecraft/items"

FAMILIES = {
    "ember": ("fire", 0), "blaze": ("fire", 1), "inferno": ("fire", 2),
    "frost": ("ice", 0), "glacier": ("ice", 1), "abyss": ("ice", 2),
    "zephyr": ("storm", 0), "storm": ("storm", 1), "tempest": ("storm", 2),
    "boulder": ("earth", 0), "mountain": ("earth", 1), "titan": ("earth", 2),
    "radiant": ("light", 0), "luminous": ("light", 1), "divine": ("light", 2),
    "shadow": ("shadow", 0), "void": ("shadow", 1), "nether": ("shadow", 2),
    "mystic": ("arcane", 0), "arcane": ("arcane", 1), "ethereal": ("arcane", 2),
    "phoenix": ("fire", 3), "leviathan": ("water", 3), "cyclone": ("storm", 3),
    "colossus": ("earth", 3), "seraphic": ("light", 3), "oblivion": ("shadow", 3),
    "primordial": ("arcane", 3),
}
PALETTES = {
    "fire": ((82, 30, 24, 255), (218, 73, 28, 255), (255, 198, 72, 255)),
    "ice": ((28, 58, 91, 255), (69, 153, 196, 255), (194, 247, 255, 255)),
    "storm": ((40, 47, 87, 255), (87, 115, 197, 255), (183, 226, 255, 255)),
    "earth": ((65, 48, 34, 255), (133, 97, 54, 255), (222, 187, 111, 255)),
    "light": ((104, 73, 24, 255), (222, 177, 48, 255), (255, 249, 181, 255)),
    "shadow": ((35, 27, 54, 255), (101, 58, 130, 255), (205, 139, 255, 255)),
    "arcane": ((37, 48, 87, 255), (78, 102, 181, 255), (198, 191, 255, 255)),
    "water": ((22, 63, 91, 255), (28, 137, 171, 255), (142, 242, 235, 255)),
}


def layer_color(color, tier):
    if tier < 2:
        return color
    # Higher tiers gain a pale pixel highlight and an additional bright accent.
    return tuple(min(255, int(c * 0.82 + 255 * 0.18)) for c in color[:3]) + (255,)


def jewelry_texture(key: str) -> Image.Image:
    family, form = key.rsplit("_", 1)
    theme, tier = FAMILIES[family]
    dark, mid, bright = (layer_color(c, tier) for c in PALETTES[theme])
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    if form == "ring":
        # A compact three-quarter band and a distinct setting stone.
        d.rectangle((3, 8, 12, 11), fill=dark)
        d.rectangle((4, 8, 11, 9), fill=mid)
        d.point((5, 10), fill=bright)
        d.rectangle((6, 5, 10, 7), fill=dark)
        d.rectangle((7, 4, 9, 6), fill=mid)
        d.point((8, 5), fill=bright)
    elif form == "necklace":
        d.line([(3, 3), (4, 8), (6, 11), (8, 12), (10, 11), (12, 8), (13, 3)], fill=dark, width=2)
        d.line([(4, 4), (5, 8), (7, 10), (9, 10), (11, 8), (12, 4)], fill=mid, width=1)
        d.rectangle((7, 10, 9, 12), fill=dark)
        d.point((8, 11), fill=bright)
        if tier >= 2:
            d.point((4, 5), fill=bright)
            d.point((12, 5), fill=bright)
    elif form == "bracelet":
        d.rectangle((3, 5, 12, 10), fill=dark)
        d.rectangle((4, 6, 11, 9), fill=mid)
        d.rectangle((6, 6, 9, 9), fill=dark)
        d.point((7, 7), fill=bright)
        d.point((8, 8), fill=bright)
        if tier >= 2:
            d.point((4, 7), fill=bright)
            d.point((11, 8), fill=bright)
    else:  # pendant
        d.line([(5, 3), (6, 6), (8, 7), (10, 6), (11, 3)], fill=dark, width=1)
        d.line([(6, 3), (7, 5), (9, 5), (10, 3)], fill=mid, width=1)
        d.polygon([(8, 6), (12, 9), (8, 14), (4, 9)], fill=dark)
        d.polygon([(8, 7), (11, 9), (8, 12), (5, 9)], fill=mid)
        d.point((8, 8), fill=bright)
    # Tier marks communicate progression even when two families share a palette.
    if tier == 2:
        d.point((2, 3), fill=bright)
        d.point((13, 12), fill=bright)
    elif tier == 3:
        d.point((3, 3), fill=bright)
        d.point((12, 3), fill=bright)
        d.point((3, 12), fill=mid)
        d.point((12, 12), fill=mid)
    return image


def equipment_texture(key: str) -> Image.Image:
    set_name, slot = key.split("_", 1)
    tier = {"apprentice": 0, "scholar": 0, "enchanter": 1, "archmage": 2}[set_name]
    dark, mid, bright = (layer_color(c, tier) for c in ((47, 37, 72, 255), (119, 75, 160, 255), (220, 187, 255, 255)))
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    if slot == "hat":
        d.rectangle((3, 10, 12, 12), fill=dark)
        d.polygon([(5, 9), (6, 4), (8, 2), (10, 4), (11, 9)], fill=mid)
        d.point((8, 4), fill=bright)
        d.line([(6, 8), (10, 8)], fill=bright if tier else dark, width=1)
    elif slot == "robe":
        d.polygon([(5, 2), (7, 3), (9, 3), (11, 2), (13, 5), (11, 7), (12, 14), (4, 14), (5, 7), (3, 5)], fill=dark)
        d.polygon([(6, 3), (7, 4), (9, 4), (10, 3), (11, 5), (10, 7), (11, 13), (5, 13), (6, 7), (5, 5)], fill=mid)
        d.line([(8, 4), (8, 11)], fill=bright, width=1)
        d.polygon([(8, 6), (10, 8), (8, 10), (6, 8)], fill=bright if tier else dark)
        if tier >= 1:
            d.point((5, 10), fill=bright)
            d.point((11, 10), fill=bright)
    elif slot == "leggings":
        d.polygon([(4, 2), (12, 2), (12, 7), (10, 7), (10, 14), (7, 14), (7, 8), (6, 14), (3, 14), (4, 7)], fill=dark)
        d.rectangle((5, 3, 11, 6), fill=mid)
        d.rectangle((5, 7, 6, 12), fill=mid)
        d.rectangle((8, 7, 9, 12), fill=mid)
        d.point((8, 4), fill=bright)
    else:  # boots
        d.polygon([(3, 3), (7, 3), (7, 10), (6, 11), (6, 13), (2, 13), (2, 11), (4, 10)], fill=dark)
        d.polygon([(9, 3), (13, 3), (12, 10), (14, 11), (14, 13), (9, 13), (8, 11), (9, 10)], fill=dark)
        d.rectangle((3, 6, 6, 10), fill=mid)
        d.rectangle((10, 6, 12, 10), fill=mid)
        d.point((5, 7), fill=bright)
        d.point((11, 7), fill=bright)
    # Higher sets use an unmistakable brighter crest and extra trim.
    if tier == 1:
        d.point((8, 3 if slot != "boots" else 5), fill=bright)
    elif tier == 2:
        d.point((7, 3 if slot != "boots" else 5), fill=bright)
        d.point((9, 3 if slot != "boots" else 5), fill=bright)
        if slot in ("hat", "robe"):
            d.rectangle((7, 1, 9, 1), fill=bright)
    return image


def main():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    targets = [a for a in manifest["assets"] if a["kind"] == "item" and
               (1201 <= int(a["cmd"]) <= 1312 or 2797 <= int(a["cmd"]) <= 2812)]
    if len(targets) != 128:
        raise SystemExit(f"expected 128 jewelry/equipment records, found {len(targets)}")

    grouped: dict[str, list[dict]] = {}
    for asset in targets:
        key = asset["key"]
        texture = jewelry_texture(key) if 1201 <= int(asset["cmd"]) <= 1312 else equipment_texture(key)
        texture.save(PACK / asset["texture"], optimize=True)
        model_name = asset["model"].split(":", 1)[1].split("/", 1)[1]
        model_path = MODELS / f"{model_name}.json"
        model_path.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {
            "layer0": f"alkatraz:item/{key}"}}, indent=2) + "\n", encoding="utf-8")
        grouped.setdefault(asset["material"].lower(), []).append(asset)

    ITEM_DEFS.mkdir(parents=True, exist_ok=True)
    for material, records in grouped.items():
        path = ITEM_DEFS / f"{material}.json"
        if path.exists():
            definition = json.loads(path.read_text(encoding="utf-8"))
            dispatch = definition.get("model")
            if dispatch is None or dispatch.get("type") != "minecraft:range_dispatch" or dispatch.get("property") != "minecraft:custom_model_data":
                raise SystemExit(f"unsupported existing definition shape: {path}")
        else:
            definition = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                                     "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{material}"}, "entries": []}}
            dispatch = definition["model"]
        entries = dispatch.setdefault("entries", [])
        for asset in records:
            threshold = int(asset["cmd"])
            item_model = {"type": "minecraft:model", "model": asset["model"]}
            if asset["key"].endswith("_hat"):
                # In the GUI use the flat icon; in head/hand/ground contexts use
                # the 3D item model. With no equippable asset_id, modern clients
                # render this same item model when the helmet is worn.
                item_model = {
                    "type": "minecraft:select",
                    "property": "minecraft:display_context",
                    "cases": [{"when": "gui", "model": item_model}],
                    "fallback": {
                        "type": "minecraft:model",
                        "model": asset["model"].replace("_hat", "_hat_3d"),
                    },
                }
            entry = {"threshold": threshold, "model": item_model}
            existing = [e for e in entries if int(e["threshold"]) == threshold]
            if existing:
                current = existing[0].get("model", {})
                is_expected = current == item_model
                # Existing flat GUI output is upgraded to the context-aware
                # hat definition above, while other model conflicts stay fatal.
                if asset["key"].endswith("_hat") and current == {"type": "minecraft:model", "model": asset["model"]}:
                    existing[0]["model"] = item_model
                    is_expected = True
                if not is_expected:
                    raise SystemExit(f"CMD conflict for {material} {threshold}: {existing[0]}")
            else:
                entries.append(entry)
        entries.sort(key=lambda e: int(e["threshold"]))
        path.write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(targets)} custom item models and textures across {len(grouped)} materials.")


if __name__ == "__main__":
    main()
