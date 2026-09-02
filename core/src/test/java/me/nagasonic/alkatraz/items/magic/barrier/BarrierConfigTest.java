package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierConfigTest {

    // ── 1. All defaults from Map.of() ──────────────────────────────────────

    @Test
    void allDefaultsFromEmptyMap() {
        BarrierConfig cfg = BarrierConfig.fromConfig(Map.of());

        assertEquals(BarrierConfig.HoldMode.NONE, cfg.holdMode());
        assertEquals(40, cfg.maxHitpoints());
        assertEquals(40, cfg.hitpoints());
        assertEquals(BarrierConfig.Shape.CAP, cfg.shape());
        assertEquals(3.0, cfg.radius());
        assertEquals(1.0, cfg.verticalOffset());
        assertTrue(cfg.persistHpAcrossSummons());
        assertEquals(1, cfg.affinityScaling().bonusPer10Affinity());
        assertEquals(BarrierType.COMBINED, cfg.barrierType());
        assertEquals(0.75, cfg.regen().hpPerSecond());
        assertEquals(2.0, cfg.regen().manaCostPerHp());
        assertEquals(BarrierConfig.CooldownConfig.CooldownMoment.NONE, cfg.cooldown().on());
        assertEquals(BarColor.PINK, cfg.healthBar().color());
        assertEquals(BarStyle.SOLID, cfg.healthBar().style());
    }

    @Test
    void allDefaultsFromNull() {
        BarrierConfig cfg = BarrierConfig.fromConfig(null);

        assertEquals(BarrierConfig.HoldMode.NONE, cfg.holdMode());
        assertEquals(40, cfg.maxHitpoints());
        assertEquals(40, cfg.hitpoints());
        assertEquals(BarrierConfig.Shape.CAP, cfg.shape());
        assertEquals(3.0, cfg.radius());
        assertEquals(1.0, cfg.verticalOffset());
        assertTrue(cfg.persistHpAcrossSummons());
        assertEquals(BarrierType.COMBINED, cfg.barrierType());
        assertEquals(BarrierConfig.CooldownConfig.CooldownMoment.NONE, cfg.cooldown().on());
        assertEquals(BarColor.PINK, cfg.healthBar().color());
        assertEquals(BarStyle.SOLID, cfg.healthBar().style());
    }

    // ── 2. Explicit values parsed ──────────────────────────────────────────

    @Test
    void explicitTopLevelValuesParsed() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("hold_mode", "SNEAKING");
        map.put("stop_trigger", "on_sneak");
        map.put("max_duration_ticks", 200);
        map.put("max_simultaneous", 3);
        map.put("refresh_on_resummon", true);
        map.put("max_hitpoints", 80);
        map.put("hitpoints", 60);
        map.put("persist_hp_across_summons", false);
        map.put("barrier_type", "PHYSICAL");
        map.put("shape", "SPHERE");
        map.put("radius", 5.5);
        map.put("vertical_offset", 2.0);
        map.put("render_interval_ticks", 8);
        map.put("equip_check_interval_ticks", 40);
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(BarrierConfig.HoldMode.SNEAKING, cfg.holdMode());
        assertEquals("on_sneak", cfg.stopTrigger());
        assertEquals(200, cfg.maxDurationTicks());
        assertEquals(3, cfg.maxSimultaneous());
        assertTrue(cfg.refreshOnResummon());
        assertEquals(80, cfg.maxHitpoints());
        assertEquals(60, cfg.hitpoints());
        assertFalse(cfg.persistHpAcrossSummons());
        assertEquals(BarrierType.PHYSICAL, cfg.barrierType());
        assertEquals(BarrierConfig.Shape.SPHERE, cfg.shape());
        assertEquals(5.5, cfg.radius());
        assertEquals(2.0, cfg.verticalOffset());
        assertEquals(8, cfg.renderIntervalTicks());
        assertEquals(40, cfg.equipCheckIntervalTicks());
    }

    // ── 3. reset_on list parsed to enum set ────────────────────────────────

    @Test
    void resetOnListParsed() {
        Map<String, Object> map = Map.of("reset_on", List.of("SHATTER", "QUIT"));
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(Set.of(BarrierConfig.ResetOn.SHATTER, BarrierConfig.ResetOn.QUIT), cfg.resetOn());
    }

    @Test
    void resetOnListDefaultsWhenEmptyList() {
        Map<String, Object> map = Map.of("reset_on", List.of());
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(Set.of(
                BarrierConfig.ResetOn.SHATTER,
                BarrierConfig.ResetOn.DEATH,
                BarrierConfig.ResetOn.QUIT,
                BarrierConfig.ResetOn.UNEQUIP), cfg.resetOn());
    }

    // ── 4. Case-insensitive enums ──────────────────────────────────────────

    @Test
    void enumParsingCaseInsensitive() {
        Map<String, Object> map = Map.of(
                "hold_mode", "sneaking",
                "shape", "sphere",
                "barrier_type", "magic"
        );
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(BarrierConfig.HoldMode.SNEAKING, cfg.holdMode());
        assertEquals(BarrierConfig.Shape.SPHERE, cfg.shape());
        assertEquals(BarrierType.MAGIC, cfg.barrierType());
    }

    // ── 5. Bad enum falls back to default ──────────────────────────────────

    @Test
    void badEnumFallsBackToDefault() {
        Map<String, Object> map = Map.of(
                "hold_mode", "INVALID_MODE",
                "shape", "NOPE",
                "barrier_type", "NOT_A_TYPE"
        );
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(BarrierConfig.HoldMode.NONE, cfg.holdMode());
        assertEquals(BarrierConfig.Shape.CAP, cfg.shape());
        assertEquals(BarrierType.COMBINED, cfg.barrierType());
    }

    // ── 6. Nested section maps parsed ──────────────────────────────────────

    @Test
    void nestedAffinityScalingParsed() {
        Map<String, Object> map = Map.of(
                "affinity_scaling", Map.of("enabled", false, "element", "FIRE", "bonus_per_10_affinity", 3.0)
        );
        BarrierConfig.AffinityScaling a = BarrierConfig.fromConfig(map).affinityScaling();

        assertFalse(a.enabled());
        assertEquals(Element.FIRE, a.element());
        assertEquals(3.0, a.bonusPer10Affinity());
    }

    @Test
    void nestedRegenConfigParsed() {
        Map<String, Object> map = Map.of(
                "regen", Map.of("hp_per_second", 2.0, "mana_cost_per_hp", 5.0, "active_only", false, "partial_on_low_mana", false)
        );
        BarrierConfig.RegenConfig r = BarrierConfig.fromConfig(map).regen();

        assertEquals(2.0, r.hpPerSecond());
        assertEquals(5.0, r.manaCostPerHp());
        assertFalse(r.activeOnly());
        assertFalse(r.partialOnLowMana());
    }

    @Test
    void nestedHealthBarConfigParsed() {
        Map<String, Object> map = Map.of(
                "health_bar", Map.of("color", "BLUE", "style", "SEGMENTED_6", "visible_to", List.of("ALL"))
        );
        BarrierConfig.HealthBarConfig hb = BarrierConfig.fromConfig(map).healthBar();

        assertEquals(BarColor.BLUE, hb.color());
        assertEquals(BarStyle.SEGMENTED_6, hb.style());
        assertEquals(Set.of("ALL"), hb.visibleTo());
    }

    @Test
    void nestedCooldownConfigParsed() {
        Map<String, Object> map = Map.of(
                "cooldown", Map.of("on", "SHATTER", "seconds", 30.0, "deny_sound", "note.bass")
        );
        BarrierConfig.CooldownConfig cd = BarrierConfig.fromConfig(map).cooldown();

        assertEquals(BarrierConfig.CooldownConfig.CooldownMoment.SHATTER, cd.on());
        assertEquals(30.0, cd.seconds());
        assertEquals("note.bass", cd.denySound());
    }

    @Test
    void nestedSfxConfigParsed() {
        Map<String, Object> map = Map.of(
                "sfx", Map.of("on_hit", Map.of("particle", "flame", "count", 10, "size", 2.0, "sound", "hit"))
        );
        BarrierConfig.SfxConfig sfx = BarrierConfig.fromConfig(map).sfx();

        assertEquals("flame", sfx.onHit().particle());
        assertEquals(10, sfx.onHit().count());
        assertEquals(2.0, sfx.onHit().size());
        assertEquals("hit", sfx.onHit().sound());
        assertTrue(sfx.onShatter().none());
        assertTrue(sfx.onSummon().none());
        assertTrue(sfx.onEnd().none());
    }

    @Test
    void nestedParticlesConfigParsed() {
        Map<String, Object> map = Map.of(
                "particles", Map.of("type", "END_ROD", "color", "red", "count_per_render", 100)
        );
        BarrierConfig.ParticleConfig p = BarrierConfig.fromConfig(map).particles();

        assertEquals(Particle.END_ROD, p.type());
        assertEquals("red", p.color());
        assertEquals(100, p.countPerRender());
    }

    @Test
    void nestedPhysicalConfigParsed() {
        Map<String, Object> map = Map.of(
                "physical", Map.of("push_entities", true, "deflect_projectiles", true, "ignore_tags", List.of("tag_a", "tag_b"))
        );
        BarrierConfig.PhysicalConfig ph = BarrierConfig.fromConfig(map).physical();

        assertTrue(ph.pushEntities());
        assertTrue(ph.deflectProjectiles());
        assertEquals(List.of("tag_a", "tag_b"), ph.ignoreTags());
    }

    @Test
    void nestedCapAndFollowParsed() {
        Map<String, Object> map = Map.of(
                "cap", Map.of("yaw_arc_degrees", 90.0),
                "follow", Map.of("player", false, "pitch", true)
        );
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(90.0, cfg.cap().yawArcDegrees());
        assertEquals(-25, cfg.cap().pitchMinDegrees());
        assertFalse(cfg.follow().player());
        assertTrue(cfg.follow().pitch());
    }

    // ── 7. reset_on with bad entries skipped ───────────────────────────────

    @Test
    void resetOnSkipsInvalidEntries() {
        Map<String, Object> map = Map.of("reset_on", List.of("SHATTER", "INVALID", "DEATH"));
        BarrierConfig cfg = BarrierConfig.fromConfig(map);

        assertEquals(Set.of(BarrierConfig.ResetOn.SHATTER, BarrierConfig.ResetOn.DEATH), cfg.resetOn());
    }
}
