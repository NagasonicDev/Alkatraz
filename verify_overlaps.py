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
        "edge_paths": {
            k.lower(): [[wp["x"], wp["y"]] for wp in v]
            for k, v in (ndata.get("edge_paths") or {}).items()
        },
    }

def compute_path(px, py, cx, cy):
    dx = (1 if cx > px else -1) if cx != px else 0
    dy = (1 if cy > py else -1) if cy != py else 0
    x, y = px + dx, py + dy
    path = []
    while x != cx or y != cy:
        path.append((x, y))
        if x != cx: x += dx
        if y != cy: y += dy
    return path

def compute_path_with_waypoints(px, py, waypoints, cx, cy):
    path = []
    wx, wy = px, py
    for wp in waypoints:
        path.extend(compute_path(wx, wy, wp[0], wp[1]))
        wx, wy = wp
    path.extend(compute_path(wx, wy, cx, cy))
    return path

cat_slot_edges = defaultdict(lambda: defaultdict(list))

for child_id, cinfo in node_info.items():
    for parent_id in cinfo["parents"]:
        if parent_id not in node_info:
            continue
        pinfo = node_info[parent_id]
        if pinfo["category"] != cinfo["category"]:
            continue
        cat = cinfo["category"]
        wps = cinfo["edge_paths"].get(parent_id)
        if wps:
            path = compute_path_with_waypoints(pinfo["x"], pinfo["y"], wps, cinfo["x"], cinfo["y"])
        else:
            path = compute_path(pinfo["x"], pinfo["y"], cinfo["x"], cinfo["y"])
        for slot in path:
            cat_slot_edges[cat][slot].append((parent_id, child_id))

total_overlaps = 0
for cat in sorted(cat_slot_edges.keys()):
    cat_overlaps = sum(1 for s, e in cat_slot_edges[cat].items() if len(e) > 1)
    if cat_overlaps > 0:
        print(f"\n{cat}: {cat_overlaps} overlapping slots")
        for slot, edges in sorted(cat_slot_edges[cat].items()):
            if len(edges) > 1:
                print(f"  Slot {slot}: {len(edges)} edges")
                for p, c in edges:
                    print(f"    {p} -> {c}")
        total_overlaps += cat_overlaps
    else:
        print(f"{cat}: no overlaps")

print(f"\nTotal overlapping slots: {total_overlaps}")
