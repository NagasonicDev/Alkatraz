# AI-Framework and Goal-System Test Coverage Design

**Date:** 2026-09-16
**Status:** Approved
**Phase:** Test coverage expansion for the general AI framework API (`api.ai`),
the goal-brain API (`api.mobs`), and the core goal engine.

## Goal

Add exhaustive JUnit test coverage — run automatically by the CI GitHub
Action's `unit-tests` job (`mvn -B -T 1C test`) — for every aspect of both:

1. the general AI framework (`me.nagasonic.alkatraz.api.ai`), and
2. the goal system (`me.nagasonic.alkatraz.api.mobs` types + the core goal
   machinery in `me.nagasonic.alkatraz.mobs`).

No CI workflow changes are required; any JUnit test in `api`/`core` runs in the
existing pipeline automatically.

## Scope

- **api.ai:** already exhaustively covered by 10 test files (54 tests committed
  with `a580364`). This phase only fills validated gaps found during planning —
  none expected.
- **api.mobs:** add `SpellCastConfigTest`; fold any remaining small gaps
  (GoalBrain.Entry immutability, Goal defaults, GoalFlag) into existing tests
  only if actually missing.
- **core goal machinery:** NEW coverage with Mockito:
  - `GoalFactoryTest`
  - `AiApplierTest`
  - `KeepSpellRangeGoalTest`
  - `CastSpellGoalTest`
  - `MagicBrainsTest`
  - `AiSpecRegistryTest` (extend with edge cases)

## Out of Scope

- No production code changes (testability seams explicitly rejected).
- No CI workflow YAML changes.
- No integration/yaml-fixture harnesses (Approach A chosen; YAML fixtures and
  lifecycle integration explicitly declined).

## Approach

Depth-first unit tests. Each engine component is exercised in isolation with
Mockito; `api` module remains mock-free.

### Dependency changes

- `core/pom.xml`: add `mockito-core` (5.x) with **test** scope. Inline static
  mocking is the default since Mockito 5, so `mockStatic(...)` works without a
  separate `mockito-inline` artifact. Java 17 compatible.
- `api/pom.xml`: unchanged.

## Test Inventory

### api module (no mocking lib)

| File | Covers |
|------|--------|
| `SpellCastConfigTest` | record accessors; min/maxCastDist `0` semantics |
| (gap-tests into existing `GoalTest`/`GoalBrainTest`/`NativeGoalSpecTest` only if actually missing) | |

### core module (Mockito)

| File | Covers |
|------|--------|
| `AiSpecRegistryTest` (extend) | edge cases: null spec, duplicate register() override behavior, unmodifiable registered() view |
| `AiApplierTest` | guard clauses (non-Mob entity, null NMS, null nativeMob/brain); dispatch chain: wipeGoals → per-entry AiSpecRegistry.build OR bridgeCustomGoal → addGoal(priority, isTargetGoal); IllegalArgumentException on unknown goal type; NMS mocked via mockStatic(Alkatraz.class) |
| `GoalFactoryTest` | all 9 native specs parsed from in-memory YamlConfiguration sections with exact defaults (melee speed 1.0, water_avoiding_stroll 0.8, look_at_player range 8.0, panic 1.25, avoid walk 0.8/sprint 1.2, nearest target-class Player must-see true); both custom goals (cast_spell, keep_spell_range); `target:` flag routing → addTargetGoal/addNativeTarget; Class.forName fallback to Player; unknown type no-op |
| `KeepSpellRangeGoalTest` | canStart inside/outside band; shouldContinue; tick recalc cadence (10 ticks); strafe-away vs move-toward destination math; stop() clears navigation; flags() returns MOVE |
| `CastSpellGoalTest` | cooldown gate decrement; dead/null target; range; LoS; wind-up accumulation; selectSpell shuffle + canMobCast filtering; fireCast wand path + SpellPrepareEvent cancel (returns early); cooldown reset after fire; flags() returns LOOK |
| `MagicBrainsTest` | reload(String) parses display-name (default "&f"+id) / melee-range (default 0) / wand; goals: vs targets: section routing (target flag injected); cache getters; ConfigManager mocked via mockStatic, in-memory YAML (no temp files) |

### Backstop gates

- `mvn -q -pl api test` — green (existing 54 + SpellCastConfigTest → 55)
- `mvn -q -pl core test` — green (existing AiSpecRegistryTest 6 + new)
- root `mvn compile` + `mvn test` — BUILD SUCCESS, 20 modules, all JUnit green
- grep audit: no production sources changed; only `core/pom.xml`,
  `core/src/test/**`, `api/src/test/**` touched

## Commit

Single end-of-plan commit on `master`, plain-language message, no conventional
prefix (repo git-commit-style).

## Out of Scope (future)

- Integration harness, YAML file fixtures, lifecycle tests.
- Engine work for `api.ai` (no engine exists yet — pure declarative types).
- Testability seams or other production changes.