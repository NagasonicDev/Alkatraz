# Mob Brain System — Full AI Coverage Expansion Plan

**Status:** planning document, not an implementation prompt. This lays out
*what* to build, in *what order*, and the design decisions that need to be
locked in before writing code. Once a phase below is agreed, it can be turned
into an implementation prompt in the same style as the original Goal-system
one.

**Revision note (this expansion):** two requirements were added on top of the
original plan scope:

1. **Complete coverage** of `net.minecraft.world.entity.ai` — not just goals
   and `TaskBrain`, but `control`, `navigation`, `attributes`, and a clear
   stance on the adjacent `schedule`/`Pose` packages — all described by **one
   per-mob document** (`AiProfile`).
2. **Core-driven application** — every new AI capability must be configurable
   at runtime and applied *through core*, never by reaching into individual
   NMS modules at call sites. NMS modules become a set of *leaf-builders
   registered into a core registry*, plus a handful of irreducible primitives.
   No module ever decides how the AI of a mob is assembled; it only knows how
   to construct native objects for a given spec.

**Starting point:** the current system (`MOB-BRAIN-SYSTEM.md`) covers exactly
one branch of vanilla AI — `net.minecraft.world.entity.ai.goal` +
`.goal.target`, via `MobBrain` / `Goal` / `NativeGoalSpec` /
`MobBrainContext`. Everything below is new.

---

## 1. What "the whole `ai` package" actually is

Vanilla's `net.minecraft.world.entity.ai` has eight sub-packages. Two are
already covered. The rest represent six more systems, each with a genuinely
different shape — this isn't "add more goal types," it's several distinct
mechanisms that need their own abstractions.

| Package | What it does | Used by | Status |
|---|---|---|---|
| `ai.goal` + `.goal.target` | Priority-ordered, poll-based behaviour units (`canUse`/`tick`) | Most simple mobs (zombies, skeletons, spiders...) | ✅ Done (`MobBrain`, `Goal`, `NativeGoalSpec`) |
| `ai.behavior` | Memory-driven behaviour units run inside a scheduled `Activity` | Villagers, piglins, foxes, frogs, allays, wardens, camels, sniffers, hoglins | ❌ Not covered — **primary target of this plan** |
| `ai.sensing` | Populates memory from world state every N ticks (the input side of Brain) | Same mobs as above | ❌ Not covered |
| `ai.memory` | Typed "blackboard" slots (`MemoryModuleType<T>`) that behaviours/sensors read and write | Same mobs as above | ❌ Not covered |
| `ai.control` | The actuator layer goals/behaviours ultimately call into (`MoveControl`, `LookControl`, `JumpControl`, body rotation) | All mobs | ❌ Not covered (currently hidden inside `MobBrainContext`) |
| `ai.navigation` | Pathfinding strategy (`GroundPathNavigation`, `FlyingPathNavigation`, `WaterBoundPathNavigation`, amphibious variants) | All mobs | ❌ Not covered — every mob inherits whatever its base vanilla class ships with |
| `ai.attributes` | Stat modifiers (speed, follow range, attack damage...) | All mobs | ❌ Not covered by the brain system (partially handled by `MobModifier` for vanilla mobs — see §8) |
| `ai.village` + `.village.poi` | Villager professions, POI claiming, gossip | Villagers only | ❌ Out of scope (see §12) |

Two more things sit right next to `ai` and are natural companions to this
work:

| Adjacent system | Package | Relationship |
|---|---|---|
| `Schedule` / `Activity` | `net.minecraft.world.entity.schedule` | The thing that tells a *Brain* which behaviours are eligible right now (time-of-day gated and activity gating). The `Activity` half is **core** to `TaskBrain`; the vanilla `Schedule` half (per-hour behaviour lists) is replacable by our own time-of-day predicates, see §6.4. |
| `Pose` / `AnimationState` | `net.minecraft.world.entity` | Cosmetic/state layer (Warden emerging, sniffer digging, etc.) — not "AI", but config-driven mobs will want to trigger these declaratively too. Small, optional. See §9. |

**Recommendation (unchanged):** treat `ai.behavior` + `ai.sensing` +
`ai.memory` + `Activity` as one unit of work (the **Task Brain**; the existing
goal system is renamed internally to the **Goal Brain** — see §2). `ai.control`,
`ai.navigation`, and `ai.attributes` are smaller, mostly-orthogonal additions.
`ai.village`/`poi` is explicitly deferred.

---

## 2. Naming: resolving the "Brain" collision

Vanilla itself uses "Brain" for the memory/behaviour system specifically
(`net.minecraft.world.entity.ai.Brain<E>`), which collides with this
project's existing use of "MobBrain" for the goal-based system.

**Option A — split into named systems under one umbrella (recommended).**

```
AiProfile                 (new — the complete, YAML-described AI document for one mob)
├── GoalBrain             (renamed from today's MobBrain — same class, same fields)
├── TaskBrain             (new — the ai.behavior/.sensing/.memory/Activity direction)
├── ControlProfile        (new — navigation kind + move/look strategies, §7)
└── AttributeProfile      (new — declarative stat block, §8)
```

- `MobBrain` is renamed to `GoalBrain` (mechanical rename, no behaviour
  change). A deprecated `MobBrain extends GoalBrain` alias can be kept for one
  release if source compatibility for downstream consumers matters.
- `AiProfile` is the new top-level thing `brains/<id>.yml` describes: it can
  carry any subset of GoalBrain / TaskBrain / ControlProfile / AttributeProfile.
  Vanilla itself mixes goal-style and brain-style AI on the same mob (Piglin,
  Villager idle behaviour), so the data model must allow a profile to carry
  both at once even if 95% of profiles only use one.
- `NMS.applyBrain(entity, MobBrain)` becomes `NMS.applyProfile(entity,
  AiProfile)`, with application orchestration living entirely in core
  (`AiApplier`, see §3) and `applyProfile` internally dispatching to
  `applyGoalBrain` / `applyTaskBrain` / `applyControl` / `applyAttributes` as
  present. Legacy call sites that only ever set a `GoalBrain` keep working via
  an overload/fallback.

**Option B — keep "MobBrain" as-is, call the new thing something unrelated.**
Avoids a rename, but permanently confusing given vanilla's own naming. Not
recommended.

This plan assumes **Option A** throughout.

---

## 3. Core-driven application: the architecture this whole expansion is built on

The single most important decision in this expansion: **who performs the
application work**. Today, attaching AI to a mob is split across 16 module
files (`GoalBuilder.apply`, `NativeGoalFactory`, `GoalBridge`,
`NmsMobBrainContext`, `NMS_<v>.applyBrain`). Every new goal type or new AI
subsystem means editing a switch in up to 16 places. That does not scale to
covering all of `entity.ai`.

### 3.1 The principle

> Core decides *what* a mob does, *when* it does it, and *how the pieces are
> assembled*. NMS modules only answer *which native Minecraft object
> corresponds to a given api spec*, for that one version.

Concretely:

| Concern | Owner |
|---|---|
| YAML parsing (`brains/<id>.yml` → `AiProfile`) | Core (`AiProfileLoader`) |
| Caching, NBT id resolution, reload sweeps | Core (`MagicBrains` → extended; `MagicBrainServiceImpl`) |
| Wipe-and-rebuild orchestration (goal selectors, memory, navigation) | Core (`AiApplier`) |
| Which spec maps to which native constructor | Core's `AiSpecRegistry`, **populated by each module** at startup |
| Runtime editing commands / API surface | Core + api (`MagicBrainService` / `MagicAiService`) |
| The `TaskBrain` engine itself (memory store, activity scheduler, sensors) | Core (pure Bukkit, no NMS) |
| Construction of *native* `Goal`/`Behavior`/`PathNavigation`/`MoveControl`/`MemoryModuleType` | Module leaf-builders, registered into `AiSpecRegistry` |
| Unwrap `LivingEntity` → native `Mob` / `Entity` handle | Module primitives (`unwrap` methods) |

### 3.2 `AiSpecRegistry` — the single registration point in core

`NativeGoalRegistry` (core today) already tracks which `NativeGoalSpec`
classes are supported per module via `markSupported` + startup assertion
`assertAllSupported()`. This is generalized into **`AiSpecRegistry`**, a core
registry that holds the *builder functions themselves*:

```java
// core: me.nagasonic.alkatraz.mobs.ai.AiSpecRegistry
public final class AiSpecRegistry {
    // Spec class -> (nativePlayerHandle, spec) -> constructed native object (as Object)
    private static final Map<Class<?>, BiFunction<Object, Object, Object>> BUILDERS =
        new HashMap<>();

    public static <S> void register(Class<S> specClass, BiFunction<Object, S, Object> builder)
    public static boolean isSupported(Class<?> specClass)
    public static Object build(Object nativeMob, Class<?> specClass, Object spec)
        // throws IllegalStateException if unregistered (mirrors assertAllSupported failure style)
    public static void assertAllSupported(Class<?>... required)  // startup coverage contract
}
```

Key properties:

- **The registry lives in core.** Modules register into it from
  `registerMagicEntities()` (an existing per-module hook — no new call sites
  needed), e.g. `AiSpecRegistry.register(FlyingMoveControl.class, (mob, spec) -> new FlyingMoveControl((Mob) mob, spec.speed(), spec.canFloat()))`.
- **Core never touches native types.** The cached value is `Object`; module
  builders cast the handle to version-native `Mob`/`Entity` at registration
  time, inside code that is compiled against that version. Core stays 100%
  NMS-free.
- **Adding a native spec = one sealed record in api + one registration line
  per module.** No orchestration changes anywhere. `assertAllSupported` still
  catches any module that forgets to register, so a forgotten module fails at
  startup, not at runtime.
- **Custom (non-native) specs never touch modules at all.** They are built
  entirely in core and dispatched by class in the same registry with a
  core-supplied builder.

### 3.3 `AiApplier` — the single orchestrator in core

```java
// core: me.nagasonic.alkatraz.mobs.ai.AiApplier
public final class AiApplier {
    /** Apply every present subsystem of an AiProfile onto a live mob. */
    public static void applyProfile(LivingEntity entity, AiProfile profile);

    /** Wipe all brain-managed AI and re-apply from the profile's NBT id. */
    public static void reapply(LivingEntity entity);

    /** Apply only the GoalBrain (used when a profile has no TaskBrain). */
    public static void applyGoalBrain(LivingEntity entity, GoalBrain goals);

    /** Apply only navigation/control; leaves goals and task-brain untouched. */
    public static void applyControl(LivingEntity entity, ControlProfile control);

    /** Apply only the attribute block (pure Bukkit — no NMS involved at all). */
    public static void applyAttributes(LivingEntity entity, AttributeProfile attributes);
}
```

`applyProfile` is the only entry point modules' `applyBrain`-replacement needs;
its internals drive the wipe/re-attach order (goal selectors → task brain →
control → attributes) with `applyAttributes` last so stat overrides always win.

### 3.4 The minimal per-module surface

After the refactor, each of the 16 modules implements exactly:

| Module unit | What it is | Lines today → after |
|---|---|---|
| `NMS_<v>.applyProfile(entity, profile)` | now a 2-line bridge: `AiApplier.applyProfile(entity, profile)` | 4 → 2 |
| `entity/AiSpecRegistration` (rename of `NativeGoalFactory`) | registers every supported spec-class builder into core `AiSpecRegistry` | switch-per-spec → one registration per spec |
| `entity/GoalBridge` | remains (vanilla `Goal` wrapper for custom core goals) | unchanged |
| `entity/NmsMobBrainContext` | remains (api-context → native wrapping) | unchanged |
| `entity/NmsTaskBrainContext` (new) | api task-context → native wrapping (memory layer, look/control) | new, ~60 lines |
| `entity/MagicEntitySpawner` + definitions | unchanged | unchanged |

Every orchestration class that today lives in modules — `GoalBuilder`'s
remove-all loop, priority ordering, dispatch — **moves into core**
(`AiApplier` + `AiSpecRegistry` + `TaskBrainEngine`). This is the concrete
delivery of "done through core, instead of individual calls in each NMS
module": after this phase, a new AI feature requires touching at most the api
record + core engine + one registration line per module, and usually less.

### 3.5 What this means for the rest of this plan

Every subsystem below (§4 GoalBrain, §5 TaskBrain, §7 control/navigation, §8
attributes) is specified as: **api types → YAML shape → core application
path → module leaf-builders to register**. If a subsystem needs no native
constructors (attributes), it is *pure core* and needs zero module changes.

---

## 4. `GoalBrain`

The existing system, renamed per §2. Content is unchanged: `Goal` interface,
`GoalFlag`, `MobBrainContext`, the sealed `NativeGoalSpec` (`Float`,
`MeleeAttack`, `WaterAvoidingRandomStroll`, `LookAtPlayer`, `RandomLookAround`,
`HurtByTarget`, `NearestAttackableTarget`, `Panic`, `AvoidEntity`), and the core
custom goals `CastSpellGoal` / `KeepSpellRangeGoal` via `GoalFactory`.

**Under the core-driven architecture, the GoalBrain path changes only in who
runs it:**
- `GoalBuilder.apply(Mob, MagicEntity, MobBrain)` (16 module copies of the
  wipe+loop+attach logic) is deleted. Its logic moves to core `AiApplier.applyGoalBrain`,
  which reaches the native selector through a tiny module primitive
  (e.g. `Object goalSelector(Object nativeMob)` / `Object targetSelector(...)`
  or, simpler, a single registered primitive `wipeAndAdd(handle, entries… , specRegistryDispatch)`).
- `NmsMobBrainContext` stays a module class (it is the version-specific
  implementation of the api context — irreducible), and the goal builders stay
  registered in `AiSpecRegistry`.

**Optional extension (deferred, §12):** wrap more of the ~40 remaining generic
vanilla goals (`BowShootGoal`, `CrossbowAttackGoal`, `RangedAttackGoal`,
`LeapAtTargetGoal`, `MoveThroughVillageGoal`...) on demand, one record + one
registration per module each, `assertAllSupported` catching gaps. Not required
for coverage completeness — the pattern is established and proven.

---

## 5. `TaskBrain`: the `ai.behavior` / `ai.sensing` / `ai.memory` / `Activity` system

### 5.1 Architecture decision: a core-owned brain engine

§3.5 of the original plan correctly flagged that *hand-assigning, wiping, and
swapping a vanilla `Brain` at runtime* is a fragile, version-dependent
operation (`Brain.removeAllBehaviors()` availability, `Brain.provider().makeBrain()`
construction, `LivingEntity#brain` field swapping all differ across 1.19→26.2).

The core-driven architecture sidesteps most of that by **running the memory
and activity machinery in core, not in the vanilla `Brain`**:

```
AiProfile.TaskBrain (api, declarative)
        │  loaded by AiProfileLoader (core)
        ▼
TaskBrainEngine (core, pure Bukkit)
   ├── per-entity MobMemoryStore   — Map<MemoryKey<?>, TimedValue<?>>, NBT-persistable
   ├── ActivityScheduler           — picks current activity (priority + memory conditions + time-of-day)
   ├── SensorRunner                — runs sensors every N ticks, writes memory
   └── BehaviorRunner              — runs eligibility + tick() for behaviors of the active activity
        │
        ├── core behaviors (api Behavior, built in core — zero NMS)
        └── native behaviors (NativeBehaviorSpec → module leaf-builders via AiSpecRegistry)
```

**Why this is safe to promise at runtime:** the engine state is *our*
blackboard object keyed by entity UUID — wiping it is `Map.clear()`,
swapping a whole profile is `TaskBrainEngine.setState(uuid, newProfile)`,
and memory editing is `setMemory(uuid, key, value, expiry)`. None of that
requires touching a version-fragile native `Brain`. Only the *native-behavior
bridge* (a mob that wants vanilla `WardenAi`-style behaviours) needs the real
vanilla `Brain`, and that is the narrow, explicitly-spiked surface in §5.6.

- **Day-one runtime capability:** full memory editing + activity override +
  sensor tweaks + profile swap, for any task-brain mob, live. This is the
  original §3.5 "ship the easy one" — and with a core engine it is *all*
  easy.
- **Deferred native piece:** assigning a real vanilla `Brain` (or the last
  few percent of behaviour fidelity that only native behaviors give) stays a
  spiked, per-mob, load-time capability — never assumed for runtime swaps.

### 5.2 Ticking

The engine is driven by a single core ticker, no NMS involvement:

```java
// core: me.nagasonic.alkatraz.mobs.ai.TaskBrainTicker
public final class TaskBrainTicker implements Listener {
    // Bukkit repeating task on the main thread (Alkatraz.getInstance()).
    // Staggers entities into K buckets (e.g. K = 4, every-tick for small
    // rosters or every-other-tick buckets for large ones) to bound cost.
    public static void tickAll();          // called from Bukkit scheduler
    public static void attach(LivingEntity e, TaskBrain brain);  // main-thread
    public static void detach(LivingEntity e);
    public static void setMemory(Entity e, MemoryKey<?> k, Object v, long expiryTicks);
}
```

Sensor cadence (vanilla: 20 ticks for the standard set) lives in the
`Sensor` spec, and behavior eligibility is re-evaluated only when memory or
activity changes, so per-tick cost is a few map reads.

### 5.3 New api types (`api.mobs.task`)

```java
public sealed interface AiSpec {}                       // umbrella (api.mobs)

// ---- memory
public final class MemoryKey<T> {                        // constants, closed set (Option A, §5.5)
    public static final MemoryKey<LivingEntity> ATTACK_TARGET = new MemoryKey<>("attack_target");
    public static final MemoryKey<Location>     WALK_TARGET   = new MemoryKey<>("walk_target");
    public static final MemoryKey<List<LivingEntity>> NEAREST_HOSTILES = new MemoryKey<>("nearest_hostiles");
    public static final MemoryKey<Boolean>      IS_HURT       = new MemoryKey<>("is_hurt");
    // ...
    public String id();
}
public enum MemoryStatus { PRESENT, ABSENT, REGISTERED }

// ---- context (extends MobBrainContext: shared move/look/target primitives; §10.1)
public interface TaskBrainContext extends MobBrainContext {
    <T> Optional<T> getMemory(MemoryKey<T> key);
    <T> void setMemory(MemoryKey<T> key, T value);
    <T> void setMemoryWithExpiry(MemoryKey<T> key, T value, long ticks);
    void eraseMemory(MemoryKey<?> key);
    boolean hasMemory(MemoryKey<?> key);
}

// ---- behavior
public interface Behavior {
    Map<MemoryKey<?>, MemoryStatus> memoryRequirements();   // PRESENT / ABSENT / REGISTERED
    boolean canStart(TaskBrainContext ctx);
    default void start(TaskBrainContext ctx) {}
    boolean shouldContinue(TaskBrainContext ctx);
    void tick(TaskBrainContext ctx);
    default void stop(TaskBrainContext ctx) {}
    int minDuration();
    int maxDuration();
}
public interface CustomBehavior extends Behavior {}          // marker: core-owned, ships in core

// ---- native behavior spec (mirror of NativeGoalSpec, sealed, grows on demand — §5.6)
public sealed interface NativeBehaviorSpec extends AiSpec {
    record LookAtTargetSink(int maxLookDistance, int probability)          implements NativeBehaviorSpec {}
    record MoveToTargetSink(int closeEnough, int speed)                    implements NativeBehaviorSpec {}
    record SleepInBed()                                                     implements NativeBehaviorSpec {}
    record RunOne(List<NativeBehaviorSpec> options)                        implements NativeBehaviorSpec {}
    record RunSometimes(int chance, NativeBehaviorSpec delegate)           implements NativeBehaviorSpec {}
    record SetEntityLookTarget(int maxDistance, int probability)           implements NativeBehaviorSpec {}
    record StartAttacking(Function<TaskBrainContext, Optional<LivingEntity>> func) implements NativeBehaviorSpec {}
    record StopAttackingIfTargetInvalid(int unknownExpiry)                 implements NativeBehaviorSpec {}
    // ... grows mob-by-mob
}

// ---- sensor (mirrors Goal-vs-NativeGoalSpec split)
public sealed interface Sensor extends AiSpec {
    record NearestLivingEntities(int radius) implements Sensor {}
    record NearestPlayers(int radius)         implements Sensor {}
    record HurtBy()                            implements Sensor {}
    // custom sensors: implement CustomSensor into the same registry, core-built
}
public interface CustomSensor extends Sensor {
    void sense(TaskBrainContext ctx);           // writes memory; core-built sensor
}

// ---- activity + schedule
public record ActivitySpec(
    String id,                                   // "CORE", "IDLE", "FIGHT", "PANIC"... or custom
    int priority,                                // higher = preferred when multiple eligible
    List<Sensor> sensors,
    List<PrioritizedBehavior> behaviors,
    List<MemoryKey<?>> activationMemories,       // enter when memory conditions true
    List<MemoryKey<?>> deactivationMemories,     // exit when these become false
    List<Pair<MemoryKey<?>, Long>> memoriesOnEnter,   // write-on-entry, expiryTicks
    List<MemoryKey<?>> memoriesOnExit,           // erase-on-exit
    TimeOfDayPredicate schedule               // optional time-of-day/phase gate (§5.4)
) {
    public record PrioritizedBehavior(int priority, Object behaviorOrSpec, int chancePerRun /* -1 = always */) {}
}

public final class TaskBrain {
    private final List<ActivitySpec> activities = new ArrayList<>();
    private final List<MemoryKey<?>> coreMemories = new ArrayList<>();
    private final List<Sensor> coreSensors = new ArrayList<>();
    private String defaultActivity = "IDLE";
    public TaskBrain addActivity(ActivitySpec spec) {...}
    public TaskBrain addCoreMemory(MemoryKey<?> key) {...}
    public TaskBrain addCoreSensor(Sensor sensor) {...}
    public TaskBrain defaultActivity(String id) {...}
    // getters...
}
```

### 5.4 Vanilla `Schedule` — our time-of-day gate instead of the vanilla table

Vanilla `Schedule` is a per-hour mapping of *activity → behavior-list-per-hour*,
heavily entangled with the vanilla `Brain`. Replicating it wholesale is not
worth it. Instead, `ActivitySpec.schedule` uses `TimeOfDayPredicate` **in
core**:

```java
public enum TimeOfDayPredicate { DAY, NIGHT, DAWN, DUSK, MIDNIGHT }
// ActivitySpec applies predicate => the Activity is only *eligible* during that
// window; when it stops matching, the current activity falls back to default.
```

This covers the 95% use case (villager sleeps at night, piglin barricades at
night, warden hunts when... ) with zero native `Schedule` dependency, and stays
runtime-editable because it is plain data in the profile.

### 5.5 `MemoryKey` and the closed-set problem

Same trade-off as the original plan. **Recommendation (unchanged): Option A —
closed, scoped-to-need set of constants**, consistent with how `NativeGoalSpec`
already works in this project. Grow one constant at a time as real mobs need
it. The `MemoryKey` type is deliberately *not* sealed so it can remain an open
registry of constants in api; runtime validation in `TaskBrainEngine` (unknown
id → startup/profile-load failure, not silent) gives the same safety net as
`assertAllSupported`.

Note: because the engine's blackboard is core-owned, `MemoryKey` does **not**
have to correspond to a vanilla `MemoryModuleType` at all — most keys never
need a native counterpart. The only keys that need native resolution are the
ones a *native behavior* (via the vanilla `Brain` bridge, §5.6) has to share
with our engine, and those are resolved through `AiSpecRegistry` per version.

### 5.6 Native behavior bridging — scoping and honesty

Vanilla ships ~150 concrete `Behavior` implementations. Do **not** attempt a
full inventory-and-wrap. Two delivery tiers:

- **Tier 1 — core behaviors (bulk of coverage, zero NMS).** Behaviors written
  as `Behavior`/`CustomBehavior` in core against `TaskBrainContext`, e.g.
  `AttackTargetBehavior`, `MoveToTargetBehavior`, `LookAtBehavior`,
  `RandomStrollBehavior`. These cover most reskin/extension needs that don't
  require vanilla-fidelity internals.
- **Tier 2 — native behaviors (fidelity, per-mob, spiked).** Only wrap vanilla
  behaviors a specific target mob genuinely needs, using the real `<Mob>Ai`
  class as the source of truth (e.g. `VillagerAi`, `PiglinAi`, `WardenAi`).
  Registered into `AiSpecRegistry` per module, applied by constructing a real
  vanilla `Brain` via `Brain.provider(...).makeBrain(...)` and swapping it in.
  **This is the only path subject to the original §3.5 spike** — treat as
  load-time capability, assigned at spawn / explicit reload, never assumed for
  live per-tick swaps. If the spike shows instability, Tier 2 stays
  experimental and Tier 1 fully supports runtime editing alone.

**Prioritize the generic, broadly-reusable wrappers first** (RunOne,
RunSometimes, SetEntityLookTarget, MoveToTargetSink, StartAttacking,
StopAttackingIfTargetInvalid) since they recur across every task-brain mob.

### 5.7 Runtime surface

Provided by core through the api **`MagicAiService`** (superset of today's
`MagicBrainService`, see §10.3):

- `setProfile(entity, profileId)` / `reloadProfile(entity)` /
  `clearProfile(entity)` — full profile (goals + tasks + control + attributes)
- `setMemory(entity, keyId, value)` / `getMemory(entity, keyId)` /
  `eraseMemory(entity, keyId)` — live blackboard editing (day-one)
- `overrideActivity(entity, activityIdOrNull)` — live activity pin
- `setNavigation(entity, kind)` / `setAttribute(entity, name, value)` — live,
  pure-core for attributes, registry-dispatched for navigation

---

## 6. `TaskBrain` application path inside core

1. **`AiProfileLoader`** (core) parses `brains/<id>.yml` into an `AiProfile`
   (goals + tasks + control + attributes), via `GoalFactory` for goal specs and
   new `TaskBrainFactory` for activity/memory/sensor specs. Unknown ids /
   unknown memory keys fail loudly at load.
2. **`MagicBrains`** (core) caches `AiProfile` objects keyed by id, same
   cache strategy as today (profile cache + derived caches for display-name /
   melee-range / wand, which remain on `GoalBrain`).
3. **`MagicBrainServiceImpl`** resolves the profile id from NBT
   (`alkatraz_brain` → `magic_entity_type`), then `AiApplier.applyProfile`
   runs: goal wipe+rebuild → task-brain attach (or `TaskBrainTicker.attach`)
   → control → attributes. `reloadBrains()` sweep extends to re-apply the full
   `AiProfile`, exactly as `/alkatraz reload` already does for goals today.
4. **Module side:** `NMS_<v>` registers its leaf-builders
   (`AiSpecRegistry.register(...)` for every supported `NativeGoalSpec` /
   `NativeBehaviorSpec` / `Sensor`-native / control / navigation spec) inside
   the existing `registerMagicEntities()` hook, and implements the two raw
   primitives `AiApplier` needs (unwrap `LivingEntity`→native `Mob`; selector
   access for goal attach).

---

## 7. `ai.control` + `ai.navigation`: the actuator & pathfinding layer

Both are leaf-builder subsystems: small sealed specs in api, native
constructors registered in `AiSpecRegistry`, orchestration in core.

### 7.1 `ControlProfile`

```yaml
# in brains/<id>.yml (optional section)
control:
  navigation: flying          # ground | flying | water | amphibious  (§7.2)
  move:                       # optional; omit = keep vanilla MoveControl
    type: flying              # flying | water | generic
    speed: 1.2
    can-float: true
  look:                       # optional; omit = keep vanilla LookControl
    type: body-rotation       # body-rotation | vanilla
    max-yaw-change: 20
  jump:                       # optional
    type: random              # random | (skip)
    jump-chance: 0.1
```

```java
// api.mobs
public enum NavigationKind { GROUND, FLYING, WATER, AMPHIBIOUS }
public sealed interface MoveControlSpec extends AiSpec {
    record Flying(double speed, boolean canFloat) implements MoveControlSpec {}
    record Water(double speed)                       implements MoveControlSpec {}
    record Generic(double speed)                     implements MoveControlSpec {}
}
public sealed interface LookControlSpec extends AiSpec {
    record BodyRotation(int maxYawChange) implements LookControlSpec {}
    record Vanilla()                             implements LookControlSpec {}
}
public final class ControlProfile {
    public static ControlProfile of(NavigationKind nav, MoveControlSpec move, LookControlSpec look);
}
```

### 7.2 Application

- **Navigation:** `AiApplier.applyControl` dispatches `NavigationKind` to the
  module's registered builder (each module constructs `FlyingPathNavigation` /
  `WaterBoundPathNavigation` / `AmphibiousPathNavigation` / `GroundPathNavigation`
  with the correct ctor for that version — signatures have been historically
  stable but are verified per version at registration time) and assigns it to
  the native mob (`mob.setNavigation(...)`). Reassignable live via
  `MagicAiService.setNavigation`.
- **Control:** move/look/jump strategies similarly dispatched and swapped onto
  `mob.moveControl` / `lookControl` / `jumpControl`. Optional — a profile that
  omits `control:` keeps vanilla actuators for that mob type, preserving the
  "default should stay zero-config" rule.

---

## 8. `ai.attributes`: declarative stat block

**Findings:** Alkatraz has a full player/items/progression attribute system
(`api.magic.attribute.AttributeService` / `AttributeType` /
`AttributeContribution`, used by spells and `ResearchService`) — for players
and gear, not for mobs. `MobModifier` already stamps *elemental* stats/spells
on mobs at spawn but does not cover the base vanilla mob attribute bank
(health, speed, follow range, attack damage, armor). **Conclusion: build a new,
mob-side declarative block — do not touch the player progression system.**

**This subsystem is pure core** (Bukkit `Attribute` API + XSeries
`XAttribute` for cross-version name mapping — the same pattern as
`MaterialCompat`/`XMaterial` already used in core):

```yaml
# in brains/<id>.yml (optional section)
attributes:
  max-health: 40.0
  movement-speed: 0.28
  follow-range: 48
  attack-damage: 7.0
  armor: 4.0
```

```java
// api.mobs
public final class AttributeProfile {
    public static AttributeProfile of(Map<String, Double> values);
}
// core
public final class MobAttributeHelper {         // pure Bukkit + XSeries
    public static void apply(LivingEntity entity, AttributeProfile profile);
    public static void clear(LivingEntity entity, AttributeProfile profile);  // restore base
}
```

- Verified-base via `entity.getAttribute(XAttribute.find("max-health"))` then
  `AttributeInstance.<active attributes>`. Bukkit attribute API + XAttribute
  enum is stable 1.19→26.2; the enum-name drift is confined to the XSeries
  mapping (already a dependency).
- Applied **last** in `AiApplier.applyProfile` so stat overrides always win.
- Runtime re-apply via `MagicAiService.setAttribute` / the profile reload sweep
  (same path as goals). Editing is `apply` on the fly; `clear` restores
  vanilla base so a reload never stacks modifiers.

---

## 9. (Adjacent, optional) `Pose` / `AnimationState` triggers

Small cosmetic layer, explicitly optional and out of the "AI" core:

```yaml
# in brains/<id>.yml (optional)
pose:
  - when: on-panic          # example trigger
    pose: DIGGING           # vanilla Pose name for the target version
  - when: when-activity, FIGHT
    animation: RISING       # AnimationState name
```

Trigger vocabulary and vanilla name→state mapping live in a core
`PoseDispatcher`; the native side is a module leaf-builder (`setPose/startAnimation`
primitives registered per version). Defer until a concrete mob needs cosmetic
state (the Warden-emerge conversation was the original driver); do not build
speculatively.

---

## 10. Runtime configuration surface

### 10.1 Concepts

Everything the plugin can do to a mob's AI at runtime:

| Action | API (api module) | Core impl | Native need |
|---|---|---|---|
| Assign full profile | `MagicAiService.setProfile(entity, id)` | `MagicBrainServiceImpl` (extended) | `AiApplier` |
| Re-apply (after config reload) | `reloadBrains()` / `reloadProfile(entity)` | same | `AiApplier` |
| Clear AI (passive mob) | `clearProfile(entity)` | wipe + empty profile | `AiApplier` |
| Read assigned profile | `getProfile(entity)` → id | NBT resolution | unwrap |
| Live memory edit | `set/get/eraseMemory(entity, keyId, value)` | `TaskBrainEngine` blackboard | none |
| Pin/clear current activity | `overrideActivity(entity, idOrNull)` | `ActivityScheduler` | none |
| Swap navigation live | `setNavigation(entity, kind)` | `AiApplier.applyControl` | registered nav builder |
| Swap attribute live | `setAttribute(entity, name, value)` | `MobAttributeHelper` | none |
| Toggle sensors live | `addSensor/removeSensor(entity, spec)` | `SensorRunner` | registered native sensor |

### 10.2 Command surface (`/alkatraz`)

Current `/alkatraz reload` already sweeps `MagicBrainService.reloadBrains()`.
Extend the command family:

```
/alkatraz brain set <mob> <profile>
/alkatraz brain clear <mob>
/alkatraz brain memory set <mob> <key> <value> [ticks]
/alkatraz brain memory get <mob> <key>
/alkatraz brain activity <mob> [<activity>|clear]
/alkatraz brain nav <mob> <ground|flying|water|amphibious>
/alkatraz brain attr <mob> <name> [<value>]
```

All routed through `MagicAiService` on the main thread (`ensureMainThread`
pattern already exists in `MagicBrainServiceImpl`).

### 10.3 Service refactor

`MagicBrainService` (existing api interface) is **extended** into
`MagicAiService` (same holder pattern: `getInstance/setInstance`), keeping the
existing 5 methods binary-compatible and adding the task/control/nav/attr
methods from §5.7/§10.1. `MagicBrainServiceImpl` grows accordingly and keeps
its `ensureMainThread` + NBT-resolution logic.

### 10.4 NBT keys

| Key | Value | Owner |
|---|---|---|
| `alkatraz_brain` | profile-id (existing) | unchanged |
| `magic_entity_type` | magic mob identity (existing, fallback resolution) | unchanged |
| `alkatraz_memory` | serialized blackboard for task-brain mobs (optional persistence) | `TaskBrainEngine` (NBT-API `NBT.modifyPersistentData`) |
| `alkatraz_activity` | pinned activity override, if any | `ActivityScheduler` |

---

## 11. Per-module surface matrix (what "implementing" a subsystem means per version)

For each of the 16 modules, "adding support" for the whole `ai` package is
exactly this checklist, once:

- [ ] `NMS_<v>`: replace `applyBrain` with `applyProfile` bridging to
      `AiApplier` (2 lines).
- [ ] `AiSpecRegistration` (formerly `NativeGoalFactory`): one
      `AiSpecRegistry.register(...)` call per supported native spec across
      every subsystem this module supports:
      - GoalBrain natives: `FloatGoal`, `MeleeAttackGoal`,
        `WaterAvoidingRandomStrollGoal`, `LookAtPlayerGoal`,
        `RandomLookAroundGoal`, `HurtByTargetGoal`,
        `NearestAttackableTargetGoal`, `PanicGoal`, `AvoidEntityGoal` (existing
        set, now registered not switch-built) — verify class names per version
        (already cataloged).
      - TaskBrain natives (only if a target mob needs them): the Tier-2
        behavior + sensor set for that mob + `MemoryModuleType` resolution for
        shared keys.
      - Control/navigation natives: `FlyingPathNavigation`,
        `WaterBoundPathNavigation`, `AmphibiousPathNavigation`,
        `FlyingMoveControl`, `WaterMoveControl` + native `setPose`/animation
        primitives if §9 ships.
- [ ] `NmsTaskBrainContext` (new): api task-context over native `Mob` (memory
      ops route to `TaskBrainEngine` in core, so this class is mostly the
      shared `MobBrainContext` members + a reference into the core engine).
- [ ] `AiSpecRegistry.assertAllSupported(...)` at startup — a module that
      forgets a registration fails fast.

Nothing else. In particular, **no per-module YAML parsing, no per-module
priority loops, no per-module wipe logic, no per-module reload sweeping** —
all of that is core.

---

## 12. Explicitly out of scope (for now)

- **`ai.village` / `ai.village.poi`** — villager professions, POI claiming,
  gossip, reputation, trading blocks. A genuinely separate subsystem; if a
  magic villager variant is wanted, scope it as its own project informed by
  this one's patterns.
- **Full enumeration of all ~150 vanilla `Behavior` classes** — §5.6. Grown on
  demand, mob-by-mob.
- **Vanilla `Schedule` hour-tables** — replaced by the core `TimeOfDayPredicate`
  gate (§5.4). Full per-hour behaviour tables add version-drift risk for no
  gain at this stage.
- **Data-pack-driven Brain customization** as the delivery mechanism — the
  core engine is the delivery mechanism; datapack hooks are not assumed.
- **Full runtime *native-`Brain` swap*** — §5.6 Tier 2 remains load-time until
  the §3.5-spike proves otherwise.
- **`GoalBrain` extrapolation to every remaining vanilla goal** (§4 extension)
  and **Pose/Animation** (§9) — defer until a concrete mob needs them.

---

## 13. Proposed phase order

| Phase | Deliverable | Depends on |
|---|---|---|
| 0 | Resolve naming (§2 — assume Option A), confirm attribute collision findings (§8), pick one target task-brain mob | — |
| **A** | **Core-driven refactor of the GoalBrain path** — `AiSpecRegistry` + `AiApplier` in core; collapse `GoalBuilder`/`NativeGoalFactory` per module into `AiSpecRegistration`; verify `mvn compile` + startup assertion across all 16 modules. *This is what makes every later phase "through core."* | 0 |
| 1 | `TaskBrain` api types + core engine (`TaskBrainEngine`, `MobMemoryStore`, `ActivityScheduler`, `SensorRunner`, `TaskBrainTicker`) + `TaskBrainFactory` YAML loader, for exactly the chosen phase-1 mob | A |
| 2 | Spike: native-`Brain` swap feasibility for Tier 2 (§5.6); ship memory-editing + activity-override runtime API regardless of outcome | 1 |
| 3 | Roll `TaskBrain` support out to all sixteen NMS modules for the phase-1 mob (`NmsTaskBrainContext` + native registrations) | 1, 2 |
| 4 | `ai.attributes` pure-core block (§8) + `MagicAiService`/command surface for it — independent, can run parallel to 1–3 | A |
| 5 | `ai.navigation` + `ai.control` leaf-builders + `ControlProfile` (§7) — independent | A |
| 6 | Second, third... task-brain mob, each growing `NativeBehaviorSpec`/`MemoryKey`/sensor coverage incrementally | 3 |
| 7 | (optional) `Pose`/`AnimationState` triggers (§9) — only if a concrete mob needs it | 5 |

Phases 4 and 5 can start immediately after Phase A and don't block or depend
on the `TaskBrain` work. Phase A itself is a prerequisite for *everything*:
it is the refactor that makes the "through core" property true.

---

## 14. How a phase turns into an implementation prompt

Each phase above becomes its own implementation plan in the style of the
existing ones (`docs/superpowers/plans/2026-09-15-core-driven-magic-mob-brains.md`,
`2026-09-16-live-mob-brain-editing.md`): checkbox steps, per-module template
catalog (A–H style) where a phase touches modules, explicit **Interfaces:
Consumes / Produces** blocks at the top of each task, and the standing repo
rules (JAVA_HOME `C:\Program Files\Java\jdk-25`, gate `mvn compile` at root,
PowerShell, single end-of-plan commit with no conventional prefixes). The
per-module matrix in §11 is the canonical "what a phase touches" checklist to
expand into tasks.