import yaml
from collections import defaultdict

with open("core/src/main/resources/research.yml", "r") as f:
    data = yaml.safe_load(f)

nodes = data["nodes"]

node_info = {}
for nid, ndata in nodes.items():
    node_info[nid] = {
        "category": ndata["category"],
        "x": ndata["position"]["x"],
        "y": ndata["position"]["y"],
        "parents": ndata.get("parents", []),
    }

# Track slot usage per category
cat_slot_edges = defaultdict(lambda: defaultdict(list))

for child_id, cinfo in node_info.items():
    for parent_id in cinfo["parents"]:
        if parent_id not in node_info:
            continue
        pinfo = node_info[parent_id]
        if pinfo["category"] != cinfo["category"]:
            continue
        
        px, py = pinfo["x"], pinfo["y"]
        cx, cy = cinfo["x"], cinfo["y"]
        dx = (1 if cx > px else -1) if cx != px else 0
        dy = (1 if cy > py else -1) if cy != py else 0
        
        x, y = px + dx, py + dy
        cat = cinfo["category"]
        edge_label = f"{parent_id} -> {child_id}"
        
        while x != cx or y != cy:
            cat_slot_edges[cat][(x, y)].append((edge_label, parent_id, child_id))
            if x != cx:
                x += dx
            if y != cy:
                y += dy

# Report overlaps per category
any_overlaps = False
for cat in sorted(cat_slot_edges.keys()):
    overlaps = {slot: edges for slot, edges in cat_slot_edges[cat].items() if len(edges) > 1}
    if overlaps:
        any_overlaps = True
        print(f"\n{'='*70}")
        print(f"CATEGORY: {cat} - {len(overlaps)} overlapping slots")
        print(f"{'='*70}")
        for (sx, sy), edges in sorted(overlaps.items()):
            print(f"\n  Slot ({sx}, {sy}) - {len(edges)} edges:")
            for edge_label, pid, cid in edges:
                print(f"    {edge_label}")

if not any_overlaps:
    print("No intra-category overlapping edge slots found!")
else:
    # Summary
    print(f"\n{'='*70}")
    print("SUMMARY")
    print(f"{'='*70}")
    for cat in sorted(cat_slot_edges.keys()):
        overlaps = {s: e for s, e in cat_slot_edges[cat].items() if len(e) > 1}
        if overlaps:
            max_overlap = max(len(e) for e in overlaps.values())
            print(f"  {cat}: {len(overlaps)} overlapping slots (max {max_overlap} edges per slot)")
        else:
            print(f"  {cat}: no overlaps")
