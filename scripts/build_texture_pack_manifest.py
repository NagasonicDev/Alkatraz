"""Generate and validate the Alkatraz Modern texture-pack asset manifest."""
from __future__ import annotations

import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "core/src/main/resources/magic/items"
SPELLS = ROOT / "core/src/main/resources/spells"
CONFIG = ROOT / "core/src/main/resources/texturepack.yml"
OUT = ROOT / "resource-packs/Alkatraz-Modern/asset-manifest.json"


def read_config():
    return yaml.safe_load(CONFIG.read_text(encoding="utf-8"))


def source_files(directory):
    result = {}
    for path in sorted(directory.glob("*.yml")):
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        result[path.stem] = (path, data)
    return result


def build():
    config = read_config()
    item_sources = source_files(ITEMS)
    spell_sources = source_files(SPELLS)
    item_ids = config["custom_model_data"]["items"]
    gui_ids = config["custom_model_data"]["gui"]
    records = []

    for key, (path, data) in item_sources.items():
        cmd = data.get("custom_model_data")
        if cmd is None:
            continue
        expected = item_ids.get(key)
        if expected is None or int(expected) != int(cmd):
            raise SystemExit(f"item CMD unresolved/mismatched: {path} has {cmd}, manifest has {expected}")
        records.append({"kind": "item", "key": key, "material": data["material"], "cmd": int(cmd),
                        "model": f"alkatraz:item/{key}", "texture": f"assets/alkatraz/textures/item/{key}.png",
                        "source": path.relative_to(ROOT).as_posix()})

    for key, (path, data) in spell_sources.items():
        cmd = data.get("gui_custom_model_data")
        if cmd is None or not data.get("gui_item"):
            continue
        records.append({"kind": "spell", "key": key, "material": str(data["gui_item"]).upper(), "cmd": int(cmd),
                        "model": f"alkatraz:item/spell_{key}",
                        "texture": f"assets/alkatraz/textures/item/spell_{key}.png",
                        "source": path.relative_to(ROOT).as_posix()})

    gui_material = {"blank_pane": "GRAY_STAINED_GLASS_PANE", "border_pane": "GRAY_STAINED_GLASS_PANE",
                    "header_pane": "GRAY_STAINED_GLASS_PANE", "back_button": "BARRIER",
                    "next_page": "ARROW", "prev_page": "ARROW", "confirm_button": "LIME_CONCRETE",
                    "cancel_button": "RED_CONCRETE", "info_button": "KNOWLEDGE_BOOK", "close_button": "BARRIER",
                    "search_button": "COMPASS", "sort_button": "HOPPER", "create_button": "EMERALD",
                    "duplicate_button": "ENDER_PEARL"}
    for key, material in gui_material.items():
        records.append({"kind": "gui", "key": key, "material": material, "cmd": int(gui_ids[key]),
                        "model": f"alkatraz:item/gui_{key}",
                        "texture": f"assets/alkatraz/textures/item/gui_{key}.png",
                        "source": "core/src/main/resources/texturepack.yml"})

    connector_materials = {"completed": "PURPLE_STAINED_GLASS_PANE", "available": "PINK_STAINED_GLASS_PANE",
                           "in_progress": "RED_DYE", "locked": "RED_DYE", "hidden": "RED_DYE"}
    for key, cmd in config.get("connector_textures", {}).items():
        state = "in_progress" if key.endswith("_in_progress") else key.rsplit("_", 1)[-1]
        records.append({"kind": "connector", "key": key, "material": connector_materials[state], "cmd": int(cmd),
                        "model": f"alkatraz:item/connector_{key}",
                        "texture": f"assets/alkatraz/textures/item/connector_{key}.png",
                        "source": "core/src/main/resources/texturepack.yml"})

    research = yaml.safe_load((ROOT / "core/src/main/resources/research.yml").read_text(encoding="utf-8"))
    node_cmds = config.get("research_node_textures", {})
    state_materials = ("RED_DYE", "RED_CONCRETE", "WRITABLE_BOOK", "ENCHANTED_BOOK")
    for key, data in research.get("nodes", {}).items():
        if key not in node_cmds:
            raise SystemExit(f"research node CMD unresolved: {key}")
        icon_material = str(data.get("icon", "PAPER")).upper()
        if icon_material == "BOOK_AND_QUILL":
            icon_material = "WRITABLE_BOOK"
        for material in {*state_materials, icon_material}:
            model_key = f"research_node_{material.lower()}"
            records.append({"kind": "research_node", "key": f"{key}_{material.lower()}",
                            "material": material, "cmd": int(node_cmds[key]),
                            "model": f"alkatraz:item/{model_key}",
                            "texture": f"minecraft:item/{material.lower()}",
                            "source": "core/src/main/resources/research.yml"})

    for key, cmd in config.get("research_pan_arrows", {}).items():
        records.append({"kind": "research_pan_arrow", "key": key, "material": "ARROW", "cmd": int(cmd),
                        "model": f"alkatraz:item/research_pan_arrow_{key}",
                        "texture": f"assets/alkatraz/textures/item/research_pan_arrow_{key}.png",
                        "source": "core/src/main/resources/texturepack.yml"})

    seen = {}
    for row in records:
        pair = (row["material"], row["cmd"])
        if pair in seen:
            raise SystemExit(f"duplicate material/CMD {pair}: {seen[pair]} and {row['key']}")
        seen[pair] = row["key"]
        if not row["source"]:
            raise SystemExit(f"unresolved source for {row['key']}")

    expected_item_count = sum("custom_model_data" in data for _, data in item_sources.values())
    if sum(row["kind"] == "item" for row in records) != expected_item_count:
        raise SystemExit("one or more live item YAMLs were not resolved")
    OUT.write_text(json.dumps({"schema": 1, "assets": records}, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} records: {sum(r['kind']=='item' for r in records)} items, "
          f"{sum(r['kind']=='spell' for r in records)} spells, {sum(r['kind']=='gui' for r in records)} GUI, "
          f"{sum(r['kind']=='connector' for r in records)} connectors")


if __name__ == "__main__":
    build()
