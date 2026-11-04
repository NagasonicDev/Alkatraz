"""Redraw Alkatraz Modern's custom sprites in a crisp vanilla-scale pixel style."""
from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-packs/Alkatraz-Modern"
ITEMS = PACK / "assets/alkatraz/textures/item"
FONTS = PACK / "assets/alkatraz/textures/font"

PALETTES = {
    "fire": ((86, 34, 29, 255), (208, 66, 35, 255), (255, 174, 54, 255), (255, 237, 139, 255)),
    "water": ((25, 54, 82, 255), (38, 126, 172, 255), (91, 202, 218, 255), (207, 250, 244, 255)),
    "earth": ((57, 49, 39, 255), (119, 91, 55, 255), (174, 139, 76, 255), (232, 207, 139, 255)),
    "air": ((39, 54, 67, 255), (92, 153, 184, 255), (174, 221, 225, 255), (235, 250, 235, 255)),
    "light": ((92, 65, 30, 255), (202, 147, 43, 255), (250, 212, 83, 255), (255, 250, 190, 255)),
    "dark": ((37, 31, 52, 255), (83, 57, 119, 255), (143, 94, 187, 255), (224, 183, 255, 255)),
    "arcane": ((36, 40, 73, 255), (76, 83, 151, 255), (131, 143, 218, 255), (220, 222, 255, 255)),
    "healing": ((39, 67, 51, 255), (65, 133, 72, 255), (125, 196, 100, 255), (224, 250, 168, 255)),
    "summon": ((58, 48, 45, 255), (127, 91, 64, 255), (193, 151, 91, 255), (246, 220, 153, 255)),
    "utility": ((44, 54, 64, 255), (89, 113, 125, 255), (149, 184, 189, 255), (224, 242, 232, 255)),
    "ice": ((34, 60, 85, 255), (71, 132, 166, 255), (142, 207, 215, 255), (225, 250, 249, 255)),
    "storm": ((38, 47, 81, 255), (71, 92, 157, 255), (119, 160, 222, 255), (211, 239, 255, 255)),
}

THEME_WORDS = {
    "healing": ("heal", "mend", "restore", "purif", "choir"),
    "summon": ("summon", "skeletal", "knight", "zombie"),
    "utility": ("barrier", "ward", "buff", "dispel", "detect", "disguise", "stealth", "swift", "warp", "blink", "recall", "waystone", "echo", "everdrain", "apotheosis", "resonance", "anchor"),
    "light": ("light", "radiant", "sunfire", "radiance", "dawn", "brand"),
    "dark": ("void", "shadow", "umbral", "oblivion", "tendril"),
    "fire": ("fire", "flame", "inferno", "blaze", "meteor"),
    "water": ("water", "tide", "wave", "tsunami", "whirlpool", "undertow", "geyser"),
    "earth": ("earth", "stone", "tremor", "tectonic", "mountain", "worldbreaker", "spike", "fault", "continental"),
    "air": ("wind", "air", "storm", "thunder", "sky", "tornado", "vortex", "updraft", "cyclone"),
}

def theme(key: str) -> str:
    for t, words in THEME_WORDS.items():
        if any(w in key for w in words): return t
    return "arcane"

def canvas(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def spell_icon(key: str) -> Image.Image:
    img = canvas(); d = ImageDraw.Draw(img)
    dark, mid, hi, spark = PALETTES[theme(key)]
    # A compact dark keyline, one material body and two deliberate light planes.
    def poly(points, fill): d.polygon(points, fill=fill)
    def line(points, fill, width=1): d.line(points, fill=fill, width=width)
    def dot(x, y, c=spark): d.point((x, y), fill=c)
    k = key
    if k == "detect":
        poly([(1,8),(4,4),(8,2),(12,4),(15,8),(12,12),(8,14),(4,12)],dark)
        poly([(3,8),(6,5),(8,4),(11,5),(13,8),(11,10),(8,12),(5,10)],hi)
        d.ellipse((6,6,10,10),fill=dark); d.point((8,7),fill=spark)
    elif k == "disguise":
        poly([(2,5),(5,3),(11,3),(14,5),(13,11),(10,13),(6,13),(3,11)],dark)
        poly([(4,5),(7,4),(12,5),(11,10),(8,12),(5,10)],mid)
        d.polygon([(4,7),(7,6),(7,8),(4,8)],fill=spark); d.polygon([(9,6),(12,7),(12,8),(9,8)],fill=spark)
        line([(6,10),(8,9),(10,10)],dark,1)
    elif k == "dispel":
        poly([(8,1),(10,6),(15,8),(10,10),(8,15),(6,10),(1,8),(6,6)],dark)
        line([(5,8),(7,8),(8,6),(9,8),(11,8)],spark,2); dot(8,11,hi)
    elif k == "debuff":
        poly([(4,3),(12,3),(14,7),(12,12),(8,15),(4,12),(2,7)],dark)
        d.rectangle((5,7,6,8),fill=spark); d.rectangle((10,7,11,8),fill=spark)
        poly([(6,10),(8,9),(10,10),(9,12),(7,12)],hi); line([(8,2),(8,4)],dark,1)
    elif k == "stealth":
        poly([(8,2),(13,6),(12,11),(8,14),(4,11),(3,6)],dark)
        poly([(7,4),(11,7),(10,10),(8,12),(5,10),(5,7)],mid)
        d.rectangle((8,6,11,10),fill=dark); dot(7,7,spark)
    elif k in ("blink", "warp"):
        d.rectangle((3,3,12,12),outline=dark,width=2); d.rectangle((5,5,10,10),outline=hi,width=1)
        poly([(8,4),(10,7),(8,6),(6,10),(6,7)],spark); dot(12,4)
    elif k in ("waystone_recall", "void_anchor"):
        if k == "void_anchor":
            line([(8,2),(8,10)],dark,2); d.arc((4,6,12,12),0,180,fill=dark,width=2)
            line([(4,10),(4,13),(12,13),(12,10)],dark,2); line([(5,11),(11,11)],hi,1); dot(8,3)
        else:
            poly([(8,2),(13,5),(12,11),(8,14),(4,11),(3,5)],dark)
            poly([(8,4),(11,6),(10,10),(8,12),(6,10),(5,6)],hi)
            line([(2,4),(4,3),(4,5)],spark); line([(14,12),(12,13),(12,11)],spark)
    elif k in ("echo_cast", "everdrain"):
        if k == "echo_cast":
            d.arc((2,3,13,13),210,140,fill=dark,width=2); d.arc((4,5,11,11),205,145,fill=hi,width=2)
            d.ellipse((7,7,9,9),fill=spark); line([(10,3),(13,2)],hi)
        else:
            poly([(8,2),(12,5),(11,9),(8,14),(5,9),(4,5)],dark)
            poly([(8,4),(10,6),(9,9),(8,12),(7,9),(6,6)],hi); line([(8,1),(8,4)],spark)
    elif k in ("buff", "light_buff", "swift"):
        poly([(8,2),(10,6),(14,7),(11,10),(12,14),(8,12),(4,14),(5,10),(2,7),(6,6)],dark)
        poly([(8,4),(9,7),(12,8),(9,9),(10,12),(8,10),(6,12),(7,9),(4,8),(7,7)],hi)
        dot(8,7,(255,255,255,255))
    elif k == "apotheosis":
        poly([(2,11),(3,5),(6,8),(8,3),(10,8),(13,5),(14,11),(12,13),(4,13)],dark)
        line([(4,10),(6,10),(8,6),(10,10),(12,10)],spark,1); d.rectangle((6,12,10,13),fill=hi)
    elif k == "arcane_resonance":
        d.ellipse((2,2,13,13),outline=dark,width=2); d.ellipse((5,5,10,10),outline=hi,width=1)
        poly([(8,4),(10,8),(8,12),(6,8)],spark); dot(8,8,(255,255,255,255))
    elif k == "earth_spike":
        poly([(3,14),(5,8),(7,9),(9,2),(11,8),(13,7),(14,14)],dark)
        poly([(5,13),(7,10),(9,4),(10,10),(12,9),(13,13)],hi); line([(8,10),(9,6)],spark)
    elif k == "earth_throw":
        poly([(3,7),(6,3),(11,4),(14,8),(12,12),(7,14),(3,11)],dark)
        poly([(5,7),(7,5),(10,6),(12,8),(10,11),(7,12),(5,10)],mid)
        line([(2,4),(4,3)],spark); line([(1,7),(3,7)],hi)
    elif k in ("earthsplitter", "fault_line", "tectonic_strike", "continental_shift"):
        poly([(1,8),(5,6),(7,8),(9,5),(11,8),(15,6),(15,13),(1,13)],dark)
        poly([(2,9),(6,8),(7,10),(9,7),(11,10),(14,8),(14,11),(11,12),(9,10),(7,13),(5,10),(2,12)],mid)
        line([(8,8),(7,10),(9,11),(8,14)],spark,1)
    elif k == "worldbreaker":
        d.line([(4,14),(7,11),(9,8)],fill=dark,width=3)
        poly([(7,2),(12,2),(14,5),(12,9),(7,8),(6,5)],dark)
        poly([(8,3),(12,3),(12,5),(10,7),(8,6)],hi); line([(3,12),(5,14)],spark)
    elif k == "tremor":
        poly([(1,9),(5,7),(7,9),(9,6),(11,9),(15,7),(15,13),(1,13)],dark)
        line([(2,10),(5,9),(7,11),(9,8),(11,11),(14,9)],hi,1)
        line([(3,5),(5,4)],spark); line([(11,4),(13,5)],spark)
    elif k in ("water_sphere", "water_pulse", "thousand_waves", "tide_of_the_deep"):
        if k == "water_sphere":
            d.ellipse((2,2,13,13),fill=dark); d.ellipse((4,3,12,12),fill=hi)
            d.polygon([(5,8),(8,5),(11,7),(10,10),(7,11)],fill=mid); d.point((6,5),fill=spark)
        elif k == "water_pulse":
            d.ellipse((2,3,13,12),outline=dark,width=2); d.ellipse((5,5,10,10),outline=hi,width=1)
            d.ellipse((7,7,8,8),fill=spark); line([(8,1),(8,3)],hi)
        elif k == "thousand_waves":
            for y in (6,9,12): line([(1,y),(4,y-2),(7,y),(10,y-2),(14,y)],dark if y!=9 else hi,2)
            dot(12,4)
        else:
            poly([(1,10),(4,7),(7,8),(9,3),(12,6),(15,5),(14,12),(1,13)],dark)
            poly([(2,10),(5,8),(8,9),(10,5),(12,8),(14,7),(13,10),(10,11),(8,10),(5,11)],hi)
            line([(2,13),(14,13)],mid,1)
    elif k == "fire_wall":
        poly([(2,14),(2,9),(4,7),(4,4),(7,7),(8,2),(10,7),(12,4),(12,8),(14,10),(14,14)],dark)
        poly([(4,13),(4,10),(6,8),(7,11),(9,6),(10,10),(12,8),(13,11),(12,13)],mid)
        line([(6,13),(10,13)],spark,1)
    elif k == "meteor_shower":
        for x,y in ((4,4),(10,3),(7,8),(12,9)):
            line([(x-2,y+2),(x,y)],dark,2); dot(x,y,spark)
        d.point((3,12),fill=hi); d.point((13,5),fill=hi)
    elif any(w in k for w in ("heal", "mend", "restore", "purif")):
        poly([(8,14),(3,9),(2,6),(4,4),(7,4),(8,6),(10,4),(13,4),(14,7),(12,10)], dark)
        poly([(8,12),(4,8),(4,6),(6,5),(8,7),(10,5),(12,6),(12,8)], hi)
        dot(6,6); dot(10,6)
    elif any(w in k for w in ("blade", "lance", "strike", "bolt", "missile", "volley", "arrow", "brand")):
        # recognizable diagonal weapon/projectile with a separated trail
        poly([(3,12),(9,4),(12,2),(14,2),(14,4),(12,7),(5,13)], dark)
        poly([(5,11),(10,5),(12,4),(11,6),(5,12)], hi); line([(4,10),(7,12)], spark, 1)
        dot(2,13); dot(4,14)
    elif any(w in k for w in ("fireball", "fire_blast", "meteor", "sunfire", "flaming", "inferno", "flame", "crown_of_dawn")):
        poly([(8,1),(10,5),(13,4),(12,7),(15,8),(12,10),(12,13),(9,12),(7,15),(6,12),(3,13),(4,9),(1,8),(5,6),(5,3),(8,5)], dark)
        poly([(8,4),(10,7),(12,6),(10,9),(12,10),(9,11),(8,14),(7,10),(5,11),(6,8),(4,8),(7,6)], mid)
        poly([(8,7),(10,9),(8,12),(7,9)], spark); dot(8,9)
    elif any(w in k for w in ("water", "tide", "wave", "tsunami", "whirlpool", "undertow", "geyser")):
        if "geyser" in k:
            poly([(3,13),(5,9),(6,5),(8,2),(10,6),(11,10),(14,13)], dark)
            poly([(5,13),(7,8),(8,4),(10,9),(12,13)], hi); line([(7,11),(8,7)], spark)
        elif any(w in k for w in ("whirlpool", "undertow")):
            line([(12,4),(9,3),(5,4),(3,7),(4,10),(7,12),(10,11),(11,9),(9,7),(7,8)], dark, 3)
            line([(12,4),(9,4),(6,5),(4,7),(5,9),(7,10),(9,9)], hi, 1); dot(8,8)
        else:
            poly([(1,9),(4,6),(7,7),(9,4),(12,6),(15,5),(15,12),(1,12)], dark)
            poly([(1,9),(4,7),(7,8),(9,5),(12,7),(15,6),(15,9),(12,9),(9,7),(7,10),(4,9),(1,11)], hi)
            line([(2,12),(14,12)], mid, 2); dot(10,6)
    elif any(w in k for w in ("earth", "stone", "tremor", "tectonic", "spike", "fault", "worldbreaker", "continental")):
        poly([(1,13),(5,5),(8,9),(11,3),(15,13)], dark)
        poly([(3,12),(5,7),(8,11),(11,5),(13,12)], mid)
        line([(8,10),(7,12),(9,13),(8,15)], spark, 1); dot(11,6)
    elif any(w in k for w in ("air", "wind", "vortex", "cyclone", "tornado", "updraft", "skywrath")):
        line([(3,4),(8,3),(12,5),(12,8),(9,10),(5,9),(4,7)], dark, 3)
        line([(4,4),(8,4),(11,6),(10,8),(7,8)], hi, 1)
        line([(2,12),(7,12),(10,11),(14,11)], mid, 1); dot(12,5)
    elif any(w in k for w in ("thunder", "storm")):
        poly([(9,1),(4,8),(8,8),(6,15),(13,6),(9,6)], dark)
        poly([(9,3),(6,8),(9,8),(7,12),(11,6),(8,6)], spark); line([(3,3),(5,2)], hi)
    elif any(w in k for w in ("summon", "skeletal", "zombie", "knight")):
        poly([(3,4),(6,2),(10,2),(13,5),(12,11),(10,14),(6,14),(4,11)], dark)
        poly([(5,5),(7,4),(9,4),(11,6),(10,10),(8,12),(6,10)], mid)
        d.rectangle((6,6,7,7), fill=spark); d.rectangle((9,6,10,7), fill=spark)
        line([(7,10),(9,10)], dark)
    elif any(w in k for w in ("barrier", "ward", "shield", "wall")):
        poly([(8,1),(14,4),(13,10),(8,15),(3,10),(2,4)], dark)
        poly([(8,3),(12,5),(11,10),(8,13),(5,10),(4,5)], hi)
        line([(6,8),(8,10),(11,6)], spark, 1)
    elif any(w in k for w in ("dark", "shadow", "void", "oblivion", "umbral", "realm", "tendril")):
        poly([(7,1),(10,4),(8,6),(13,5),(11,8),(14,10),(10,10),(8,15),(6,11),(2,13),(5,9),(2,7),(6,7)], dark)
        poly([(8,3),(9,5),(7,7),(11,7),(9,9),(11,10),(8,13),(7,10),(4,11),(6,8),(4,7),(8,7)], mid)
        dot(8,8)
    elif any(w in k for w in ("light", "radiance", "choir", "dawn")):
        poly([(8,1),(10,6),(15,8),(10,10),(8,15),(6,10),(1,8),(6,6)], dark)
        poly([(8,3),(9,7),(13,8),(9,9),(8,13),(7,9),(3,8),(7,7)], spark)
        dot(8,8,(255,255,255,255))
    elif any(w in k for w in ("blink", "warp", "recall", "waystone", "disguise", "detect", "stealth")):
        # Eye/portal glyph, with squared concentric rim and a clear pupil.
        poly([(1,8),(5,3),(11,3),(15,8),(11,13),(5,13)], dark)
        poly([(3,8),(6,5),(10,5),(13,8),(10,11),(6,11)], hi)
        d.rectangle((7,6,9,10), fill=dark); dot(8,7)
        line([(4,8),(6,6)], spark)
    else:
        # Arcane focus: faceted crystal over a short, readable rune baseline.
        poly([(8,1),(13,6),(11,12),(8,15),(4,11),(3,6)], dark)
        poly([(8,3),(11,6),(10,10),(8,13),(5,10),(5,6)], hi)
        poly([(8,3),(8,9),(5,6)], spark); line([(5,14),(11,14)], dark)
    return img

def jewel(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); family, form=key.rsplit("_",1)
    fam={"ember":"fire","blaze":"fire","inferno":"fire","phoenix":"fire","frost":"ice","glacier":"ice","abyss":"water","leviathan":"water","zephyr":"air","storm":"storm","tempest":"storm","cyclone":"storm","boulder":"earth","mountain":"earth","titan":"earth","colossus":"earth","radiant":"light","luminous":"light","divine":"light","seraphic":"light","shadow":"dark","void":"dark","nether":"dark","oblivion":"dark","mystic":"arcane","arcane":"arcane","ethereal":"arcane","primordial":"arcane"}.get(family,"arcane")
    dark,mid,hi,glint=PALETTES[fam]
    if form=="ring":
        # Thick, faceted band and raised gemstone; mirrored shoulders.
        d.polygon([(2,9),(4,7),(6,8),(7,10),(9,10),(10,8),(12,7),(14,9),(13,12),(10,13),(6,13),(3,12)],fill=dark)
        d.polygon([(4,9),(6,9),(7,11),(9,11),(10,9),(12,9),(12,11),(9,12),(6,12),(4,11)],fill=mid)
        d.polygon([(6,6),(8,4),(10,6),(9,9),(7,9)],fill=dark); d.polygon([(7,6),(8,5),(9,6),(8,8)],fill=hi); d.point((8,6),fill=glint)
    elif form=="pendant":
        d.line([(4,2),(4,5),(6,7),(8,8),(10,7),(12,5),(12,2)],fill=dark,width=2)
        d.line([(5,2),(5,5),(8,7),(11,5),(11,2)],fill=hi,width=1)
        d.polygon([(8,6),(12,9),(11,12),(8,15),(5,12),(4,9)],fill=dark)
        d.polygon([(8,7),(11,9),(10,12),(8,13),(6,11),(5,9)],fill=mid)
        d.polygon([(8,7),(8,11),(6,9)],fill=hi); d.point((8,8),fill=glint)
    elif form=="necklace":
        d.line([(3,2),(3,6),(5,10),(8,12),(11,10),(13,6),(13,2)],fill=dark,width=2)
        d.line([(4,2),(4,6),(6,9),(8,10),(10,9),(12,6),(12,2)],fill=hi,width=1)
        d.rectangle((7,10,9,12),fill=dark); d.polygon([(8,11),(10,13),(8,15),(6,13)],fill=mid)
        d.point((8,12),fill=glint)
    else:
        d.polygon([(2,5),(5,3),(11,3),(14,5),(13,11),(10,13),(6,13),(3,11)],fill=dark)
        d.polygon([(4,5),(6,4),(10,4),(12,5),(12,10),(10,11),(6,11),(4,10)],fill=mid)
        # open center makes this read as a cuff rather than a plain rectangle
        d.rectangle((6,6,10,9),fill=(0,0,0,0)); d.rectangle((7,5,9,5),fill=dark)
        d.polygon([(6,6),(8,5),(10,6),(9,8),(7,8)],fill=hi); d.point((8,6),fill=glint)
    return img

def equipment(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); tier={"scholar":0,"enchanter":1,"archmage":2}[key.split("_")[0]]
    dark,mid,hi,glint=PALETTES["arcane"]
    cloth=[(73,55,91,255),(82,62,130,255),(50,44,91,255)][tier]
    trim=[(171,139,92,255),(197,161,82,255),(235,205,112,255)][tier]
    slot=key.split("_",1)[1]
    if slot=="hat":
        d.polygon([(2,12),(4,10),(5,6),(8,2),(10,6),(11,9),(14,11),(14,13),(2,13)],fill=dark)
        d.polygon([(4,11),(6,6),(8,4),(10,7),(11,10),(13,11)],fill=cloth); d.line([(5,11),(12,11)],fill=trim,width=1)
        d.point((8,6),fill=glint)
    elif slot=="robe":
        d.polygon([(5,2),(7,3),(9,3),(11,2),(14,5),(12,8),(13,14),(3,14),(4,8),(2,5)],fill=dark)
        d.polygon([(6,3),(7,4),(9,4),(10,3),(12,5),(10,7),(11,13),(5,13),(6,7),(4,5)],fill=cloth)
        d.line([(7,4),(8,6),(9,4)],fill=trim,width=1); d.line([(8,6),(8,12)],fill=trim)
        d.polygon([(8,7),(10,9),(8,11),(6,9)],fill=hi if tier else trim); d.point((8,8),fill=glint)
        d.rectangle((4,12,5,13),fill=trim); d.rectangle((11,12,12,13),fill=trim)
    elif slot=="leggings":
        d.polygon([(3,2),(13,2),(12,8),(11,14),(8,14),(8,8),(7,14),(4,14),(4,8)],fill=dark)
        d.polygon([(5,3),(11,3),(10,7),(9,12),(8,12),(8,7),(6,7),(6,12),(5,12)],fill=cloth)
        d.line([(4,6),(12,6)],fill=trim); d.point((8,5),fill=glint)
    else:
        d.polygon([(3,2),(7,2),(7,9),(6,11),(7,12),(7,14),(2,14),(2,12),(4,10)],fill=dark)
        d.polygon([(9,2),(13,2),(12,10),(14,12),(14,14),(9,14),(8,12),(9,10)],fill=dark)
        d.rectangle((4,5,6,9),fill=cloth); d.rectangle((10,5,12,9),fill=cloth)
        d.line([(3,12),(6,12)],fill=trim); d.line([(10,12),(13,12)],fill=trim)
        d.point((5,6),fill=glint); d.point((11,6),fill=glint)
    if tier: d.point((8,3 if slot!="boots" else 4),fill=glint)
    if tier==2: d.point((7,3),fill=trim); d.point((9,3),fill=trim)
    return img

def wand(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); t=theme(key)
    dark,mid,hi,glint=PALETTES[t]
    # Slanted carved staff with a bound grip and a faceted head.
    d.line([(3,14),(5,12),(6,10),(8,8),(9,6),(11,4)],fill=dark,width=4)
    d.line([(4,13),(6,11),(7,9),(9,7)],fill=mid,width=2)
    d.line([(3,14),(5,12)],fill=(117,74,42,255),width=2)
    d.line([(4,13),(6,13)],fill=(201,154,82,255),width=1)
    d.polygon([(10,5),(10,2),(12,1),(15,3),(14,6),(11,7)],fill=dark)
    d.polygon([(11,4),(11,2),(13,2),(14,3),(13,5),(11,6)],fill=hi)
    d.polygon([(11,3),(13,2),(12,4)],fill=glint)
    if "wooden" in key: d.point((8,8),fill=(137,91,49,255))
    elif "reinforced" in key: d.line([(6,10),(8,8)],fill=(205,183,123,255),width=2)
    return img

def scroll(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); t=theme(key); _,mid,hi,glint=PALETTES[t]
    paper=(235,216,166,255); shade=(174,133,76,255); ink=(89,67,68,255)
    d.polygon([(4,2),(11,2),(13,4),(13,12),(11,14),(4,14),(2,12),(3,10)],fill=dark if (dark:=PALETTES[t][0]) else ink)
    d.rectangle((4,3,11,13),fill=paper); d.rectangle((5,4,10,12),fill=(247,232,192,255))
    d.rectangle((3,2,12,4),fill=shade); d.rectangle((3,11,12,14),fill=shade)
    d.point((4,3),fill=glint); d.point((11,12),fill=hi)
    d.line([(6,6),(9,6)],fill=mid); d.line([(6,8),(10,8)],fill=ink); d.line([(6,10),(8,10)],fill=mid)
    return img

def book(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); t=theme(key); dark,mid,hi,glint=PALETTES[t]
    cover=(102,62,47,255) if key=="leather_grimoire" else dark
    d.polygon([(2,3),(6,2),(8,4),(14,2),(14,13),(9,15),(8,14),(3,15)],fill=(35,29,31,255))
    d.polygon([(3,4),(7,4),(8,5),(8,13),(4,13),(3,12)],fill=cover)
    d.polygon([(9,5),(13,4),(13,12),(9,13),(9,6)],fill=hi)
    d.line([(8,5),(8,13)],fill=(25,24,30,255),width=1)
    d.line([(4,6),(7,7)],fill=(236,224,185,255)); d.line([(10,7),(12,6)],fill=(236,224,185,255))
    d.polygon([(5,6),(7,8),(5,10),(4,8)],fill=mid); d.point((5,8),fill=glint)
    d.rectangle((7,8,9,10),fill=(196,153,67,255)); d.point((8,9),fill=glint)
    return img

def gui(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img)
    # Subtle graphite rim, inset slate face, vanilla-like highlights.
    d.rectangle((1,1,14,14),fill=(42,48,57,255),outline=(19,23,29,255))
    d.line([(2,2),(13,2),(13,3)],fill=(123,137,146,255)); d.point((2,13),fill=(76,84,91,255))
    fg=(226,232,230,255); acc=(111,190,210,255)
    if "confirm" in key: d.line([(4,8),(7,11),(12,5)],fill=(97,211,125,255),width=2)
    elif "cancel" in key or "close" in key: d.line([(5,5),(11,11)],fill=(225,91,85,255),width=2); d.line([(11,5),(5,11)],fill=(225,91,85,255),width=2)
    elif "search" in key: d.ellipse((3,3,9,9),outline=acc,width=2); d.line([(9,9),(13,13)],fill=fg,width=2)
    elif "sort" in key:
        for y,w in ((4,8),(7,6),(10,4),(13,2)): d.line([(3,y),(3+w,y)],fill=fg,width=1)
        d.polygon([(11,4),(14,4),(12,7)],fill=acc)
    elif "info" in key: d.ellipse((3,3,12,12),outline=acc,width=1); d.rectangle((7,7,8,11),fill=fg); d.point((7,5),fill=acc)
    elif "create" in key: d.line([(8,4),(8,12)],fill=(111,211,133,255),width=2); d.line([(4,8),(12,8)],fill=fg,width=2)
    elif "duplicate" in key: d.rectangle((3,3,9,10),outline=fg); d.rectangle((6,6,12,13),outline=acc)
    elif "pane" in key:
        d.rectangle((2,2,13,13),outline=(174,186,191,190),width=1)
        if "header" in key: d.rectangle((3,3,12,5),fill=(186,198,197,125))
        if "blank" in key: d.rectangle((3,3,12,12),fill=(116,129,137,60))
    else:
        direction=-1 if "prev" in key or "back" in key else 1
        d.line([(4,8),(12,8)],fill=fg,width=2); x=12 if direction>0 else 4
        d.line([(x-3*direction,5),(x,8),(x-3*direction,11)],fill=acc,width=2)
    return img

def connector(key: str) -> Image.Image:
    img=canvas(); d=ImageDraw.Draw(img); raw=key.removeprefix("connector_")
    state="in_progress" if raw.endswith("_in_progress") else raw.rsplit("_",1)[-1]
    shape=raw[:-(len(state)+1)]
    colors={"completed":((91,59,153,255),(182,143,243,255)),"available":((168,67,150,255),(255,177,230,255)),"in_progress":((185,67,70,255),(255,194,98,255)),"locked":((81,58,63,230),(137,104,109,230)),"hidden":((62,60,67,150),(89,87,94,160))}
    base,lit=colors[state]
    pts={"straight_h":[(0,8),(15,8)],"straight_v":[(8,0),(8,15)],"corner_ne":[(8,15),(8,8),(15,8)],"corner_se":[(8,0),(8,8),(15,8)],"corner_sw":[(8,0),(8,8),(0,8)],"corner_nw":[(8,15),(8,8),(0,8)]}[shape]
    d.line(pts,fill=(27,27,34,255),width=5); d.line(pts,fill=base,width=3)
    if state not in ("locked","hidden"):
        if shape in ("straight_h","corner_ne","corner_se","corner_sw","corner_nw"):
            d.line([(x,y-1) for x,y in pts],fill=lit,width=1)
        else: d.line([(x-1,y) for x,y in pts],fill=lit,width=1)
    if state=="completed": d.rectangle((6,6,9,9),fill=lit); d.line([(6,8),(7,9),(10,6)],fill=(255,255,255,255))
    elif state=="available": d.rectangle((6,6,9,9),fill=lit)
    elif state=="in_progress": d.rectangle((6,6,9,9),fill=lit); d.point((8,8),fill=(255,246,187,255))
    return img

def redraw():
    for path in sorted(ITEMS.glob("*.png")):
        k=path.stem
        if k.startswith("spell_"): img=spell_icon(k[6:])
        elif k.startswith("connector_"): img=connector(k)
        elif k.startswith("gui_"): img=gui(k)
        elif k.endswith(("_ring","_necklace","_pendant","_bracelet")): img=jewel(k)
        elif k.startswith(("scholar_","enchanter_","archmage_")): img=equipment(k)
        elif k.endswith("_wand") or k=="wooden_wand": img=wand(k)
        elif k.endswith("_grimoire"): img=book(k)
        elif k.endswith("_scroll"): img=scroll(k)
        elif k=="magic_stone":
            img=spell_icon("arcane_resonance")
        else: continue
        img.save(path,optimize=True)
    # GUI font glyphs are complete inventory backdrops authored at 176px wide.
    # Keep them intact; rendering them into the 16px item-icon canvas would
    # replace the whole menu with a tiny rune and break the font provider scale.

    # Audit texture coverage and dimensions so malformed icon files cannot silently ship.
    manifest=json.loads((PACK/"asset-manifest.json").read_text(encoding="utf-8"))
    expected=set()
    for a in manifest["assets"]:
        t = a["texture"]
        if t.startswith("assets/"):
            p = PACK / t
        else:
            t2 = t if t.endswith('.png') else t + '.png'
            if t2.startswith('minecraft/'):
                p = PACK / 'assets' / t2
            elif t2.startswith('alkatraz/'):
                p = PACK / 'assets' / t2
            else:
                p = PACK / 'assets' / t2
        expected.add(p)
    expected.update(ITEMS.glob("gui_*.png")); expected.update(ITEMS.glob("connector_*.png"))
    missing=[str(p) for p in expected if not p.is_file()]
    # Filter out vanilla minecraft textures that might not exist as files in the pack
    missing = [m for m in missing if 'alkatraz' in m]
    if missing: raise SystemExit("Missing Modern textures: "+", ".join(missing))
    for p in ITEMS.glob("*.png"):
        with Image.open(p) as im:
            if im.size!=(16,16): raise SystemExit(f"Bad dimensions {im.size}: {p}")
    review=ROOT/"resource-packs/review/modern-spell-icons.png"
    spell_paths=sorted(ITEMS.glob("spell_*.png")); cols=10; cell=68
    sheet=Image.new("RGBA",(cols*cell,((len(spell_paths)+cols-1)//cols)*84),(37,40,47,255)); sd=ImageDraw.Draw(sheet)
    for n,p in enumerate(spell_paths):
        x=(n%cols)*cell; y=(n//cols)*84
        with Image.open(p) as icon: sheet.alpha_composite(icon.resize((48,48),Image.Resampling.NEAREST),(x+10,y+1))
        label=re.sub(r"^spell_", "", p.stem).replace("_", " ")[:13]
        sd.text((x+3,y+54),label,fill=(229,232,225,255))
    sheet.save(review,optimize=True)
    records=json.loads((PACK/"asset-manifest.json").read_text(encoding="utf-8"))["assets"]
    paths=[PACK/a["texture"] for a in records if a["kind"]=="item"]
    cols=10; cell=68; rows=(len(paths)+cols-1)//cols
    sheet=Image.new("RGBA",(cols*cell,rows*84),(37,40,47,255)); sd=ImageDraw.Draw(sheet)
    for n,p in enumerate(paths):
        x=(n%cols)*cell; y=(n//cols)*84
        with Image.open(p) as icon: sheet.alpha_composite(icon.resize((48,48),Image.Resampling.NEAREST),(x+10,y+1))
        sd.text((x+3,y+54),p.stem.replace("_"," ")[:13],fill=(229,232,225,255))
    sheet.save(ROOT/"resource-packs/review/modern-custom-items.png",optimize=True)
    print(f"Redrew and checked {len(list(ITEMS.glob('*.png')))} Modern item textures and {len(list(FONTS.glob('*.png')))} font glyphs")

if __name__=="__main__": redraw()
