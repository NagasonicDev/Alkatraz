# Alkatraz

Spigot/Paper/Purpur magic plugin: spell casting by click sequence, wands, magic circles 0-5, 11 equipment slots, engravings, research tree, magic mobs, config-driven recipes, public API. Targets MC 1.19-1.26. Maven multi-module, `me.nagasonic:alkatraz-parent`, version via `${revision}` in the parent pom, parent `javaVersion` 17.

## Layout
- `api/`   public API, published to a Maven repo; external plugins depend on it. **Additive changes only.** Flag anything breaking.
- `core/`  version-independent plugin logic.
- `dist/`  assembles the shaded final jar (`Alkatraz_${revision}.jar`).
- `v1_19_R1` ... `v1_21_R7`, `v26_R1`, `v26_R2`, `v26_R3`  one NMS adapter module per revision (18 total).
- `mob-brain-system-ai-expansion-plan.md`  design plan for mob AI. Read it before touching magic mobs.
- Wiki (github.com/NagasonicDev/Alkatraz/wiki) documents features and API usage; keep it in mind when behaviour changes.

## Hard rules
1. `net.minecraft.*` and `org.bukkit.craftbukkit.*` imports live ONLY in version modules. `core` and `api` talk to NMS through an interface. A feature that needs NMS = interface in core/api + an implementation in every version module (or an explicit, documented fallback).
2. Bukkit/Spigot API is main-thread only. Async is for pure computation/IO; hop back with the scheduler before touching worlds, entities, inventories.
3. Spells, items, engravings, research nodes and recipes are config/YAML driven and must survive `/alkatraz reload`. Do not hardcode user-facing strings; use the language files.
4. Do not edit `target/` output, and never change or commit the machine-specific `testServerPath` in the parent pom.
5. Cleanup matters: cancel tasks, remove listeners, remove boss bars/glow/temporary blocks on disable, quit, death and world change.
6. Spell click codes (R/L/S sequences) must stay unique per spell and must not become prefixes of another spell's code.

## Build (always quiet, you only need errors)
- Fast, single area:  `mvn -B -q -pl core -am package`
- Full:               `mvn -B -q clean package`
- Install to test server (ask first):  `mvn -B -q -PexportToServer clean package`
- There is no automated test server; say clearly when something needs in-game verification and give exact steps (commands, items, expected result).

## Verification before "done"
Compile the affected module with `-am`. If `api/` or `core/` changed, run the full build so every version module still compiles.

## Unverified assumptions (confirm with me, then delete this section)
- Whether NMS modules need Spigot BuildTools artifacts in the local `.m2`.
- Which modules override the Java version (the 26_R* modules may need newer than 17).
- Where spell/item YAML and language files live, and whether any unit tests exist.
