# Warnings Report Filtering Design

Date: 2026-08-01
Status: Approved (user: "Seems fine")

## Problem

The CI warnings report is drowned in intended warnings. Every integration-test
cell deliberately loads malformed and conflicting recipe fixtures, which produce
the same set of `[Alkatraz]` warnings on every boot and every `recipes reload`.
Because the report counts unique full log lines (including timestamps), each of
the ~9 fixture warnings appears once per reload (~5 reloads), so each cell shows
~40 warning lines. Real, unexpected warnings are impossible to spot.

## Decision

Keep only *unexpected* warnings in the warnings reports. Apply the filter at the
source — the `Extract plugin warnings` step — so every downstream consumer (per-cell
counts, consolidated `Plugin Warnings Summary`, `::warning::` annotations)
inherits it automatically.

## Change

Single edit to `.github/workflows/test-plugin.yml`, `Extract plugin warnings`
step (~lines 246-267). Replace the current extract for warnings:

```yaml
          if [ -n "$LOG" ] && grep -q 'WARN\].*\[Alkatraz\]' "$LOG" 2>/dev/null; then
            grep 'WARN\].*\[Alkatraz\]' "$LOG" | sort -u > "$WARN_FILE"
            COUNT=$(wc -l < "$WARN_FILE")
            echo "Found ${COUNT} unique warning(s)"
          else
            touch "$WARN_FILE"
          fi
```

with:

```yaml
          if [ -n "$LOG" ] && grep -q 'WARN\].*\[Alkatraz\]' "$LOG" 2>/dev/null; then
            # Keep only unexpected warnings: extract the [Alkatraz] message
            # (timestamp stripped), collapse repeats across reloads, then drop
            # the intentionally produced warnings from the CI-only recipe
            # fixtures. Each pattern maps to one or more fixtures in
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

## Behavior

- **Dedupe by message:** `grep -oE '\[Alkatraz\].*'` strips the
  `[HH:MM:SS WARN]:` prefix so `sort -u` collapses identical messages. A cell's
  ~40 fixture-warning lines collapse to the 9 unique messages, then are filtered
  out entirely.
- **Filter precision:** every dropped pattern is anchored to a CI-only fixture
  identifier (`alkatraz:ci_*`, the `minecraft:stick` conflict fixture, the
  `'definitely_not_a_material'` and `'WAFFLE'` fixture values). A genuine
  production warning of the same shape cannot be produced by shipped recipes, so
  no real warning can be masked.
- **Empty report:** when no unexpected warnings exist, `$WARN_FILE` is empty and
  `report-warnings` prints `No plugin warnings found across all versions.`
- **Format note:** warning file lines are now `[Alkatraz] <message>` instead of
  full timestamped lines. The only consumer is `report-warnings`, which prints
  lines verbatim, so this is safe and cleaner in the summary.

## Out of Scope

- `errors-*.txt` extraction unchanged (no fixture produces errors).
- Test assertions in `test-recipes.sh`/`test-commands.sh` unchanged — they assert
  on the raw server log, not on the report.

## Verification

- Re-dispatch the `test-plugin.yml` workflow (`workflow_dispatch`).
- Expect: each cell's `Extract plugin warnings` prints `Found 0 unique unexpected
  warning(s)`; the consolidated summary shows `No plugin warnings found across
  all versions.` (no `### ⚠️` sections, no `::warning::` annotations) unless a
  real warning appears — which would then be the only thing reported.
- All 5 recipe test sections and all other scripts must still pass.
