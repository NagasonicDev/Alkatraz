"""Author a distinct, full-slot pixel-art skill icon for every Modern spell.

Spell identity and circle level are read from each spell's YAML immediately
before painting. Element is encoded in the frame; the artwork has an independent
spell-specific palette and silhouette.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path

import yaml
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / "core/src/main/resources/spells"
PACK = ROOT / "resource-packs/Alkatraz-Modern"
TEXTURES = PACK / "assets/alkatraz/textures/item"

# Element color is reserved for the frame, making it an immediately readable
# legend. The art itself gets a deliberately free, per-spell colorway.
ELEMENT = {
    "FIRE": ((104, 34, 30), (226, 75, 39), (255, 172, 65)),
    "WATER": ((25, 66, 101), (44, 150, 195), (134, 229, 231)),
    "EARTH": ((77, 58, 35), (170, 123, 61), (231, 199, 122)),
    "AIR": ((39, 70, 91), (97, 166, 184), (193, 229, 218)),
    "LIGHT": ((106, 77, 31), (220, 177, 74), (255, 241, 172)),
    "DARK": ((56, 37, 79), (132, 79, 164), (214, 169, 230)),
    "NONE": ((48, 56, 84), (113, 135, 181), (208, 225, 244)),
}

# Eight stops per spell colorway. Related spells do not inherit their element's
# hue, so e.g. fire spells can read as garnet, jade, violet, or ivory.
PALETTE = {
    "fireball": "ember", "fire_blast": "citrine", "fire_wall": "garnet",
    "flame_lance": "rose", "flaming_volley": "jade", "inferno_pilgrimage": "amber",
    "inferno_rite": "violet", "meteor_shower": "cobalt", "sunfire_barrage": "ivory",
    "geyser": "azure", "water_sphere": "mint", "water_pulse": "rose",
    "thousand_waves": "jade", "tide_of_the_deep": "amethyst", "tsunami": "cobalt",
    "undertow": "copper", "whirlpool": "teal", "everdrain": "crimson",
    "wind_vortex": "silver", "tornado": "jade", "cyclone_nest": "amber",
    "skywrath": "violet", "stormcaller": "cobalt", "thunderclap": "ivory",
    "air_burst": "coral", "air_blades": "steel", "updraft": "mint", "swift": "amber",
    "wind_barrier": "rose", "earth_spike": "jade", "earth_throw": "copper",
    "earthen_wall": "ochre", "earthsplitter": "crimson", "continental_shift": "teal",
    "fault_line": "violet", "tectonic_strike": "cobalt", "tremor": "amber",
    "worldbreaker": "obsidian", "heal": "mint", "lesser_heal": "rose",
    "light_buff": "azure", "light_bolt": "crimson", "radiant_brand": "violet",
    "radiance": "ivory", "choir_of_light": "teal", "crown_of_dawn": "amber",
    "apotheosis": "amethyst", "dark_tendrils": "jade", "oblivion": "obsidian",
    "shadow_realm": "cobalt", "skeletal_knights": "ivory", "umbral_volley": "crimson",
    "summon_zombies": "ochre", "debuff": "violet", "stealth": "teal",
    "disguise": "rose", "detect": "amber", "dispel": "mint", "magic_missile": "copper",
    "blink": "azure", "warp": "amethyst", "waystone_recall": "jade",
    "arcane_resonance": "rose", "arcane_ward": "silver", "barrier": "cobalt",
    "buff": "amber", "echo_cast": "teal", "void_anchor": "obsidian",
}

RAMPS = {
    "ember": ((48,20,34),(96,30,42),(157,40,42),(218,62,39),(247,112,48),(255,177,75),(255,222,142),(255,246,211)),
    "citrine": ((52,34,30),(105,53,37),(166,82,42),(222,123,47),(247,174,56),(255,211,99),(255,237,163),(255,250,218)),
    "garnet": ((38,19,34),(78,24,42),(128,30,49),(181,44,58),(222,74,69),(244,125,91),(255,183,144),(255,224,198)),
    "rose": ((43,27,48),(82,38,70),(133,52,99),(184,75,126),(219,112,150),(242,163,177),(255,207,207),(255,239,230)),
    "jade": ((22,38,39),(28,70,57),(39,109,69),(58,151,79),(94,190,94),(145,220,123),(197,240,163),(232,252,214)),
    "amber": ((45,32,29),(91,48,34),(143,73,35),(193,105,39),(228,146,47),(248,185,72),(255,218,119),(255,244,196)),
    "violet": ((31,24,46),(57,34,77),(91,44,112),(132,58,147),(174,82,178),(208,123,202),(233,175,228),(250,224,247)),
    "cobalt": ((20,29,49),(30,48,81),(39,75,127),(48,108,177),(67,148,212),(111,190,234),(167,222,248),(224,246,255)),
    "ivory": ((48,39,34),(93,73,48),(145,114,58),(194,158,68),(226,196,91),(246,224,135),(255,241,184),(255,252,224)),
    "azure": ((22,36,57),(28,64,101),(36,101,153),(47,143,195),(75,184,221),(125,216,238),(181,237,247),(228,251,255)),
    "mint": ((24,43,42),(32,79,70),(45,121,99),(63,163,126),(98,198,151),(148,225,181),(196,243,210),(232,255,238)),
    "amethyst": ((34,25,50),(62,35,88),(98,47,130),(139,63,171),(177,89,202),(208,132,222),(233,180,240),(250,228,255)),
    "crimson": ((42,22,33),(82,28,43),(132,34,51),(185,43,59),(222,66,67),(245,112,86),(255,172,133),(255,224,197)),
    "copper": ((42,30,29),(86,48,38),(132,70,46),(180,98,54),(212,137,73),(234,176,107),(250,211,155),(255,239,209)),
    "teal": ((20,38,43),(26,69,74),(34,106,108),(46,145,143),(75,181,169),(126,211,193),(178,235,219),(226,250,240)),
    "silver": ((32,38,49),(62,73,89),(98,112,128),(136,153,165),(174,190,195),(208,222,221),(234,243,235),(255,255,246)),
    "steel": ((26,34,47),(49,62,79),(77,96,116),(109,132,150),(145,170,181),(184,207,208),(220,237,227),(248,255,241)),
    "coral": ((48,28,35),(95,38,47),(148,53,57),(198,78,64),(228,117,75),(245,161,105),(255,205,156),(255,239,216)),
    "ochre": ((41,33,28),(82,61,38),(124,90,47),(167,120,55),(200,153,68),(224,187,96),(242,219,143),(255,243,200)),
    "obsidian": ((20,20,31),(37,30,50),(57,41,73),(77,53,96),(101,69,116),(132,91,142),(172,130,175),(220,191,218)),
}

def rgba(c, a=255): return (c[0], c[1], c[2], a)

class Painter:
    """Crisp, stepped-edged primitives and faceted material rendering."""
    def __init__(self, image, ramp):
        self.im=image; self.d=ImageDraw.Draw(image); self.c=ramp
    def line(self, pts, color, width=1): self.d.line(pts, fill=rgba(color), width=width, joint="curve")
    def poly(self, pts, color, outline=None, width=1): self.d.polygon(pts, fill=rgba(color), outline=rgba(outline) if outline else None, width=width)
    def rect(self, box, color, outline=None, width=1): self.d.rectangle(box, fill=rgba(color), outline=rgba(outline) if outline else None, width=width)
    def ellipse(self, box, color, outline=None, width=1): self.d.ellipse(box, fill=rgba(color) if color else None, outline=rgba(outline) if outline else None, width=width)
    def arc(self, box, start, end, color, width=1): self.d.arc(box,start,end,fill=rgba(color),width=width)
    def facet(self, pts, color): self.d.polygon(pts, fill=rgba(color))
    def gem(self, x, y, r, angle=0):
        c=self.c; points=[]
        for i in range(8):
            a=math.radians(45*i+angle); points.append((x+math.cos(a)*r,y+math.sin(a)*r))
        self.poly(points,c[1],c[0],3)
        self.facet([points[0],points[1],(x,y)],c[5]); self.facet([points[1],points[2],(x,y)],c[6])
        self.facet([points[2],points[3],(x,y)],c[4]); self.facet([points[3],points[4],(x,y)],c[3])
        self.facet([points[4],points[5],(x,y)],c[2]); self.facet([points[5],points[6],(x,y)],c[1])
        self.facet([points[6],points[7],(x,y)],c[3]); self.facet([points[7],points[0],(x,y)],c[7])
        self.line([points[0],(x,y),points[4]],c[0],1); self.line([points[2],(x,y),points[6]],c[0],1)
        self.line([points[7],points[0],points[1]],c[7],2)
    def orb(self,x,y,r):
        c=self.c; self.ellipse((x-r,y-r,x+r,y+r),c[1],c[0],3)
        self.ellipse((x-r+4,y-r+4,x+r-4,y+r-4),c[3])
        self.facet([(x-r+6,y-r+8),(x,y-r+3),(x+3,y+1),(x-r+4,y+8)],c[5])
        self.facet([(x+2,y+2),(x+r-5,y-r+8),(x+r-3,y+5),(x+1,y+r-4)],c[2])
        inset=max(1,r//4); self.arc((x-r+inset,y-r+inset,x+r-inset,y+r-inset),195,270,c[7],max(1,r//8))
        self.ellipse((x-r//2,y-r//2,x-r//2+5,y-r//2+5),c[7])
    def flame(self,x,y,h,w=18):
        c=self.c
        self.poly([(x-w//2,y+h//2),(x-w//2+2,y),(x-w//4,y+h//5),(x,y-h//2),(x+w//5,y-h//8),(x+w//2,y+h//2)],c[1],c[0],3)
        self.poly([(x-w//3,y+h//2-3),(x-w//5,y+2),(x+1,y-h//5),(x+w//3,y+h//2-3)],c[4])
        self.poly([(x-3,y+h//2-4),(x-1,y),(x+4,y+h//4)],c[7])
        self.line([(x-w//3,y+h//2-5),(x-w//5,y+2),(x+1,y-h//5)],c[6],2)
    def droplet(self,x,y,r,tilt=0):
        c=self.c; pts=[(x,y-r),(x+r,y-r//5),(x+r-2,y+r//2),(x,y+r),(x-r+2,y+r//2),(x-r,y-r//5)]
        self.poly(pts,c[2],c[0],3)
        self.facet([(x,y-r+4),(x+r-4,y-r//5),(x+1,y+2)],c[5])
        self.facet([(x+1,y+2),(x+r-2,y-r//5),(x+r-3,y+r//2),(x,y+r-3)],c[3])
        self.facet([(x,y+2),(x,y+r-3),(x-r+5,y+r//2)],c[1])
        self.line([(x-r//2,y-r//5),(x-2,y-r//2),(x+2,y-r//2)],c[7],3)
    def bolt(self, x,y,s=1):
        c=self.c; p=[(x+8*s,y-43*s),(x-14*s,y-5*s),(x-2*s,y-5*s),(x-13*s,y+40*s),(x+18*s,y-7*s),(x+4*s,y-7*s)]
        self.poly(p,c[1],c[0],3); self.poly([(x+5*s,y-27*s),(x-6*s,y-3*s),(x+5*s,y-3*s),(x-4*s,y+23*s),(x+11*s,y-5*s),(x+1*s,y-5*s)],c[6])
        self.line([p[0],p[1],p[2]],c[7],2)
    def ring(self,x,y,r,thick=5,segments=0):
        c=self.c; self.ellipse((x-r,y-r,x+r,y+r),None,c[0],thick+4)
        self.arc((x-r+2,y-r+2,x+r-2,y+r-2),205,330,c[6],thick)
        self.arc((x-r+2,y-r+2,x+r-2,y+r-2),25,150,c[2],thick)
        if segments:
            for i in range(segments):
                a=math.tau*i/segments; xx=x+math.cos(a)*r; yy=y+math.sin(a)*r
                self.ellipse((xx-3,yy-3,xx+3,yy+3),c[5],c[0],1)

def make_background(key,element,level,seed):
    n=128; im=Image.new("RGBA",(n,n)); p=im.load()
    # Neutral blue-black enamel with restrained pixel grain and a soft core lift.
    for y in range(n):
        for x in range(n):
            r=math.sqrt(((x-63.5)/96)**2+((y-56)/94)**2)
            lift=max(0,1-r)*13 + max(0,1-(x+y)/250)*6
            grain=((x*37+y*61+seed)%19)-9
            grain2=((x*17+y*43+seed//7)%17)-8
            grain3=((x*53+y*23+seed//13)%15)-7
            p[x,y]=(int(max(0,16+lift*.72+grain*.42)),
                    int(max(0,20+lift*1.04+grain2*.42)),
                    int(max(0,31+lift*1.42+grain3*.42)),255)
    d=ImageDraw.Draw(im); low,mid,hi=ELEMENT.get(element,ELEMENT["NONE"])
    # Chamfered two-tone frame: element color has a stable and explicit location.
    d.polygon([(0,9),(9,0),(118,0),(127,9),(127,118),(118,127),(9,127),(0,118)],fill=rgba(low))
    d.line([(2,10),(10,2),(117,2),(125,10)],fill=rgba(hi),width=2)
    d.line([(2,10),(2,117),(10,125)],fill=rgba(mid),width=2)
    d.line([(125,10),(125,117),(117,125)],fill=rgba(low),width=2)
    d.polygon([(8,13),(13,8),(114,8),(119,13),(119,110),(110,119),(18,119),(8,109)],fill=(24,30,43,255))
    d.line([(12,15),(15,12),(112,12)],fill=rgba((66,74,88)),width=1)
    d.line([(12,16),(12,108)],fill=rgba((45,53,67)),width=1)
    # Four geometric corner inlays reinforce a coherent RPG skill-tile language.
    for x,y,sx,sy in ((11,11,1,1),(116,11,-1,1),(11,116,1,-1),(116,116,-1,-1)):
        d.line([(x,y+sy*8),(x,y),(x+sx*8,y)],fill=rgba(mid),width=2)
        d.point((x+sx*2,y+sy*2),fill=rgba(hi)); d.point((x+sx*3,y+sy*3),fill=rgba(hi))
    # Ten recessed sockets encode magic-circle level 0..9. Each lit pip is one level.
    centers=[24+i*8.85 for i in range(10)]
    for i,x in enumerate(centers):
        d.ellipse((x-3,111,x+3,117),fill=(10,13,21,255),outline=rgba((67,71,83)),width=1)
        if i<level:
            d.ellipse((x-2,112,x+2,116),fill=rgba((247,190,82)))
            d.point((x-1,112),fill=rgba((255,240,174)))
            d.point((x+1,116),fill=rgba((146,82,37)))
    return im

# Every key has an explicit, spell-specific visual construction. Shared drawing
# tools provide material consistency; no finished symbol or texture is reused.
def draw_art(key, p: Painter, options: dict):
    c=p.c; d=p.d; C=(64,59)
    def ticks(points, col=None):
        col=col or c[5]
        for x,y,r in points:
            p.line([(x-r,y),(x+r,y)],col,2); p.line([(x,y-r),(x,y+r)],col,2)
            p.ellipse((x-2,y-2,x+2,y+2),c[7])
    if key=="fireball":
        p.line([(26,93),(40,78),(50,68)],c[1],12); p.line([(29,91),(42,77)],c[5],3)
        p.orb(73,53,29); p.flame(73,51,38,24); p.ellipse((83,38,90,45),c[7])
        for x,y in ((29,83),(40,67),(47,56)): p.poly([(x,y-6),(x+4,y),(x,y+7),(x-3,y)],c[4])
    elif key=="fire_blast":
        for i in range(12):
            a=math.tau*i/12; x=64+math.cos(a)*39; y=59+math.sin(a)*39
            p.poly([(64+math.cos(a)*21,59+math.sin(a)*21),(x,y),(64+math.cos(a+.17)*28,59+math.sin(a+.17)*28)],c[2+i%3],c[0],2)
        p.orb(64,59,20); p.flame(64,57,31,24); ticks([(64,18,4),(25,46,3),(101,44,4)])
    elif key=="fire_wall":
        for i,x in enumerate((31,47,64,81,97)):
            h=(61,77,86,72,58)[i]; p.rect((x-8,92-h,x+8,94),c[1],c[0],3)
            p.poly([(x-7,90-h),(x-6,87-h),(x-2,90-h),(x+1,78-h),(x+4,90-h),(x+7,88-h),(x+7,91-h)],c[4+i%2])
            p.line([(x-2,91-h),(x-1,80-h),(x+2,90-h)],c[7],2)
        p.line([(23,97),(104,97)],c[0],5); p.line([(27,100),(100,100)],c[3],2)
    elif key=="flame_lance":
        p.line([(29,94),(86,37)],c[0],18); p.line([(31,91),(85,37)],c[2],11)
        p.poly([(69,51),(90,27),(104,24),(99,41),(85,57)],c[1],c[0],3)
        p.poly([(82,43),(93,29),(100,28),(96,39),(86,49)],c[6]); p.line([(37,83),(72,48)],c[7],3)
        p.poly([(23,97),(39,92),(35,105)],c[4]); p.gem(89,35,5,22)
    elif key=="flaming_volley":
        for x,y,a in ((39,49,-1),(64,37,0),(88,49,1)):
            p.flame(x,y,38,22); p.line([(x-2,y+23),(x+2,y+39)],c[1],5)
            p.poly([(x-8,y+27),(x+1,y+19),(x+8,y+27),(x,y+33)],c[3],c[0],2)
        p.arc((26,40,102,111),205,335,c[5],3); ticks([(25,32,3),(104,31,3)])
    elif key=="inferno_pilgrimage":
        for i,(x,y) in enumerate(((43,92),(52,76),(63,60),(74,43),(84,27))):
            p.rect((x-8,y,x+8,y+9),c[1+i%2],c[0],2); p.line([(x-5,y+2),(x+4,y+2)],c[6],2)
        p.flame(64,39,45,26); p.arc((29,52,98,113),205,330,c[4],3)
        for x,y in ((36,60),(92,70),(48,43),(80,49)): p.gem(x,y,5,0)
    elif key=="inferno_rite":
        p.ring(64,62,39,4,8); p.ring(64,62,25,3,3)
        for x,y in ((38,75),(64,32),(90,75)): p.flame(x,y,31,17)
        p.poly([(64,43),(76,60),(70,80),(64,88),(57,77),(53,61)],c[2],c[0],3)
        p.facet([(64,48),(72,61),(64,68)],c[7]); p.facet([(64,68),(64,84),(59,76)],c[4])
        ticks([(31,49,3),(97,49,3),(64,99,4)])
    elif key=="meteor_shower":
        for x,y,r in ((40,41,13),(68,55,18),(88,30,10),(46,78,9)):
            p.line([(x-r*2,y+r*2),(x-r//2,y+r//2)],c[0],8); p.line([(x-r*2,y+r*2),(x-r//2,y+r//2)],c[2],3)
            p.gem(x,y,r,22); p.poly([(x-r,y-r//2),(x,y-r),(x+r//2,y)],c[6])
        p.line([(25,94),(94,94)],c[1],3)
    elif key=="sunfire_barrage":
        p.ring(64,42,24,4,8); p.orb(64,42,13)
        for x,y in ((36,73),(50,83),(64,76),(78,83),(92,73)):
            p.flame(x,y,25,15); p.line([(x,y+14),(x,y+23)],c[5],2)
        p.arc((25,40,103,105),205,335,c[4],4); ticks([(25,60,4),(103,60,4)])
    elif key=="geyser":
        p.poly([(27,94),(36,76),(46,81),(55,55),(64,24),(72,57),(82,78),(92,72),(101,94)],c[2],c[0],3)
        p.poly([(39,91),(51,72),(61,41),(68,57),(80,86),(91,84)],c[5]); p.line([(31,97),(98,97)],c[1],5)
        for x,y in ((39,41),(91,47),(48,29),(81,21)): p.droplet(x,y,6)
        p.line([(61,78),(64,48),(68,73)],c[7],3)
    elif key=="water_sphere":
        p.ring(64,60,37,4,6); p.orb(64,60,29); p.droplet(64,57,18)
        p.arc((40,36,88,84),190,270,c[7],3); p.ellipse((49,42,55,48),c[7])
        for a in range(0,360,60):
            x=64+math.cos(math.radians(a))*45; y=60+math.sin(math.radians(a))*45; p.ellipse((x-3,y-3,x+3,y+3),c[5])
    elif key=="water_pulse":
        for r,w in ((38,5),(28,4),(17,3)): p.arc((64-r,61-r,64+r,61+r),195,345,c[1+r//20],w)
        p.orb(64,60,10); p.line([(24,87),(37,81)],c[5],3); p.line([(104,87),(91,81)],c[5],3)
        p.poly([(22,81),(34,79),(28,90)],c[6]); p.poly([(106,81),(94,79),(100,90)],c[6]); ticks([(64,17,4)])
    elif key=="thousand_waves":
        for i,y in enumerate((43,56,69,82,95)):
            pts=[]
            for x in range(25,104,8): pts.append((x,y+int(math.sin((x/8+i*.6))*5)))
            p.line(pts,c[1+i%3],8-i%2); p.line([(x,y-2) for x,y in pts[1:-2]],c[6],2)
        p.gem(64,49,9,22); p.droplet(90,32,7)
    elif key=="tide_of_the_deep":
        p.ring(64,62,39,4,10); p.arc((28,25,100,98),210,505,c[3],9); p.arc((41,38,87,85),200,500,c[6],6)
        p.droplet(64,65,22); p.poly([(64,43),(75,64),(64,80),(53,64)],c[5],c[0],2)
        p.line([(31,85),(45,88),(53,96)],c[7],3); p.line([(97,39),(83,36),(78,28)],c[2],3)
    elif key=="tsunami":
        p.poly([(23,92),(25,68),(36,57),(51,58),(61,43),(70,27),(84,21),(85,39),(99,42),(105,59),(101,92)],c[1],c[0],4)
        p.poly([(31,87),(34,68),(47,66),(58,73),(69,53),(78,36),(83,31),(82,52),(96,53),(99,65),(94,86)],c[4])
        p.poly([(78,37),(84,29),(87,45),(98,48),(89,53),(79,49)],c[7]); p.line([(27,98),(100,98)],c[3],4)
        for x,y in ((38,51),(50,46),(94,34)): p.droplet(x,y,5)
    elif key=="undertow":
        p.arc((27,24,101,98),210,500,c[1],9); p.arc((39,36,89,86),205,500,c[4],7); p.arc((51,48,77,73),210,495,c[6],5)
        p.poly([(64,43),(75,57),(68,61),(65,88),(58,88),(60,62),(52,57)],c[2],c[0],2)
        p.line([(64,96),(64,84)],c[7],3); p.poly([(57,86),(64,96),(71,86)],c[7])
    elif key=="whirlpool":
        p.arc((25,25,103,103),200,510,c[1],9); p.arc((38,38,90,90),195,510,c[4],7); p.arc((51,51,77,77),180,500,c[6],5)
        p.orb(64,64,8); p.poly([(68,56),(88,39),(84,62),(69,70)],c[7]); p.line([(30,90),(45,96)],c[5],3)
    elif key=="everdrain":
        p.droplet(64,54,33); p.arc((35,34,91,90),30,165,c[5],4); p.line([(64,83),(64,100)],c[0],6)
        p.poly([(53,94),(64,106),(75,94)],c[1],c[0],2); p.gem(64,53,10,0); p.line([(57,52),(62,57),(72,46)],c[7],3)
    elif key=="wind_vortex":
        for box,st,en,w in (((26,28,102,99),190,510,8),((39,40,89,87),185,510,7),((52,52,76,75),180,500,5)):
            p.arc(box,st,en,c[1+w%3],w)
        p.line([(32,35),(46,30),(57,34)],c[6],3); p.line([(97,89),(84,96),(73,92)],c[4],3)
        p.poly([(77,24),(94,27),(84,42)],c[7]); p.orb(64,64,6)
    elif key=="tornado":
        p.poly([(55,23),(73,23),(70,37),(85,47),(91,61),(83,73),(76,83),(71,101),(60,101),(58,85),(45,78),(37,66),(43,52),(57,39)],c[1],c[0],3)
        for y,w in ((42,21),(55,36),(69,27),(82,16)): p.line([(64-w//2,y),(64+w//2,y)],c[5 if y%2 else 3],5)
        p.line([(61,31),(67,44),(61,58),(68,71),(62,89)],c[7],3); p.arc((27,22,101,105),210,335,c[4],3)
    elif key=="cyclone_nest":
        for x,y,r in ((39,67,20),(64,45,27),(88,71,18)):
            p.arc((x-r,y-r,x+r,y+r),190,500,c[1],5); p.arc((x-r+7,y-r+7,x+r-7,y+r-7),200,490,c[5],3)
            p.gem(x,y,r//3,22)
        p.line([(31,98),(96,98)],c[3],4); ticks([(64,17,4)])
    elif key=="skywrath":
        p.poly([(24,68),(31,52),(44,50),(52,39),(62,48),(71,36),(88,47),(102,59),(97,76),(81,83),(45,82)],c[1],c[0],4)
        p.facet([(31,61),(46,56),(56,64),(72,56),(88,60),(94,70),(78,76),(48,75)],c[4]); p.gem(78,62,7,0)
        p.bolt(64,70,.7); p.line([(35,88),(22,97),(46,94)],c[6],3); p.line([(94,42),(103,32)],c[7],3)
    elif key=="stormcaller":
        p.ring(64,53,31,4,8); p.poly([(31,69),(41,52),(54,55),(64,35),(75,48),(90,43),(100,65),(85,78),(43,80)],c[1],c[0],3)
        p.bolt(65,69,.8); p.line([(34,91),(52,91)],c[3],4); p.line([(80,95),(99,95)],c[5],3); ticks([(29,41,4),(97,31,3)])
    elif key=="thunderclap":
        p.bolt(64,61,1.15)
        for x,y,flip in ((31,35,1),(94,36,-1),(28,85,1),(99,82,-1)):
            p.poly([(x,y),(x+13*flip,y+4),(x+4*flip,y+12),(x+14*flip,y+20),(x-2*flip,y+14),(x+6*flip,y+8)],c[4],c[0],2)
        p.ring(64,62,43,3,4)
    elif key=="air_burst":
        p.ring(64,61,35,5,8); p.poly([(64,25),(76,45),(99,48),(80,64),(88,88),(64,76),(40,88),(48,64),(29,48),(52,45)],c[1],c[0],3)
        p.orb(64,59,16); p.arc((43,38,85,80),200,275,c[7],4); ticks([(26,31,4),(102,31,4),(64,16,5)])
    elif key=="air_blades":
        for x,y,a in ((39,77,-1),(64,60,0),(87,41,1)):
            p.poly([(x-17,y+18),(x-6,y-10),(x+4,y-21),(x+2,y+1),(x+17,y-8),(x+4,y+12),(x-8,y+23)],c[1],c[0],3)
            p.facet([(x-6,y+9),(x+1,y-11),(x+3,y+1),(x+9,y-4),(x+1,y+13)],c[6]); p.line([(x-14,y+16),(x-2,y+2)],c[7],2)
        p.arc((27,31,99,102),205,330,c[4],3)
    elif key=="updraft":
        for x,off in ((49,0),(64,12),(79,-6)):
            p.line([(x,100),(x-9,83+off),(x+6,67+off),(x-6,50+off),(x+7,30+off)],c[1],8)
            p.line([(x-1,92),(x-5,82+off),(x+5,67+off),(x-3,50+off)],c[5],3)
        p.poly([(46,38),(64,20),(82,38),(70,34),(64,43),(58,34)],c[7],c[0],2); ticks([(31,57,3),(96,58,3)])
    elif key=="swift":
        p.poly([(24,72),(42,66),(49,49),(63,45),(74,52),(86,42),(101,51),(105,65),(94,76),(77,76),(62,92),(45,93)],c[1],c[0],4)
        p.facet([(31,70),(51,63),(57,54),(64,55),(72,62),(87,52),(96,57),(92,67),(75,69),(62,83),(47,85)],c[5])
        for y in (42,51,60): p.line([(22,y),(43,y)],c[3 if y==51 else 6],3)
        p.gem(91,58,6,22)
    elif key=="wind_barrier":
        p.arc((25,22,103,104),185,355,c[1],9); p.arc((35,31,93,93),190,350,c[4],6); p.line([(27,95),(101,95)],c[0],5)
        for x in (42,64,86): p.line([(x,73),(x+8,59),(x,46)],c[6],3)
        p.gem(64,38,9,22); ticks([(35,44,3),(93,44,3)])
    elif key=="earth_spike":
        p.poly([(29,94),(38,75),(46,78),(56,45),(64,19),(74,54),(82,76),(92,66),(101,94)],c[2],c[0],4)
        p.facet([(64,22),(70,55),(64,85),(56,46)],c[5]); p.facet([(64,85),(82,76),(92,69),(99,92)],c[1]); p.line([(29,98),(101,98)],c[0],5)
        p.gem(64,54,8,0)
    elif key=="earth_throw":
        for x,y,r in ((43,68,15),(69,49,19),(84,77,13),(47,91,10)):
            pts=[(x-r,y-r//2),(x-r//2,y-r),(x+r//2,y-r),(x+r,y),(x+r//2,y+r),(x-r//2,y+r),(x-r,y+r//3)]
            p.poly(pts,c[2],c[0],3); p.facet([pts[1],pts[2],(x,y)],c[5]); p.facet([(x,y),pts[3],pts[4]],c[1]); p.line([pts[0],pts[1],pts[2]],c[6],2)
        p.line([(26,100),(97,100)],c[3],4)
    elif key=="earthen_wall":
        for row,(y,xs) in enumerate(((54,(34,58,82)),(74,(46,70,94)),(93,(34,58,82)))):
            for x in xs:
                p.poly([(x-10,y-10),(x+8,y-10),(x+11,y-5),(x+9,y+10),(x-9,y+10),(x-12,y+4)],c[2+row%2],c[0],2)
                p.line([(x-8,y-7),(x+5,y-7)],c[6],2); p.facet([(x+1,y-5),(x+8,y-5),(x+8,y+7),(x,y+7)],c[1])
        p.line([(29,103),(99,103)],c[0],4)
    elif key=="earthsplitter":
        p.poly([(32,32),(52,40),(61,57),(58,76),(49,95),(38,90),(47,72),(45,61)],c[2],c[0],3)
        p.poly([(96,32),(76,40),(67,57),(70,76),(79,95),(90,90),(81,72),(83,61)],c[2],c[0],3)
        p.poly([(60,39),(66,50),(60,59),(69,71),(61,81),(67,92),(60,103),(56,91),(62,78),(54,68),(60,58),(55,49)],c[7])
        p.line([(28,104),(100,104)],c[0],4); ticks([(39,31,3),(89,31,3)])
    elif key=="continental_shift":
        p.poly([(25,83),(35,60),(49,67),(58,46),(71,55),(83,38),(102,77),(93,91),(37,93)],c[2],c[0],3)
        for x,y in ((41,67),(61,55),(84,49)): p.poly([(x-9,y),(x,y-14),(x+8,y),(x+4,y+7),(x-6,y+8)],c[5],c[0],2)
        p.arc((21,33,107,104),205,335,c[6],3); p.line([(29,99),(98,99)],c[1],4)
    elif key=="fault_line":
        p.poly([(25,47),(52,49),(61,60),(56,75),(28,83)],c[2],c[0],3); p.poly([(103,47),(76,49),(67,60),(72,75),(100,83)],c[2],c[0],3)
        p.line([(63,26),(57,45),(69,53),(58,64),(69,75),(59,94),(64,105)],c[7],5)
        p.line([(32,45),(54,51)],c[6],3); p.line([(74,72),(96,79)],c[5],3)
    elif key=="tectonic_strike":
        p.gem(64,60,27,22); p.poly([(42,35),(64,26),(87,36),(77,46),(64,42),(52,49)],c[2],c[0],3)
        p.poly([(35,72),(51,64),(62,77),(58,98),(43,104),(30,90)],c[1],c[0],3)
        p.poly([(93,72),(77,64),(66,77),(70,98),(85,104),(98,90)],c[1],c[0],3)
        p.line([(42,78),(54,84)],c[6],3); p.line([(86,78),(74,84)],c[6],3)
    elif key=="tremor":
        for y,w in ((49,21),(65,34),(82,25),(97,42)):
            p.line([(64-w,y),(58,y+5),(64,y+10),(70,y+5),(64+w,y)],c[2 if y%2 else 4],5)
        p.gem(64,64,14,0); p.line([(27,39),(43,47)],c[5],3); p.line([(101,39),(85,47)],c[5],3)
    elif key=="worldbreaker":
        p.poly([(64,20),(73,42),(94,34),(84,56),(103,68),(81,75),(85,101),(64,87),(42,102),(46,77),(24,68),(44,55),(34,34),(56,42)],c[2],c[0],4)
        p.gem(64,61,18,22); p.facet([(64,45),(77,60),(64,64)],c[7]); p.facet([(64,64),(64,79),(52,61)],c[1])
        p.ring(64,61,42,2,8)
    elif key in ("heal","lesser_heal","light_buff","buff","radiance","radiant_brand","choir_of_light","crown_of_dawn","apotheosis","dispel","light_bolt"):
        if key=="light_bolt":
            p.ring(64,61,35,3,4); p.bolt(64,61,1.05)
            p.poly([(42,40),(58,43),(47,55)],c[7]); p.poly([(82,77),(67,73),(77,61)],c[5])
            for x,y in ((38,30),(89,33),(94,84),(33,82)): p.gem(x,y,5,22)
        elif key=="heal":
            p.ring(64,62,34,4,4); p.rect((56,37,72,87),c[3],c[0],3); p.rect((39,54,89,70),c[3],c[0],3)
            p.rect((60,42,68,61),c[7]); p.rect((44,58,62,66),c[5]); ticks([(31,34,4),(96,34,4)])
        elif key=="lesser_heal":
            p.ring(64,62,27,3,4); p.rect((58,44,70,80),c[3],c[0],3); p.rect((46,55,82,67),c[3],c[0],3)
            p.line([(60,47),(60,59)],c[7],3); p.gem(64,92,7,22)
        elif key in ("light_buff","buff"):
            p.orb(64,58,24); p.ring(64,58,37,3,8)
            for i in range(8):
                a=math.tau*i/8; x=64+math.cos(a)*48; y=58+math.sin(a)*40
                p.poly([(x,y-8),(x+5,y),(x,y+8),(x-5,y)],c[5],c[0],1)
            p.gem(64,58,11,22)
        elif key=="radiance":
            p.orb(64,61,21); p.ring(64,61,34,3,0)
            for i in range(16):
                a=math.tau*i/16; r=44 if i%2==0 else 38; x=64+math.cos(a)*r; y=61+math.sin(a)*r
                p.line([(64+math.cos(a)*30,61+math.sin(a)*30),(x,y)],c[5 if i%2==0 else 3],3)
            p.gem(64,61,9,22)
        elif key=="radiant_brand":
            p.ring(64,61,36,5,6); p.gem(64,61,26,22); p.arc((34,31,94,91),200,270,c[7],4)
            p.poly([(64,24),(70,49),(96,43),(77,62),(92,82),(67,73),(58,99),(53,73),(30,83),(48,62),(32,45),(56,50)],c[5])
            p.orb(64,61,9)
        elif key=="choir_of_light":
            for x,y,r in ((35,68,10),(50,51,13),(64,39,16),(78,51,13),(93,68,10)):
                p.gem(x,y,r,22); p.poly([(x-r,y+r),(x,y+r+17),(x+r,y+r)],c[2],c[0],2)
            p.arc((25,28,103,105),205,335,c[5],4); ticks([(64,19,4)])
        elif key=="crown_of_dawn":
            p.poly([(26,84),(27,49),(42,66),(49,39),(64,61),(79,39),(86,66),(101,49),(102,84)],c[2],c[0],4)
            p.rect((27,84,101,97),c[1],c[0],3); p.gem(64,52,13,22)
            for x,y in ((34,67),(49,57),(79,57),(94,67)): p.gem(x,y,6,0)
            p.line([(36,88),(92,88)],c[6],3)
        elif key=="apotheosis":
            p.ring(64,61,38,4,12); p.poly([(64,26),(70,53),(99,43),(78,64),(93,91),(65,75),(36,94),(49,66),(30,44),(57,53)],c[3],c[0],3)
            p.gem(64,60,18,22); p.arc((45,42,83,80),205,270,c[7],3); ticks([(64,16,4),(106,61,4)])
        else: # dispel: a snapped curse-ring and a clean severing stroke
            p.ring(64,62,35,5,8); p.arc((30,30,98,98),22,140,c[1],8); p.bolt(64,60,.78)
            p.poly([(37,30),(49,31),(40,44)],c[7]); p.poly([(88,90),(100,91),(91,78)],c[5])
    elif key in ("dark_tendrils","oblivion","shadow_realm","skeletal_knights","umbral_volley","summon_zombies","debuff","stealth","disguise"):
        if key=="dark_tendrils":
            for x,sgn in ((40,-1),(57,-1),(73,1),(89,1)):
                pts=[(64,100),(x,80),(x+sgn*10,64),(x,47),(x+sgn*8,33)]
                p.line(pts,c[1],10); p.line([(a-2,b) for a,b in pts],c[5],2)
                p.poly([(x+sgn*8,33),(x+sgn*16,24),(x+sgn*12,42)],c[2],c[0],2)
            p.orb(64,70,12)
        elif key=="oblivion":
            p.ring(64,62,37,4,8); p.ellipse((39,37,89,87),c[0],c[0],2); p.ellipse((48,46,80,78),c[1])
            p.poly([(64,47),(73,62),(64,78),(55,62)],c[5],c[0],2); p.ellipse((61,58,67,65),c[7])
            p.arc((23,21,105,103),200,320,c[3],2)
        elif key=="shadow_realm":
            p.poly([(30,94),(30,56),(42,43),(51,53),(64,31),(76,51),(87,41),(99,56),(99,94)],c[1],c[0],4)
            p.rect((42,60,86,93),c[0]); p.poly([(51,91),(53,63),(62,75),(69,54),(78,74),(80,91)],c[2])
            p.gem(64,48,8,22); p.arc((25,30,103,107),205,335,c[5],3)
        elif key=="skeletal_knights":
            for x in (47,81):
                p.ellipse((x-10,35,x+10,57),c[5],c[0],3); p.rect((x-8,50,x+8,56),c[2])
                p.ellipse((x-5,42,x-1,46),c[0]); p.ellipse((x+2,42,x+6,46),c[0])
                p.poly([(x-12,62),(x+12,62),(x+17,91),(x,84),(x-17,91)],c[1],c[0],3)
                p.line([(x-5,65),(x-5,81)],c[6],2); p.line([(x+5,65),(x+5,81)],c[6],2)
            p.line([(29,100),(99,100)],c[3],4); p.gem(64,68,9,22)
        elif key=="umbral_volley":
            for x,y,s in ((40,51,11),(64,40,15),(87,53,11),(51,79,9),(78,81,9)):
                p.poly([(x-s,y+s),(x-s//2,y-s),(x+s,y-s),(x+s//2,y),(x+s,y+s)],c[1],c[0],3)
                p.gem(x,y,s//2,22); p.line([(x-s,y+s+4),(x+s,y+s+4)],c[5],2)
            p.arc((28,31,100,102),205,335,c[3],3)
        elif key=="summon_zombies":
            for x,y,s in ((48,57,17),(80,66,14)):
                p.rect((x-s,y-s,x+s,y+s),c[1],c[0],3); p.rect((x-s+4,y-s+5,x+s-4,y+3),c[2])
                p.rect((x-s+5,y-s+9,x-s+9,y-s+14),c[6]); p.rect((x+s-9,y-s+9,x+s-5,y-s+14),c[7])
                p.line([(x-8,y+9),(x,y+12),(x+8,y+9)],c[0],3); p.line([(x-s+5,y+s),(x-s+7,y+s+10)],c[3],4)
            p.line([(30,100),(96,100)],c[4],4); p.gem(64,31,7,0)
        elif key=="debuff":
            p.poly([(64,27),(97,44),(91,78),(64,98),(37,78),(31,44)],c[1],c[0],4)
            p.rect((43,48,53,59),c[6]); p.rect((75,48,85,59),c[6]); p.poly([(47,71),(64,65),(81,71),(76,83),(52,83)],c[2])
            p.line([(51,75),(64,71),(77,75)],c[7],3); p.arc((39,38,89,90),20,150,c[5],3)
        elif key=="stealth":
            p.poly([(64,25),(94,39),(99,66),(85,91),(43,91),(29,66),(34,39)],c[1],c[0],4)
            p.poly([(42,61),(47,47),(63,41),(83,47),(88,63),(78,78),(50,78)],c[0]); p.line([(41,84),(87,84)],c[3],3)
            p.ellipse((52,54,58,60),c[7]); p.ellipse((70,54,76,60),c[7]); p.arc((37,32,91,91),195,330,c[5],3)
        else: # disguise: two interlocking face plates, one fading away
            p.poly([(35,42),(62,34),(68,72),(52,91),(34,78)],c[2],c[0],3); p.poly([(66,34),(94,42),(95,78),(77,91),(62,72)],c[4],c[0],3)
            p.line([(47,55),(54,55)],c[7],3); p.line([(76,55),(84,55)],c[7],3)
            p.line([(45,72),(54,68),(60,72)],c[0],3); p.line([(69,72),(78,68),(86,72)],c[0],3)
            p.arc((31,29,98,98),200,340,c[5],3)
    elif key in ("detect","magic_missile","blink","warp","waystone_recall","arcane_resonance","arcane_ward","barrier","echo_cast","void_anchor"):
        if key=="detect":
            p.poly([(25,62),(40,43),(64,35),(88,43),(103,62),(88,81),(64,89),(40,81)],c[2],c[0],4)
            p.ellipse((46,43,82,80),c[1],c[0],3); p.ellipse((54,51,74,72),c[5]); p.ellipse((61,56,69,66),c[0]); p.ellipse((63,57,67,61),c[7])
            ticks([(31,41,3),(96,40,3)])
        elif key=="magic_missile":
            p.poly([(28,91),(57,46),(77,29),(99,25),(101,47),(82,68),(40,98)],c[1],c[0],4)
            p.facet([(57,46),(77,29),(99,25),(82,50)],c[5]); p.facet([(82,50),(101,47),(82,68),(40,98)],c[2]); p.line([(38,85),(71,51)],c[7],3)
            p.line([(23,101),(34,93)],c[3],5); p.gem(88,37,8,22)
        elif key=="blink":
            for r,col in ((35,c[1]),(25,c[2]),(14,c[0])): p.poly([(64,61-r),(64+r,61),(64,61+r),(64-r,61)],col,c[0],3)
            p.poly([(57,29),(83,53),(70,53),(79,74),(47,52),(61,52)],c[7],c[5],2); p.arc((29,26,99,98),200,330,c[6],3)
        elif key=="warp":
            p.poly([(37,30),(91,30),(101,43),(87,61),(101,79),(91,93),(37,93),(27,79),(41,61),(27,43)],c[1],c[0],4)
            p.poly([(47,42),(81,42),(89,51),(77,61),(89,72),(81,82),(47,82),(39,72),(51,61),(39,51)],c[3])
            p.gem(64,61,17,22); p.line([(32,61),(49,61)],c[7],3)
        elif key=="waystone_recall":
            p.poly([(64,24),(93,39),(88,79),(64,99),(40,79),(35,39)],c[2],c[0],4); p.poly([(64,34),(83,44),(80,75),(64,89),(48,75),(45,44)],c[4])
            p.gem(64,61,16,0); p.ring(64,61,43,3,6); p.arc((21,20,107,104),205,335,c[6],3)
            for x,y in ((35,38),(92,38),(35,83),(93,83)): p.rect((x-3,y-3,x+3,y+3),c[7])
        elif key=="arcane_resonance":
            p.ring(64,61,40,4,12); p.ring(64,61,25,3,6); p.gem(64,61,14,22)
            for x in (39,89): p.line([(x,36),(x,86)],c[4],2)
            p.arc((29,28,99,94),195,340,c[7],3); ticks([(64,17,3),(105,61,3),(64,105,3),(23,61,3)])
        elif key=="arcane_ward":
            p.poly([(64,23),(94,36),(89,72),(64,99),(39,72),(34,36)],c[1],c[0],4)
            p.poly([(64,33),(84,42),(81,69),(64,88),(47,69),(44,42)],c[3]); p.gem(64,60,14,22)
            p.line([(51,60),(61,70),(80,48)],c[7],4); p.line([(64,30),(64,42)],c[5],3)
        elif key=="barrier":
            p.arc((26,26,102,102),190,350,c[1],9); p.arc((37,38,91,91),190,350,c[4],5); p.line([(29,89),(99,89)],c[0],5)
            for x in (39,51,64,77,89): p.line([(x,82),(x,67)],c[5],3)
            p.gem(64,44,10,0); p.poly([(64,54),(72,66),(64,75),(56,66)],c[3],c[0],2)
        elif key=="echo_cast":
            for r,w,col in ((38,4,c[1]),(27,3,c[4]),(16,2,c[6])): p.arc((64-r,61-r,64+r,61+r),200,340,col,w)
            p.orb(64,61,12); p.arc((32,30,96,94),20,160,c[5],3); p.arc((38,36,90,88),205,335,c[3],2)
            for x,y in ((31,38),(97,38),(34,88),(94,88)): p.gem(x,y,5,22)
        else: # void anchor: weighted abyssal core with four hooked chains
            p.ring(64,60,32,6,4); p.ellipse((46,42,82,78),c[0],c[0],2); p.gem(64,60,14,22)
            for x,y,sx,sy in ((35,34,-1,-1),(93,34,1,-1),(35,86,-1,1),(93,86,1,1)):
                p.line([(x,y),(x+sx*8,y+sy*7),(x+sx*13,y+sy*2)],c[1],5)
                p.poly([(x+sx*13,y+sy*2),(x+sx*18,y+sy*1),(x+sx*15,y+sy*7)],c[5],c[0],2)
            p.arc((25,22,103,99),205,335,c[3],3)
    else:
        raise KeyError(f"No new, dedicated icon composition for spell {key!r}")

    # The distinct silhouette and facets above are the complete spell emblem.

def compose_spell(key, element, level, role, options):
    seed=int(hashlib.sha256(key.encode()).hexdigest()[:8],16)
    ramp=RAMPS[PALETTE[key]]
    image=make_background(key,element,level,seed)
    art=Image.new("RGBA",(128,128),(0,0,0,0)); painter=Painter(art,ramp)
    draw_art(key,painter,options)
    # Apply a common upper-left key light to every material face. The small
    # deterministic per-pixel variation creates hand-shaded texture instead of
    # flat vector fills while keeping hard pixel edges and crisp silhouettes.
    ap=art.load()
    for y in range(128):
        for x in range(128):
            r,g,b,a=ap[x,y]
            if not a: continue
            nearest=min(range(len(ramp)),key=lambda i:(r-ramp[i][0])**2+(g-ramp[i][1])**2+(b-ramp[i][2])**2)
            if nearest==0 or nearest==7: continue
            light=1.13 - .21*(x/127) - .25*(y/127)
            facets=(((x*17+y*31+seed)%13)-6)*.009
            bands=(.025 if (x+y+seed%7)%9==0 else 0) - (.02 if (x-y+seed%5)%11==0 else 0)
            factor=max(.66,min(1.28,light+facets+bands))
            ap[x,y]=(min(255,int(r*factor)),min(255,int(g*factor)),min(255,int(b*factor)),a)
    # Enlarge and center the artwork within the safe field. This avoids the
    # tiny floating-glyph look and makes each composition occupy its slot.
    bounds=art.getbbox()
    if bounds:
        crop=art.crop(bounds); avail=(94,88)
        scale=min(avail[0]/crop.width,avail[1]/crop.height,1.42)
        size=(max(1,round(crop.width*scale)),max(1,round(crop.height*scale)))
        art=Image.new("RGBA",(128,128),(0,0,0,0))
        crop=crop.resize(size,Image.Resampling.NEAREST)
        art.alpha_composite(crop,(64-size[0]//2,60-size[1]//2))
    # Shadow and colored aura separate foreground geometry from the dark enamel.
    alpha=art.getchannel("A")
    glow=Image.new("RGBA",art.size,(*ramp[4],0)); glow.putalpha(alpha.filter(ImageFilter.GaussianBlur(8)).point(lambda v:int(v*.22)))
    image.alpha_composite(glow)
    shadow=Image.new("RGBA",art.size,(0,0,0,0)); mask=alpha.filter(ImageFilter.MaxFilter(5)).point(lambda v:int(v*.8))
    shadow.paste((5,7,13,255),(0,0,128,128)); shadow.putalpha(mask); image.alpha_composite(shadow,(2,3))
    image.alpha_composite(art)
    return image

def main():
    TEXTURES.mkdir(parents=True,exist_ok=True)
    seen={}; keys=[]
    # The YAML is opened and read for every spell before its own artwork is made.
    for source in sorted(SPELLS.glob("*.yml")):
        if source.stem.endswith("_options"): continue
        spell=yaml.safe_load(source.read_text(encoding="utf-8")) or {}
        options_path=SPELLS/f"{source.stem}_options.yml"
        options_doc=yaml.safe_load(options_path.read_text(encoding="utf-8")) or {} if options_path.exists() else {}
        key=str(spell.get("id",source.stem))
        if spell.get("gui_item") is None or spell.get("gui_custom_model_data") is None: continue
        element=str(spell.get("element","NONE")).upper(); role=str(spell.get("role","")).upper()
        level=int(spell.get("level",0))
        if not 0<=level<=9: raise ValueError(f"{source.name}: unsupported circle level {level}")
        if key not in PALETTE: raise KeyError(f"Missing free colorway for {key}")
        image=compose_spell(key,element,level,role,options_doc.get("options",{}) or {})
        path=TEXTURES/f"spell_{key}.png"; image.save(path,optimize=True)
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if digest in seen: raise SystemExit(f"Duplicate spell texture: {key} equals {seen[digest]}")
        seen[digest]=key; keys.append(key)
        color_count=len(image.getcolors(maxcolors=65536) or [])
        if image.size!=(128,128) or color_count<300:
            raise SystemExit(f"Detail check failed for {key}: size={image.size}, colors={color_count}")
        print(f"Painted {key} ({element}, circle {level}, {PALETTE[key]} palette, {color_count} colors)")
    manifest=json.loads((PACK/"asset-manifest.json").read_text(encoding="utf-8"))
    expected={item["key"] for item in manifest["assets"] if item["kind"]=="spell"}
    if set(keys)!=expected: raise SystemExit(f"Spell YAML/manifest mismatch: missing={sorted(expected-set(keys))}, extra={sorted(set(keys)-expected)}")
    # Large contact sheet for visual review; only modern spell textures appear.
    review=ROOT/"resource-packs/review/modern-individual-spells.png"
    cols=6; cell=156; cell_h=176; rows=(len(keys)+cols-1)//cols
    sheet=Image.new("RGBA",(cols*cell,rows*cell_h),(20,23,30,255)); draw=ImageDraw.Draw(sheet)
    for i,key in enumerate(keys):
        x=(i%cols)*cell; y=(i//cols)*cell_h
        sheet.alpha_composite(Image.open(TEXTURES/f"spell_{key}.png").convert("RGBA"),(x+14,y+1))
        draw.text((x+7,y+134),key.replace("_"," ")[:22],fill=(231,234,238,255))
    sheet.save(review,optimize=True)
    print(f"Built {len(keys)} distinct 128x128 spell icons; review sheet: {review}")

if __name__=="__main__": main()
