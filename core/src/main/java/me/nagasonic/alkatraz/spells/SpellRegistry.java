package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.implementation.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class SpellRegistry {
    /** Immutable snapshot of all four spell maps, published with a single volatile write. */
    private static final class SpellMaps {
        final Map<Class<?>, Spell> allSpells;
        final Map<String, Spell> allSpellsByID;
        final Map<String, Spell> allSpellsByIdFull;
        final Map<String, Spell> allSpellsByCode;

        SpellMaps(Map<Class<?>, Spell> allSpells, Map<String, Spell> allSpellsByID,
                  Map<String, Spell> allSpellsByIdFull, Map<String, Spell> allSpellsByCode) {
            this.allSpells = allSpells;
            this.allSpellsByID = allSpellsByID;
            this.allSpellsByIdFull = allSpellsByIdFull;
            this.allSpellsByCode = allSpellsByCode;
        }
    }

    private static volatile SpellMaps maps = new SpellMaps(
            Collections.unmodifiableMap(new HashMap<>()),
            Collections.unmodifiableMap(new HashMap<>()),
            Collections.unmodifiableMap(new HashMap<>()),
            Collections.unmodifiableMap(new HashMap<>()));

    private static final java.util.concurrent.atomic.AtomicInteger registeredCount = new java.util.concurrent.atomic.AtomicInteger(0);
private static final List<me.nagasonic.alkatraz.api.spells.Spell> REGISTERED_API_SPELLS = new CopyOnWriteArrayList<>();


    /**
     * id -> constructor. This is the SINGLE authored mapping from a spell id to the
     * class that serves it. registerSpells() iterates {@link #SPELL_IDS}, which is
     * derived from this map's key order, so an id can never be registered without a
     * class behind it and a class can never be reachable without an id.
     *
     * <p>Two spell ids deliberately do not follow the PascalCase-of-the-id rule:
     * {@code earthsplitter} is served by {@link Fissure}, and {@code magic_missile}
     * by {@link MagicMissile} whose getSpellBook() returns null (documented at
     * english.lang). Everything else is PascalCase of the id with underscores
     * removed, and the type token is the id uppercased.
     */
    private static final Map<String, Supplier<Spell>> SPELL_FACTORY = new LinkedHashMap<>();

    /**
     * Authors one entry into {@link #SPELL_FACTORY}.
     *
     * <p><b>The name and argument order are load-bearing and must not be
     * "cleaned up".</b> Wave 4's {@code SpellRegistryParityTest} does not call
     * {@link #registerSpells()} - constructing 67 spells needs a live PluginManager -
     * so it discovers the registry roster by scanning this file as text for the
     * pattern {@code registerIfEnabled("(<id>)"}. Renaming this method, or moving
     * these calls out of this file, silently empties that roster and fails
     * {@code theThreeListsHaveTheSameSize}. Java resolves the
     * overload against the second parameter type ({@code Supplier<Spell>} here).
     */
    private static void registerIfEnabled(String id, Supplier<Spell> factory) {
        SPELL_FACTORY.put(id, factory);
    }

    static {
        registerIfEnabled("air_blades", () -> new AirBlades("AIR_BLADES"));
        registerIfEnabled("air_burst", () -> new AirBurst("AIR_BURST"));
        registerIfEnabled("barrier", () -> new Barrier("BARRIER"));
        registerIfEnabled("blink", () -> new Blink("BLINK"));
        registerIfEnabled("buff", () -> new Buff("BUFF"));
        registerIfEnabled("debuff", () -> new Debuff("DEBUFF"));
        registerIfEnabled("dark_tendrils", () -> new DarkTendrils("DARK_TENDRILS"));
        registerIfEnabled("earthen_wall", () -> new EarthenWall("EARTHEN_WALL"));
        registerIfEnabled("detect", () -> new Detect("DETECT"));
        registerIfEnabled("disguise", () -> new Disguise("DISGUISE"));
        registerIfEnabled("dispel", () -> new Dispel("DISPEL"));
        registerIfEnabled("earth_spike", () -> new EarthSpike("EARTH_SPIKE"));
        registerIfEnabled("earth_throw", () -> new EarthThrow("EARTH_THROW"));
        registerIfEnabled("fireball", () -> new Fireball("FIREBALL"));
        registerIfEnabled("fire_blast", () -> new FireBlast("FIRE_BLAST"));
        registerIfEnabled("fire_wall", () -> new FireWall("FIRE_WALL"));
        registerIfEnabled("flaming_volley", () -> new FlamingVolley("FLAMING_VOLLEY"));
        registerIfEnabled("geyser", () -> new Geyser("GEYSER"));
        registerIfEnabled("heal", () -> new Heal("HEAL"));
        registerIfEnabled("lesser_heal", () -> new LesserHeal("LESSER_HEAL"));
        registerIfEnabled("light_buff", () -> new LightBuff("LIGHT_BUFF"));
        registerIfEnabled("magic_missile", () -> new MagicMissile("MAGIC_MISSILE"));
        registerIfEnabled("stealth", () -> new Stealth("STEALTH"));
        registerIfEnabled("summon_zombies", () -> new SummonZombies("SUMMON_ZOMBIES"));
        registerIfEnabled("skeletal_knights", () -> new SkeletalKnights("SKELETAL_KNIGHTS"));
        registerIfEnabled("swift", () -> new Swift("SWIFT"));
        registerIfEnabled("tremor", () -> new Tremor("TREMOR"));
        registerIfEnabled("whirlpool", () -> new Whirlpool("WHIRLPOOL"));
        registerIfEnabled("water_pulse", () -> new WaterPulse("WATER_PULSE"));
        registerIfEnabled("water_sphere", () -> new WaterSphere("WATER_SPHERE"));
        registerIfEnabled("wind_barrier", () -> new WindBarrier("WIND_BARRIER"));
        registerIfEnabled("wind_vortex", () -> new WindVortex("WIND_VORTEX"));
        registerIfEnabled("meteor_shower", () -> new MeteorShower("METEOR_SHOWER"));
        registerIfEnabled("tsunami", () -> new Tsunami("TSUNAMI"));
        registerIfEnabled("earthsplitter", () -> new Fissure("EARTHSPLITTER"));
        registerIfEnabled("tornado", () -> new Tornado("TORNADO"));
        registerIfEnabled("radiance", () -> new Radiance("RADIANCE"));
        registerIfEnabled("shadow_realm", () -> new ShadowRealm("SHADOW_REALM"));
        registerIfEnabled("warp", () -> new Warp("WARP"));
        registerIfEnabled("flame_lance", () -> new FlameLance("FLAME_LANCE"));
        registerIfEnabled("inferno_pilgrimage", () -> new InfernoPilgrimage("INFERNO_PILGRIMAGE"));
        registerIfEnabled("sunfire_barrage", () -> new SunfireBarrage("SUNFIRE_BARRAGE"));
        registerIfEnabled("inferno_rite", () -> new InfernoRite("INFERNO_RITE"));
        registerIfEnabled("undertow", () -> new Undertow("UNDERTOW"));
        registerIfEnabled("everdrain", () -> new Everdrain("EVERDRAIN"));
        registerIfEnabled("thousand_waves", () -> new ThousandWaves("THOUSAND_WAVES"));
        registerIfEnabled("tide_of_the_deep", () -> new TideOfTheDeep("TIDE_OF_THE_DEEP"));
        registerIfEnabled("tectonic_strike", () -> new TectonicStrike("TECTONIC_STRIKE"));
        registerIfEnabled("fault_line", () -> new FaultLine("FAULT_LINE"));
        registerIfEnabled("continental_shift", () -> new ContinentalShift("CONTINENTAL_SHIFT"));
        registerIfEnabled("worldbreaker", () -> new Worldbreaker("WORLDBREAKER"));
        registerIfEnabled("updraft", () -> new Updraft("UPDRAFT"));
        registerIfEnabled("thunderclap", () -> new Thunderclap("THUNDERCLAP"));
        registerIfEnabled("cyclone_nest", () -> new CycloneNest("CYCLONE_NEST"));
        registerIfEnabled("stormcaller", () -> new Stormcaller("STORMCALLER"));
        registerIfEnabled("skywrath", () -> new Skywrath("SKYWRATH"));
        registerIfEnabled("light_bolt", () -> new LightBolt("LIGHT_BOLT"));
        registerIfEnabled("radiant_brand", () -> new RadiantBrand("RADIANT_BRAND"));
        registerIfEnabled("choir_of_light", () -> new ChoirOfLight("CHOIR_OF_LIGHT"));
        registerIfEnabled("apotheosis", () -> new Apotheosis("APOTHEOSIS"));
        registerIfEnabled("crown_of_dawn", () -> new CrownOfDawn("CROWN_OF_DAWN"));
        registerIfEnabled("umbral_volley", () -> new UmbralVolley("UMBRAL_VOLLEY"));
        registerIfEnabled("void_anchor", () -> new VoidAnchor("VOID_ANCHOR"));
        registerIfEnabled("oblivion", () -> new Oblivion("OBLIVION"));
        registerIfEnabled("arcane_ward", () -> new ArcaneWard("ARCANE_WARD"));
        registerIfEnabled("waystone_recall", () -> new WaystoneRecall("WAYSTONE_RECALL"));
        registerIfEnabled("echo_cast", () -> new EchoCast("ECHO_CAST"));
        registerIfEnabled("arcane_resonance", () -> new ArcaneResonance("ARCANE_RESONANCE"));
    }

    /**
     * Every bundled spell id, in registration order.
     *
     * <p><b>This declaration MUST stay below the static block above.</b> Java runs
     * field initialisers and static blocks in source order, so a SPELL_IDS declared
     * above it would take {@code SPELL_FACTORY.keySet()} while the map is still
     * empty and freeze an empty list. {@link #registerSpells()} would then loop zero
     * times and log "Registered 0 spells." with every spell in the plugin silently
     * dead. Nothing in the test suite catches that: {@code SpellRegistryParityTest}
     * reads this file as text and never executes it.
     *
     * <p>Derived from SPELL_FACTORY rather than authored twice. The id strings are
     * ALSO duplicated, as literals, in {@code Alkatraz.spellConfigPaths()} - that
     * duplication is deliberate and is what wave 4's SpellRegistryParityTest keeps
     * honest. Do not "de-duplicate" it by deriving this list from Alkatraz: that
     * empties the test's saved-config roster and fails the wave-7 gate.
     */
    public static final List<String> SPELL_IDS = List.copyOf(SPELL_FACTORY.keySet());

    public static void registerSpells() {
        registeredCount.set(0);
        for (String id : SPELL_IDS) {
            Supplier<Spell> factory = SPELL_FACTORY.get(id);
            if (factory == null) {
                Alkatraz.logWarning("SpellRegistry: '" + id
                        + "' is in SPELL_IDS with no factory entry and was not registered.");
                continue;
            }
            YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + id + ".yml").get();
            if (!spellConfig.getBoolean("enabled")) continue;
            registerSpell(factory.get());
            registeredCount.incrementAndGet();
        }
        Alkatraz.logInfo("Registered " + registeredCount.get() + " spells.");
    }

    public static Map<Class<?>, Spell> getAllSpells() {
        return maps.allSpells;
    }

    public static Map<String, Spell> getAllSpellsByID() {
        return maps.allSpellsByID;
    }

    public static <T extends Spell> Spell getSpell(Class<T> spell){
        Map<Class<?>, Spell> all = maps.allSpells;
        if (!all.containsKey(spell)) throw new IllegalArgumentException("Spell " + spell.getSimpleName() + " was not registered for usage");
        return all.get(spell);
    }

    public static Spell getSpellByCode(String code){
        if (code == null || code.trim().isEmpty()) return null;
        return maps.allSpellsByCode.get(code.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static Spell getSpell(String id){
        if (id == null || id.isEmpty()) return null;
        return maps.allSpellsByID.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public static Spell getSpellFromName(String name) {
        for (Spell spell : maps.allSpells.values()){
            if (Objects.equals(spell.getId(), name)){
                return spell;
            }
        }
        return null;
    }

    public static void registerSpell(Spell spell){
        long start = System.nanoTime();
        spell.loadConfiguration();
        if (!validateSpellCode(spell, maps.allSpellsByIdFull.values())) {
            return;
        }
        // Build the replacement set first, then swap all four maps in one pass so
        // concurrent readers cannot observe a spell present in some maps but not others.
        Map<Class<?>, Spell> spells = new HashMap<>(maps.allSpells);
        spells.put(spell.getClass(), spell);
        Map<String, Spell> byId = new HashMap<>(maps.allSpellsByID);
        byId.put(spell.getId().toLowerCase(java.util.Locale.ROOT), spell);
        Map<String, Spell> byIdFull = new HashMap<>(maps.allSpellsByIdFull);
        byIdFull.put(spell.getId().toLowerCase(java.util.Locale.ROOT), spell);
        Map<String, Spell> byCode = new HashMap<>(maps.allSpellsByCode);
        if (spell.getCode() != null && !spell.getCode().trim().isEmpty()) {
            byCode.put(spell.getCode().trim().toUpperCase(java.util.Locale.ROOT), spell);
        }
        maps = new SpellMaps(Collections.unmodifiableMap(spells), Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byIdFull), Collections.unmodifiableMap(byCode));
        Alkatraz.logHigh("Registered spell: " + spell.getId() + " (" + spell.getElement() + ") in "
                + (System.nanoTime() - start) / 1_000_000 + "ms");
    }

    public static boolean isRegistered(Class<? extends Spell> spell){
        return maps.allSpells.containsKey(spell);
    }

    public static void registerApiSpell(me.nagasonic.alkatraz.api.spells.Spell apiSpell) {
        if (REGISTERED_API_SPELLS.contains(apiSpell)) {
            Alkatraz.logWarning("API spell already registered: " + apiSpell.getId());
            return;
        }
        ApiSpellAdapter adapter = new ApiSpellAdapter(apiSpell);
        adapter.loadConfiguration();
        if (!validateSpellCode(adapter, maps.allSpellsByIdFull.values())) {
            return;
        }
        String id = adapter.getId().toLowerCase(java.util.Locale.ROOT);
        // Build all affected maps first, then swap them together so concurrent readers
        // cannot observe the spell in one lookup map but not another.
        Map<String, Spell> combinedFull = new HashMap<>(maps.allSpellsByIdFull);
        combinedFull.put(id, adapter);
        Map<String, Spell> byId = new HashMap<>(maps.allSpellsByID);
        byId.put(id, adapter);
        Map<String, Spell> byCode = new HashMap<>(maps.allSpellsByCode);
        if (adapter.getCode() != null && !adapter.getCode().trim().isEmpty()) {
            byCode.put(adapter.getCode().trim().toUpperCase(java.util.Locale.ROOT), adapter);
        }
        maps = new SpellMaps(maps.allSpells, Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(combinedFull), Collections.unmodifiableMap(byCode));
        REGISTERED_API_SPELLS.add(apiSpell);
        Alkatraz.logHigh("Registered API spell: " + adapter.getId());
    }


    public static Map<String, Spell> getAllSpellsByIdFull() {
        return maps.allSpellsByIdFull;
    }

    private static boolean validateSpellCode(Spell spell, java.util.Collection<Spell> existingSpells) {
        String code = spell.getCode();
        if (code == null || code.trim().isEmpty()) return true;
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() != 5 || !normalized.matches("[RLS]+")) {
            Alkatraz.logSevere("Spell '" + spell.getId() + "' has invalid click code '" + code + "' (expected 5 R/L/S chars).");
            return false;
        }
        boolean collision = false;
        for (Spell existing : existingSpells) {
            if (existing == spell) continue;
            String other = existing.getCode();
            if (other == null || other.trim().isEmpty()) continue;
            String otherNormalized = other.trim().toUpperCase(java.util.Locale.ROOT);
            if (otherNormalized.equals(normalized)) {
                Alkatraz.logSevere("Spell code collision: '" + spell.getId() + "' shares click code " + code
                        + " with '" + existing.getId() + "'.");
                collision = true;
            } else if (otherNormalized.startsWith(normalized) || normalized.startsWith(otherNormalized)) {
                Alkatraz.logSevere("Spell code prefix conflict: '" + spell.getId() + "' code " + code
                        + " is a prefix of '" + existing.getId() + "' code " + other + ".");
                collision = true;
            }
        }
        return !collision;
    }
    /**
     * Adds a single spell to the supplied accumulator maps (used by reload() to
     * build a complete replacement set before swapping the volatile fields).
     */
    private static boolean registerSpell(Spell spell,
            Map<Class<?>, Spell> spells,
            Map<String, Spell> byId,
            Map<String, Spell> byIdFull,
            Map<String, Spell> byCode) {
        spell.loadConfiguration();
        if (!validateSpellCode(spell, byIdFull.values())) return false;
        spells.put(spell.getClass(), spell);
        byId.put(spell.getId().toLowerCase(java.util.Locale.ROOT), spell);
        byIdFull.put(spell.getId().toLowerCase(java.util.Locale.ROOT), spell);
        if (spell.getCode() != null && !spell.getCode().trim().isEmpty()) {
            byCode.put(spell.getCode().trim().toUpperCase(java.util.Locale.ROOT), spell);
        }
        return true;
    }

    /**
     * Registers an API spell into the supplied accumulator maps (reload variant).
     */
    private static void registerApiSpell(me.nagasonic.alkatraz.api.spells.Spell apiSpell,
            Map<Class<?>, Spell> spells,
            Map<String, Spell> byId,
            Map<String, Spell> byIdFull,
            Map<String, Spell> byCode) {
        ApiSpellAdapter adapter = new ApiSpellAdapter(apiSpell);
        adapter.loadConfiguration();
        if (!validateSpellCode(adapter, byIdFull.values())) return;
        String id = adapter.getId().toLowerCase(java.util.Locale.ROOT);
        byIdFull.put(id, adapter);
        byId.put(id, adapter);
        if (adapter.getCode() != null && !adapter.getCode().trim().isEmpty()) {
            byCode.put(adapter.getCode().trim().toUpperCase(java.util.Locale.ROOT), adapter);
        }
        REGISTERED_API_SPELLS.add(apiSpell);
        Alkatraz.logHigh("Registered API spell: " + adapter.getId());
    }

    public static void reload() {
        // Stop any long-lived tasks owned by the outgoing spell instances before
        // they are replaced, so detached upkeep loops (Stealth, ...) cannot keep
        // charging mana/altering players across reloads.
        for (Spell old : new java.util.ArrayList<>(maps.allSpellsByIdFull.values())) {
            try { old.cancelTasks(); } catch (Exception ignored) { }
            if (old instanceof Listener) {
                HandlerList.unregisterAll((Listener) old);
            }
        }
        java.util.List<me.nagasonic.alkatraz.api.spells.Spell> apiSpells =
                new java.util.ArrayList<>(REGISTERED_API_SPELLS);

        // Drop stale mastery boss bars before replacing spell instances so the
        // old bars (and their cancel tasks) cannot outlive the spells that
        // created them.
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile mp =
                    me.nagasonic.alkatraz.playerdata.profiles.ProfileManager.getProfile(p, me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile.class);
            if (mp == null) continue;
            for (org.bukkit.boss.BossBar bar : new java.util.ArrayList<>(mp.getMasteryBars().values())) {
                try { bar.removeAll(); } catch (Exception ignored) { }
            }
            for (int taskId : mp.getMasteryBarTaskIds().values()) {
                try { org.bukkit.Bukkit.getScheduler().cancelTask(taskId); } catch (Exception ignored) { }
            }
            mp.getMasteryBars().clear();
            mp.getMasteryBarTaskIds().clear();
            mp.clearAllSpellModifiers();
        }

        // Build the complete replacement maps first, so readers never observe a
        // half-populated registry during reload. Swap all four in one pass.
        Map<Class<?>, Spell> newAllSpells = new HashMap<>();
        Map<String, Spell> newById = new HashMap<>();
        Map<String, Spell> newByIdFull = new HashMap<>();
        Map<String, Spell> newByCode = new HashMap<>();

        REGISTERED_API_SPELLS.clear();
        registeredCount.set(0);
        for (String id : SPELL_IDS) {
            java.util.function.Supplier<Spell> factory = SPELL_FACTORY.get(id);
            if (factory == null) {
                Alkatraz.logWarning("SpellRegistry: '" + id
                        + "' is in SPELL_IDS with no factory entry and was not registered.");
                continue;
            }
            org.bukkit.configuration.file.YamlConfiguration spellConfig =
                    me.nagasonic.alkatraz.config.ConfigManager.getConfig("spells/" + id + ".yml").get();
            if (!spellConfig.getBoolean("enabled")) continue;
            Spell spell = factory.get();
            if (registerSpell(spell, newAllSpells, newById, newByIdFull, newByCode)) {
                registeredCount.incrementAndGet();
            }
        }
        for (me.nagasonic.alkatraz.api.spells.Spell apiSpell : apiSpells) {
            registerApiSpell(apiSpell, newAllSpells, newById, newByIdFull, newByCode);
        }

        maps = new SpellMaps(Collections.unmodifiableMap(newAllSpells),
                Collections.unmodifiableMap(newById),
                Collections.unmodifiableMap(newByIdFull),
                Collections.unmodifiableMap(newByCode));
        Alkatraz.logInfo("Registered " + registeredCount.get() + " spells.");
    }
}
