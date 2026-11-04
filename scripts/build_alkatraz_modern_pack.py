"""Build and verify Alkatraz-Modern only, including its authored pixel-art pass."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"

PIPELINE = (
    "build_texture_pack_manifest.py",
    "build_spell_and_book_assets.py",
    "build_jewelry_and_equipment_assets.py",
    "build_texture_pack_gui.py",
    "build_texture_pack_research_and_font.py",
    "redesign_modern_textures.py",
    "build_individual_spell_textures.py",
    "build_fireball_icon.py",
    "fix_modern_vanilla_item_fallbacks.py",
    "sync_armor_pack_versions.py",
    "verify_texture_pack.py",
)

def main() -> None:
    for name in PIPELINE:
        subprocess.run([sys.executable, str(SCRIPTS / name)], cwd=ROOT, check=True)

if __name__ == "__main__":
    main()
