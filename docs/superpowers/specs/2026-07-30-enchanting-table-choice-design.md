# Enchanting Table Choice Design

## Summary
Remove the wand requirement for accessing the Arcane Table from enchanting tables, and let players choose between the Arcane Table menu and the vanilla enchanting table via a small choice GUI.

## Changes

### 1. New `EnchantingTableChoiceMenu`
A 27-slot `Menu` subclass with two clickable options:
- **Arcane Table** (Slot 11): Opens `WandTableSelectionMenu`
- **Enchanting Table** (Slot 15): Opens the vanilla enchanting GUI via `Player.openEnchanting()`

### 2. New `EnchantingTableListener`
A listener at `EventPriority.LOW` that catches any `PlayerInteractEvent` with `RIGHT_CLICK_BLOCK` on `Material.ENCHANTING_TABLE`, cancels the event, and opens the choice menu.

### 3. Modified `WandComponentHandler`
Remove the enchanting table click handling block (no longer opens Arcane Table directly). The early return is kept to prevent wand casting code from firing on enchanting table clicks.

### 4. Registration
Register `EnchantingTableListener` in `Alkatraz.onEnable()`.

### 5. Language Strings
Add strings for the choice menu items to `english.lang`.

### 6. Tutorial Update
Update `tutorial.step3_chat` to reference right-clicking an enchanting table instead of `/wandtable`.

### 7. Wiki
The wiki is hosted on GitHub and cannot be updated locally.
