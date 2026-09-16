# The Mob Brain System

An in-depth guide to how Alkatraz's magic mob AI works, how it is structured
across the module layers, and how to use and extend it.

---

## 1. What it is

The Mob Brain System is Alkatraz's declarative, data-driven AI system for
*magic mobs* — custom mob variants (and, since the runtime-brain feature, any
vanilla mob) whose behaviour is described in plain YAML instead of hard-coded
Java.

A "brain" is a small YAML file that lists goals (behaviour units) and target
goals (what the mob hunts), each with a priority. The core module reads the
YAML into a `MobBrain` object, and the version-specific NMS module translates
that object into Minecraft's native goal system.

The same brain YAML can be applied to:

- **Magic mobs** (spawned entities with a `magic_entity_type` tag) — their
  brain is looked up by their type.
- **Any vanilla mob** — brains can be assigned, swapped, cleared and
  re-applied at runtime through the `MagicBrainService` API.

### Key properties

- **Version-agnostic API.** The `api` module defines brains and goals with
  zero NMS types. Core goals are written once and run on every supported
  Minecraft version (1.19 through 26.2).
- **Data-driven.** No Java is needed to change what a mob does; edit
  `brains/<id>.yml` and reload.
- **Runtime-editable.** Brains can be applied to already-spawned entities,
  persisted in NBT, and re-applied after a reload.

---

## 2. Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│  brains/*.yml   (YAML: goals + targets + display/melee/wand) │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  core module  (me.nagasonic.alkatraz.mobs)                   │
│                                                              │
│  MagicBrains         loads YAML → MobBrain, caches           │
│  NativeGoalRegistry  declares which native specs exist       │
│  MagicEntities       facade: spawn, identity, spells         │
│  MagicEntityRegistry profiles + natural-replacement roll     │
│  MagicBrainServiceImpl runtime brain editing (NBT + apply)   │
│  goals/  GoalFactory, CastSpellGoal, KeepSpellRangeGoal      │
└───────────────┬──────────────────────┬───────────────────────┘
                │                      │
                │ Alkatraz.getNms()    │ MagicEntities.spawn()
                ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│  api module  (me.nagasonic.alkatraz.api.mobs)  — pure Bukkit │
│                                                              │
│  MobBrain, Goal, GoalFlag, MobBrainContext, NativeGoalSpec,  │
│  SpellCastConfig, MagicEntityType, MagicBrainService,        │
│  NmsMobFactory                                               │
└───────────────┬──────────────────────────────────────────────┘
                │ implemented/bridged per version
                ▼
┌─────────────────────────────────────────────────────────────┐
│  NMS modules  (v1_19_R1 … v26_R2)  — the only NMS touches    │
│                                                              │
│  NMS_v<X>      core NMS impl (spawn + applyBrain + others)   │
│  GoalBuilder   wipes selectors, wires a MobBrain onto a mob  │
│  GoalBridge    vanilla Goal ←→ api Goal adapter              │
│  NativeGoalFactory  NativeGoalSpec → vanilla goal            │
│  NmsMobBrainContext  api MobBrainContext ←→ NMS mob         │
│  MagicEntitySpawner + definitions/NMSMagic{Zombie,Skeleton}  │
└─────────────────────────────────────────────────────────────┘
```

The layers are strictly directional:

- `api` knows **nothing** about NMS, CraftBukkit internals, config, or NBT.
- `core` owns config, NBT persistence, spawning coordination and the concrete
  goal implementations.
- NMS modules own the *only* code in the project that imports
  `net.minecraft.*` or `org.bukkit.craftbukkit.*` for this feature.

Any new Minecraft version = one new NMS module that implements the same core
`NMS` interface with the same bridge classes. The api and core logic never
change.

---

## 3. The api module (`me.nagasonic.alkatraz.api.mobs`)

This module has only Bukkit-API dependencies (spigot-api, snakeyaml, JetBrains
annotations). Everything below is safe to use from any plugin.

### 3.1 `MobBrain`

The declarative, ordered list of goals for a mob. Built with a fluent builder:

```java
MobBrain brain = MobBrain.builder()
    .addGoal(1, new CastSpellGoal(new SpellCastConfig(6.0, 12.0, 14.0, 40)))
    .addNative(1, new NativeGoalSpec.Float())
    .addNativeTarget(2, new NativeGoalSpec.NearestAttackableTarget(Player.class, true))
    .build();
```

Each `MobBrain.Entry` is a record of `(priority, goalOrSpec, isTargetGoal)`:

- **priority** — vanilla goal priority; **lower runs first** within the same
  selector.
- **goalOrSpec** — one of:
  - an api `Goal` (custom, pluggable behaviour, e.g. `CastSpellGoal`)
  - a `NativeGoalSpec` (a vanilla goal described declaratively)
- **isTargetGoal** — if `true`, goes to the **target selector** (hunting);
  otherwise the **goal selector** (movement/behaviour).

Builder methods:

| Method | Selector | Kind |
|---|---|---|
| `addGoal(priority, Goal)` | goal | custom api goal |
| `addTargetGoal(priority, Goal)` | target | custom api goal |
| `addNative(priority, NativeGoalSpec)` | goal | vanilla goal |
| `addNativeTarget(priority, NativeGoalSpec)` | target | vanilla goal |

### 3.2 `Goal`

The version-agnostic behaviour unit — a mirror of a vanilla goal, named after
Minecraft's `Goal` class:

| api method | vanilla equivalent |
|---|---|
| `canStart(ctx)` | `canUse()` |
| `shouldContinue(ctx)` (default = `canStart`) | `canContinueToUse()` |
| `start(ctx)` (no-op default) | `start()` |
| `stop(ctx)` (no-op default) | `stop()` |
| `tick(ctx)` | `tick()` |
| `flags()` (default empty) | `setFlags(...)` — MOVE/LOOK/JUMP/TARGET |

Goals receive a `MobBrainContext` so they never touch NMS directly.

### 3.3 `MobBrainContext`

An `org.bukkit`-typed view over the vanilla mob the goal runs on. Each NMS
module implements it by wrapping its `net.minecraft.world.entity.Mob`.

Methods:

| Method | Purpose |
|---|---|
| `self()` | the Bukkit `Mob` |
| `getTarget()` / `setTarget(LivingEntity)` | read / set the mob's target |
| `moveTo(Location, speed)` | path to a destination; `true` if a path was found |
| `strafeAwayFrom(target, keepDistance, speed)` | retreat math for range-keeping (keeps `keepDistance + 1` away, nudges velocity if no path) |
| `getVelocity()` / `setVelocity(Vector)` | read / override motion |
| `hasLineOfSight(entity)` | raycast line of sight |
| `lookAt(eyeLocation)` | vanilla look control (30° yaw, max head pitch) |
| `isNavigating()` | whether a path is being computed/traversed |
| `distanceSq(entity)` | squared distance |
| `getMainHandItem()` | main-hand item (used to detect wands) |
| `stopNavigating()` | stop current pathfinding |

### 3.4 `NativeGoalSpec`

A *sealed interface* of records describing vanilla goals, so the full goal
inventory is reachable from the api without leaking NMS types. Each NMS
module has a `NativeGoalFactory` that switches over these records and builds
the real vanilla goal.

| Record | Vanilla goal | Notes |
|---|---|---|
| `Float()` | `FloatGoal` | rise in water/lava |
| `MeleeAttack(speed, pauseWhenMobIdle)` | `MeleeAttackGoal` | chase + attack |
| `WaterAvoidingRandomStroll(speed)` | `WaterAvoidingRandomStrollGoal` | wander, avoiding water |
| `LookAtPlayer(range)` | `LookAtPlayerGoal` | head follows players |
| `RandomLookAround()` | `RandomLookAroundGoal` | idle head movement |
| `HurtByTarget()` | `HurtByTargetGoal` | target whoever hurts you |
| `NearestAttackableTarget(targetClass, mustSee)` | `NearestAttackableTargetGoal` | hunt nearest match |
| `Panic(speed)` | `PanicGoal` | flee fire/damage |
| `AvoidEntity(avoidClass, maxDist, walkSpeed, sprintSpeed)` | `AvoidEntityGoal` | flee a class |

All nine must be supported by every NMS module — this is enforced at startup
by `NativeGoalRegistry.assertAllSupported()` (see §6.4).

### 3.5 `MagicEntityType`

The canonical registry of custom magic mob types:

```java
public enum MagicEntityType {
    ZOMBIE_MAGE("zombie_mage"),
    ZOMBIE_FIGHTER("zombie_fighter"),
    SKELETAL_MAGE("skeletal_mage");
}
```

- `NBT_KEY = "magic_entity_type"` — stamped onto spawned entities so they can
  be identified anywhere without NMS access.
- `getId()` → the id used for config paths.
- `getConfigPath()` → `mobs/<id>.yml` (the *profile* config: elements, spells,
  natural replacement — distinct from the *brain* config).
- `fromId(String)` → case-insensitive lookup, empty if unknown.

### 3.6 `SpellCastConfig`

A record consumed by the spell-casting goals:

```java
record SpellCastConfig(
    double minCastDist,   // back away if closer
    double maxCastDist,   // close in if farther
    double castRange,     // max cast distance
    int    cooldownTicks  // ticks between cast attempts
)
```

`hasRangeKeeping()` returns `true` when `minCastDist > 0 && maxCastDist > 0`.
Setting both to `0` disables range-keeping (the mob relies on other goals for
positioning, e.g. a melee fighter).

### 3.7 `MagicBrainService`

The **runtime brain editing** API. Works on *any* living mob — magic or
vanilla. Core registers a singleton at startup; callers use
`MagicBrainService.getInstance()`.

| Method | Behaviour |
|---|---|
| `boolean setBrain(entity, brainId)` | Assign a brain: reload `brains/<id>.yml`, stamp the id in NBT, apply immediately. `true` = valid mob + resolvable brain. |
| `boolean reloadBrain(entity)` | Re-apply the entity's current brain from disk (single entity). `true` if an id resolved and was applied. |
| `int reloadBrains()` | Sweep every living mob in every loaded world; re-apply each resolvable brain. Returns the count updated (`0` when off-thread). |
| `boolean clearBrain(entity)` | Remove the brain tag + apply an **empty** brain → mob becomes passive. Vanilla AI is not restored (unless it respawns). |
| `@Nullable String getBrain(entity)` | The *resolvable* brain id, or `null`. |

**Id resolution order (per entity):**
1. explicit `alkatraz_brain` NBT tag (assigned by `setBrain`)
2. `magic_entity_type` NBT tag (a magic mob's brain = its type)
3. otherwise `null`

**Thread safety.** All write operations are safe to call from any thread:
off the Bukkit main thread they are scheduled onto it. Boolean returns are
meaningful *on the main thread*; off-thread calls accept the request
asynchronously and return `true`.

### 3.8 `NmsMobFactory`

The contract each NMS module's spawner implements:

```java
Optional<Entity> spawnMagicEntity(String id, Location location);
```

`MagicEntities.spawn(...)` routes spawning through the singleton implementation
installed per version module (`Alkatraz.getNms()`).

---

## 4. The core module (`me.nagasonic.alkatraz.mobs`)

### 4.1 `MagicBrains` — the brain loader

Loads each brain YAML into a cached `MobBrain`, plus the attributes the NMS
base classes read at spawn time.

Four caches keyed by brain id (`String`):

- `brainCache` — `MobBrain`
- `displayNameCache` — display-name (color-coded, e.g. `&8Zombie Mage`)
- `meleeRangeCache` — melee attack range
- `wandCache` — wand item key (or `null` for empty-handed)

Public API:

| Method | Purpose |
|---|---|
| `registerAll()` | Clear all caches and `reload(id)` for every `MagicEntityType` |
| `reload(String id)` | (Re)load one brain + attributes from `brains/<id>.yml` |
| `brain(MagicEntityType)` / `brain(String id)` | Cached brain lookup (`null` if never loaded) |
| `displayName(type)` / `meleeRange(type)` / `wand(type)` | Cached attributes |

`reload(id)` reads:

- `display-name` (default `&f<id>`)
- `melee-range` (default `0`)
- `wand` (optional)
- `goals` section → builder via `applySection(builder, section, false)`
- `targets` section → builder via `applySection(builder, section, true)`

`applySection` sets `target: true` on each entry before delegating to
`GoalFactory.applyFromConfig` — so the **same YAML shape** works for both
sections; only the top-level key decides the selector.

`BRAIN_KEY = "alkatraz_brain"` is the NBT tag that persists an explicit brain
assignment (see §4.3).

### 4.2 `MagicBrainServiceImpl` — runtime editing engine

The core implementation of `MagicBrainService`.

```java
private static void ensureMainThread(Runnable task) {
    if (Bukkit.isPrimaryThread()) task.run();
    else Bukkit.getScheduler().runTask(Alkatraz.getInstance(), task);
}
```

- `resolveBrainId(entity)` — reads `alkatraz_brain` first (if non-blank),
  then `magic_entity_type`, else `null`.
- `setBrain` — validates (`Mob` instance, non-blank id), reloads the id,
  stamps `alkatraz_brain`, then `Alkatraz.getNms().applyBrain(entity, brain)`.
  Off-thread path schedules the same work and returns `true` once accepted.
- `reloadBrain` — resolves the id, reloads from disk, applies.
- `reloadBrains` — for each world, for each `Mob` living entity with a
  resolvable id: `MagicBrains.reload(id)` then apply. Returns the counter.
- `clearBrain` — removes `alkatraz_brain` and applies
  `MobBrain.builder().build()` (empty → passive).
- `getBrain` — returns `resolveBrainId(entity)`.

Note the sweep `reloadBrains()` calls `MagicBrains.reload(brainId)` itself, so
it re-reads **any** id from disk (not just the 3 enum types cached by
`registerAll()`). This is what makes edits to custom brain YAMLs take effect on
live mobs after `/alkatraz reload`.

### 4.3 `MagicEntities` — facade

The version-agnostic entry point for magic mobs:

- `registerProfiles()` — calls `MagicEntityRegistry.registerAll()` then
  `MagicBrains.registerAll()`. The single bootstrap call used at startup and
  on `/alkatraz reload`.
- `spawn(MagicEntityType, Location)` / `spawn(String id, Location)` — delegates
  to `Alkatraz.getNms().spawnMagicEntity(...)`, cast to `LivingEntity`.
- `isMagicEntity(entity)` / `getType(entity)` — identity via the
  `magic_entity_type` NBT tag.
- `getSpellIds(entity)` / `getSpells(entity)` — read the `mob_spells` NBT
  roster and resolve through `SpellRegistry`.
- `getAffinity(element, entity)` / `getResistance(element, entity)` — read
  stored elemental stats from NBT.

### 4.4 `MagicEntity` — interface implemented by NMS mob classes

Each NMS magic-mob class (`NMSMagicZombie`, `NMSMagicSkeleton`) implements this
core interface. Since Java interfaces can't hold mutable instance state,
implementors own a `MagicData` value object (maps of affinities/resistances +
a spell list).

`initMagic(profile, type, self)` — the constructor hook:
1. copies affinities/resistances from the `MobProfile`
2. resolves spell ids through `SpellRegistry` into the in-memory roster
3. stamps persistent NBT: `<element>_affinity`, `<element>_resistance`,
   `mob_spells` (comma-joined), and `magic_entity_type`

Plus default accessors: `getAffinity`, `getResistance`, `getSpells`,
`pickRandomSpell`.

### 4.5 `MagicEntityRegistry` — profiles + natural replacement

Caches one `MobProfile` per `MagicEntityType` (parsed from `mobs/<id>.yml`).
Also builds a **weighted replacement pool** for natural spawns:

- if a profile has `replaces` + `spawn_chance`, a vanilla spawn of that base
  type can be replaced by a magic variant
- when several magic mobs replace the same base, chances pool (they are
  relative weights, not absolute probabilities)
- `rollReplacement(baseType, spawnReason)` picks one candidate, filtering by
  `spawn_reasons`

The actual hooking lives in `MagicEntitySpawnListener` (§4.7).

### 4.6 `MobProfile`

Value object parsed from `mobs/<id>.yml`:

- elemental `affinities` / `resistances`
- `spells` (list of spell ids)
- `replaces` (vanilla `EntityType` this mob replaces)
- `spawn_chance` (0–1 fractions and 1–100 percentages both accepted)
- `spawn_reasons` (defaults to `NATURAL`)

### 4.7 `MagicEntitySpawnListener` — natural spawn replacement

Listens to `CreatureSpawnEvent` at HIGH priority. If the spawning entity is
already a magic entity, it's skipped. Otherwise it rolls the registry's
replacement pool; if a magic type wins, the vanilla spawn is cancelled and the
magic variant is spawned one tick later through `MagicEntities.spawn`.

### 4.8 `MobModifier` — vanilla mob stat stamping

A sibling system: when a *vanilla* mob spawns, it stamps elemental
affinities/resistances and a spell roster onto the entity from `mobs/<type>.yml`.
Magic entities skip this because their NMS constructor already stamped values.
Player-count scaling (players within 50 blocks at spawn, min ×1.0) makes
vanilla mobs harder in crowded areas. Also provides the static NBT readers
`getMobSpellIds`, `getEntityAffinity`, `getEntityResistance` used by
`MagicEntities`.

### 4.9 `NativeGoalRegistry` — coverage contract

Because `NativeGoalSpec` is sealed, the whole inventory is a closed set. Each
NMS module's `NativeGoalFactory.registerCoverage()` marks its supported
classes via `markSupported(...)`, then `assertAllSupported()` throws at startup
if any of the nine is missing. This guarantees no module drifts out of feature
parity.

---

## 5. The core goals (`me.nagasonic.alkatraz.mobs.goals`)

### 5.1 `GoalFactory` — YAML → goal mapping

Two registry maps turn YAML `type:` strings into api `Goal`s or
`NativeGoalSpec`s.

**Custom goals (`CUSTOM`):**

| type | produced goal | YAML keys |
|---|---|---|
| `cast_spell` | `CastSpellGoal(new SpellCastConfig(min-cast-dist, max-cast-dist, cast-range=14, cooldown-ticks=40))` | `min-cast-dist`, `max-cast-dist`, `cast-range`, `cooldown-ticks` |
| `keep_spell_range` | `KeepSpellRangeGoal(min-distance=6, max-distance=12, speed=1.1)` | `min-distance`, `max-distance`, `speed` |

**Native goals (`NATIVE`):**

| type | spec | YAML keys |
|---|---|---|
| `float` | `Float()` | — |
| `melee_attack` | `MeleeAttack(speed=1.0, pause-when-mob-idle=false)` | `speed`, `pause-when-mob-idle` |
| `water_avoiding_stroll` | `WaterAvoidingRandomStroll(speed=0.8)` | `speed` |
| `look_at_player` | `LookAtPlayer(range=8.0)` | `range` |
| `random_look_around` | `RandomLookAround()` | — |
| `hurt_by_target` | `HurtByTarget()` | — |
| `nearest_attackable_target` | `NearestAttackableTarget(targetClass, must-see=true)` | `target-class` (default `org.bukkit.entity.Player`), `must-see` |
| `panic` | `Panic(speed=1.25)` | `speed` |
| `avoid_entity` | `AvoidEntity(avoidClass, max-dist=8, walk-speed=0.8, sprint-speed=1.2)` | `avoid-class` (default Player), `max-dist`, `walk-speed`, `sprint-speed` |

For class-valued types, the factory tries `Class.forName(...)` — the class is
validated as a `LivingEntity` subtype, otherwise the default (Player) is used.

`applyFromConfig(builder, section)` reads `priority` (default 1), `type`, and
`target` (default false), then routes to `addGoal` / `addTargetGoal` /
`addNative` / `addNativeTarget` accordingly.

**Extensibility hooks:** `addCustomGoal(type, factory)` and
`addNativeGoal(type, factory)` let other code register their own YAML-triggered
goal types.

### 5.2 `CastSpellGoal`

The flagship magic-mob goal. Implements api `Goal`, flags = `LOOK`.

Behaviour:

- `canStart` — respects a per-goal cooldown (`cooldownTimer`), requires a live
  target within `castRange²`, with line of sight, and picks a spell via
  `selectSpell(ctx, MagicEntities.getSpells(ctx.self()))`.
- `selectSpell` — shuffles the mob's spell roster (from NBT) and returns the
  first that `canMobCast`. **If the roster is empty the goal silently never
  starts.** This is the mechanism by which "vanilla mobs get AI-only brains":
  a `cast_spell` goal on an entity with no `mob_spells` NBT does nothing.
- `tick` — looks at the target, winds up for `WIND_UP_TICKS` (= 15), then fires
  a `SpellPrepareEvent` (cancellable), plays the spell's `circleAction`, waits
  `fullCastTime * 20` ticks, then cancels the circle and calls `spell.mobCast`.
  Applies the global cooldown afterwards.
- `shouldContinue` — while still within `castRange × 1.2` and the target is
  alive and the wind-up hasn't completed.

### 5.3 `KeepSpellRangeGoal`

Range-keeping movement goal. Implements api `Goal`, flags = `MOVE`.

- `canStart` — the target is closer than `minDistance` or farther than
  `maxDistance`.
- `tick` — every `PATH_RECALC_INTERVAL` (=10) ticks:
  - too close → `ctx.strafeAwayFrom(target, minDistance, speed)`
  - too far → `moveTowardTarget` paths to a point `desiredDist`
    (`(min+max)/2`) from the target.
- `stop` — `ctx.stopNavigating()`.

---

## 6. The NMS modules (`me.nagasonic.alkatraz.nms_v<X>`)

There are sixteen, one per supported Minecraft version
(see §9 for the version table). Each is a full restatement of the bridge
mechanics against that version's mappings.

### 6.1 `NMS` (core interface, `me.nagasonic.alkatraz.nms`)

The contract every module implements (also extends `Listener`). Relevant to the
Mob Brain system:

```java
void registerMagicEntities();                       // coverage check
Optional<Entity> spawnMagicEntity(String key, Location location);
default Optional<Entity> spawnMagicEntity(MagicEntityType type, Location location);
void applyBrain(LivingEntity entity, MobBrain brain);   // runtime brain edit
```

`applyBrain` is the critical bridge for runtime editing:

```java
@Override
public void applyBrain(org.bukkit.entity.LivingEntity entity, me.nagasonic.alkatraz.api.mobs.MobBrain brain) {
    if (!(entity instanceof org.bukkit.entity.Mob)) return;
    net.minecraft.world.entity.Mob handle = (net.minecraft.world.entity.Mob) ((CraftLivingEntity) entity).getHandle();
    GoalBuilder.apply(handle, null, brain);
}
```

Notes:

- The `MagicEntity` argument of `GoalBuilder.apply` is **unused** (see §6.2),
  so `null` is always safe — this is exactly why vanilla mobs are supported
  with zero extra work.
- Old versions (1.19/1.20/1.21) use the versioned CraftBukkit package
  (`org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity`), while 26.x
  uses the unversioned package.
- The interface has a default `onEnable()`; the `NMS_<v>` class is discovered
  reflectively by `Alkatraz.setupNMS()` via
  `me.nagasonic.alkatraz.nms_<v>.NMS_<v>`.

### 6.2 `GoalBuilder`

The heart of wiring. Identical signature in all 16 modules:

```java
public static void apply(Mob mob, MagicEntity magic, MobBrain brain) {
    mob.goalSelector.removeAllGoals(g -> true);
    mob.targetSelector.removeAllGoals(g -> true);

    NmsMobBrainContext ctx = new NmsMobBrainContext(mob);

    for (MobBrain.Entry entry : brain.entries()) {
        Goal goal;
        if (entry.goalOrSpec() instanceof NativeGoalSpec spec) {
            goal = NativeGoalFactory.build(spec, mob);
        } else if (entry.goalOrSpec() instanceof me.nagasonic.alkatraz.api.mobs.Goal apiGoal) {
            goal = new GoalBridge(apiGoal, ctx);
        } else {
            throw new IllegalArgumentException("Unknown goal type: " + ...);
        }

        if (entry.isTargetGoal()) mob.targetSelector.addGoal(entry.priority(), goal);
        else mob.goalSelector.addGoal(entry.priority(), goal);
    }
}
```

**Applying a brain replaces ALL current goals** — both selectors are wiped
first. A brain with zero entries therefore makes a mob completely passive
(`clearBrain` relies on this). Note that wiping the goal selectors also removes
the entity's vanilla default AI; no vanilla goal survives an application.

### 6.3 `GoalBridge`

Adapts a vanilla `net.minecraft.world.entity.ai.goal.Goal` to an api `Goal`:

- constructor calls `setFlags(mapFlags(apiGoal.flags()))`
- `mapFlags` translates each api `GoalFlag` (MOVE/LOOK/JUMP/TARGET) to the
  vanilla `Goal.Flag`
- lifecycle methods delegate 1:1: `canUse→canStart`, `canContinueToUse→
  shouldContinue`, `start→start`, `stop→stop`, `tick→tick`

### 6.4 `NativeGoalFactory`

Switches on every `NativeGoalSpec` record and constructs the real vanilla goal:

```java
if (spec instanceof NativeGoalSpec.Float) return new FloatGoal(mob);
if (spec instanceof NativeGoalSpec.MeleeAttack ma)
    return new MeleeAttackGoal((PathfinderMob) mob, ma.speed(), ma.pauseWhenMobIdle());
...
```

For class-typed specs it maps Bukkit classes → NMS classes:

| Bukkit class | NMS class |
|---|---|
| `org.bukkit.entity.Player` | `net.minecraft.world.entity.player.Player` |
| `org.bukkit.entity.AbstractVillager` | `...npc.villager.AbstractVillager` |
| `org.bukkit.entity.IronGolem` | `...animal.golem.IronGolem` |
| anything else | falls back to `Player` |

`registerCoverage()` calls `NativeGoalRegistry.markSupported(...)` for all nine
specs; `NMS_v<X>.registerMagicEntities()` runs it then asserts coverage.

### 6.5 `NmsMobBrainContext`

The per-version `MobBrainContext`. Wraps `net.minecraft.world.entity.Mob`:

- `self()` → `(org.bukkit.entity.Mob) mob.getBukkitEntity()`
- `getTarget()` → NMS target unwrapped to Bukkit (or null)
- `setTarget(target)` → `((CraftLivingEntity) target).getHandle()` cast to NMS
  LivingEntity
- `moveTo(...)` → `mob.getNavigation().moveTo(x, y, z, speed)`
- `strafeAwayFrom(...)` → retreat vector math: normalize (self − target), aim
  `keepDistance + 1` blocks out, and if `nav.moveTo` returns false, nudge
  `setDeltaMovement` by 0.15× the retreat direction
- `getVelocity/setVelocity` → `Vec3` ↔ `Vector`
- `hasLineOfSight(entity)` → `mob.hasLineOfSight(((CraftEntity) entity).getHandle())`
- `lookAt(...)` → `mob.getLookControl().setLookAt(x, y, z, 30.0F, mob.getMaxHeadXRot())`
- `isNavigating()` → `!mob.getNavigation().isDone()`
- `distanceSq(entity)` → `mob.distanceToSqr(((CraftEntity) entity).getHandle())`
- `getMainHandItem()` → `CraftItemStack.asBukkitCopy(mob.getMainHandItem())`
- `stopNavigating()` → `mob.getNavigation().stop()`

### 6.6 `MagicEntitySpawner`

Implements api `NmsMobFactory` and is the spawning bridge:

```java
public class MagicEntitySpawner implements NmsMobFactory {
    public static final MagicEntitySpawner INSTANCE = new MagicEntitySpawner();
    ...
    public Optional<Entity> spawnMagicEntity(String id, Location location) {
        return MagicEntityType.fromId(id).flatMap(type -> spawnByType(type, location));
    }
}
```

`spawnByType` switches on the type:

- `ZOMBIE_MAGE`, `ZOMBIE_FIGHTER` → `NMSMagicZombie.spawn(type, location).getBukkitEntity()`
- `SKELETAL_MAGE` → `NMSMagicSkeleton.spawn(type, location).getBukkitEntity()`

`NMS_v<X>.spawnMagicEntity` delegates straight to
`MagicEntitySpawner.INSTANCE.spawnMagicEntity(key, location)`.

### 6.7 Definitions: `NMSMagicZombie` / `NMSMagicSkeleton`

The concrete NMS entity bases. `NMSMagicZombie extends Zombie implements
MagicEntity` (skeleton equivalent extends `Skeleton`). One class per mob
*base*, not per mob type — type identity comes from the stored
`MagicEntityType`.

```java
private final MagicData magicData = new MagicData();
private final MagicEntityType magicType;

public MagicEntityType entityType() { return magicType; }
public MobBrain brain() { return MagicBrains.brain(magicType); }

protected NMSMagicZombie(EntityType<? extends Zombie> type, Level level, MagicEntityType magicType) {
    super(type, level);
    this.magicType = magicType;
    registerGoals();

    MobProfile profile = MagicEntityRegistry.getProfile(magicType)
        .orElseThrow(() -> new IllegalStateException(magicType.getId()
            + " profile not loaded - did you call MagicEntities.registerProfiles()?"));
    initMagic(profile, magicType, (org.bukkit.entity.LivingEntity) getBukkitEntity());
}

@Override
protected final void registerGoals() {
    if (magicType == null) return;
    GoalBuilder.apply(this, this, brain());
}

@Override
public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
    double range = MagicBrains.meleeRange(magicType);
    if (range > 0 && distanceTo(target) >= range) return false;  // melee-range gate
    return super.doHurtTarget(level, target);
}

@Override
public net.minecraft.network.chat.Component getDisplayName() {
    return Component.literal(ColorFormat.format(MagicBrains.displayName(magicType)));
}
```

Key points:

- **`registerGoals()` is `final`** — no per-mob subclassing of AI. AI is
  entirely data-driven.
- `melee-range` is enforced in `doHurtTarget`: if the config range is > 0, a
  successful melee hit requires the target to be *within* that range. Setting
  it to `0` disables the gate (any distance, e.g. a fighter that just swings).
- Display names are colour-formatted (`&` codes) from the brain config.
- The constructor loads its profile from `MagicEntityRegistry` and stamps NBT
  via `initMagic`. On a reload, brains come from `MagicBrains` cache which
  `registerAll()` re-populated.

**Spawning:**

```java
public static NMSMagicZombie spawn(MagicEntityType magicType, Location location) {
    ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
    NMSMagicZombie mob = new NMSMagicZombie(EntityType.ZOMBIE, level, magicType);
    mob.setPos(location.getX(), location.getY(), location.getZ());
    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
            EntitySpawnReason.COMMAND, null);
    String wand = MagicBrains.wand(magicType);
    if (wand != null) {
        mob.setItemInHand(InteractionHand.MAIN_HAND,
            CraftItemStack.asNMSCopy(MagicItemServices.get().createItem(MagicKeys.alkatraz(wand))));
    }
    level.addFreshEntityWithPassengers(mob);
    return mob;
}
```

The optional `wand` key equips a magic item (e.g. `wooden_wand`) made through
`MagicItemServices` — the item the `CastSpellGoal` detects via
`getMainHandItem()` + `WandUtils.isWand(...)`.

---

## 7. The brain YAML format

A brain file lives at `core/src/main/resources/brains/<id>.yml`
(extracted to `brains/<id>.yml` on the server) and looks like:

```yaml
display-name: "&8Zombie Mage"      # colour-coded display name
melee-range: 3.0                   # >0 gates melee swings to within this range
wand: wooden_wand                  # optional; item key to equip on spawn
goals:                             # goal selector (behaviour)
  - priority: 1
    type: float
  - priority: 2
    type: keep_spell_range
    min-distance: 6.0
    max-distance: 12.0
    speed: 1.1
  - priority: 3
    type: cast_spell
    min-cast-dist: 6.0
    max-cast-dist: 12.0
    cast-range: 14.0
    cooldown-ticks: 40
targets:                           # target selector (hunting)
  - priority: 1
    type: hurt_by_target
  - priority: 2
    type: nearest_attackable_target
    target-class: org.bukkit.entity.Player
    must-see: true
```

The `goals` / `targets` sections use the identical entry schema; only the
section name decides which selector the entry goes into. Available `type`
values are listed in §5.1.

### The shipped brains

| id | file | role |
|---|---|---|
| `zombie_mage` | `brains/zombie_mage.yml` | ranged caster: keeps 6–12 blocks, casts (40-tick cooldown), melee-range 3.0, wooden wand |
| `zombie_fighter` | `brains/zombie_fighter.yml` | melee: `melee_attack` + opportunistic `cast_spell` (long 1800-tick cooldown), no wand, melee-range 0; hunts players, villagers and iron golems |
| `skeletal_mage` | `brains/skeletal_mage.yml` | skele  caster: keeps range, casts fast (30-tick cooldown), wooden wand |

### The mob profile YAML (`mobs/<id>.yml`)

Complements the brain: elemental stats, spell roster, natural replacement.

```yaml
magic_affinity: 0.0        # <element>_affinity / <element>_resistance per element
fire_affinity: 0.2
...
spells:
  - fireball
replaces: ZOMBIE           # vanilla type this mob can replace
spawn_chance: 0.05         # 0–1 fraction or 1–100 percentage
spawn_reasons:
  - NATURAL
```

`MagicEntityRegistry`/`MobModifier` parse these; `MagicEntity.initMagic`
stamps the values into NBT.

---

## 8. Lifecycle & runtime flow

### Startup (`Alkatraz.onEnable`, core lines ~154–157)

1. `ProfileRegistry.registerProfiles()` — player data profiles
2. `MagicEntities.registerProfiles()`
   → `MagicEntityRegistry.registerAll()` (parse + cache `mobs/*.yml` profiles)
   → `MagicBrains.registerAll()` (parse + cache `brains/*.yml` brains)
3. `MagicBrainService.setInstance(new MagicBrainServiceImpl())` — install the
   runtime editing service
4. `nms.registerMagicEntities()` — native goal coverage check
   (`registerCoverage()` + `assertAllSupported()`)

The `NMS` instance itself is created in `setupNMS()` by reflecting
`me.nagasonic.alkatraz.nms_<v>.NMS_<v>` where `<v>` comes from
`MinecraftVersion.getServerVersion().getNmsVersion()`.

### Spawning a magic mob

- **Command** — `/alkatraz summon <type>` (see `AlkatrazCommand.handleSummon`)
  → `MagicEntityType.fromId` → `MagicEntities.spawn`.
- **Natural spawn** — `MagicEntitySpawnListener.onCreatureSpawn` rolls the
  registry replacement pool, cancels the vanilla spawn, spawns the magic
  variant next tick.
- Either way the NMS constructor runs `registerGoals()` (wiring `brain()` =
  `MagicBrains.brain(type)` through `GoalBuilder`) and `initMagic(...)`
  (stamping NBT). If a `wand` is configured it's equipped after
  `finalizeSpawn`.

### `/alkatraz reload`

`AlkatrazCommand.handleReload` (permission `commands.reload`):

1. reloads all configs, progression, spells
2. `MagicEntities.registerProfiles()` — rebuilds profile + brain caches
3. `MagicBrainService.getInstance().reloadBrains()` — **sweeps every living mob
   in every world** and re-applies each resolvable brain, so live entities
   update without re-spawning
4. `MagicItemBootstrap.reload()`

The sweep is the key runtime behaviour: after editing `brains/<id>.yml` on the
server, a single `/alkatraz reload` pushes the new AI to every affected mob —
magic mobs (via `magic_entity_type`) and vanilla mobs with an explicit
`alkatraz_brain` tag alike.

### Runtime brain editing (any time, any mob)

```java
MagicBrainService brains = MagicBrainService.getInstance();

brains.setBrain(mob, "zombie_fighter");       // assign + persist + apply
String id = brains.getBrain(mob);             // "zombie_fighter" or null
brains.reloadBrain(mob);                       // re-apply from disk (1 mob)
int n = brains.reloadBrains();                 // re-apply everywhere (count)
brains.clearBrain(mob);                        // passive (empty brain)
```

All safe off-thread; return values are meaningful on the main thread.

---

## 9. Version support

Sixteen NMS modules, all implementing the same core `NMS` interface:

| module | Minecraft |
|---|---|
| `v1_19_R1` | 1.19.1 |
| `v1_19_R2` | 1.19.3 |
| `v1_19_R3` | 1.19.4 |
| `v1_20_R1` | 1.20.1 |
| `v1_20_R2` | 1.20.2 |
| `v1_20_R3` | 1.20.4 |
| `v1_20_R4` | 1.20.6 |
| `v1_21_R1` | 1.21.1 |
| `v1_21_R2` | 1.21.3 |
| `v1_21_R3` | 1.21.4 |
| `v1_21_R4` | 1.21.5 |
| `v1_21_R5` | 1.21.8 |
| `v1_21_R6` | 1.21.10 |
| `v1_21_R7` | 1.21.11 |
| `v26_R1` | 26.1.2 |
| `v26_R2` | 26.2 |

Version-consistent across all modules:

- `net.minecraft.world.entity.Mob` is the wrapped type everywhere.
- `GoalBuilder.apply(Mob, MagicEntity, MobBrain)` has the identical signature
  in all sixteen.
- CraftBukkit's `CraftLivingEntity.getHandle()` yields
  `net.minecraft.world.entity.LivingEntity`, cast to `Mob`.

Exception: `CraftLivingEntity` is **unversioned** in the two 26.x modules but
**versioned** (`org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity`, …)
in the 1.19–1.21 modules.

---

## 10. NBT keys used

Written by the NMS constructor (`MagicEntity.initMagic`), `MobModifier`
(vanilla mobs), or the brain service:

| key | type | meaning |
|---|---|---|
| `magic_entity_type` | String | the `MagicEntityType` id; marks a magic mob and is a brain-id fallback |
| `alkatraz_brain` | String | explicit runtime brain assignment (takes priority over the type) |
| `mob_spells` | String | comma-separated spell ids the mob may cast |
| `<element>_affinity` | double | spell damage bonus for an element (`magic_` for `Element.NONE`) |
| `<element>_resistance` | double | damage reduction for an element |

---

## 11. How to extend

### Add a new brain (no code)

Drop a `brains/<id>.yml` (see §7), then assign it at runtime with
`setBrain(entity, "<id>")`, or spawn it as a magic type by wiring the type
through the mob profile + NMS spawner. After editing YAML, `/alkatraz reload`
re-applies it to live mobs.

### Add a custom api goal

1. Implement `me.nagasonic.alkatraz.api.mobs.Goal` in core (or a plugin);
   use the `MobBrainContext` for all entity interaction.
2. Register a YAML type name:
   ```java
   GoalFactory.addCustomGoal("my_goal", section -> new MyGoal(
       section.getDouble("range", 10)));
   ```
3. Reference `type: my_goal` in any brain YAML.

Because the goal is written against `MobBrainContext`, it automatically runs
on every supported version.

### Add a new vanilla goal to the inventory

1. Add a record to the sealed `NativeGoalSpec` interface.
2. Add it to `NativeGoalRegistry.assertAllSupported()` and to every
   `NativeGoalFactory` switch + `registerCoverage()` in all sixteen modules.
   The startup assertion will enforce completeness.
3. Add a `NATIVE` factory entry in `GoalFactory` if you want it reachable from
   YAML.

### Add a new magic mob type

1. New enum constant in `MagicEntityType` with a unique id.
2. `mobs/<id>.yml` profile (elements, spells, replacement rules).
3. `brains/<id>.yml` brain.
4. Route the id in `MagicEntitySpawner.spawnByType` to the right NMS base
   (zombie-based or skeleton-based), adding a new base class if needed.

---

## 12. Key invariants

- Brains are **declarative**: YAML → `MobBrain` → native goals; no per-mob
  subclassing of AI (`registerGoals()` is final).
- Applying a brain **replaces all goals** (both selectors are wiped). An empty
  brain = passive mob. Vanilla AI is gone until respawn/restart.
- The api module never touches NMS, config, or NBT. Version parity is enforced
  by the sealed spec + the startup assertion.
- A `cast_spell` goal only ever fires if the entity's NBT spell roster is
  non-empty and a spell passes `canMobCast`; vanilla mobs therefore get
  AI-only behaviour from caster brains.
- Magic mobs inherit their brain id from `magic_entity_type` unless an explicit
  `alkatraz_brain` tag overrides it.