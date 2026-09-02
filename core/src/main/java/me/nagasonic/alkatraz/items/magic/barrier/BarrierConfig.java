package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record BarrierConfig(
        HoldMode holdMode, String stopTrigger, int maxDurationTicks, int maxSimultaneous,
        boolean refreshOnResummon, double maxHitpoints, double hitpoints,
        boolean persistHpAcrossSummons, Set<ResetOn> resetOn,
        AffinityScaling affinityScaling, BarrierType barrierType,
        HealthBarConfig healthBar, RegenConfig regen, Shape shape, double radius,
        double verticalOffset, CapConfig cap, FollowConfig follow, ParticleConfig particles,
        PhysicalConfig physical, CooldownConfig cooldown, SfxConfig sfx,
        int renderIntervalTicks, int equipCheckIntervalTicks) {

    public enum HoldMode { NONE, SNEAKING }
    public enum ResetOn { SHATTER, DEATH, QUIT, UNEQUIP }
    public enum Shape { SPHERE, CAP }

    public record AffinityScaling(boolean enabled, Element element, double bonusPer10Affinity) {}
    public record HealthBarConfig(boolean enabled, String titleLangKey, BarColor color, BarStyle style,
                                  Set<String> visibleTo, double viewRadius, int clearDelayTicks) {}
    public record RegenConfig(boolean enabled, double hpPerSecond, double manaCostPerHp,
                              boolean activeOnly, boolean partialOnLowMana) {}
    public record CapConfig(double yawArcDegrees, double pitchMinDegrees, double pitchMaxDegrees) {}
    public record FollowConfig(boolean player, boolean facing, boolean pitch) {}
    public record ParticleConfig(Particle type, String color, String color2, int countPerRender,
                                 double size, double collisionRadius, int componentLifeTicks) {}
    public record PhysicalConfig(boolean pushEntities, double pushStrength, List<String> ignoreTags,
                                 boolean deflectProjectiles, double deflectSpeedMultiplier) {}
    public record CooldownConfig(CooldownMoment on, double seconds, String denySound) {
        public enum CooldownMoment { NONE, SUMMON, SHATTER, DISPOSE }
    }
    public record SfxConfig(Fx onHit, Fx onShatter, Fx onSummon, Fx onEnd) {
        public record Fx(String particle, String color, int count, double size, String sound) {
            public boolean none() { return particle == null && sound == null; }
            public static Fx empty() { return new Fx(null, null, 0, 1.0, null); }
        }
    }

    public static BarrierConfig fromConfig(Map<String, Object> config) {
        Map<String, Object> c = config == null ? Map.of() : config;
        return new BarrierConfig(
                parseEnum(HoldMode.class, str(c, "hold_mode"), HoldMode.NONE),
                str(c, "stop_trigger"),
                intOf(c, "max_duration_ticks", 0),
                intOf(c, "max_simultaneous", 1),
                bool(c, "refresh_on_resummon", false),
                dbl(c, "max_hitpoints", 40),
                dbl(c, "hitpoints", 40),
                bool(c, "persist_hp_across_summons", true),
                resetOn(c),
                affinity(section(c, "affinity_scaling")),
                parseEnum(BarrierType.class, str(c, "barrier_type"), BarrierType.COMBINED),
                healthBar(section(c, "health_bar")),
                regen(section(c, "regen")),
                parseEnum(Shape.class, str(c, "shape"), Shape.CAP),
                dbl(c, "radius", 3.0),
                dbl(c, "vertical_offset", 1.0),
                cap(section(c, "cap")),
                follow(section(c, "follow")),
                particles(section(c, "particles")),
                physical(section(c, "physical")),
                cooldown(section(c, "cooldown")),
                sfx(section(c, "sfx")),
                intOf(c, "render_interval_ticks", 4),
                intOf(c, "equip_check_interval_ticks", 20));
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static double dbl(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static int intOf(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(String.valueOf(v));
        return def;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> out = new HashMap<>();
            map.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        if (v instanceof org.bukkit.configuration.ConfigurationSection cs) {
            Map<String, Object> out = new HashMap<>();
            for (String k : cs.getKeys(false)) out.put(k, cs.get(k));
            return out;
        }
        return Map.of();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E def) {
        if (raw == null || raw.isBlank()) return def;
        try { return Enum.valueOf(type, raw.toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }

    private static Set<ResetOn> resetOn(Map<String, Object> c) {
        Object raw = c.get("reset_on");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Set.of(ResetOn.SHATTER, ResetOn.DEATH, ResetOn.QUIT, ResetOn.UNEQUIP);
        }
        Set<ResetOn> res = new HashSet<>();
        for (Object o : list) {
            ResetOn r = parseEnum(ResetOn.class, String.valueOf(o), null);
            if (r != null) res.add(r);
        }
        return res.isEmpty() ? Set.of() : res;
    }

    private static AffinityScaling affinity(Map<String, Object> m) {
        return new AffinityScaling(
                bool(m, "enabled", true),
                parseEnum(Element.class, str(m, "element"), Element.LIGHT),
                dbl(m, "bonus_per_10_affinity", 1));
    }

    private static HealthBarConfig healthBar(Map<String, Object> m) {
        Set<String> visible = m.get("visible_to") instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.toSet())
                : Set.of("CASTER");
        return new HealthBarConfig(
                bool(m, "enabled", true),
                str(m, "title_lang_key"),
                parseEnum(BarColor.class, str(m, "color"), BarColor.PINK),
                parseEnum(BarStyle.class, str(m, "style"), BarStyle.SOLID),
                visible,
                dbl(m, "view_radius", 15),
                intOf(m, "clear_delay_ticks", 20));
    }

    private static RegenConfig regen(Map<String, Object> m) {
        return new RegenConfig(
                bool(m, "enabled", true),
                dbl(m, "hp_per_second", 0.75),
                dbl(m, "mana_cost_per_hp", 2.0),
                bool(m, "active_only", true),
                bool(m, "partial_on_low_mana", true));
    }

    private static CapConfig cap(Map<String, Object> m) {
        return new CapConfig(
                dbl(m, "yaw_arc_degrees", 110),
                dbl(m, "pitch_min_degrees", -25),
                dbl(m, "pitch_max_degrees", 25));
    }

    private static FollowConfig follow(Map<String, Object> m) {
        return new FollowConfig(
                bool(m, "player", true),
                bool(m, "facing", true),
                bool(m, "pitch", false));
    }

    private static ParticleConfig particles(Map<String, Object> m) {
        return new ParticleConfig(
                parseParticleType(str(m, "type"), Particle.FLAME),
                str(m, "color"),
                str(m, "color2"),
                intOf(m, "count_per_render", 60),
                dbl(m, "size", 1.0),
                dbl(m, "collision_radius", 0.6),
                intOf(m, "component_lifetime_ticks", 8));
    }

    private static Particle parseParticleType(String raw, Particle def) {
        if (raw == null || raw.isBlank()) return def;
        String upper = raw.trim().toUpperCase();
        if (upper.equals("DUST") || upper.equals("REDSTONE")) {
            return me.nagasonic.alkatraz.util.Utils.DUST;
        }
        try { return Particle.valueOf(upper); } catch (IllegalArgumentException e) { return def; }
    }

    private static PhysicalConfig physical(Map<String, Object> m) {
        List<String> tags = m.get("ignore_tags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("summoned_zombie");
        return new PhysicalConfig(
                bool(m, "push_entities", false),
                dbl(m, "push_strength", 0.3),
                tags,
                bool(m, "deflect_projectiles", false),
                dbl(m, "deflect_speed_multiplier", 1.2));
    }

    private static CooldownConfig cooldown(Map<String, Object> m) {
        String raw = str(m, "on");
        return new CooldownConfig(
                parseEnum(CooldownConfig.CooldownMoment.class, raw, CooldownConfig.CooldownMoment.NONE),
                dbl(m, "seconds", 90),
                str(m, "deny_sound"));
    }

    private static SfxConfig sfx(Map<String, Object> m) {
        return new SfxConfig(fx(m, "on_hit"), fx(m, "on_shatter"), fx(m, "on_summon"), fx(m, "on_end"));
    }

    private static SfxConfig.Fx fx(Map<String, Object> m, String key) {
        Map<String, Object> s = section(m, key);
        if (s.isEmpty()) return SfxConfig.Fx.empty();
        return new SfxConfig.Fx(str(s, "particle"), str(s, "color"),
                intOf(s, "count", 0), dbl(s, "size", 1.0), str(s, "sound"));
    }
}
