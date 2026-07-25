import yaml

waypoints = {
    # === FIRE ===
    "hellfire_sovereign": {"cinder_ward": [[-7, 4], [-8, 4]]},
    "cinder_ward": {"smoke_shroud": [[-4, 2]]},
    "inferno_mastery": {"pyroclasm": [[1, -7], [1, -8]]},
    # === WATER ===
    "tidal_sovereign": {"glacial_barrier": [[-7, 4], [-8, 4]]},
    "glacial_barrier": {"frost_ward": [[-4, 2]]},
    "abyssal_mastery": {"pressure_surge": [[1, -7], [1, -8]]},
    # === EARTH ===
    "mountain_wraith": {"crystal_resonance": [[-7, 4], [-8, 4]]},
    "crystal_resonance": {"bedrock_ward": [[-4, 2]]},
    "earth_sovereign": {"seismic_focus": [[1, -7], [1, -8]]},
    # === AIR ===
    "skyward_phantom": {"skyward_barrier": [[-7, 4], [-8, 4]]},
    "skyward_barrier": {"squall_ward": [[-4, 2]]},
    "wind_sovereign": {"tempest_focus": [[1, -7], [1, -8]]},
    # === LIGHT ===
    "celestial_phantom": {"restoration_ward": [[-7, 4], [-8, 4]]},
    "restoration_ward": {"aegis_light": [[-4, 2]]},
    "light_sovereign": {"solar_focus": [[1, -7], [1, -8]]},
    # === DARK ===
    "eclipse_phantom": {"abyssal_ward": [[-7, 4], [-8, 4]]},
    "abyssal_ward": {"void_ward": [[-4, 2]]},
    "dark_sovereign": {"shadow_focus": [[1, -7], [1, -8]]},
    # === MAGIC (all 12 overlap fixes) ===
    "grand_ward": {"barrier_weave": [[-7, 4], [-8, 4]]},
    "barrier_weave": {"disciplined_channel": [[0, 2], [-3, 1], [-5, 2]]},
    "mana_sovereignty": {"arcane_theory": [[-1, -7], [-1, -8]]},
    "mana_reservoir": {"ritual_geometry": [[-1, -5]]},
    "disciplined_channel": {
        "mana_conservation": [[1, -2], [1, 1]],
        "ritual_geometry": [[-2, -1], [-2, 1]],
        "spellcraft_field_notes": [[2, -1], [2, 1]],
    },
    "mana_conduit": {"disciplined_channel": [[1, 4], [1, 5]]},
    "arcane_nexus": {"combat_casting": [[1, 7], [1, 8]]},
    "spell_synthesis": {"spell_resonance": [[7, -4]]},
    "ward_craft_route": None,  # placeholder, handled below
}

# ward_craft has TWO children that need custom paths:
# 1. ward_craft → barrier_weave: route through (-4,1),(-5,2) to avoid (-4,2) overlap
#    But barrier_weave is the CHILD, so barrier_weave.edge_paths already covers disciplined_channel.
#    We need ward_craft to have edge_paths for... wait.
#    Actually, edge_paths are on the CHILD node. ward_craft→barrier_weave:
#    barrier_weave is the child. We already set barrier_weave.edge_paths for disciplined_channel.
#    We also need barrier_weave.edge_paths for ward_craft.
#
#    But wait — ward_craft→barrier_weave has no overlap with the updated disciplined_channel path.
#    Let me check: ward_craft(-3,1)→barrier_weave(-5,3): (-4,2)
#    disciplined_channel→barrier_weave: (0,2),(-1,1),(-2,1),(-3,1),(-4,2),(-5,2)
#    Still overlap at (-4,2)!
#
#    Fix: add ward_craft→barrier_weave waypoint to barrier_weave.edge_paths
#    barrier_weave edge_paths for ward_craft: [(-4,1),(-5,2)]

# Also: awakened_focus→ward_craft overlaps with disciplined_channel→barrier_weave at (-2,1)
# awakened_focus(0,0)→ward_craft(-3,1): (-1,0),(-2,1)
# Fix: ward_craft.edge_paths for awakened_focus: [(-2,0)]

# Updated waypoints with all magic fixes:
waypoints["barrier_weave"]["ward_craft"] = [[-4, 1], [-5, 2]]
waypoints["ward_craft"] = {"awakened_focus": [[-2, 0]]}

with open("core/src/main/resources/research.yml", "r") as f:
    data = yaml.safe_load(f)

for node_id, wp in waypoints.items():
    if wp is None:
        continue
    if node_id in data["nodes"]:
        data["nodes"][node_id]["edge_paths"] = {
            pid: [{"x": w[0], "y": w[1]} for w in wps]
            for pid, wps in wp.items()
        }

with open("core/src/main/resources/research.yml", "w") as f:
    yaml.dump(data, f, default_flow_style=False, sort_keys=False, allow_unicode=True, width=120)

print("Done!")
