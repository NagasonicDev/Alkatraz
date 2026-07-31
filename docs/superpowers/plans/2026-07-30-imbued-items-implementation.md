# Imbued Items Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players imbue vanilla tools/weapons/armor with magic stones via crafting, making them magic items usable in the Engraving Table.

**Architecture:** 5 tier YAMLs + `ImbueManager` for tier mapping + programmatic `ShapelessRecipe` registration + crafting listener that writes PDC data onto the vanilla item.

**Tech Stack:** Java 17, Spigot/Paper API, Maven

---

### Task 1: Create 5 imbued tier ItemDefinition YAMLs

**Files:**
- Create: `core/src/main/resources/magic/items/imbued_tier1.yml`
- Create: `core/src/main/resources/magic/items/imbued_tier2.yml`
- Create: `core/src/main/resources/magic/items/imbued_tier3.yml`
- Create: `core/src/main/resources/magic/items/imbued_tier4.yml`
- Create: `core/src/main/resources/magic/items/imbued_tier5.yml`

**Produced keys:** `alkatraz:imbued_tier1` through `alkatraz:imbued_tier5`

- [ ] Create `imbued_tier1.yml`:
```yaml
key: alkatraz:imbued_tier1
material: BARRIER
display_name: 'Imbued Component'
components:
  - alkatraz:equipment
attributes:
  alkatraz:max_mana: 10
```
- [ ] Create `imbued_tier2.yml` (max_mana: 25)
- [ ] Create `imbued_tier3.yml` (max_mana: 50)
- [ ] Create `imbued_tier4.yml` (max_mana: 75)
- [ ] Create `imbued_tier5.yml` (max_mana: 100)

---

### Task 2: Add imbuing config section

**Files:**
- Modify: `core/src/main/resources/config.yml`
- Create: config reader methods (in existing SpellbookConfig or new ImbueConfig)

- [ ] Add to `config.yml`:
```yaml
# ==============================
# Imbuing System
# Converts vanilla tools/weapons/armor into magic items
# using magic stones in a crafting table.
# ==============================
imbuing:
  stone_costs:
    1: 1
    2: 2
    3: 3
    4: 4
    5: 5
```
- [ ] Add `ImbueConfig` reader class or add methods to `SpellbookConfig`:
  - `getImbueStoneCost(int tier)` → int (defaults to tier number)

---

### Task 3: Create ImbueManager

**Create:** `core/src/main/java/me/nagasonic/alkatraz/items/magic/imbue/ImbueManager.java`

**Consumes:** `MagicItemStack.writeInstance()`, `MagicItemInstance.createDefault()`, tier config

**Produces:** `imbue(ItemStack)` → imbued ItemStack

- [ ] `isImbuable(Material)` → boolean
- [ ] `getTier(Material)` → int
- [ ] `getTierKey(Material)` → NamespacedKey
- [ ] `imbue(ItemStack input)` → transformed clone with PDC data
- [ ] Tier mapping based on material name patterns (with config override support)

---

### Task 4: Register imbuing recipes programmatically

**Modify:** `MagicItemRecipeManager.java` — add `registerImbuingRecipes()` method

**Consumes:** `ImbueManager.isImbuable()`, `ImbueManager.getStoneCount()`, magic stone item

- [ ] Iterate all imbuable materials from ItemTypeMapper
- [ ] Register `ShapelessRecipe` for each: 1 vanilla item + N magic stones
- [ ] Recipe keys: `alkatraz:imbue_<material>` (lowercase)

---

### Task 5: Extend RecipeCraftListener for imbuing detection

**Modify:** `RecipeCraftListener.java`

- [ ] Detect imbuing recipes by key prefix `imbue_`
- [ ] Extract vanilla item from matrix
- [ ] Call `ImbueManager.imbue()` and set result

---

### Task 6: Wire up in MagicItemBootstrap

**Modify:** `MagicItemBootstrap.java`

- [ ] Save default tier YAML resources in `loadDefinitions()`
- [ ] Call `registerImbuingRecipes()` after `ItemTypeMapper.load()`
- [ ] Ensure `registerImbuingRecipes()` runs in correct order

---

### Task 7: Verify compilation

- [ ] Run `mvn compile -pl core -am -q`
- [ ] Fix any errors
