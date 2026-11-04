"""Audit Alkatraz Modern asset references, dispatches, dimensions, and build ZIP."""
from __future__ import annotations

import json
import hashlib
import zipfile
from collections import defaultdict
from pathlib import Path

import yaml
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
MANIFEST = PACK / "asset-manifest.json"
REVIEW = ROOT / "resource-packs/review"


def verify() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assets = manifest["assets"]
    by_material: dict[str, dict[int, str]] = defaultdict(dict)
    errors: list[str] = []
    for asset in assets:
        material, cmd = asset["material"].lower(), int(asset["cmd"])
        if cmd in by_material[material]:
            errors.append(f"duplicate {material} CMD {cmd}: {by_material[material][cmd]} and {asset['key']}")
        by_material[material][cmd] = asset["key"]
        model_path = PACK / "assets/alkatraz/models/item" / f"{asset['model'].rsplit('/', 1)[-1]}.json"
        if not model_path.is_file():
            errors.append(f"missing model: {model_path.relative_to(ROOT)}")
        else:
            try:
                model = json.loads(model_path.read_text(encoding="utf-8"))
                def check_uvs(node):
                    if isinstance(node, dict):
                        uv = node.get("uv")
                        if isinstance(uv, list) and (len(uv) != 4 or any(not isinstance(v, (int, float)) or v < 0 or v > 16 for v in uv)):
                            errors.append(f"UV outside 0..16: {model_path.relative_to(ROOT)} {uv}")
                        for value in node.values(): check_uvs(value)
                    elif isinstance(node, list):
                        for value in node: check_uvs(value)
                check_uvs(model)
                textures = model.get("textures", {})
                texture_ref = textures.get("layer0") or textures.get("atlas") or next(iter(textures.values()), "")
                if not texture_ref:
                    errors.append(f"model has no texture reference: {model_path.relative_to(ROOT)}")
                    texture_ref = "alkatraz:item/__missing__"
                namespace, texture = texture_ref.split(":", 1) if ":" in texture_ref else ("minecraft", texture_ref)
                if namespace != "minecraft":
                    texture_path = PACK / "assets" / namespace / "textures" / (texture + ".png")
                    if not texture_path.is_file(): errors.append(f"missing texture: {texture_path.relative_to(ROOT)}")
            except Exception as exc:
                errors.append(f"invalid model {model_path.relative_to(ROOT)}: {exc}")

    for material, expected in by_material.items():
        definition = PACK / "assets/minecraft/items" / f"{material}.json"
        if not definition.is_file():
            errors.append(f"missing item definition for {material}")
            continue
        try:
            data = json.loads(definition.read_text(encoding="utf-8"))
            dispatch = data["model"]
            if dispatch.get("type") != "minecraft:range_dispatch" or dispatch.get("property") != "minecraft:custom_model_data":
                errors.append(f"wrong dispatch type: {definition.relative_to(ROOT)}")
                continue
            fallback_model = dispatch.get("fallback", {}).get("model")
            expected_fallback = f"minecraft:block/{material}"
            if fallback_model not in (f"minecraft:item/{material}", expected_fallback):
                errors.append(f"missing vanilla fallback: {definition.relative_to(ROOT)}")
            entries = dispatch.get("entries", [])
            thresholds = [int(e["threshold"]) for e in entries]
            if thresholds != sorted(set(thresholds)):
                errors.append(f"thresholds are duplicated or unsorted: {definition.relative_to(ROOT)}")
            for cmd in expected:
                matches = [e for e in entries if int(e["threshold"]) == cmd]
                if len(matches) != 1:
                    errors.append(f"{material} CMD {cmd} appears {len(matches)} times in dispatch")
        except Exception as exc:
            errors.append(f"invalid item definition {definition.relative_to(ROOT)}: {exc}")

    # Validate all pack JSON, texture sizes, and font references (font glyphs use variable dimensions).
    for path in PACK.rglob("*.json"):
        try: json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc: errors.append(f"invalid JSON {path.relative_to(ROOT)}: {exc}")
    for path in (PACK / "assets/alkatraz/textures").rglob("*.png"):
        try:
            with Image.open(path) as image:
                if path.stem.startswith("spell_"):
                    expected_size = (16, 16) if path.stem == "spell_fireball" else (128, 128)
                else:
                    expected_size = (16, 16)
                # Skip validation for entity equipment textures and armor item textures that have specific dimensions
                skip_dims = False
                if "font" in path.parts:
                    skip_dims = True
                s = str(path).replace("\\", "/")
                if "entity/equipment" in s or "textures/entity" in s or "/entity/" in s:
                    skip_dims = True
                if "item/armor" in s:
                    skip_dims = True
                if "/node/" in s or "node/" in s:
                    skip_dims = True
                if not skip_dims and image.size != expected_size:
                    errors.append(f"unexpected texture dimensions {image.size}: {path.relative_to(ROOT)}")
        except Exception as exc: errors.append(f"invalid texture {path.relative_to(ROOT)}: {exc}")
    spell_textures = sorted((PACK / "assets/alkatraz/textures/item").glob("spell_*.png"))
    spell_hashes: dict[str, str] = {}
    for path in spell_textures:
        try:
            with Image.open(path) as image:
                expected = (16, 16) if path.stem == "spell_fireball" else (128, 128)
                if image.size != expected: errors.append(f"spell icon dimensions {image.size}, expected {expected}: {path.relative_to(ROOT)}")
                min_colors = 30 if path.stem == "spell_fireball" else 300
                if len(image.getcolors(maxcolors=65536) or []) < min_colors:
                    errors.append(f"spell icon has insufficient shaded color detail: {path.relative_to(ROOT)}")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest in spell_hashes:
                errors.append(f"spell icon duplicates {spell_hashes[digest]}: {path.relative_to(ROOT)}")
            spell_hashes[digest] = str(path.relative_to(ROOT))
        except Exception as exc: errors.append(f"invalid spell texture {path.relative_to(ROOT)}: {exc}")
    if len(spell_textures) != 68:
        errors.append(f"expected 68 unique spell icons, found {len(spell_textures)}")
    font = PACK / "assets/minecraft/font/default.json"
    if not font.is_file(): errors.append("missing default font provider file")
    else:
        for provider in json.loads(font.read_text(encoding="utf-8")).get("providers", []):
            if provider.get("type") == "bitmap":
                target = PACK / "assets/alkatraz/textures" / provider["file"].split(":", 1)[-1]
                if not target.is_file(): errors.append(f"missing font texture {target.relative_to(ROOT)}")
            elif provider.get("type") == "space":
                if provider.get("advances", {}).get("\uf808") != -8:
                    errors.append("F808 font spacer must offset GUI title glyphs by -8px")

    # A native and nearest-neighbor enlarged connector review sheet supports pixel-level inspection.
    connector_files = sorted((PACK / "assets/alkatraz/textures/item").glob("connector_*.png"))
    tile_w, tile_h = 128, 30
    sheet = Image.new("RGBA", (6 * tile_w, 5 * tile_h), (32, 34, 40, 255))
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(connector_files):
        with Image.open(path) as img:
            tx, ty = (index % 6) * tile_w, (index // 6) * tile_h
            x, y = tx + (tile_w - 16) // 2, ty
            sheet.alpha_composite(img.convert("RGBA"), (x, y))
            label = path.stem.replace("connector_", "")
            bbox = draw.textbbox((0, 0), label)
            draw.text((tx + (tile_w - (bbox[2] - bbox[0])) // 2, ty + 17), label, fill="white")
    REVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(REVIEW / "research-connectors.png")
    enlarged = sheet.resize((sheet.width * 4, sheet.height * 4), Image.Resampling.NEAREST)
    enlarged.save(REVIEW / "research-connectors-8x.png")

    # Review the configured title and icon strings as bitmap sequences at native and enlarged scale.
    font_map = json.loads((PACK / "font-glyph-map.json").read_text(encoding="utf-8"))
    strings = font_map["configured_strings"]
    glyph_sheet = Image.new("RGBA", (360, len(strings) * 28), (32, 34, 40, 255))
    glyph_draw = ImageDraw.Draw(glyph_sheet)
    labels = ["menu_title_spells", "menu_title_equipment", "menu_title_research", "menu_title_recipes",
              "menu_title_stats", "menu_title_grimoire", "icon_back", "icon_confirm", "icon_cancel", "icon_info"]
    codepoints = {int(code[2:], 16): path for code, path in font_map["codepoints"].items()}
    for row, (label, string) in enumerate(zip(labels, strings)):
        y = row * 28
        glyph_draw.text((4, y + 8), label, fill="white")
        x = 180
        for char in string:
            glyph_path = PACK / codepoints[ord(char)]
            with Image.open(glyph_path) as glyph:
                glyph_sheet.alpha_composite(glyph.convert("RGBA"), (x, y + 5))
                x += glyph.width + 3
    glyph_sheet.save(REVIEW / "font-glyphs.png")
    glyph_sheet.resize((glyph_sheet.width * 4, glyph_sheet.height * 4), Image.Resampling.NEAREST).save(
        REVIEW / "font-glyphs-4x.png")

    if errors:
        raise SystemExit("Texture pack verification failed:\n- " + "\n- ".join(errors))
    # Keep the pack root (pack.mcmeta) at ZIP root; exclude generated review and manifest metadata.
    archive = ROOT / "resource-packs/Alkatraz-Modern.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in sorted(PACK.rglob("*")):
            if path.is_file() and path.name != "asset-manifest.json":
                zf.write(path, path.relative_to(PACK).as_posix())
    print(f"Verified {len(assets)} manifest assets across {len(by_material)} materials; wrote {archive.relative_to(ROOT)}")


if __name__ == "__main__":
    verify()
