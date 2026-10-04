package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Drives the real {@link SpellAttackContext}. There is deliberately **no
 * stand-in** here: this class is the single most load-bearing mechanism in the
 * rebalance, because it is what makes spec section 4's rules 1, 2 and 5
 * mechanical rather than conventional. A ledger clone would pass whether or not
 * {@code SpellAttackContext} itself regressed, which is exactly the failure mode
 * it exists to prevent.
 *
 * <p><b>Which method throws.</b> {@code hit(target)} never throws - it returns
 * {@code false} for anyone this cast has already touched, because
 * {@code AttackProperties.hit} writes into a {@code Set<UUID>} that is never
 * cleared for the life of the cast ({@code AttackProperties.java:20} declares the
 * field, {@code :62-65} is the only writer, and nothing ever removes). The
 * regression guard is in {@code power(target)}: scaling the same target twice
 * inside one damage tick throws {@link IllegalStateException}, which is the
 * double-apply. Spec section 4.4's prose ("the throw is on the second call to
 * {@code hit()} within a single damage tick") reads either way; these assertions
 * pin the behaviour the T6 implementation actually has.
 *
 * <p>Bukkit-free apart from three Mockito entity mocks and one
 * {@code MockedStatic<Alkatraz>} that only exists because {@code Utils}' static
 * initialiser probes the server version - see {@link #setUp()}. {@code AttackProperties}
 * is a plain class with no server dependency, and {@link Probe} overrides both
 * {@code getPower} overloads so the assertions are about how many times the
 * context calls the scaler rather than about what the scaler computes - no
 * profile read, no NBT read, no option lookup.
 */
class SpellAttackContextTest {

    /**
     * A concrete {@code AttackSpell} whose only job is to make the number of
     * {@code getPower} applications observable. Without this there is no way to
     * assert rule 1 mechanically.
     */
    private static final class Probe extends AttackSpell {

        int targetedApplications;
        int untargetedApplications;

        Probe(double basePower) {
            super("probe");
            this.id = "context_probe";
            this.element = Element.FIRE;
            this.basePower = basePower;
        }

        @Override
        public double getPower(LivingEntity caster, LivingEntity target, double power) {
            targetedApplications++;
            return power;
        }

        @Override
        public double getPower(LivingEntity caster, double power) {
            untargetedApplications++;
            return power;
        }

        @Override
        public void loadConfiguration() {
        }

        @Override
        public void castAction(Player p, ItemStack wand) {
        }

        @Override
        public void mobCastAction(Mob caster, ItemStack wand) {
        }

        @Override
        public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
            return 0;
        }

        @Override
        public ItemStack getSpellBook() {
            return null;
        }

        @Override
        public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        }

        @Override
        public void onCountered(Location location) {
        }
    }

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private LivingEntity caster;
    private LivingEntity targetA;
    private LivingEntity targetB;
    private MockedStatic<Alkatraz> alkatrazMock;

    @BeforeEach
    void setUp() {
        // The 4-arg of(...) calls Utils.castLocation(caster), and touching ANY
        // static on Utils runs Utils.<clinit>, whose first statement is
        // `Particle.valueOf(oldOrNew("REDSTONE", "DUST"))` (Utils.java:39).
        // oldOrNew probes the server version through MinecraftVersion.<clinit>,
        // which does `Alkatraz.getInstance().getServer().getVersion()`
        // (MinecraftVersion.java:59) - so without this the whole class dies with
        // ExceptionInInitializerError, and the JVM then poisons every later
        // Utils access with NoClassDefFoundError.
        //
        // The reported version has to resolve to a real Particle constant on the
        // spigot-api 1.19 test classpath, whose Particle enum only carries the
        // pre-1.20.5 names. 1.19.4 therefore resolves to MINECRAFT_1_20_4-or-older
        // -> hasNewMappings() false -> every Particle.valueOf below hits an old
        // name (REDSTONE, EXPLOSION_LARGE, SMOKE_NORMAL, SPELL_WITCH, ...).
        Server server = mock(Server.class);
        when(server.getVersion()).thenReturn("git-Paper-1.19.4 (MC: 1.19.4)");
        Alkatraz plugin = mock(Alkatraz.class);
        when(plugin.getServer()).thenReturn(server);
        alkatrazMock = mockStatic(Alkatraz.class);
        alkatrazMock.when(Alkatraz::getInstance).thenReturn(plugin);

        // A plain LivingEntity, deliberately NOT a Player: the 4-arg of(...) then
        // resolves getWandPower through the no-profile path, so ProfileManager and
        // the equipment snapshot are never touched. That is the same reason the
        // context holds a LivingEntity - mob casts need it too.
        caster = mock(LivingEntity.class);
        when(caster.getEyeLocation()).thenReturn(new Location(null, 0.0, 0.0, 0.0));
        targetA = mock(LivingEntity.class);
        when(targetA.getUniqueId()).thenReturn(A);
        targetB = mock(LivingEntity.class);
        when(targetB.getUniqueId()).thenReturn(B);
    }

    /** A leaked static mock would break every other test class in the module. */
    @AfterEach
    void tearDown() {
        alkatrazMock.close();
    }

    /** The 5-arg overload: properties the spell already seeded, wrapped. */
    private SpellAttackContext seeded(Probe spell, double power) {
        return SpellAttackContext.of(spell,
                new AttackProperties(caster, new Location(null, 0.0, 0.0, 0.0), power, AttackType.MAGIC),
                caster, null);
    }

    @Test
    void fourArgOverloadSeedsItsOwnPropertiesFromRawBasePower() {
        Probe spell = new Probe(4.0);
        SpellAttackContext context = SpellAttackContext.of(spell, caster, null, AttackType.MAGIC);

        AttackProperties props = context.properties();
        assertNotNull(props, "the 4-arg overload builds its own AttackProperties");
        assertEquals(4.0, props.getRemainingPower(), 1e-9,
                "raw getBasePower() * getWandPower - the wand is null so getWandPower is the neutral 1.0. "
                        + "Nothing is pre-scaled here; pre-scaling is the bug this class exists to catch");
        assertEquals(4.0, props.getInitialPower(), 1e-9, "initialPower is the same raw base");
        assertSame(caster, props.getCaster(), "the seeded properties carry the caster");
        assertSame(caster, context.caster(), "the context keeps the LivingEntity, not a Player");
        assertNull(context.wand(), "a null wand is passed through untouched");

        context.tick();
        assertTrue(context.hit(targetA), "the first hit on a target registers");
        assertEquals(4.0, context.power(targetA), 1e-9, "the seeded remaining power is what reaches damage");
        assertEquals(1, spell.targetedApplications, "exactly one getPower application for one damage instance");
    }

    @Test
    void fiveArgOverloadWrapsThePropertiesTheSpellAlreadySeeded() {
        Probe spell = new Probe(4.0);
        // radiance and shadow_realm carry no power: key at all, so they seed their
        // own value from their own config and hand it in - see wave 3 T12 Step 4
        // and T13 Step 4.
        AttackProperties own = new AttackProperties(caster, new Location(null, 0.0, 0.0, 0.0), 12.5, AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(spell, own, caster, null);

        assertSame(own, context.properties(),
                "one shared AttackProperties per cast - countering kills everything built on it (rule 5)");
        context.tick();
        assertTrue(context.hit(targetA), "the first hit registers");
        assertEquals(12.5, context.power(targetA), 1e-9,
                "the spell's own seeded value reaches damage, NOT getBasePower() - that is the whole "
                        + "reason this overload exists");
        assertEquals(1, spell.targetedApplications, "exactly one getPower application for one damage instance");
    }

    @Test
    void oneDamageInstanceAppliesGetPowerExactlyOnce() {
        Probe spell = new Probe(4.0);
        SpellAttackContext context = seeded(spell, 4.0);
        context.tick();

        assertTrue(context.hit(targetA), "the first hit on a target registers");
        assertEquals(4.0, context.power(targetA), 1e-9, "power reaches damage at the seeded value");
        assertEquals(1, spell.targetedApplications, "exactly one getPower application for one damage instance");

        assertFalse(context.hit(targetA), "hasHit rejects a repeated target (rule 2)");
        assertEquals(1, spell.targetedApplications, "a deduped repeat must not apply getPower a second time");
    }

    @Test
    void secondDamageInstanceForOneTargetInOneTickThrows() {
        Probe spell = new Probe(4.0);
        SpellAttackContext context = seeded(spell, 4.0);
        context.tick();

        // A BURST volley hitting two DIFFERENT targets on one tick is legal.
        assertTrue(context.hit(targetA), "first target");
        assertTrue(context.hit(targetB), "a second, different target on the same tick is legal");
        context.power(targetA);
        context.power(targetB);
        assertEquals(2, spell.targetedApplications, "two targets, two applications, one tick");

        // The same target twice in one damage tick is the double-apply regression.
        assertThrows(IllegalStateException.class, () -> context.power(targetA),
                "scaling one target twice inside one damage tick is the 2-arg-at-cast + 3-arg-at-impact bug");

        // The untargeted overload is mutually exclusive with the targeted one, in
        // either order.
        SpellAttackContext untargetedFirst = seeded(new Probe(4.0), 4.0);
        untargetedFirst.tick();
        assertEquals(4.0, untargetedFirst.power(), 1e-9, "the untargeted overload applies its own power");
        assertThrows(IllegalStateException.class, () -> untargetedFirst.power(targetA),
                "the untargeted getPower overload must not share a tick with a targeted one");

        SpellAttackContext targetedFirst = seeded(new Probe(4.0), 4.0);
        targetedFirst.tick();
        targetedFirst.power(targetA);
        assertThrows(IllegalStateException.class, () -> targetedFirst.power(),
                "...and the exclusivity holds in the other order too");
    }

    @Test
    void repeatedTargetAcrossDamageTicksDoesNotThrow() {
        Probe spell = new Probe(7.2);
        SpellAttackContext context = seeded(spell, 7.2);

        // This is what makes a SUSTAIN field work: same target, new damage tick,
        // no throw. tornado, whirlpool, wind_vortex, radiance and shadow_realm
        // all depend on this - if it ever throws, every field lands zero ticks.
        for (int tick = 0; tick < 5; tick++) {
            context.tick();
            assertEquals(tick + 1L, context.currentTick(), "tick() is the damage-tick boundary");
            // The canonical SUSTAIN shape RECORDS every strike so a single counter
            // still kills the whole field, and gates the repeats on its own
            // runnable-local ledger rather than on this return value. So hit() is
            // called on every tick here, and only the first returns true - the
            // dedupe set is scoped to the CAST, not to the damage tick.
            boolean registered = context.hit(targetA);
            if (tick == 0) {
                assertTrue(registered,
                        "the first strike of the cast registers");
            } else {
                assertFalse(registered,
                        "hit() dedupes for the life of the CAST, not for the damage tick: every repeat "
                                + "returns false without throwing, so a repeated target across damage "
                                + "ticks is a plain no-op, never an error");
            }
            context.power(targetA);
        }

        assertEquals(5, spell.targetedApplications, "one getPower per damage tick, five ticks, five applications");
        assertFalse(context.hit(targetA),
                "hasHit is a cast-lifetime set, so a SUSTAIN field's REPEATS must be gated on its own "
                        + "runnable-local interval ledger - see 'SUSTAIN cadence: hasHit must not gate the "
                        + "repeats' at the top of parts/03-wave3-migrations.md");
        assertEquals(20L, SpellAttackContext.DAMAGE_INTERVAL_TICKS,
                "a SUSTAIN field damages a target at most once every second");
    }

    @Test
    void spentAfterCounterOrExhaustedPower() {
        SpellAttackContext countered = seeded(new Probe(4.0), 4.0);
        assertFalse(countered.isSpent(), "a fresh cast is not spent");
        countered.properties().counter();
        assertTrue(countered.isSpent(), "counter() cancels the properties, so every runnable polls true");
        assertTrue(countered.properties().isCountered(), "countered and cancelled are both set");

        SpellAttackContext drained = seeded(new Probe(4.0), 4.0);
        drained.properties().reducePower(0.5);
        assertEquals(2.0, drained.properties().getRemainingPower(), 1e-9, "reducePower scales the shared power");
        assertFalse(drained.isSpent(), "half power is still a live cast");

        drained.properties().reducePower(0.0);
        assertEquals(0.0, drained.properties().getRemainingPower(), 1e-9,
                "the value is driven all the way to zero");
        assertTrue(drained.isSpent(),
                "AttackProperties.reducePower cancels at <= 0 (AttackProperties.java:34-39), so isSpent() "
                        + "reports true and the owning runnable stops");
    }
}