# Warnings Report Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the CI warnings report show only unintended warnings by deduplicating warning messages and filtering out the intentionally produced warnings from the recipe test fixtures.

**Architecture:** Single edit to the `Extract plugin warnings` step in `.github/workflows/test-plugin.yml`. The extraction pipeline becomes: grep WARN lines → extract the `[Alkatraz] <message>` portion (timestamps stripped) → `sort -u` (dedupe by message) → `grep -vE` dropping the known fixture warnings. Every downstream consumer (per-cell count, consolidated summary, `::warning::` annotations) inherits the filtered file, so only unintended warnings are ever reported.

**Tech Stack:** GitHub Actions (YAML workflow), GNU grep -E / sort (Ubuntu runner), Python 3 + PyYAML (local verification on this Windows machine).

## Global Constraints

- Only `.github/workflows/test-plugin.yml` is modified (the `Extract plugin warnings` step). No changes to test scripts, plugin code, config, or lang.
- The dropped-pattern list is the verbatim ERE from the approved spec — copy it character-for-character into the `grep -vE` filter. Each pattern maps to fixtures in `scripts/test-fixtures/recipes/`.
- The `errors-*.txt` extraction block is untouched.
- Bash is NOT installed on this machine: pipeline behavior is verified with a Python re simulation (same semantics); real verification is the CI matrix via `workflow_dispatch`.
- Single final commit + push (project convention). The unrelated pre-existing working-tree changes (`.gitignore`, `api/.../Permission.java`, `core/pom.xml`, `AlkatrazCommand.java`, `core/.../Permission.java`, `CastEventListener.java`, `ItemInstanceSerializer.java`, `SpellHotbarManager.java`, `english.lang`, `test-commands.sh`, `test-mobs.sh`, untracked `core/src/test/`) must NOT be staged.
- Evidence and ledger entries go in `.superpowers/sdd/` (gitignored scratch), per the subagent-driven-development convention.

---

## Task 1: Filter expected warnings in the Extract plugin warnings step

**Files:**
- Modify: `.github/workflows/test-plugin.yml` (the `Extract plugin warnings` step, ~lines 246-267)

**Interfaces:**
- Consumes: `$LOG` (server.log path), existing `WARN_FILE`/`ERROR_FILE` naming
- Produces: `warnings-${{ matrix.loader }}-${{ matrix.mc_version }}.txt` containing ONLY unexpected `[Alkatraz] <message>` lines, deduplicated. The `report-warnings` job (unchanged) cats these files into the step summary and emits `::warning::` annotations per line.

- [ ] **Step 1: Read the step**

Read `.github/workflows/test-plugin.yml` lines 246-267 and confirm the current warnings/errors extraction blocks.

- [ ] **Step 2: Replace the warnings-extraction block**

Replace ONLY this block (the warnings `if`):

```yaml
          if [ -n "$LOG" ] && grep -q 'WARN\].*\[Alkatraz\]' "$LOG" 2>/dev/null; then
            grep 'WARN\].*\[Alkatraz\]' "$LOG" | sort -u > "$WARN_FILE"
            COUNT=$(wc -l < "$WARN_FILE")
            echo "Found ${COUNT} unique warning(s)"
          else
            touch "$WARN_FILE"
          fi
```

with this block (comment + pipeline + count line):

```yaml
          if [ -n "$LOG" ] && grep -q 'WARN\].*\[Alkatraz\]' "$LOG" 2>/dev/null; then
            # Keep only unexpected warnings: extract the [Alkatraz] message
            # (timestamp stripped), collapse repeats across reloads, then drop
            # the intentionally produced warnings from the CI-only recipe
            # fixtures. Each pattern maps to fixtures in
            # scripts/test-fixtures/recipes/.
            grep 'WARN\].*\[Alkatraz\]' "$LOG" \
              | grep -oE '\[Alkatraz\].*' \
              | sort -u \
              | grep -vE "\[Alkatraz\] (Duplicate recipe key overwritten: alkatraz:ci_(dup|override)|Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient|Empty recipe shape for alkatraz:ci_bad_(shape|type)|Recipe key minecraft:stick conflicts with an existing Bukkit recipe; set override_vanilla: true to replace it|Recipe missing 'definition'/'id' key|Unknown ingredient 'definitely_not_a_material' in recipe|Unknown recipe type 'WAFFLE', defaulting to SHAPED)" \
              > "$WARN_FILE"
            COUNT=$(wc -l < "$WARN_FILE")
            echo "Found ${COUNT} unique unexpected warning(s)"
          else
            touch "$WARN_FILE"
          fi
```

Leave the `ERROR` `if` block and the final `echo "Warnings: ..."` line exactly as they are.

- [ ] **Step 3: Simulate the pipeline locally (Python)**

Write the following verification script to `C:\Users\adamq\AppData\Local\Temp\opencode\verify_warnings_filter.py`:

```python
import re
sample = """[10:34:05 WARN]: [Alkatraz] Duplicate recipe key overwritten: alkatraz:ci_dup
[10:34:05 WARN]: [Alkatraz] Duplicate recipe key overwritten: alkatraz:ci_override
[10:34:05 WARN]: [Alkatraz] Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient
[10:34:05 WARN]: [Alkatraz] Empty recipe shape for alkatraz:ci_bad_shape
[10:34:05 WARN]: [Alkatraz] Empty recipe shape for alkatraz:ci_bad_type
[10:34:05 WARN]: [Alkatraz] Recipe key minecraft:stick conflicts with an existing Bukkit recipe; set override_vanilla: true to replace it
[10:34:05 WARN]: [Alkatraz] Recipe missing 'definition'/'id' key
[10:34:05 WARN]: [Alkatraz] Unknown ingredient 'definitely_not_a_material' in recipe
[10:34:05 WARN]: [Alkatraz] Unknown recipe type 'WAFFLE', defaulting to SHAPED
[10:34:11 WARN]: [Alkatraz] Duplicate recipe key overwritten: alkatraz:ci_dup
[10:34:11 WARN]: [Alkatraz] Duplicate recipe key overwritten: alkatraz:ci_override
[10:35:02 WARN]: [Alkatraz] Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient
[10:35:08 WARN]: [Alkatraz] Some genuine warning"""
line_re = re.compile(r"WARN\].*\[Alkatraz\]")
extract_re = re.compile(r"\[Alkatraz\].*")
drop_re = re.compile(r"\[Alkatraz\] (Duplicate recipe key overwritten: alkatraz:ci_(dup|override)|Empty ingredients for shapeless recipe alkatraz:ci_bad_ingredient|Empty recipe shape for alkatraz:ci_bad_(shape|type)|Recipe key minecraft:stick conflicts with an existing Bukkit recipe; set override_vanilla: true to replace it|Recipe missing 'definition'/'id' key|Unknown ingredient 'definitely_not_a_material' in recipe|Unknown recipe type 'WAFFLE', defaulting to SHAPED)")
seen = set()
for line in sample.splitlines():
    if line_re.search(line):
        m = extract_re.search(line)
        if m:
            seen.add(m.group(0))
unique = sorted(seen)
surviving = [m for m in unique if not drop_re.search(m)]
print("unique messages:", len(unique))
print("surviving:", surviving)
assert len(unique) == 10, "expected 10 unique messages (9 fixtures + 1 genuine), got %d" % len(unique)
assert surviving == ["[Alkatraz] Some genuine warning"], "unexpected survivors: %r" % surviving
print("PASS: 9 fixture warnings collapsed+filtered; only the genuine warning remains")
```

Run it:

```powershell
python "C:\Users\adamq\AppData\Local\Temp\opencode\verify_warnings_filter.py"
```

Expected output:
```
unique messages: 10
surviving: ['[Alkatraz] Some genuine warning']
PASS: 9 fixture warnings collapsed+filtered; only the genuine warning remains
```

- [ ] **Step 4: YAML parse check**

Run:

```powershell
python -c "import yaml; yaml.safe_load(open(r'.github/workflows/test-plugin.yml', encoding='utf-8')); print('YAML OK')"
```

Expected: `YAML OK` (exit 0). If PyYAML reports an error, read the message and fix the workflow edit; note that PyYAML (YAML 1.1) converts the `on:` key to boolean `True` — that is expected and NOT an error.

- [ ] **Step 5: Verify the edit scope**

Grep the workflow to confirm: the `grep -vE` filter line exists exactly once, the `ERROR\].*\[Alkatraz\]` extraction is unchanged, and the final `echo "Warnings: ...` line is unchanged.

Run: `Select-String -Path .github/workflows/test-plugin.yml -Pattern 'grep -vE','ERROR\].*\[Alkatraz\]','unique unexpected warning'`
Expected: one `grep -vE` line, one unchanged `ERROR` extraction, one `unique unexpected warning` echo.

- [ ] **Step 6: Record evidence**

Append a brief, report, and diff for this task under `.superpowers/sdd/warnings-filter/` and add a completed ledger line to `.superpowers/sdd/progress.md`.

---

## Task 2: Final verification, commit and push

**Files:**
- Modify: `.github/workflows/test-plugin.yml` (Task 1)
- Create: `docs/superpowers/specs/2026-08-01-warnings-report-filtering-design.md` (already written, approved)
- Create: `docs/superpowers/plans/2026-08-01-warnings-report-filtering.md` (this plan)

**Interfaces:**
- Produces: the committed change set and pushed branch for CI verification.

- [ ] **Step 1: Full-diff review**

Run: `git status` and `git diff .github/workflows/test-plugin.yml`
Expected: only `.github/workflows/test-plugin.yml` modified plus the two new docs; the diff for the workflow shows exactly the one block replacement from Task 1 Step 2. Confirm none of the unrelated working-tree files are staged.

- [ ] **Step 2: Commit**

Stage and commit with one plain-language message (project style, no prefix):

```bash
git add .github/workflows/test-plugin.yml docs/superpowers/specs/2026-08-01-warnings-report-filtering-design.md docs/superpowers/plans/2026-08-01-warnings-report-filtering.md
git commit -m "filter intended fixture warnings out of the CI warnings report"
```

- [ ] **Step 3: Push**

Run: `git push origin master`
Expected: `master -> master` fast-forward.

- [ ] **Step 4: CI confirmation**

Tell the user to re-dispatch **Actions → test-plugin.yml → Run workflow**.
Expected: every cell prints `Found 0 unique unexpected warning(s)` from the `Extract plugin warnings` step, the consolidated summary shows `No plugin warnings found across all versions.` (no `### ⚠️` sections, no `::warning::` annotations), and all test sections still pass.

---

## Completion criteria

- `.github/workflows/test-plugin.yml` diff is exactly the one block replacement; errors extraction untouched.
- Local Python simulation prints `PASS` and PyYAML parse reports `YAML OK`.
- One commit pushed containing the workflow change + spec + plan.
- CI matrix shows 0 unexpected warnings on every cell with all tests passing.
