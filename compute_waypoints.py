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

# Build all edges and track per-category slot usage
all_edges = []
cat_slot_edges = defaultdict(lambda: defaultdict(list))

for child_id, cinfo in node_info.items():
    for parent_id in cinfo["parents"]:
        if parent_id not in node_info:
            continue
        pinfo = node_info[parent_id]
        if pinfo["category"] != cinfo["category"]:
            continue
        cat = cinfo["category"]
        path = compute_path(pinfo["x"], pinfo["y"], cinfo["x"], cinfo["y"])
        all_edges.append((cat, parent_id, child_id, path))
        for slot in path:
            cat_slot_edges[cat][slot].append((parent_id, child_id))

# Find edges that have overlaps
edge_overlap_slots = defaultdict(list)
for cat, pid, cid, path in all_edges:
    for slot in path:
        if len(cat_slot_edges[cat][slot]) > 1:
            edge_overlap_slots[(cat, pid, cid)].append(slot)

print(f"Total overlapping edges: {len(edge_overlap_slots)}")

# For each overlapping edge, try to find waypoints
edge_waypoints = {}

for (cat, pid, cid), overlap_slots in sorted(edge_overlap_slots.items()):
    pinfo = node_info[pid]
    cinfo = node_info[cid]
    px, py = pinfo["x"], pinfo["y"]
    cx, cy = cinfo["x"], cinfo["y"]
    
    # Build set of slots used by OTHER edges in this category
    other_slots = set()
    for c, op, oc, opath in all_edges:
        if c == cat and (op, oc) != (pid, cid):
            other_slots.update(opath)
    
    # Also add node positions as "occupied"
    for nid, ni in node_info.items():
        if ni["category"] == cat:
            other_slots.add((ni["x"], ni["y"]))
    
    best = None
    # Try single waypoints at various offsets from midpoint
    mid_x = (px + cx) // 2
    mid_y = (py + cy) // 2
    
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            if dx == 0 and dy == 0:
                continue
            test_wp = [(mid_x + dx, mid_y + dy)]
            test_path = compute_path_with_waypoints(px, py, test_wp, cx, cy)
            if not any(s in overlap_slots for s in test_path):
                # Check no overlap with other edges (allow overlap with node positions since those are not rendered as edges)
                edge_slots_in_path = [s for s in test_path if s not in other_slots or s == (cx, cy)]
                # Actually, we only care about edge-to-edge overlap, not edge-to-node
                # Nodes overwrite edges, so that's fine
                if not any(s in other_slots and s != (cx, cy) and s != (px, py) for s in test_path):
                    best = test_wp
                    break
        if best:
            break
    
    if best:
        edge_waypoints[(cat, pid, cid)] = best

print(f"Routes found: {len(edge_waypoints)}")
still_missing = len(edge_overlap_slots) - len(edge_waypoints)
if still_missing > 0:
    print(f"Still need routes: {still_missing}")

# Verify
final_slot_edges = defaultdict(lambda: defaultdict(list))
for cat, pid, cid, _ in all_edges:
    key = (cat, pid, cid)
    pinfo = node_info[pid]
    cinfo = node_info[cid]
    if key in edge_waypoints:
        path = compute_path_with_waypoints(pinfo["x"], pinfo["y"], edge_waypoints[key], cinfo["x"], cinfo["y"])
    else:
        path = compute_path(pinfo["x"], pinfo["y"], cinfo["x"], cinfo["y"])
    for slot in path:
        final_slot_edges[cat][slot].append((pid, cid))

remaining = 0
for cat in sorted(final_slot_edges.keys()):
    for slot, edges in final_slot_edges[cat].items():
        if len(edges) > 1:
            remaining += 1

print(f"\nRemaining overlaps after fix: {remaining}")

# Group by child node for YAML output
child_waypoints = defaultdict(dict)
for (cat, pid, cid), wps in edge_waypoints.items():
    child_waypoints[cid][pid] = wps

# Output YAML
for child_id in sorted(child_waypoints.keys()):
    print(f"\n  {child_id}:")
    print(f"    edge_paths:")
    for parent_id, wps in sorted(child_waypoints[child_id].items()):
        wp_str = ", ".join(f'{{ x: {w[0]}, y: {w[1]} }}' for w in wps)
        print(f"      {parent_id}: [{wp_str}]")
