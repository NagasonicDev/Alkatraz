# General AI Framework API — Design

**Status:** approved design, ready for an implementation plan
**Date:** 2026-09-16

## 1. Purpose

Alkatraz is gaining an AI framework that covers more than the goal system:
memory-driven behaviors (TaskBrain), movement control, pathfinding
navigation, and stat attributes. This design locks in **Phase B**: the
**general API layer** for all of these subsystems, built as a **standalone,
completely general mini-framework** with zero Alkatraz-specific concepts —
because the framework may be applied to other plugins.

The general API comes **first**. Alkatraz specialization (core engine,
runtime service, YAML structures) comes **after** the API is built.

## 2. Goals

- Deliver general, declarative API types in the api module for four
  subsystems: **TaskBrain**, **control**, **navigation**, **attributes**.
- The framework is **standalone**: only `spigot-api` (`org.bukkit`) and
  `org.jetbrains.annotations`. No reference to `api.mobs`, `api.magic`,
  MagicEntity, brains YAML, or any Alkatraz concept.
- Reusable by other plugins: goals excluded (see Non-goals), the framework
  ships as self-contained Java types in one namespace.
- The api module gains a real test suite so the GitHub Action verifies the
  system works: tests for the new general types **and** for the existing
  goals subsystem.

## 3. Non-goals (explicitly out of this phase)

- **No engine.** No `TaskBrainEngine`, `MobMemoryStore`, `ActivityScheduler`,
  `SensorRunner`, `TaskBrainTicker`, apply/dispatch logic, or registry.
- **No NMS.** No leaf-builders, no `AiSpecRegistration`, no per-version
  bridging.
- **No YAML.** No `brains/<id>.yml` structures, no `TaskBrainFactory`, no
  loader. YAML comes after the API (and after core) per the user's ordering.
- **No Alkatraz specialization.** No `MagicBrains`/`MagicBrainServiceImpl`
  wiring, no `AiProfileLoader`, no runtime service.
- **No goal-system redesign.** The existing goals (`api.mobs`:
  `GoalBrain`, `Goal`, `MobBrainContext`, `NativeGoalSpec`, `GoalFlag`) stay
  exactly as they are. They are already Bukkit-typed and reusable; they get
  tests but no changes.

## 4. Decisions locked during brainstorming

| Decision | Choice |
|---|---|
| Shape | **A — sealed spec family**: config-shaped objects are records under a sealed `AiSpec`; executable units are interfaces in the same family |
| Scope | **All four subsystems' general API now** (TaskBrain + control + navigation + attributes) |
| Placement | **New api sub-package, same module** (`me.nagasonic.alkatraz.api.ai`) |
| Generality | **Standalone mini-framework**: nothing Alkatraz-specific, no `api.mobs` references |
| Goals | Keep as-is, out of scope for generalization; add tests |
| Java | 17 (records + sealed; confirmed against `api/pom.xml`) |
| Phasing | Engine (core) and YAML structures come in later phases, after the API |

## 5. Package layout

```
me.nagasonic.alkatraz.api.ai              AiSpec, AiProfile
me.nagasonic.alkatraz.api.ai.task         TaskBrain family (memory, sensor, behavior, activity)
me.nagasonic.alkatraz.api.ai.control      ControlProfile + move/look/jump specs
me.nagasonic.alkatraz.api.ai.navigation   NavigationKind / NavigationSpec
me.nagasonic.alkatraz.api.ai.attribute    AttributeProfile / AttributeValue
```

Dependencies of the api module remain unchanged (spigot-api, snakeyaml,
jetbrains annotations — all `provided`); junit5 is added as a **test-scope**
dependency only.

## 6. Sealed umbrella

```java
public sealed interface AiSpec {}
```

Every declarative spec in all four subsystems implements `AiSpec` (directly
or via a sub-interface). This gives the framework one sealed family and one
dispatch story later (mirrors how `NativeGoalSpec` works today, generalized).

## 7. TaskBrain family — `api.ai.task`

### 7.1 Memory

```java
public final class MemoryKey<T> {
    public static final MemoryKey<LivingEntity> ATTACK_TARGET = new MemoryKey<>("attack_target");
    public static final MemoryKey<Location>     WALK_TARGET    = new MemoryKey<>("walk_target");
    public static final MemoryKey<List<LivingEntity>> NEAREST_HOSTILES = new MemoryKey<>("nearest_hostiles");
    public static final MemoryKey<Boolean>      IS_HURT         = new MemoryKey<>("is_hurt");
    public String id()
}
```

- **Open registry** (not sealed): public constructor for custom keys;
  `equals`/`hashCode` on `id`.
- Unknown ids fail loudly later at engine/profile-load time (like
  `AiSpecRegistry.assertAllSupported`). Not a silent op.

```java
public enum MemoryStatus { PRESENT, ABSENT, REGISTERED }
```

`REGISTERED` mirrors vanilla's "key must be registered even when the value is
absent"; the empty-value concept is an engine concern and is only named here.

### 7.2 Runtime context

```java
public interface TaskBrainContext {
    LivingEntity getEntity();
    <T> Optional<T> getMemory(MemoryKey<T> key);
    <T> void setMemory(MemoryKey<T> key, T value);
    <T> void setMemoryWithExpiry(MemoryKey<T> key, T value, long ticks);
    void eraseMemory(MemoryKey<?> key);
    boolean hasMemory(MemoryKey<?> key);
}
```

Standalone: does **not** extend `MobBrainContext`.

### 7.3 Behaviors

```java
public interface Behavior {
    Map<MemoryKey<?>, MemoryStatus> memoryRequirements();
    boolean canStart(TaskBrainContext ctx);
    default void start(TaskBrainContext ctx) {}
    boolean shouldContinue(TaskBrainContext ctx);
    void tick(TaskBrainContext ctx);
    default void stop(TaskBrainContext ctx) {}
    int minDuration();
    int maxDuration();
}

public interface CustomBehavior extends Behavior {}   // implemented in core/plugin, zero NMS

public sealed interface NativeBehaviorSpec extends AiSpec {
    record LookAtTargetSink(int maxLookDistance, int probability)  implements NativeBehaviorSpec {}
    record MoveToTargetSink(int closeEnough, int speed)            implements NativeBehaviorSpec {}
    record SleepInBed()                                            implements NativeBehaviorSpec {}
    record RunOne(List<NativeBehaviorSpec> options)                implements NativeBehaviorSpec {}
    record RunSometimes(int chance, NativeBehaviorSpec delegate)   implements NativeBehaviorSpec {}
    record SetEntityLookTarget(int maxDistance, int probability)   implements NativeBehaviorSpec {}
    record StartAttacking(Function<TaskBrainContext, Optional<LivingEntity>> func)
            implements NativeBehaviorSpec {}
    record StopAttackingIfTargetInvalid(int unknownExpiry)         implements NativeBehaviorSpec {}
}
```

`StartAttacking` holds a `Function` — a factory hook the bridged builder
invokes; it is the one spec that is not pure-data.

### 7.4 Sensing

```java
public sealed interface Sensor extends AiSpec {
    record NearestLivingEntities(int radius) implements Sensor {}
    record NearestPlayers(int radius)          implements Sensor {}
    record HurtBy()                            implements Sensor {}
}

public interface CustomSensor extends Sensor {
    void sense(TaskBrainContext ctx);
}
```

### 7.5 Activities & the brain container

```java
public enum TimeOfDayPredicate { DAY, NIGHT, DAWN, DUSK, MIDNIGHT }

public record TimedMemory(MemoryKey<?> key, long expireTicks) {}

public record PrioritizedBehavior(int priority, Object behaviorOrSpec, int chancePerRun) {}

public record ActivitySpec(
        String id,
        int priority,
        List<Sensor> sensors,
        List<PrioritizedBehavior> behaviors,
        List<MemoryKey<?>> activationMemories,     // enter when all present
        List<MemoryKey<?>> deactivationMemories,   // exit when any absent
        List<TimedMemory> memoriesOnEnter,         // write on entry, with expiry
        List<MemoryKey<?>> memoriesOnExit,         // erase on exit
        TimeOfDayPredicate schedule) implements AiSpec {}

public final class TaskBrain {
    // builder (fluent): addActivity(ActivitySpec) / addCoreMemory(MemoryKey<?>) /
    //                   addCoreSensor(Sensor) / defaultActivity(String)
    // getters (unmodifiable): activities() / coreMemories() / coreSensors() / defaultActivity()
}
```

- `PrioritizedBehavior.behaviorOrSpec` is an `Object` carrying either a
  `Behavior` or a `NativeBehaviorSpec` — matches how `GoalBrain.Entry`
  already describes its union.
- `chancePerRun` = -1 means "always".
- Default activity id defaults to `"IDLE"`.

## 8. Control — `api.ai.control`

```java
public sealed interface MoveControlSpec extends AiSpec {
    record Flying(double speed, boolean canFloat) implements MoveControlSpec {}
    record Water(double speed)                      implements MoveControlSpec {}
    record Generic(double speed)                    implements MoveControlSpec {}
}

public sealed interface LookControlSpec extends AiSpec {
    record BodyRotation(int maxYawChange) implements LookControlSpec {}
    record Vanilla()                        implements LookControlSpec {}
}

public enum JumpControlSpec { RANDOM, NONE }

public final class ControlProfile {
    public static ControlProfile of(NavigationSpec navigation,
                                    MoveControlSpec move,
                                    LookControlSpec look,
                                    Optional<Double> jumpChance);   // present ⇒ RANDOM jump
    // getters: Optional<NavigationSpec> / Optional<MoveControlSpec> /
    //          Optional<LookControlSpec> / Optional<Double> (jumpChance)
}
```

Rules:
- Each slot individually omittable; omitted = keep the vanilla actuator for
  that slot (the "default stays zero-config" rule).
- `jumpChance` as a single `Optional<Double>` knob rather than a nested
  `type: random` record.

## 9. Navigation — `api.ai.navigation`

```java
public enum NavigationKind { GROUND, FLYING, WATER, AMPHIBIOUS }

public sealed interface NavigationSpec extends AiSpec {
    record Default()                 implements NavigationSpec {}   // keep vanilla nav
    record FixedType(NavigationKind kind) implements NavigationSpec {}
}
```

Wrapping `NavigationKind` in `NavigationSpec` lets a profile express "leave
the base class's default navigation untouched" via `Default()`, consistent
with the omit-to-keep-default rule elsewhere.

## 10. Attributes — `api.ai.attribute`

```java
public record AttributeValue(org.bukkit.attribute.Attribute attribute, double base) {}

public final class AttributeProfile {
    public static AttributeProfile of(AttributeValue... values);
    public static AttributeProfile of(List<AttributeValue> values);
    public List<AttributeValue> values();   // unmodifiable, insertion order
}
```

The expansion doc's `Map<String,Double>` + XSeries name-mapping exists so
YAML keys like `max-health` map cross-version. That is a data-loading concern
and belongs to the later YAML phase. The general framework speaks
`org.bukkit.attribute.Attribute` directly.

## 11. Combined per-mob document — `api.ai`

```java
public record AiProfile(
        Optional<TaskBrain> taskBrain,
        Optional<ControlProfile> control,
        Optional<AttributeProfile> attributes) {}
```

Goals are not part of this general document (they stay in `api.mobs`). The
later Alkatraz profile = this general `AiProfile` plus the goals block.

## 12. Cross-cutting rules

1. **Validation lives in the API** — defensive, developer-facing
   `IllegalArgumentException`:
   - non-blank ids;
   - priorities and distances/speeds non-negative (chance clamped to -1 or
     0..100);
   - `defaultActivity` must reference an existing activity when set;
   - an activity's activation/on-enter/on-exit memory-key references must be
     in `coreMemories` or declared on the activity itself.
2. **Immutability** — records + unmodifiable lists; builders only where
   fluent assembly adds value (`TaskBrain`, `ControlProfile`,
   `AttributeProfile`, `ActivitySpec` via compact constructor).
3. **No engine-facing hooks** — no registry, no apply, no NMS, no ticker.
   The api compiles standalone and the gate is `mvn -q -pl api compile`.
4. **Javadoc** on every public type (the module already attaches javadoc jars).

## 13. Testing

- Add **junit5 (org.junit.jupiter) test scope** to `api/pom.xml`; no mocking
  library — the api contains no NMS and no I/O, so tests are pure
  builder/record/validation assertions.
- Test the **new general types**: `TaskBrain` builder validation
  (duplicate/unknown activity ids, bad chances, default-activity
  referencing), `MemoryKey` equality/`id()`, activity memory-set validation,
  `ControlProfile` slot omittance, `AttributeProfile` ordering/immutability,
  `AiProfile` optional-slot behavior.
- Test the **existing goals subsystem** (`api.mobs`): `GoalBrain` builder
  (addGoal/addTargetGoal/addNative/addNativeTarget ordering, priorities,
  immutability via the unmodifiable `entries()` view), `NativeGoalSpec`
  record accessors, and `GoalFlag`/the api `Goal` interface contract using a
  trivial test `Goal` implementation.
- The existing GitHub Action (`test-plugin.yml`) runs `mvn test` across the
  reactor, so the new api tests are exercised there automatically;
  locally the gate is `mvn -q -pl api test`.

## 14. Out of scope / future phases

- **Core engine** (TaskBrainEngine, MobMemoryStore, ActivityScheduler,
  SensorRunner, TaskBrainTicker; apply/dispatch via a registry): later
  phase, general.
- **Alkatraz specialization** (AiProfileLoader, MagicBrains/TaskBrainFactory
  YAML integration, MagicBrainServiceImpl, per-mob brains YAML): after the
  engine, per the user's ordering.
- **NMS leaf-builders** for native behaviors/control/navigation and the
  vanilla-Brain swap spikes: per-mob, per-version, separate phases.
- **Goals generalization** into the framework namespace: explicitly not
  happening; goals stay `api.mobs`.

## 15. Acceptance criteria

1. `mvn -q -pl api compile` passes with the new types in place.
2. `mvn -q -pl api test` passes with tests for both the new framework and the
   existing goals subsystem.
3. `mvn compile` and `mvn test` at the root reactor stay green (all 20
   modules).
4. Grep audit: no `api.ai` type imports or references `me.nagasonic.alkatraz.api.mobs`
   / `me.nagasonic.alkatraz.api.magic` / NMS packages.
5. No engine, registry, NMS, or YAML code landed; this phase is types + tests
   only.